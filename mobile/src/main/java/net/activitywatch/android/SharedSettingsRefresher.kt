package net.activitywatch.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "SettingsRefresher"

/**
 * How often to look for settings another device has changed, while the app is on screen.
 *
 * A refresh reads a few kilobytes of text through SAF and writes only what differs, so this is
 * cheap. It runs *only* while the app is in the foreground: nothing polls in the background, where
 * nobody is looking at a stale category name anyway and the 15-minute sync cycle is soon enough.
 */
private const val POLL_INTERVAL_MS = 30_000L

/**
 * Keeps shared settings current while the owner is looking at the app.
 *
 * **Why polling, when a `ContentObserver` is the obvious answer.** It was the first answer, and it
 * does not work here. Measured on `SM-X520`, 2026-09-09, against the build that shipped it:
 *
 * - registering the observer on the sync folder's document tree succeeded, and it did fire once —
 *   for a write by *this* app;
 * - Syncthing delivering `meta.json` from the phone produced **no** notification in the following
 *   75 seconds, though the file demonstrably arrived (its mtime matched the phone's new one);
 * - writing a file into the folder from `adb shell` — another uid, like Syncthing — produced **no**
 *   notification in 40 seconds either.
 *
 * `ExternalStorageProvider` simply does not notify document-tree observers about other apps'
 * writes, and Android has never promised it would. An observer that only reports our own writes
 * tells us nothing we did not already know, so it was removed rather than kept as decoration.
 *
 * What replaces it is duller and works: ask every [POLL_INTERVAL_MS] while the app is visible.
 * Together with the refresh [start] does immediately, that means a rename made on another device
 * appears within half a minute of arriving, and is already applied whenever the app is opened.
 */
class SharedSettingsRefresher(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var syncInterface: SyncInterface? = null
    private var running = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            syncInterface?.refreshSharedSettingsAsync()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /**
     * Refresh now, then keep refreshing until [stop].
     *
     * The immediate refresh is the one that matters most: it is what makes opening the app enough
     * to see a change made elsewhere, with no manual sync and no restarting the app.
     *
     * Constructing [SyncInterface] loads the native library, so it happens off the main thread and
     * a missing library is survivable — sync is simply unavailable, exactly as [SyncScheduler]
     * treats it.
     */
    fun start() {
        if (running) return
        if (!AWPreferences(context).isSyncEnabled()) return
        running = true
        Thread {
            try {
                val sync = SyncInterface(context)
                syncInterface = sync
                handler.post {
                    if (running) {
                        handler.removeCallbacks(pollRunnable)
                        handler.post(pollRunnable)
                    }
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "aw-sync unavailable; shared settings will not refresh")
                running = false
            } catch (e: Exception) {
                Log.w(TAG, "Could not start settings refresher", e)
                running = false
            }
        }.start()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(pollRunnable)
        syncInterface = null
    }
}
