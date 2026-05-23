package com.example.zejioscafese.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.zejioscafese.R
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Canvas-based vertical bar chart for the revenue timeline. Bars are
 * tappable — the Fragment uses this to let the user drill into a single
 * bucket (a day, hour, week, etc.). The selected bar is rendered in the
 * primary brand color; other bars stay in the muted highlight color.
 */
class RevenueBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<SalesTimelinePoint> = emptyList()
    private var selectedLabel: String? = null
    // Optional Y-axis ceiling supplied by the ViewModel. When set, the
    // chart scales bars against this value instead of `data.max`, so
    // toggling a filter doesn't reshape the Y-axis under the user.
    private var referenceMax: Double = 0.0
    private val barRects = mutableListOf<BarHit>()
    private var onBarClick: ((SalesTimelinePoint) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_bar)
        style = Paint.Style.FILL
    }

    private val barHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_bar_light)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_chip_bg)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_text_secondary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_primary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val valueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_text_secondary)
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }

    private val barRadius = 6f

    fun setData(records: List<SalesTimelinePoint>) {
        data = records
        invalidate()
    }

    fun setSelectedLabel(label: String?) {
        if (selectedLabel == label) return
        selectedLabel = label
        invalidate()
    }

    fun setReferenceMax(value: Double) {
        if (referenceMax == value) return
        referenceMax = value
        invalidate()
    }

    fun setOnBarClickListener(listener: ((SalesTimelinePoint) -> Unit)?) {
        onBarClick = listener
        isClickable = listener != null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barRects.clear()
        if (data.isEmpty()) return

        val paddingLeft = 80f
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 40f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Prefer the externally-supplied reference max (typically the
        // unfiltered ALL-filter timeline max) so the Y-axis stays
        // anchored when the user toggles filters. Fall back to the
        // visible data when no reference is set.
        val rawMax = maxOf(referenceMax, data.maxOf { it.totalSales })
        if (rawMax <= 0) {
            // Still draw an empty grid so the chart doesn't collapse to
            // nothing — keeps the surface visible/tappable when a filter
            // wipes every bucket.
            drawGrid(canvas, paddingLeft, paddingTop, chartWidth, chartHeight, 0.0)
            data.forEachIndexed { index, record ->
                val barWidth = chartWidth / data.size
                val x = paddingLeft + index * barWidth
                val paint = if (record.label == selectedLabel) selectedLabelPaint else labelPaint
                canvas.drawText(record.label, x + barWidth / 2, height.toFloat() - 4f, paint)
            }
            return
        }

        // Round the chart's max upward with ~15% headroom so the tallest
        // bar doesn't fill the entire frame — without this, a top bar of
        // PHP 3,000 in a PHP 3,300 month looks like the whole month.
        val maxSales = niceMax(rawMax)
        drawGrid(canvas, paddingLeft, paddingTop, chartWidth, chartHeight, maxSales)

        val barCount = data.size
        val totalGapRatio = 0.3f
        val barWidth = chartWidth / (barCount + (barCount - 1) * totalGapRatio)
        val gapWidth = barWidth * totalGapRatio

        data.forEachIndexed { index, record ->
            val barHeight = (record.totalSales / maxSales * chartHeight).toFloat()
            val x = paddingLeft + index * (barWidth + gapWidth)
            val y = paddingTop + chartHeight - barHeight
            val barTop = if (barHeight > 0f) y else paddingTop + chartHeight - 2f
            val rect = RectF(x, barTop, x + barWidth, paddingTop + chartHeight)

            val isSelected = record.label == selectedLabel
            // Selected bar pops in the primary brand color; everything
            // else fades to the muted highlight so the focus is clear.
            val paint = if (isSelected) barPaint else barHighlightPaint
            canvas.drawRoundRect(rect, barRadius, barRadius, paint)

            // Tap target spans the entire vertical column so users can
            // hit very small bars (or empty buckets) reliably.
            barRects.add(
                BarHit(
                    record = record,
                    bounds = RectF(x, paddingTop, x + barWidth, paddingTop + chartHeight)
                )
            )

            if (barCount <= 14 || index % (barCount / 7 + 1) == 0) {
                val labelPaintToUse = if (isSelected) selectedLabelPaint else labelPaint
                canvas.drawText(record.label, x + barWidth / 2, height.toFloat() - 4f, labelPaintToUse)
            }
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        paddingLeft: Float,
        paddingTop: Float,
        chartWidth: Float,
        chartHeight: Float,
        maxSales: Double
    ) {
        for (i in 0..4) {
            val y = paddingTop + chartHeight * (1 - i / 4f)
            canvas.drawLine(paddingLeft, y, width - 16f, y, gridPaint)

            val value = maxSales * i / 4
            valueLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxisLabel(value), paddingLeft - 8f, y + 6f, valueLabelPaint)
        }
    }

    private fun formatAxisLabel(value: Double): String {
        if (value < 1000.0) return value.toInt().toString()
        val thousands = value / 1000.0
        // Show one decimal when the value isn't a whole "k" (e.g. 2.5k)
        // so the gridline labels stay accurate when niceMax lands on a
        // value that doesn't divide evenly by 4.
        return if (thousands % 1.0 == 0.0) {
            "${thousands.toInt()}k"
        } else {
            String.format(Locale.getDefault(), "%.1fk", thousands)
        }
    }

    /**
     * Rounds a raw chart max upward to a clean value with ~15% headroom.
     * Snaps to a multiple of 1, 2, 4, 8, or 10 × its order of magnitude,
     * so the 4 grid divisions always land on whole-ish numbers (e.g. a
     * raw max of 3,000 → chart max of 4,000 → grid 0/1k/2k/3k/4k).
     */
    private fun niceMax(rawMax: Double): Double {
        if (rawMax <= 0.0) return 1.0
        val target = rawMax * 1.15
        val magnitude = 10.0.pow(floor(log10(target)))
        val normalized = target / magnitude
        val niceFraction = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 4.0 -> 4.0
            normalized <= 8.0 -> 8.0
            else -> 10.0
        }
        return niceFraction * magnitude
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || onBarClick == null) {
            return super.onTouchEvent(event)
        }
        val hit = barRects.firstOrNull { it.bounds.contains(event.x, event.y) } ?: return false
        performClick()
        onBarClick?.invoke(hit.record)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private data class BarHit(val record: SalesTimelinePoint, val bounds: RectF)
}
