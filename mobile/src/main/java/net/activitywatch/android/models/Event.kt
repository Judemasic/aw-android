package net.activitywatch.android.models

import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import org.threeten.bp.DateTimeUtils
import org.threeten.bp.Instant

data class Event(val timestamp: Instant, val duration: Double = 0.0, val data: JSONObject) {
    companion object {
        fun fromUsageEvent(usageEvent: UsageEvents.Event, context: Context, includeClassname: Boolean = true): Event {
            val timestamp = DateTimeUtils.toInstant(java.util.Date(usageEvent.timeStamp))
            val pm = context.packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(usageEvent.packageName, PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES))
            } catch(e: PackageManager.NameNotFoundException) {
                "Unknown (${usageEvent.packageName})"
            }

            // Construct the data object in an exception-safe manner
            val data = JSONObject()
            data.put("app", appName)
            // `title` duplicates the app label deliberately. aw-webui's Android query runs
            // merge_events_by_keys(events, ["app", "title"]), and that helper DROPS every event
            // missing any requested key -- so without a title, an entire day of activity
            // evaluates to zero and the Activity view reads "Time active: 0s" beside a Timeline
            // full of events. Verified on device: the same bucket and day returns 4878.8s
            // without the merge and 0.0s with it.
            //
            // Upstream regression, ActivityWatch/aw-webui bf0fc84 (2026-07-24), an iOS ScreenTime
            // patch that changed the shared Android branch from ["app"] to ["app", "title"];
            // ScreenTime events carry a title, Android's never have. Still unfixed on aw-webui
            // master as of 2026-08-31. Emitting the field here rather than patching aw-webui
            // avoids a third fork and survives the query changing again. The screen actually in
            // use stays available in `classname`, which title_events groups by.
            data.put("title", appName)
            data.put("package", usageEvent.packageName)
            if(includeClassname) {
                data.put("classname", usageEvent.className)
            }

            return Event(
                timestamp = timestamp,
                duration = 0.0,
                data = data
            )
        }
    }

    override fun toString(): String {
        val event = JSONObject()
        event.put("timestamp", timestamp)
        event.put("duration", duration)
        event.put("data", data)
        return event.toString()
    }
}