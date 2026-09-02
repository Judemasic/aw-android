package net.activitywatch.android.data

import org.json.JSONObject

/**
 * Represents a single app usage session
 */
data class AppSession(
    val packageName: String,
    val appName: String,
    val className: String = "",
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long = endTime - startTime
) {
    val durationMinutes: Double get() = durationMs / 60000.0
    val durationHours: Double get() = durationMs / 3600000.0
    val durationSeconds: Double get() = durationMs / 1000.0

    /**
     * Convert session to ActivityWatch Event JSON format for heartbeats
     */
    fun toEventData(): JSONObject {
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
        data.put("package", packageName)
        if (className.isNotEmpty()) {
            data.put("classname", className)
        }
        return data
    }
}

/**
 * Represents aggregated usage data for a single app
 */
data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val sessionCount: Int,
    val sessions: List<AppSession>
) {
    val totalMinutes: Double get() = totalTimeMs / 60000.0
    val totalHours: Double get() = totalTimeMs / 3600000.0
    val averageSessionMs: Long get() = if (sessionCount > 0) totalTimeMs / sessionCount else 0
    val averageSessionMinutes: Double get() = averageSessionMs / 60000.0
}

/**
 * Represents a complete timeline for a single day
 */
data class DayTimeline(
    val date: Long, // timestamp of the day start
    val sessions: List<AppSession>,
    val appSummaries: List<AppUsageSummary>,
    val totalScreenTimeMs: Long
) {
    val totalScreenTimeHours: Double get() = totalScreenTimeMs / 3600000.0
    val totalScreenTimeMinutes: Double get() = totalScreenTimeMs / 60000.0
    val uniqueAppsCount: Int get() = appSummaries.size
}

/**
 * Internal model for tracking active activities during parsing
 */
internal data class ActivityState(
    val packageName: String,
    val className: String,
    val appName: String,
    val startTime: Long
)

/**
 * Internal model for raw usage events during parsing
 */
internal data class UsageEvent(
    val eventType: Int,
    val timeStamp: Long,
    val packageName: String,
    val className: String
)

/**
 * Represents session statistics for analysis
 */
data class SessionStats(
    val totalSessions: Int,
    val averageSessionDuration: Long,
    val longestSession: AppSession?,
    val shortestSession: AppSession?,
    val mostUsedApp: AppUsageSummary?
)
