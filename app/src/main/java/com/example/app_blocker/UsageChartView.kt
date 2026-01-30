package com.example.app_blocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class UsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ChartEntry(val label: String, val value: Long)

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB")
        strokeWidth = dp(1f)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
    }

    private var entries: List<ChartEntry> = emptyList()

    fun setData(entries: List<ChartEntry>) {
        this.entries = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) {
            return
        }

        val labelMetrics = labelPaint.fontMetrics
        val labelHeight = labelMetrics.bottom - labelMetrics.top
        val chartBottom = height - paddingBottom - labelHeight - dp(12f)
        val chartTop = paddingTop + dp(20f) // Extra space for value labels
        val chartHeight = chartBottom - chartTop
        val chartLeft = paddingLeft + dp(4f)
        val chartRight = width - paddingRight - dp(4f)

        if (entries.isEmpty()) {
            canvas.drawText(
                resources.getString(R.string.history_empty_message),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val maxValue = entries.maxOfOrNull { it.value } ?: 0L
        if (maxValue <= 0L || chartHeight <= 0f) {
            canvas.drawText(
                resources.getString(R.string.history_empty_message),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        // Dynamically adjust text size based on number of entries
        val entryCount = entries.size
        val slotWidth = (chartRight - chartLeft) / entryCount.coerceAtLeast(1)
        
        // Scale text size down if slots are narrow
        val textScale = when {
            slotWidth < dp(25f) -> 0.7f
            slotWidth < dp(35f) -> 0.8f
            slotWidth < dp(45f) -> 0.9f
            else -> 1.0f
        }
        
        labelPaint.textSize = sp(10f) * textScale
        valuePaint.textSize = sp(9f) * textScale

        val barWidth = slotWidth * 0.65f
        val axisY = chartBottom
        canvas.drawLine(chartLeft, axisY, chartRight, axisY, axisPaint)

        entries.forEachIndexed { index, entry ->
            val normalized = entry.value / maxValue.toFloat()
            val barHeight = normalized * chartHeight
            val left = chartLeft + slotWidth * index + (slotWidth - barWidth) / 2f
            val right = left + barWidth
            val top = axisY - barHeight
            val rect = RectF(left, top, right, axisY)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), barPaint)

            val xCenter = (left + right) / 2f
            
            // Format value - use compact format for narrow slots
            val valueLabel = formatValue(entry.value, slotWidth < dp(40f))
            
            // Ensure value label doesn't go above chart top
            val valueBaseline = max(top - dp(4f), chartTop)
            canvas.drawText(valueLabel, xCenter, valueBaseline, valuePaint)

            // Draw label below axis - truncate if needed
            val labelBaseline = axisY - labelMetrics.ascent + dp(2f)
            val displayLabel = truncateLabelIfNeeded(entry.label, slotWidth)
            canvas.drawText(displayLabel, xCenter, labelBaseline, labelPaint)
        }
    }

    private fun formatValue(value: Long, compact: Boolean): String {
        val hours = TimeUnit.MILLISECONDS.toHours(value)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(value) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(value) % 60
        
        return when {
            hours >= 100 -> "${hours}h" // Very large: just show hours
            hours >= 10 -> if (compact) "${hours}h" else "${hours}h"
            hours >= 1 -> if (compact || minutes == 0L) "${hours}h" else "${hours}h${minutes}m"
            minutes >= 1 -> if (compact) "${minutes}m" else "${minutes}m"
            else -> "${seconds}s"
        }
    }
    
    private fun truncateLabelIfNeeded(label: String, maxWidth: Float): String {
        val textWidth = labelPaint.measureText(label)
        if (textWidth <= maxWidth * 0.95f) {
            return label
        }
        
        // Try progressively shorter versions
        for (length in (label.length - 1) downTo 2) {
            val truncated = label.substring(0, length)
            if (labelPaint.measureText(truncated) <= maxWidth * 0.9f) {
                return truncated
            }
        }
        return label.take(2)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
