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
    
    // Async wrapper for syncPullAll
    fun syncPullAllAsync(callback: (Boolean, String) -> Unit) {
        val hostname = getDeviceName()
        performSyncAsync("Pull All", callback) {
            syncPullAll(5600, hostname)
        }
    }
    
    // Async wrapper for Push with per-device staging (Phase 1)
    fun syncPushWithDeviceIdAsync(callback: (Boolean, String) -> Unit) {
        val hostname = getDeviceName()
        val deviceId = resolveDeviceId() ?: "unknown"
        performSyncAsync("Push (device-specific)", callback) {
            syncPushWithDeviceId(5600, hostname, deviceId)
        }
    }
    
    // Async wrapper for pull from ALL hostnames (Phase 1)
    fun syncPullAllFromAllHostnamesAsync(callback: (Boolean, String) -> Unit) {
        performSyncAsync("Pull All Hostnames", callback) {
            syncPullAllFromAllHostnames(5600)
        }
    }
    
    // Async wrapper for full multi-device sync (Phase 1)
    fun syncBothMultiDeviceAsync(callback: (Boolean, String) -> Unit) {
        syncBothMultiDeviceAsync(mirrorBeforeCallback = false, callback)
    }

    // Background workers must remain active until the SAF mirror completes.
    fun syncBothAndMirrorAsync(callback: (Boolean, String) -> Unit) {
        syncBothMultiDeviceAsync(mirrorBeforeCallback = true, callback)
    }

    private fun syncBothMultiDeviceAsync(
        mirrorBeforeCallback: Boolean,
        callback: (Boolean, String) -> Unit
    ) {
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
            },
            mirrorBeforeCallback
        ) {
            // Step 2 of the cycle in 03_SYNC.md §3.2: bring every *other* device's database into
            // app-private storage before the pull. The engine only ever scans AW_SYNC_DIR, so
            // without this pass it reads a directory containing nothing but this device's own
            // output -- Blocker 1, the reason cross-device sync never worked (R21).
            // Step 1 of the cycle (export self) is the previous cycle's step 5, not repeated here.
            importPeerFiles()

            // Phase 1: Pull from ALL hostnames (not just our own)
            val pullResult = syncPullAllFromAllHostnames(5600)
            val pullJson = JSONObject(pullResult)
            if (!pullJson.getBoolean("success")) {
                return@performSyncAsync pullResult
            }
            
            // Phase 1: Push our local data to a per-device staging area
            val pushResult = syncPushWithDeviceId(5600, hostname, deviceId)
            val pushJson = JSONObject(pushResult)
            if (!pushJson.getBoolean("success")) {
                return@performSyncAsync pushResult
            }
            
            // Success: signal complete
            JSONObject().apply {
                put("success", true)
                put("message", "Successfully completed multi-device sync")
            }.toString()
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
        mirrorBeforeCallback: Boolean = false,
        syncFn: () -> String
    ) {
        val executor = Executors.newSingleThreadExecutor()
        activeExecutor = executor
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            Log.i(TAG, "Starting sync operation: $operation")
            try {
                val response = syncFn()
                val json = JSONObject(response)
                val success = json.getBoolean("success")
                val message = if (success) {
                    json.getString("message")
                } else {
                    json.getString("error")
                }

                // Worker-triggered syncs keep their WorkManager job active until mirroring
                // finishes. Other callers get the native result immediately.
                Log.i(TAG, "$operation completed: success=$success, message=$message")
                if (success && mirrorBeforeCallback) {
                    mirrorSyncFilesToSafDir()
                }
                handler.post { callback(success, message) }
                if (success && !mirrorBeforeCallback) {
                    mirrorSyncFilesToSafDir()
                }
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

    private fun mirrorSyncFilesToSafDir() {
        try {
            copySyncFilesToSafDir()
        } catch (e: Exception) {
            Log.w(TAG, "SAF mirror failed (non-fatal): ${e.message}")
        }
    }

    private fun importPeerFiles() {
        try {
            importPeerFilesFromSafDir()
        } catch (e: Exception) {
            Log.w(TAG, "SAF import failed (non-fatal): ${e.message}")
        }
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
     * Every hostname directory is scanned, not just the current one: a device rename leaves this
     * device's id sitting under its old hostname, and that data is still ours to export.
     *
     * The copy is recursive and structure-preserving, which is required for it to be usable at
     * all. aw-sync never writes a regular file at the root of the sync directory:
     * `setup_local_remote` in aw-server-rust (`aw-sync/src/sync.rs`) does `path.join(device_id)`
     * and writes `test.db` inside it, and on Android the observed tree is one level deeper still,
     * `<syncDir>/<hostname>/<device_id>/test.db`. The consuming side requires the same nesting:
     * `find_remotes` (`aw-sync/src/util.rs`) keeps only directories and looks for `*.db` one level
     * inside them, so a flattened copy would be ignored even if it were made.
     *
     * Errors are logged but do not propagate -- a copy failure must never fail the sync itself.
     */
    private fun copySyncFilesToSafDir() {
        val uriStr = AWPreferences(appContext).getSyncDirUri() ?: return
        val ourDeviceId = resolveDeviceId()
        if (ourDeviceId == null) {
            // Without our own id there is no way to tell our directory from a peer's, and
            // exporting a peer's files would break the single-writer rule.
            Log.w(TAG, "SAF export skipped: this device's id is not known yet")
            return
        }
        val safUri = Uri.parse(uriStr)
        val safDir = DocumentFile.fromTreeUri(appContext, safUri)
        if (safDir == null || !safDir.isDirectory) {
            Log.w(TAG, "SAF directory not accessible or not a directory: $uriStr")
            return
        }

        val counts = intArrayOf(0, 0) // [copied, skipped]
        val hostDirs = File(syncDir).listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (hostDir in hostDirs) {
            if (cancelRequested) {
                Log.i(TAG, "SAF export cancelled; stopping before ${hostDir.name}")
                break
            }
            // Anything under this hostname that is not our own id was imported from a peer.
            val ourDir = File(hostDir, ourDeviceId)
            if (!ourDir.isDirectory) continue

            val safHostDir = findOrCreateSafDirectory(safDir, hostDir.name, counts) ?: continue
            val safDeviceDir = findOrCreateSafDirectory(safHostDir, ourDeviceId, counts) ?: continue
            mirrorDirectory(ourDir, safDeviceDir, counts)
        }
        Log.i(TAG, "SAF export: copied=${counts[0]} skipped=${counts[1]} → $uriStr")
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
    private fun importPeerFilesFromSafDir() {
        val uriStr = AWPreferences(appContext).getSyncDirUri() ?: return
        val ourDeviceId = resolveDeviceId()
        if (ourDeviceId == null) {
            // Importing blind would risk overwriting our own live database with a stale copy.
            Log.w(TAG, "SAF import skipped: this device's id is not known yet")
            return
        }
        val safDir = DocumentFile.fromTreeUri(appContext, Uri.parse(uriStr))
        if (safDir == null || !safDir.isDirectory) {
            Log.w(TAG, "SAF directory not accessible or not a directory: $uriStr")
            return
        }

        val counts = intArrayOf(0, 0) // [copied, skipped]
        var peers = 0
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
                    counts[1]++
                    continue
                }
                peers++
                importDirectory(safDeviceDir, destDir, counts)
            }
        }
        Log.i(TAG, "SAF import: peers=$peers copied=${counts[0]} skipped=${counts[1]} ← $uriStr")
    }

    /** Recursively copy one peer's SAF directory into [destDir] under app-private storage. */
    private fun importDirectory(sourceDir: DocumentFile, destDir: File, counts: IntArray) {
        for (entry in sourceDir.listFiles()) {
            if (cancelRequested) {
                Log.i(TAG, "SAF import cancelled; stopping before ${entry.name}")
                return
            }
            val name = entry.name
            if (name == null || !isSafeEntryName(name)) {
                counts[1]++
                continue
            }
            try {
                if (entry.isDirectory) {
                    val subDir = File(destDir, name)
                    if (subDir.exists() && !subDir.isDirectory) {
                        Log.w(TAG, "Import target $name exists and is not a directory")
                        counts[1]++
                        continue
                    }
                    if (!subDir.isDirectory && !subDir.mkdirs()) {
                        Log.w(TAG, "Could not create import directory ${subDir.absolutePath}")
                        counts[1]++
                        continue
                    }
                    importDirectory(entry, subDir, counts)
                } else if (importFile(entry, File(destDir, name))) {
                    counts[0]++
                } else {
                    counts[1]++
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to import $name from SAF dir: ${e.message}")
                counts[1]++
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied importing $name from SAF dir: ${e.message}")
                counts[1]++
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
     * @return true if bytes were copied; false if the file was already current, or on any failure.
     */
    private fun importFile(source: DocumentFile, dest: File): Boolean {
        if (dest.isDirectory) {
            Log.w(TAG, "Import target ${dest.name} is a directory; cannot write a file there")
            return false
        }
        if (!needsImport(source, dest)) return false

        val tmp = File(dest.absolutePath + IMPORT_TMP_SUFFIX)
        try {
            val input = appContext.contentResolver.openInputStream(source.uri)
            if (input == null) {
                Log.w(TAG, "Null input stream for ${source.name} in SAF dir")
                return false
            }
            input.use { inp -> FileOutputStream(tmp).use { out -> inp.copyTo(out) } }

            if (cancelRequested) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(dest)) {
                Log.w(TAG, "Could not move imported ${dest.name} into place")
                tmp.delete()
                return false
            }
            // Carry the peer's timestamp across so the next sync can tell an unchanged file from
            // a new one; see needsImport.
            val sourceModified = source.lastModified()
            if (sourceModified > 0L) dest.setLastModified(sourceModified)
            return true
        } catch (e: IOException) {
            Log.w(TAG, "Failed to import ${source.name}: ${e.message}")
            tmp.delete()
            return false
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied importing ${source.name}: ${e.message}")
            tmp.delete()
            return false
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
     * be mirrored into, so it is counted as skipped rather than written over.
     */
    private fun findOrCreateSafDirectory(
        parent: DocumentFile,
        name: String,
        counts: IntArray
    ): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null && !existing.isDirectory) {
            Log.w(TAG, "SAF entry $name exists but is not a directory")
            counts[1]++
            return null
        }
        val dir = existing ?: parent.createDirectory(name)
        if (dir == null) {
            Log.w(TAG, "Could not create SAF directory for $name")
            counts[1]++
        }
        return dir
    }

    /**
     * Recursively mirror [sourceDir] into [destDir], creating subdirectories as needed so the
     * `<device_id>/` layout aw-sync produces is reproduced verbatim in the SAF tree.
     */
    private fun mirrorDirectory(sourceDir: File, destDir: DocumentFile, counts: IntArray) {
        val entries = sourceDir.listFiles() ?: return

        for (entry in entries) {
            if (cancelRequested) {
                Log.i(TAG, "SAF mirror cancelled; stopping before ${entry.name}")
                return
            }
            try {
                if (entry.isDirectory) {
                    val subDir = findOrCreateSafDirectory(destDir, entry.name, counts) ?: continue
                    mirrorDirectory(entry, subDir, counts)
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
                        counts[1]++
                        continue
                    }
                    val dest = existingFile
                        ?: destDir.createFile("application/octet-stream", entry.name)
                    if (dest == null) {
                        Log.w(TAG, "Could not create SAF file for ${entry.name}")
                        counts[1]++
                        continue
                    }
                    val out = appContext.contentResolver.openOutputStream(dest.uri, "wt")
                    if (out == null) {
                        Log.w(TAG, "Null output stream for ${entry.name} in SAF dir")
                        counts[1]++
                        continue
                    }
                    out.use { FileInputStream(entry).use { inp -> inp.copyTo(it) } }
                    counts[0]++
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to copy ${entry.name} to SAF dir: ${e.message}")
                counts[1]++
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied copying ${entry.name} to SAF dir: ${e.message}")
                counts[1]++
            }
        }
    }

    fun getSyncDirectory(): String = syncDir
}
