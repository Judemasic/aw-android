package net.activitywatch.android.models

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.threeten.bp.Instant

/**
 * The parsed result of `RustInterface.getCombinedTimeline` (roadmap 3.4).
 *
 * Deliberately free of `android.util.Log` and of any Android type so it is reachable from plain
 * JVM unit tests -- the same rule `SharedFolder.kt`'s parsers follow.
 */

/** One drawn block on a track: a span of time with a label. */
data class TimelineSpan(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    /** Which device's activity this is. Null on a per-device track, where the row says it already. */
    val device: String? = null,
    /**
     * Contended and undecided, so the view shades it (**R8**). Provisional attribution already
     * picked [label] as the one that counts (**R17**) -- shading says "and I am not sure", not
     * "and nothing counted here".
     */
    val unresolved: Boolean = false,
    /** What else was running at the same instant, kept but contributing zero to totals (**R6**). */
    val background: List<String> = emptyList(),
) {
    val durationMs: Long
        get() = endMs - startMs
}

/** One device's raw, unmodified track (**R11**) plus its own total. */
data class DeviceTrack(
    val device: String,
    /** `hostname` from the shared folder if known, else the uuid. Never empty. */
    val displayName: String,
    val isOwn: Boolean,
    val totalSeconds: Long,
    val spans: List<TimelineSpan>,
)

/**
 * The combined track, the per-device tracks, and the totals for one range.
 *
 * [combinedSeconds] is the number **R6** is about: it is the wall-clock time the owner was active
 * on *some* device, and it is normally **less** than the sum of [DeviceTrack.totalSeconds].
 */
data class CombinedTimeline(
    val startMs: Long,
    val endMs: Long,
    val combinedSeconds: Long,
    val combined: List<TimelineSpan>,
    val devices: List<DeviceTrack>,
) {
    val deviceSecondsTotal: Long
        get() = devices.sumOf { it.totalSeconds }

    companion object {
        /**
         * Parse the JSON the Rust side returns.
         *
         * Returns null for `{"error": ...}` and for anything unparseable; the caller says what a
         * null means in its own context. [errorOf] pulls the message back out.
         */
        fun parse(json: String?): CombinedTimeline? {
            if (json.isNullOrBlank()) return null
            return try {
                val root = JSONObject(json)
                if (root.has("error")) return null
                CombinedTimeline(
                    startMs = parseInstant(root.optString("start")) ?: return null,
                    endMs = parseInstant(root.optString("end")) ?: return null,
                    combinedSeconds = root.optLong("combined_seconds"),
                    combined = parseSpans(root.optJSONArray("combined"), withDevice = true),
                    devices = parseDevices(root.optJSONArray("devices")),
                )
            } catch (e: JSONException) {
                null
            }
        }

        /** The message from an `{"error": ...}` reply, or null if this is not one. */
        fun errorOf(json: String?): String? {
            if (json.isNullOrBlank()) return null
            return try {
                JSONObject(json).optString("error").takeIf { it.isNotEmpty() }
            } catch (e: JSONException) {
                null
            }
        }

        private fun parseDevices(arr: JSONArray?): List<DeviceTrack> {
            if (arr == null) return emptyList()
            val out = ArrayList<DeviceTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uuid = o.optString("device").takeIf { it.isNotEmpty() } ?: continue
                // `hostname` is JSON null whenever the shared folder has no name for this uuid,
                // which optString renders as the literal "null" -- hence the explicit isNull check.
                val hostname =
                    if (o.isNull("hostname")) null
                    else o.optString("hostname").takeIf { it.isNotEmpty() }
                out.add(
                    DeviceTrack(
                        device = uuid,
                        displayName = hostname ?: uuid,
                        isOwn = o.optBoolean("is_own"),
                        totalSeconds = o.optLong("total_seconds"),
                        spans = parseSpans(o.optJSONArray("events"), withDevice = false),
                    )
                )
            }
            return out
        }

        private fun parseSpans(arr: JSONArray?, withDevice: Boolean): List<TimelineSpan> {
            if (arr == null) return emptyList()
            val out = ArrayList<TimelineSpan>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = parseInstant(o.optString("start")) ?: continue
                val end = parseInstant(o.optString("end")) ?: continue
                if (end <= start) continue
                out.add(
                    TimelineSpan(
                        startMs = start,
                        endMs = end,
                        label = o.optString("label").takeIf { it.isNotEmpty() } ?: "(unknown)",
                        device = if (withDevice) o.optString("device").takeIf { it.isNotEmpty() } else null,
                        unresolved = o.optBoolean("unresolved"),
                        background = parseBackground(o.optJSONArray("background")),
                    )
                )
            }
            return out
        }

        private fun parseBackground(arr: JSONArray?): List<String> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = o.optString("label").takeIf { it.isNotEmpty() } ?: continue
                out.add(label)
            }
            return out
        }

        /** Serde writes RFC 3339 with a `Z` offset; `Instant.parse` reads exactly that. */
        private fun parseInstant(text: String?): Long? {
            if (text.isNullOrEmpty()) return null
            return try {
                Instant.parse(text).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }
}
