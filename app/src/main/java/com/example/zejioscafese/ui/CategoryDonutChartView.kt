package com.example.zejioscafese.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.zejioscafese.R
import java.util.Locale
import kotlin.math.min

/**
 * Lightweight Canvas-drawn donut chart for the Sales-by-Category card.
 * Renders proportionally-sized slices with a hollow center showing the
 * total revenue. Slice colors are supplied by the Fragment so they stay
 * in sync with the category legend rows below.
 */
class CategoryDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Double, val color: Int)

    private var slices: List<Slice> = emptyList()
    private var totalLabel: String = ""

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val emptyTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pos_chip_bg)
    }
    private val centerHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pos_surface)
    }
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_text_secondary)
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val centerValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_text_primary)
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val emptyMessagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pos_text_secondary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    fun setSlices(slices: List<Slice>, totalLabel: String) {
        this.slices = slices
        this.totalLabel = totalLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val diameter = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = diameter / 2f - 4f
        val innerRadius = outerRadius * 0.62f

        val rect = RectF(
            centerX - outerRadius,
            centerY - outerRadius,
            centerX + outerRadius,
            centerY + outerRadius
        )

        val total = slices.sumOf(Slice::value)
        if (total <= 0.0 || slices.isEmpty()) {
            // Empty state: render the full ring in the neutral chip color
            // so the card still has visual weight even with no data.
            canvas.drawCircle(centerX, centerY, outerRadius, emptyTrackPaint)
            canvas.drawCircle(centerX, centerY, innerRadius, centerHolePaint)
            canvas.drawText(
                resources.getString(R.string.reports_empty_categories_short),
                centerX,
                centerY + 8f,
                emptyMessagePaint
            )
            return
        }

        var startAngle = -90f
        slices.forEach { slice ->
            if (slice.value <= 0.0) return@forEach
            val sweep = (slice.value / total * 360.0).toFloat()
            slicePaint.color = slice.color
            // A 2° gap between slices keeps adjacent colors readable
            // without distorting the proportions.
            canvas.drawArc(rect, startAngle + 1f, (sweep - 2f).coerceAtLeast(0f), true, slicePaint)
            startAngle += sweep
        }

        canvas.drawCircle(centerX, centerY, innerRadius, centerHolePaint)

        canvas.drawText(
            resources.getString(R.string.reports_donut_center_subtitle),
            centerX,
            centerY - 6f,
            centerTitlePaint
        )
        canvas.drawText(totalLabel, centerX, centerY + 24f, centerValuePaint)
    }

    companion object {
        fun formatCenterTotal(amount: Double): String {
            return String.format(Locale.getDefault(), "PHP %,.0f", amount)
        }
    }
}
