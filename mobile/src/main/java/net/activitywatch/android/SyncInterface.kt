package net.activitywatch.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SyncInterface"

/** Syncthing's marker for a file two devices wrote. Per R20 we should never produce one, and
 *  importing one would feed a second copy of a peer's database to the pull. */
private const val SYNC_CONFLICT_MARKER = ".sync-conflict-"

/** Suffix of a peer file being copied in. It is not `.db`, so the Rust side's extension filter
 *  ignores it if a process death ever leaves one behind. */
private const val IMPORT_TMP_SUFFIX = ".aw-import-tmp"

class SyncInterface(context: Context) {

    companion object {
        // Shared across all SyncInterface instances (both Handler chain and AlarmManager path)
        // to prevent concurrent syncBoth() calls from different entry points.
        private val syncInFlight = AtomicBoolean(false)
    }
    private val appContext: Context = context.applicationContext
    private val syncDir: String

    /**
     * Holds the active executor so that [cancel] can interrupt it from any thread.
     * Written once per sync operation (guarded by [syncInFlight]) and read by [cancel].
     */
    @Volatile private var activeExecutor: ExecutorService? = null

    /**
     * Set by [cancel] to stop the SAF copy loop between file iterations without relying solely
     * on thread interruption.  Checked in [copySyncFilesToSafDir] before each file write so that
     * files whose truncate-and-write has not yet started are skipped, limiting the corruption
     * window to at most the file currently being written when cancellation is requested.
     */
    @Volatile private var cancelRequested = false

    /** Cached result of [resolveDeviceId]; reading it costs an HTTP round-trip to the server. */
    @Volatile private var cachedDeviceId: String? = null

    init {
        syncDir = resolveSyncDirectory(context).absolutePath
        Os.setenv("AW_SYNC_DIR", syncDir, true)

        // Set XDG environment variables to app-writable paths
        // This is required for aw-client-rust (used by aw-sync) to create lock files
        val cacheDir = context.cacheDir.absolutePath
        val filesDir = context.filesDir.absolutePath

        Os.setenv("XDG_CACHE_HOME", cacheDir, true)
        Os.setenv("XDG_CONFIG_HOME", "$filesDir/config", true)
        Os.setenv("XDG_DATA_HOME", "$filesDir/data", true)

        System.loadLibrary("aw_sync")

        // Upstream #249 calls `setDataDir(filesDir)` here. Deliberately NOT taken: it requires a
        // `Java_..._SyncInterface_setDataDir` export this fork's `android.rs` does not have, so
        // the constructor would throw UnsatisfiedLinkError, SyncScheduler would disable itself,
        // and no sync would run at all -- Blocker 6 verbatim (03_SYNC.md 2.6). This fork reaches
        // the same config.toml through XDG_DATA_HOME above, which needs no new JNI symbol
        // (03_SYNC.md 2.5). Re-check this on every upstream merge.

        // Initialize logging for aw-sync native library after loading
        awSyncInitLogging(2) // Info level
        Log.i(TAG, "aw-sync initialized with sync dir: $syncDir")
    }

    private fun resolveSyncDirectory(context: Context): File {
        val preferredDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "sync")
        if (preferredDir.exists() || preferredDir.mkdirs()) {
            return preferredDir
        }

