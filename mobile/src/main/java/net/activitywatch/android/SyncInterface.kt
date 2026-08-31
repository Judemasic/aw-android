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
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SyncInterface"

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
    private external fun syncPullAll(port: Int, hostname: String): String
    private external fun syncPull(port: Int, hostname: String): String
    private external fun syncPush(port: Int, hostname: String): String
    
    // New per-device push function (Phase 1)
    private external fun syncPushWithDeviceId(port: Int, hostname: String, deviceId: String): String
    
    // New pull-from-all-hostnames function (Phase 1)
    private external fun syncPullAllFromAllHostnames(port: Int): String
    
    external fun getSyncDir(): String

    /**
     * Get a stable device identifier for this Android device.
     * Falls back to android.os.Build fields if no reliable ID is available.
     */
    private fun getDeviceId(): String {
        val installerPackage = appContext.packageManager.getInstallerPackageName(appContext.packageName)
        if (installerPackage != null && installerPackage != "com.google.android.instantapps.supervisor") {
            // Google Play installs a unique install ID per device
            val hashCode = installerPackage.hashCode().toString()
            return "android_${hashCode.take(12)}"
        }
        
        // Fallback: build fingerprint hash as deviceId
        val fingerprint = android.os.Build.FINGERPRINT ?: "unknown_fingerprint"
        val fingerprintHash = fingerprint.hashCode().toString()
        return "android_${fingerprintHash.take(12)}"
    }
    
    private fun getDeviceName(): String {
        val raw = android.provider.Settings.Global.getString(
            appContext.contentResolver,
            android.provider.Settings.Global.DEVICE_NAME
        )?.trim()?.takeIf { it.isNotEmpty() }
            ?: android.os.Build.DEVICE ?: "unknown"
        return raw.trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .ifEmpty { "unknown" }
    }
    
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
        val deviceId = getDeviceId()
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
        val deviceId = getDeviceId()
        
        performSyncAsync(
            "Multi-Device Sync",
            { success, message ->
                syncInFlight.set(false)
                callback(success, message)
            },
            mirrorBeforeCallback
        ) {
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

    /**
     * After each successful sync, mirror the contents of the internal sync directory to the
     * user-chosen SAF directory (if one has been configured via SyncSettingsActivity).
     *
     * aw-sync writes to the app-private [syncDir] which is invisible to Syncthing and other
     * file-sync tools on Android 11+. This method mirrors [syncDir] to the SAF-granted tree
     * URI so that external sync tools can reach the data.
     *
     * The mirror is recursive and structure-preserving, which is required for the copy to
     * contain anything at all and for the result to be usable. aw-sync never writes a regular
     * file at the root of the sync directory: `setup_local_remote` in aw-server-rust
     * (`aw-sync/src/sync.rs`) does `path.join(device_id)` and writes `test.db` inside it, and
     * on Android the observed tree is one level deeper still,
     * `<syncDir>/<hostname>/<device_id>/test.db`. The consuming side requires the same nesting:
     * `find_remotes` (`aw-sync/src/util.rs`) keeps only directories and looks for `*.db` one
     * level inside them, so a flattened copy would be ignored even if it were made.
     *
     * Errors are logged but do not propagate — a copy failure must never fail the sync itself.
     */
    private fun copySyncFilesToSafDir() {
        val uriStr = AWPreferences(appContext).getSyncDirUri() ?: return
        val safUri = Uri.parse(uriStr)
        val safDir = DocumentFile.fromTreeUri(appContext, safUri)
        if (safDir == null || !safDir.isDirectory) {
            Log.w(TAG, "SAF directory not accessible or not a directory: $uriStr")
            return
        }

        val counts = intArrayOf(0, 0) // [copied, skipped]
        mirrorDirectory(File(syncDir), safDir, counts)
        Log.i(TAG, "SAF mirror: copied=${counts[0]} skipped=${counts[1]} → $uriStr")
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
                    // Reuse an existing subdirectory if present; otherwise create it. A
                    // non-directory of the same name cannot be mirrored into.
                    val existing = destDir.findFile(entry.name)
                    val subDir = when {
                        existing != null && existing.isDirectory -> existing
                        existing != null -> {
                            Log.w(TAG, "SAF entry ${entry.name} exists but is not a directory")
                            counts[1]++
                            continue
                        }
                        else -> destDir.createDirectory(entry.name)
                    }
                    if (subDir == null) {
                        Log.w(TAG, "Could not create SAF directory for ${entry.name}")
                        counts[1]++
                        continue
                    }
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
