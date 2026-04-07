package com.example.zejioscafese.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.zejioscafese.R
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import java.util.Locale

/**
 * A simple Canvas-based vertical bar chart for showing revenue across the selected report granularity.
 * No external charting library required.
 */
class RevenueBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<SalesTimelinePoint> = emptyList()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val paddingLeft = 80f
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 40f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val maxSales = data.maxOf { it.totalSales }
        if (maxSales <= 0) return

        // Draw horizontal grid lines (4 lines)
        for (i in 0..4) {
            val y = paddingTop + chartHeight * (1 - i / 4f)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)

            val value = maxSales * i / 4
            val label = if (value >= 1000) "${(value / 1000).toInt()}k" else "${value.toInt()}"
            valueLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, paddingLeft - 8f, y + 6f, valueLabelPaint)
        }

        val barCount = data.size
        val totalGapRatio = 0.3f
        val barWidth = chartWidth / (barCount + (barCount - 1) * totalGapRatio)
        val gapWidth = barWidth * totalGapRatio

        data.forEachIndexed { index, record ->
            val barHeight = (record.totalSales / maxSales * chartHeight).toFloat()
            val x = paddingLeft + index * (barWidth + gapWidth)
            val y = paddingTop + chartHeight - barHeight

            val rect = RectF(x, y, x + barWidth, paddingTop + chartHeight)
            val paint = if (index == data.lastIndex) barPaint else barHighlightPaint
            canvas.drawRoundRect(rect, barRadius, barRadius, paint)

            // Date label below
            if (barCount <= 14 || index % (barCount / 7 + 1) == 0) {
                canvas.drawText(record.label, x + barWidth / 2, height.toFloat() - 4f, labelPaint)
            }
        }
    }
}
