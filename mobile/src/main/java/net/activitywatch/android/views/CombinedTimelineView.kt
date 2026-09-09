package net.activitywatch.android.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import net.activitywatch.android.models.CombinedTimeline
import net.activitywatch.android.models.TimelineSpan

/**
 * The combined timeline (roadmap 3.4): the combined track drawn **above** the per-device tracks,
 * with unresolved contention shaded (**R8**).
 *
 * The layout is the one in [`04_COMBINED_TIMELINE.md`] §1 -- "a combined timeline above them that
 * will show everything", in the owner's words -- and the per-device rows below it are untouched raw
 * truth (**R11**), so the combined row can always be checked against what each device actually saw.
 *
 * A plain [View] rather than Compose: this module has no Compose dependency, and the whole screen
 * is a handful of rectangles on one horizontal time axis.
 */
class CombinedTimelineView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    View(context, attrs, defStyle) {

    /** Called when the owner taps a block on the combined track; null when they tap empty space. */
    var onSpanTapped: ((TimelineSpan?) -> Unit)? = null

    private var timeline: CombinedTimeline? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
        }
    private val hatch =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = Color.argb(150, 255, 255, 255)
        }
    private val gridPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 128, 128, 128)
            strokeWidth = dp(1f)
        }
    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f) }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(11f) }

    /**
     * A fixed palette indexed by the label, so one app keeps one colour across every row and across
     * a redraw. Hashing rather than assigning in order matters: the combined row and a device row
     * must agree on YouTube's colour for the eye to connect them.
     */
    private val palette =
        intArrayOf(
            Color.rgb(0x42, 0x85, 0xF4),
            Color.rgb(0xEA, 0x43, 0x35),
            Color.rgb(0xFB, 0xBC, 0x05),
            Color.rgb(0x34, 0xA8, 0x53),
            Color.rgb(0xAB, 0x47, 0xBC),
            Color.rgb(0x00, 0xAC, 0xC1),
            Color.rgb(0xFF, 0x70, 0x43),
            Color.rgb(0x8D, 0x6E, 0x63),
        )

    private fun colorFor(label: String) = palette[abs(label.hashCode()) % palette.size]

    /** Row geometry, recomputed on every draw because a row count change also changes the height. */
    private val combinedRowHeight = dp(46f)
    private val deviceRowHeight = dp(34f)
    private val rowGap = dp(10f)
    private val titleHeight = dp(18f)
    private val axisHeight = dp(20f)
    private val sidePad = dp(8f)

    /** Where each combined block ended up, so a tap can be matched back to it. */
    private val combinedHits = ArrayList<Pair<RectF, TimelineSpan>>()

    fun setTimeline(value: CombinedTimeline?) {
        timeline = value
        requestLayout() // the device count decides the height
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val deviceCount = timeline?.devices?.size ?: 0
        val height =
            (titleHeight + combinedRowHeight + rowGap) +
                deviceCount * (titleHeight + deviceRowHeight + rowGap) +
                axisHeight
        setMeasuredDimension(width, height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        combinedHits.clear()
        val tl = timeline ?: return
        val span = (tl.endMs - tl.startMs).toFloat()
        if (span <= 0f) return

        val left = sidePad
        val right = width - sidePad
        val trackWidth = right - left
        if (trackWidth <= 0f) return

        val textColor = titlePaint.color
        fun xOf(ms: Long): Float =
            left + ((ms - tl.startMs).toFloat() / span) * trackWidth

        var y = 0f

        // ---- combined track -------------------------------------------------------------
        titlePaint.isFakeBoldText = true
        canvas.drawText("Combined  ·  ${formatDuration(tl.combinedSeconds)}", left, y + dp(13f), titlePaint)
        titlePaint.isFakeBoldText = false
        y += titleHeight
        drawGrid(canvas, left, right, y, combinedRowHeight, tl)
        for (s in tl.combined) {
            val rect =
                RectF(xOf(s.startMs), y, max(xOf(s.endMs), xOf(s.startMs) + dp(1f)), y + combinedRowHeight)
            fill.color = colorFor(s.label)
            canvas.drawRect(rect, fill)
            if (s.unresolved) shade(canvas, rect)
            combinedHits.add(rect to s)
        }
        y += combinedRowHeight + rowGap

        // ---- per-device tracks, raw and unmodified (R11) ---------------------------------
        for (device in tl.devices) {
            val name = if (device.isOwn) "${device.displayName} (this device)" else device.displayName
            canvas.drawText(name, left, y + dp(13f), titlePaint)
            smallPaint.color = textColor
            val total = formatDuration(device.totalSeconds)
            canvas.drawText(total, right - smallPaint.measureText(total), y + dp(13f), smallPaint)
            y += titleHeight
            drawGrid(canvas, left, right, y, deviceRowHeight, tl)
            for (s in device.spans) {
                val rect =
                    RectF(xOf(s.startMs), y, max(xOf(s.endMs), xOf(s.startMs) + dp(1f)), y + deviceRowHeight)
                fill.color = colorFor(s.label)
                canvas.drawRect(rect, fill)
            }
            y += deviceRowHeight + rowGap
        }

        // ---- hour labels -----------------------------------------------------------------
        smallPaint.color = Color.argb(160, 128, 128, 128)
        val hours = span / 3_600_000f
        val step = if (hours > 12f) 6 else if (hours > 4f) 2 else 1
        var h = 0
        while (h * 3_600_000L <= (tl.endMs - tl.startMs)) {
            val x = left + (h * 3_600_000f / span) * trackWidth
            val text = "%02d".format((h) % 24)
            if (x + smallPaint.measureText(text) <= right) {
                canvas.drawText(text, x, y + dp(12f), smallPaint)
            }
            h += step
        }
    }

    /** Faint hour lines behind a track, so a block's position reads as a time of day. */
    private fun drawGrid(canvas: Canvas, left: Float, right: Float, top: Float, height: Float, tl: CombinedTimeline) {
        val span = (tl.endMs - tl.startMs).toFloat()
        canvas.drawRect(RectF(left, top, right, top + height), gridPaint.also { it.style = Paint.Style.FILL })
        gridPaint.style = Paint.Style.STROKE
        var h = 0L
        while (h * 3_600_000L <= (tl.endMs - tl.startMs)) {
            val x = left + (h * 3_600_000f / span) * (right - left)
            canvas.drawLine(x, top, x, top + height, gridPaint)
            h += 1
        }
    }

    /**
     * The shading for unresolved contention (**R8**): diagonal stripes plus a dark border over the
     * block's own colour.
     *
     * Stripes rather than a lighter tint on purpose -- a tint reads as "less of this activity",
     * which is the opposite of what the flag means. The block's time *is* counted; what is
     * uncertain is which of the competing activities deserved it.
     */
    private fun shade(canvas: Canvas, rect: RectF) {
        val save = canvas.save()
        canvas.clipRect(rect)
        val step = dp(9f)
        var x = rect.left - rect.height()
        while (x < rect.right) {
            canvas.drawLine(x, rect.bottom, x + rect.height(), rect.top, hatch)
            x += step
        }
        canvas.restoreToCount(save)
        stroke.color = Color.argb(200, 33, 33, 33)
        canvas.drawRect(rect, stroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val hit = combinedHits.firstOrNull { it.first.contains(event.x, event.y) }
        onSpanTapped?.invoke(hit?.second)
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        /** `2h 05m`, `05m 30s`, `12s` -- short enough to sit on a row title. */
        fun formatDuration(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return when {
                h > 0 -> "%dh %02dm".format(h, m)
                m > 0 -> "%02dm %02ds".format(m, s)
                else -> "%ds".format(s)
            }
        }
    }
}
