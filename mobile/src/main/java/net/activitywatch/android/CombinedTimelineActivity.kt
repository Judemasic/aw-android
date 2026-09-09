package net.activitywatch.android

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.activitywatch.android.models.CombinedTimeline
import net.activitywatch.android.views.CombinedTimelineView
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter

private const val TAG = "CombinedTimeline"

/**
 * The combined timeline screen (roadmap 3.4) -- one day at a time, combined track on top,
 * per-device tracks underneath, unresolved contention shaded (**R8**).
 *
 * Native and phone-first (**Q4**, resolved 2026-09-02): the pipeline is Rust, so a desktop aw-webui
 * view can be built on the same code later without either one being the other's port.
 */
class CombinedTimelineActivity : AppCompatActivity() {

    private lateinit var timelineView: CombinedTimelineView
    private lateinit var tvDate: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvDetail: TextView
    private lateinit var progress: ProgressBar

    private var day: LocalDate = LocalDate.now()

    /**
     * Built once and reused. Constructing it loads the native library and opens the datastore, which
     * is slow enough to be worth not repeating on every day change.
     */
    private val rustInterface by lazy { RustInterface(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combined_timeline)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.combined_timeline)

        timelineView = findViewById(R.id.timeline_view)
        tvDate = findViewById(R.id.tv_date)
        tvSummary = findViewById(R.id.tv_summary)
        tvDetail = findViewById(R.id.tv_detail)
        progress = findViewById(R.id.progress)

        findViewById<Button>(R.id.btn_prev).setOnClickListener { shiftDay(-1) }
        findViewById<Button>(R.id.btn_next).setOnClickListener { shiftDay(1) }
        findViewById<Button>(R.id.btn_today).setOnClickListener {
            day = LocalDate.now()
            reload()
        }

        timelineView.onSpanTapped = { span ->
            tvDetail.text =
                when {
                    span == null -> getString(R.string.combined_tap_hint)
                    span.background.isEmpty() ->
                        "${span.label} · ${CombinedTimelineView.formatDuration(span.durationMs / 1000)}"
                    else ->
                        getString(
                            R.string.combined_detail_contended,
                            span.label,
                            CombinedTimelineView.formatDuration(span.durationMs / 1000),
                            span.background.joinToString(", "),
                        )
                }
        }

        reload()
    }

    private fun shiftDay(days: Long) {
        day = day.plusDays(days)
        reload()
    }

    /**
     * Ask Rust for the day and draw it.
     *
     * The JNI call reads a day of events out of SQLite and runs an O(n²) boundary sweep, so it goes
     * on [Dispatchers.IO]; a few thousand events is tens of milliseconds, but "a few thousand" is a
     * guess about the owner's data, not a guarantee (roadmap 3.2's Result records the measurements).
     */
    private fun reload() {
        tvDate.text = day.format(DISPLAY_FORMAT)
        tvDetail.text = getString(R.string.combined_tap_hint)
        progress.visibility = View.VISIBLE

        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val end = day.plusDays(1).atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        lifecycleScope.launch {
            val raw =
                withContext(Dispatchers.IO) {
                    try {
                        // An empty hostname->uuid map is correct, not a stub: since 3.1 every
                        // imported event carries `$aw.origin.device`, and an event predating that
                        // is attributed to the hostname captured from its bucket id, which keeps it
                        // visible as its own device instead of being folded into ours. A real map
                        // needs a `hostname` field in `devices/<uuid>/meta.json`, which is a shared
                        // schema change and does not belong in this step.
                        rustInterface.getCombinedTimeline(start, end, "{}")
                    } catch (e: Throwable) {
                        Log.e(TAG, "getCombinedTimeline failed", e)
                        null
                    }
                }
            progress.visibility = View.GONE

            val parsed = CombinedTimeline.parse(raw)
            if (parsed == null) {
                val message = CombinedTimeline.errorOf(raw) ?: getString(R.string.combined_load_failed)
                Log.w(TAG, "No timeline for $day: $message")
                timelineView.setTimeline(null)
                tvSummary.text = message
                return@launch
            }

            timelineView.setTimeline(parsed)
            tvSummary.text = summarise(parsed)
        }
    }

    /**
     * The line that makes **R6** visible: the combined total against the sum of the devices'.
     *
     * When two devices were used at once the combined figure is the smaller one, and that gap *is*
     * the point of the whole feature -- two hours of device activity inside one hour is one hour.
     */
    private fun summarise(tl: CombinedTimeline): String {
        if (tl.combined.isEmpty()) return getString(R.string.combined_empty_day)
        val combined = CombinedTimelineView.formatDuration(tl.combinedSeconds)
        val devices = CombinedTimelineView.formatDuration(tl.deviceSecondsTotal)
        val shaded = tl.combined.count { it.unresolved }
        return getString(R.string.combined_summary, combined, devices, tl.devices.size, shaded)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    }
}