        val fallbackDir = File(context.filesDir, "sync")
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            Log.e(TAG, "Failed to create sync directory: ${fallbackDir.absolutePath}")
        }
        return fallbackDir
    }

    // Native JNI functions - Phase 1 multi-device sync
    // (upstream's `setDataDir` is deliberately absent -- see the init block above)
    private external fun syncPullAll(port: Int, hostname: String): String
    private external fun syncPull(port: Int, hostname: String): String
    private external fun syncPush(port: Int, hostname: String): String

    // New per-device push function (Phase 1)
    private external fun syncPushWithDeviceId(port: Int, hostname: String, deviceId: String): String

    // New pull-from-all-hostnames function (Phase 1)
    private external fun syncPullAllFromAllHostnames(port: Int): String

    // This device's identity, read from the running server rather than minted locally.
    // Returns {"success": true, "device_id": ..., "hostname": ...}.
    private external fun getDeviceId(port: Int): String

    external fun getSyncDir(): String

    // Initialize logging for aw-sync native library
    private external fun awSyncInitLogging(verbosity: Int)

    /**
     * This device's identity, as the embedded server sees it, or null if the server has not
     * answered yet.
     *
     * `aw-server` mints a persisted UUID v4 on first run and `setup_local_remote` names this
     * device's directory in the shared sync folder from that value -- so it is the identity the
     * on-disk sync layout already commits to. Reading it here keeps Kotlin and Rust on a single
     * identity rather than two that would have to be kept in correspondence forever.
     *
     * The previous implementation hashed the installer package name, which is null for every
     * sideloaded install, and then fell back to `Build.FINGERPRINT` -- which identifies a *build*,
     * not a device. Two same-model phones on the same OS version produced the same id.
     *
     * Null means "not known yet", never "mint a new one": a second identity for this device would
     * split its history across two directories in the shared folder.
     */
    private fun resolveDeviceId(): String? {
        cachedDeviceId?.let { return it }
        return try {
            val json = JSONObject(getDeviceId(5600))
            if (json.getBoolean("success")) {
                json.getString("device_id").also {
                    cachedDeviceId = it
                    Log.i(TAG, "Device id: $it")
                }
            } else {
                Log.w(TAG, "Could not read device id: ${json.optString("error")}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read device id: ${e.message}")
            null
        }
    }

    // Hostname normalisation now lives in DeviceHostname.kt, shared with MainActivity's
    // Activity-view route (upstream #250). It is byte-for-byte equivalent to the local
    // implementation it replaces -- same DEVICE_NAME lookup, same Build.DEVICE fallback, same
    // lowercase/collapse/trim -- so no device's directory name in the shared folder changes.
    private fun getDeviceName(): String = deviceHostname(appContext)

    /**
     * What happened to one file during a SAF transfer.
     *
     * Three states, because two of them used to be one: [importFile] returned `false` for *both*
     * "already current" and "the copy failed", and both incremented the same `skipped` counter.
     * A pass where every file failed and a pass with nothing to do produced the same log line and
     * the same `success=true`. Splitting them is the fix; the rest of this file's result plumbing
     * follows from it.
     */
    private enum class FileOutcome { COPIED, SKIPPED, FAILED }

    /**
     * Tally of one SAF pass (import or export).
     *
     * [skipped] and [failed] are deliberately separate. Skipping is the common, healthy case --
     * an unchanged database, or an entry name policy rejects ([isSafeEntryName]). A failure means
     * the user's data did not move, and a sync that reports success anyway is lying about the one
     * thing the user relies on it for.
     */
    private class TransferResult {
        var copied = 0
        var skipped = 0
        var failed = 0
        var peers = 0
        private var firstError: String? = null

        /** Record a failure, keeping the *first* reason -- it is the one closest to the cause. */
        fun fail(reason: String) {
            failed++
            if (firstError == null) firstError = reason
        }

        val ok: Boolean get() = failed == 0

        /** A short reason fit to show the user, noting how many later failures were folded in. */
        fun reason(): String {
            val first = firstError ?: return "$failed file(s) failed"
            return if (failed > 1) "$first (+${failed - 1} more)" else first
        }

        override fun toString() = "copied=$copied skipped=$skipped failed=$failed"
    }

    /** Outcome of one whole sync cycle, as handed to the caller's callback. */
    private class SyncOutcome(val success: Boolean, val message: String)

    /** Read the `{"success": ..., "message"|"error": ...}` envelope the JNI layer returns. */
    private fun nativeOutcome(response: String): SyncOutcome {
        val json = JSONObject(response)
        return if (json.getBoolean("success")) {
            SyncOutcome(true, json.getString("message"))
        } else {
            SyncOutcome(false, json.getString("error"))
        }
    }

    // Async wrapper for syncPullAll
    fun syncPullAllAsync(callback: (Boolean, String) -> Unit) {
        val hostname = getDeviceName()
        performSyncAsync("Pull All", callback) {
            nativeOutcome(syncPullAll(5600, hostname))
        }
    }

    // Async wrapper for Push with per-device staging (Phase 1)
    fun syncPushWithDeviceIdAsync(callback: (Boolean, String) -> Unit) {
        val hostname = getDeviceName()
        val deviceId = resolveDeviceId() ?: "unknown"
        performSyncAsync("Push (device-specific)", callback) {
            nativeOutcome(syncPushWithDeviceId(5600, hostname, deviceId))
        }
    }

    // Async wrapper for pull from ALL hostnames (Phase 1)
    fun syncPullAllFromAllHostnamesAsync(callback: (Boolean, String) -> Unit) {
        performSyncAsync("Pull All Hostnames", callback) {
            nativeOutcome(syncPullAllFromAllHostnames(5600))
        }
    }

    /**
     * Run one full multi-device cycle: import peers, pull, push, export self.
     *
     * There used to be two entry points here, differing only in whether the SAF export ran before
     * or after the callback -- background workers needed it before, so they stayed alive long
     * enough to finish copying. That split is gone, and the export now always finishes first,
     * because an export that runs *after* the caller has been told "success" can never correct
     * that answer. The cost is the few hundred milliseconds a caller waits for a true one.
     */
    fun syncBothMultiDeviceAsync(callback: (Boolean, String) -> Unit) {
        if (!syncInFlight.compareAndSet(false, true)) {
            Log.i(TAG, "Sync already in flight; skipping concurrent call")
            callback(false, "skipped: sync already in flight")
            return
        }

        val hostname = getDeviceName()
        val deviceId = resolveDeviceId() ?: "unknown"

        performSyncAsync(
            "Multi-Device Sync",
            { success, message ->
                syncInFlight.set(false)
                callback(success, message)
            }
        ) {
            // SAF failures are collected rather than thrown. A failed import still leaves the
            // native sync worth running -- peer databases imported by an earlier cycle are still
            // on disk and still readable -- so the cycle finishes and reports everything it hit.
            // Native failures are different: they end the cycle, because nothing after them can
            // succeed.
            val problems = mutableListOf<String>()

            // Step 2 of the cycle in 03_SYNC.md §3.2: bring every *other* device's database into
            // app-private storage before the pull. The engine only ever scans AW_SYNC_DIR, so
            // without this pass it reads a directory containing nothing but this device's own
            // output -- Blocker 1, the reason cross-device sync never worked (R21).
            val imported = importPeerFiles()
            if (!imported.ok) problems += "import failed: ${imported.reason()}"

            // Phase 1: Pull from ALL hostnames (not just our own)
            val pull = nativeOutcome(syncPullAllFromAllHostnames(5600))
            if (!pull.success) return@performSyncAsync pull

            // Phase 1: Push our local data to a per-device staging area
            val push = nativeOutcome(syncPushWithDeviceId(5600, hostname, deviceId))
            if (!push.success) return@performSyncAsync push

            // Step 5: publish our own database where peers can reach it. Last, and before the
            // callback -- see the note on this function.
            val exported = mirrorSyncFilesToSafDir()
            if (!exported.ok) problems += "export failed: ${exported.reason()}"

            if (problems.isEmpty()) {
                SyncOutcome(true, "Successfully completed multi-device sync")
            } else {
                SyncOutcome(false, problems.joinToString("; "))
            }
        }
    }

    /**
     * Interrupts any in-progress sync and SAF mirror operation.
     *
     * Called by [SyncWorker] via `invokeOnCancellation` when WorkManager stops or cancels
     * the worker.  Three things happen in order:
     *
     * 1. [cancelRequested] is set so that [copySyncFilesToSafDir]'s copy loop stops before
     *    starting the next file's truncate-and-write, bounding the partial-write window to
     *    at most the file currently being copied.
     * 2. [ExecutorService.shutdownNow] sends an interrupt to the executor thread, causing
     *    any blocking I/O to throw [java.io.InterruptedIOException] promptly.
     * 3. [syncInFlight] is cleared so that a future sync is not permanently blocked.
     *    This is necessary because [shutdownNow] can remove a queued-but-not-started
     *    executor task before its completion callback has a chance to clear the guard.
     */
    fun cancel() {
        cancelRequested = true
        activeExecutor?.shutdownNow()
        syncInFlight.set(false)
    }

    private fun performSyncAsync(
        operation: String,
        callback: (Boolean, String) -> Unit,
        syncFn: () -> SyncOutcome
    ) {
        val executor = Executors.newSingleThreadExecutor()
        activeExecutor = executor
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            Log.i(TAG, "Starting sync operation: $operation")
            try {
                val outcome = syncFn()
                // Log a failure at warning level. A sync that did not do what it was asked to do
                // should not be indistinguishable from a healthy one in logcat.
                if (outcome.success) {
                    Log.i(TAG, "$operation completed: success=true, message=${outcome.message}")
                } else {
                    Log.w(TAG, "$operation completed: success=false, message=${outcome.message}")
                }
                handler.post { callback(outcome.success, outcome.message) }
            } catch (e: Exception) {
                val errorMsg = "Exception: ${e.message}"
                handler.post {
                    Log.e(TAG, "$operation failed", e)
                    callback(false, errorMsg)
                }
            } finally {
                executor.shutdown()
            }
        }
    }

    private fun mirrorSyncFilesToSafDir(): TransferResult =
        runTransfer("SAF export") { copySyncFilesToSafDir() }

    private fun importPeerFiles(): TransferResult =
        runTransfer("SAF import") { importPeerFilesFromSafDir() }

    /**
     * Run one SAF pass, turning an unexpected throw into a failed [TransferResult].
     *
     * These two used to catch and log "(non-fatal)", which was true of the *process* and false of
     * the *sync*: the pass had failed, nothing reached the shared folder, and the caller was still
     * told the sync succeeded. Catching here is still right -- one bad pass should not abort a
     * cycle that can finish -- but the failure now travels back with the result instead of dying
     * in logcat.
     */
    private fun runTransfer(label: String, block: () -> TransferResult): TransferResult =
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "$label failed", e)
            TransferResult().apply { fail("${e.javaClass.simpleName}: ${e.message}") }
        }

    /**
     * Export this device's own data to the user-chosen SAF directory, if one has been configured
     * via SyncSettingsActivity.
     *
     * aw-sync writes to the app-private [syncDir], which is invisible to Syncthing and other
     * file-sync tools on Android 11+. This copies the part of [syncDir] this device owns out to
     * the SAF-granted tree URI so that those tools can reach it.
     *
     * **Only `<hostname>/<our device id>/` is exported.** [importPeerFilesFromSafDir] now copies
     * peer databases *into* [syncDir], so a whole-tree mirror would write every peer's files back
     * out under our own hand. Every file in the shared folder has exactly one writer -- the device
     * named in its path (R20) -- and writing another device's file is precisely how Syncthing is
     * made to produce `.sync-conflict-*` copies.
     *
     * **Only the current hostname's copy is exported**, not every `<any hostname>/<our id>/` in
     * the tree. Pulling from a peer has a side effect: `sync_run` calls
     * `setup_local_remote(<peer hostname>, our_device_id)`, which creates
     * `<peer hostname>/<our device id>/test.db` locally. An export that scanned every hostname
     * directory therefore published our database into *the peer's* hostname folder too --
     * observed on device 2026-09-02, where two devices produced four directories instead of two.
     * Harmless for R20 (each file still has exactly one writer, the id in its path), but it grows
     * as the square of the device count and makes every device pull the same data twice.
     *
     * A device rename leaves our id under the old hostname, which this no longer exports. That is
     * correct: after a rename the old directory is a stale snapshot either way, and the current
     * hostname gets a complete push on the very next sync.
     *
     * The copy is recursive and structure-preserving, which is required for it to be usable at
     * all. aw-sync never writes a regular file at the root of the sync directory:
     * `setup_local_remote` in aw-server-rust (`aw-sync/src/sync.rs`) does `path.join(device_id)`
     * and writes `test.db` inside it, and on Android the observed tree is one level deeper still,
     * `<syncDir>/<hostname>/<device_id>/test.db`. The consuming side requires the same nesting:
     * `find_remotes` (`aw-sync/src/util.rs`) keeps only directories and looks for `*.db` one level
     * inside them, so a flattened copy would be ignored even if it were made.
     *
     * Errors do not throw, but they are no longer silent either: they are counted into the
     * returned [TransferResult], and a caller that sees `failed > 0` must not report the sync a
     * success. Sharing nothing while claiming to have synced is the failure this reporting exists
     * to make visible.
     *
     * @return what the pass managed to do. `ok` means this device's database is now in the shared
     *   folder, or that there was deliberately nothing to put there.
     */
    private fun copySyncFilesToSafDir(): TransferResult {
        val result = TransferResult()
        // No shared folder configured. Sync is local-only by the user's choice, not broken, so
        // this is a clean result rather than a failure.
        val uriStr = AWPreferences(appContext).getSyncDirUri() ?: return result

        val ourDeviceId = resolveDeviceId()
        if (ourDeviceId == null) {
            // Without our own id there is no way to tell our directory from a peer's, and
            // exporting a peer's files would break the single-writer rule. Nothing was shared,
            // so this counts as a failure rather than a quiet skip.
            Log.w(TAG, "SAF export skipped: this device's id is not known yet")
            result.fail("this device's id is not known yet")
            return result
        }
        val safUri = Uri.parse(uriStr)
        val safDir = DocumentFile.fromTreeUri(appContext, safUri)
        if (safDir == null || !safDir.isDirectory) {
            // Usually a revoked permission grant or a deleted folder -- and precisely the case
            // that used to log a warning and let the sync report success anyway.
            Log.w(TAG, "SAF directory not accessible or not a directory: $uriStr")
            result.fail("sync folder unreachable (permission revoked, or folder deleted)")
            return result
        }

        val hostname = getDeviceName()
        val ourDir = File(File(syncDir, hostname), ourDeviceId)
        if (!ourDir.isDirectory) {
            // Nothing has been pushed under this hostname yet; the next push creates it.
            Log.i(TAG, "SAF export: nothing to export at $hostname/$ourDeviceId")
            return result
        }
        if (!cancelRequested) {
            val safHostDir = findOrCreateSafDirectory(safDir, hostname, result)
            val safDeviceDir = safHostDir?.let { findOrCreateSafDirectory(it, ourDeviceId, result) }
            if (safDeviceDir != null) mirrorDirectory(ourDir, safDeviceDir, result)
        }
        Log.i(TAG, "SAF export: $hostname/$ourDeviceId $result → $uriStr")
        return result
    }

    /**
     * Import every *other* device's directory from the SAF shared folder into app-private storage,
     * so the pull that follows has peer databases to read (**Blocker 1** in `03_SYNC.md` §2.1,
     * **R21**).
     *
     * The layout is reproduced verbatim -- SAF `<hostname>/<device_id>/` becomes
     * `<syncDir>/<hostname>/<device_id>/` -- because that is the tree
     * `pull_all_from_all_hostnames` already walks. No separate staging area is used: the engine
     * only ever scans `AW_SYNC_DIR`, so a copy anywhere else would be invisible to it.
     *
     * Our own directory is never imported. The shared-folder copy of it is, by construction, an
     * older export of the very data we are about to push, and overwriting live data with it would
     * be a data-loss path rather than a sync (R20).
     *
     * **Nothing in the SAF folder is ever opened in place (R24).** Syncthing replaces a file by
     * renaming a temp file over it, so a database read directly out of that folder can return
     * corrupt pages. Each file is copied here first and only the copy is handed to aw-sync.
     */
    private fun importPeerFilesFromSafDir(): TransferResult {
        val result = TransferResult()
        // No shared folder configured -- nothing to import from, and nothing wrong.
        val uriStr = AWPreferences(appContext).getSyncDirUri() ?: return result

        val ourDeviceId = resolveDeviceId()
        if (ourDeviceId == null) {
            // Importing blind would risk overwriting our own live database with a stale copy.
            // No peer data reached the pull, so the sync must not claim success.
            Log.w(TAG, "SAF import skipped: this device's id is not known yet")
            result.fail("this device's id is not known yet")
            return result
        }
        val safDir = DocumentFile.fromTreeUri(appContext, Uri.parse(uriStr))
        if (safDir == null || !safDir.isDirectory) {
            Log.w(TAG, "SAF directory not accessible or not a directory: $uriStr")
            result.fail("sync folder unreachable (permission revoked, or folder deleted)")
            return result
        }

        for (safHostDir in safDir.listFiles()) {
            if (cancelRequested) {
                Log.i(TAG, "SAF import cancelled; stopping before ${safHostDir.name}")
                break
            }
            val hostName = safHostDir.name
            if (!safHostDir.isDirectory || hostName == null || !isSafeEntryName(hostName)) continue

            for (safDeviceDir in safHostDir.listFiles()) {
                val deviceId = safDeviceDir.name
                if (!safDeviceDir.isDirectory || deviceId == null || !isSafeEntryName(deviceId)) {
                    continue
                }
                if (deviceId == ourDeviceId) continue

                val destDir = File(File(syncDir, hostName), deviceId)
                if (!destDir.isDirectory && !destDir.mkdirs()) {
                    Log.w(TAG, "Could not create import directory ${destDir.absolutePath}")
                    result.fail("could not create local directory for $hostName/$deviceId")
                    continue
                }
                result.peers++
                importDirectory(safDeviceDir, destDir, result)
            }
        }
        Log.i(TAG, "SAF import: peers=${result.peers} $result ← $uriStr")
        return result
    }

    /** Recursively copy one peer's SAF directory into [destDir] under app-private storage. */
    private fun importDirectory(sourceDir: DocumentFile, destDir: File, result: TransferResult) {
        for (entry in sourceDir.listFiles()) {
            if (cancelRequested) {
                Log.i(TAG, "SAF import cancelled; stopping before ${entry.name}")
                return
            }
            val name = entry.name
            if (name == null || !isSafeEntryName(name)) {
                // Policy rejection, not an error: Syncthing's own `.stversions` and
                // `.sync-conflict-*` entries land here on every single pass.
                result.skipped++
                continue
            }
            try {
                if (entry.isDirectory) {
                    val subDir = File(destDir, name)
                    if (subDir.exists() && !subDir.isDirectory) {
                        Log.w(TAG, "Import target $name exists and is not a directory")
                        result.fail("$name exists locally as a file, not a directory")
                        continue
                    }
                    if (!subDir.isDirectory && !subDir.mkdirs()) {
                        Log.w(TAG, "Could not create import directory ${subDir.absolutePath}")
                        result.fail("could not create local directory $name")
                        continue
                    }
                    importDirectory(entry, subDir, result)
                } else {
                    when (importFile(entry, File(destDir, name))) {
                        FileOutcome.COPIED -> result.copied++
                        FileOutcome.SKIPPED -> result.skipped++
                        FileOutcome.FAILED -> result.fail("could not import $name")
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to import $name from SAF dir: ${e.message}")
                result.fail("$name: ${e.message}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied importing $name from SAF dir: ${e.message}")
                result.fail("permission denied reading $name")
            }
        }
    }

    /**
     * Copy one file out of the SAF tree, writing to a temporary name and renaming it into place.
     *
     * The rename is what makes this safe: the pull opens these databases moments later, and
     * `rename(2)` publishes the file in one step, so a half-written copy is never visible under
     * the name aw-sync reads. A partial write left by a cancelled or killed sync stays parked
     * under [IMPORT_TMP_SUFFIX], which the Rust side's `.db` extension filter ignores.
     *
     * @return [FileOutcome.COPIED] if bytes were written, [FileOutcome.SKIPPED] if the file was
     *   already current or the sync was cancelled, [FileOutcome.FAILED] if it should have been
     *   copied and was not. The old signature returned `Boolean` and folded the last two together,
     *   which is what let a pass where every copy failed look exactly like a pass with no work.
     */
    private fun importFile(source: DocumentFile, dest: File): FileOutcome {
        if (dest.isDirectory) {
            Log.w(TAG, "Import target ${dest.name} is a directory; cannot write a file there")
            return FileOutcome.FAILED
        }
        if (!needsImport(source, dest)) return FileOutcome.SKIPPED

        val tmp = File(dest.absolutePath + IMPORT_TMP_SUFFIX)
        try {
            val input = appContext.contentResolver.openInputStream(source.uri)
            if (input == null) {
                Log.w(TAG, "Null input stream for ${source.name} in SAF dir")
                return FileOutcome.FAILED
            }
            input.use { inp -> FileOutputStream(tmp).use { out -> inp.copyTo(out) } }

            if (cancelRequested) {
                // A deliberate stop, not a fault. The destination is untouched, and the next
                // cycle copies this file.
                tmp.delete()
                return FileOutcome.SKIPPED
            }
            if (!tmp.renameTo(dest)) {
                Log.w(TAG, "Could not move imported ${dest.name} into place")
                tmp.delete()
                return FileOutcome.FAILED
            }
            // Carry the peer's timestamp across so the next sync can tell an unchanged file from
            // a new one; see needsImport.
            val sourceModified = source.lastModified()
            if (sourceModified > 0L) dest.setLastModified(sourceModified)
            return FileOutcome.COPIED
        } catch (e: IOException) {
            Log.w(TAG, "Failed to import ${source.name}: ${e.message}")
            tmp.delete()
            return FileOutcome.FAILED
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied importing ${source.name}: ${e.message}")
            tmp.delete()
            return FileOutcome.FAILED
        }
    }

    /**
     * Whether a peer file has to be copied again. Same length plus a source timestamp no newer
     * than our copy means the bytes are already here; syncs run every 15 minutes and peer
     * databases only grow, so re-copying unchanged ones is pure battery cost.
     *
     * An absent or non-positive source timestamp is treated as *changed*. Silently skipping a real
     * update would lose peer data, which is the exact failure this whole step exists to remove --
     * so the cheap comparison only gets to skip when it is certain.
     */
    private fun needsImport(source: DocumentFile, dest: File): Boolean {
        if (!dest.exists()) return true
        if (source.length() != dest.length()) return true
        val sourceModified = source.lastModified()
        if (sourceModified <= 0L) return true
        return sourceModified > dest.lastModified()
    }

    /**
     * SAF entry names come from a directory other tools write into, so they are untrusted input.
     * Anything that could climb out of [syncDir], and Syncthing's own bookkeeping entries
     * (`.stfolder`, `.stversions`, `*.sync-conflict-*`), are ignored rather than copied.
     */
    private fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() &&
            !name.startsWith(".") &&
            !name.contains('/') &&
            !name.contains('\\') &&
            !name.contains(SYNC_CONFLICT_MARKER)

    /**
     * Find [name] inside [parent], creating it if absent. A non-directory of the same name cannot
     * be mirrored into; that is a failure, not a skip, because our database does not get published
     * and nothing later in the pass can compensate.
     */
    private fun findOrCreateSafDirectory(
        parent: DocumentFile,
        name: String,
        result: TransferResult
    ): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null && !existing.isDirectory) {
            Log.w(TAG, "SAF entry $name exists but is not a directory")
            result.fail("$name exists in the sync folder as a file, not a directory")
            return null
        }
        val dir = existing ?: parent.createDirectory(name)
        if (dir == null) {
            Log.w(TAG, "Could not create SAF directory for $name")
            result.fail("could not create $name in the sync folder")
        }
        return dir
    }

    /**
     * Recursively mirror [sourceDir] into [destDir], creating subdirectories as needed so the
     * `<device_id>/` layout aw-sync produces is reproduced verbatim in the SAF tree.
     */
    private fun mirrorDirectory(sourceDir: File, destDir: DocumentFile, result: TransferResult) {
        val entries = sourceDir.listFiles()
        if (entries == null) {
            Log.w(TAG, "Could not list ${sourceDir.absolutePath} for export")
            result.fail("could not read ${sourceDir.name} locally")
            return
        }

        for (entry in entries) {
            if (cancelRequested) {
                Log.i(TAG, "SAF mirror cancelled; stopping before ${entry.name}")
                return
            }
            try {
                if (entry.isDirectory) {
                    val subDir = findOrCreateSafDirectory(destDir, entry.name, result) ?: continue
                    mirrorDirectory(entry, subDir, result)
                } else {
                    // Reuse an existing file if present; otherwise create a new one. A
                    // same-named DIRECTORY must be rejected rather than written into: an
                    // already-populated tree can contain one, and openOutputStream() on a
                    // directory URI fails, which would silently leave the database
                    // uncopied. The directory branch above rejects the mirror case, so
                    // this keeps the two symmetric.
                    val existingFile = destDir.findFile(entry.name)
                    if (existingFile != null && existingFile.isDirectory) {
                        Log.w(TAG, "SAF entry ${entry.name} is a directory; cannot write a file there")
                        result.fail("${entry.name} exists in the sync folder as a directory")
                        continue
                    }
                    val dest = existingFile
                        ?: destDir.createFile("application/octet-stream", entry.name)
                    if (dest == null) {
                        Log.w(TAG, "Could not create SAF file for ${entry.name}")
                        result.fail("could not create ${entry.name} in the sync folder")
                        continue
                    }
                    val out = appContext.contentResolver.openOutputStream(dest.uri, "wt")
                    if (out == null) {
                        Log.w(TAG, "Null output stream for ${entry.name} in SAF dir")
                        result.fail("could not open ${entry.name} for writing")
                        continue
                    }
                    out.use { FileInputStream(entry).use { inp -> inp.copyTo(it) } }
                    result.copied++
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to copy ${entry.name} to SAF dir: ${e.message}")
                result.fail("${entry.name}: ${e.message}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied copying ${entry.name} to SAF dir: ${e.message}")
                result.fail("permission denied writing ${entry.name}")
            }
        }
    }

    fun getSyncDirectory(): String = syncDir
}
