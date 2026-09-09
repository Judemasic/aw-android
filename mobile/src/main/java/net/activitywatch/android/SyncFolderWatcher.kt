package net.activitywatch.android

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

private const val TAG = "SyncFolderWatcher"

/**
 * How long to wait after the last change before acting on it.
 *
 * Syncthing does not deliver one file and stop — it writes a burst (temporary files, the real file,
 * then the directory's own timestamp), and each of those is a separate notification. Acting on the
 * first would read a folder that is still being written into; acting on all of them would run the
 * refresh a dozen times for one change. Waiting for the burst to go quiet does both jobs.
 *
 * Deliberately longer than it needs to be for a settings file, which is a couple of kilobytes: the
 * same burst carries the databases, which are not.
 */
private const val QUIET_PERIOD_MS = 4000L

/**
 * Watches the shared sync folder and applies settings changes as they arrive.
 *
 * **Why this exists.** Before it, a category renamed on the phone reached the tablet's disk within
 * seconds and then sat there unapplied for up to fifteen minutes, because only the periodic sync
 * cycle ever looked. On device 2026-09-09 the tablet's cycle ran four seconds before the file
 * landed and the rename was invisible until the next one — with the correct data on disk the whole
 * time, which is the most confusing shape a bug can have.
 *
 * **Best-effort, and deliberately not the only mechanism.** This asks
 * `ExternalStorageProvider` to tell us when a document tree changes. Whether it actually fires for
 * a *different* app's writes — Syncthing's — is provider- and OEM-specific, and Android has never
 * promised it. So `MainActivity.onResume` runs the same refresh unconditionally: if the observer
 * works, the change is applied while you watch; if it does not, opening the app still gets you
 * current values. Neither path is load-bearing on its own.
 *
 * A refresh writes only to the local datastore unless *this* device has a local edit to publish, so
 * our own writes cannot start a feedback loop: the cycle after a publish finds nothing left to say.
 */
class SyncFolderWatcher(private val context: Context) {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var observer: ContentObserver? = null
    private var syncInterface: SyncInterface? = null

    private val refreshRunnable = Runnable {
        Log.i(TAG, "Sync folder changed; refreshing shared settings")
        syncInterface?.refreshSharedSettingsAsync()
    }

    /**
     * Begin watching, if there is a sync folder to watch and sync is switched on.
     *
     * Safe to call when already started — the previous registration is dropped first, which is what
     * makes this the right call to make when the folder is re-chosen.
     */
    fun start(sync: SyncInterface) {
        stop()
        if (!AWPreferences(context).isSyncEnabled()) {
            Log.i(TAG, "Not watching: sync is disabled")
            return
        }
        val uriStr = AWPreferences(context).getSyncDirUri()
        if (uriStr == null) {
            Log.i(TAG, "Not watching: no sync folder chosen")
            return
        }

        syncInterface = sync
        // Its own thread: the callback runs on whichever Looper we hand it, and the main thread is
        // not somewhere to be woken up by every file Syncthing writes.
        val watcherThread = HandlerThread("SyncFolderWatcher").also { it.start() }
        val watcherHandler = Handler(watcherThread.looper)
        val watcher = object : ContentObserver(watcherHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Coalesce the burst: push the refresh back until the writes stop.
                watcherHandler.removeCallbacks(refreshRunnable)
                watcherHandler.postDelayed(refreshRunnable, QUIET_PERIOD_MS)
            }
        }

        try {
            // notifyForDescendants = true: the files that matter are two levels down, in
            // devices/<uuid>/, and a change to one of those is not a change to the tree's root.
            context.contentResolver.registerContentObserver(Uri.parse(uriStr), true, watcher)
            thread = watcherThread
            handler = watcherHandler
            observer = watcher
            Log.i(TAG, "Watching the sync folder for changes")
        } catch (e: SecurityException) {
            // The persisted permission is gone (folder re-picked, or the grant revoked). Not fatal:
            // the resume-time refresh and the periodic sync both still work.
            Log.w(TAG, "Could not watch the sync folder: ${e.message}")
            watcherThread.quitSafely()
            syncInterface = null
        }
    }

    fun stop() {
        observer?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Observer was already unregistered")
            }
        }
        handler?.removeCallbacks(refreshRunnable)
        thread?.quitSafely()
        observer = null
        handler = null
        thread = null
        syncInterface = null
    }
}
