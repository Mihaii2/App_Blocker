package com.example.app_blocker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.TimeUnit
import kotlin.math.max

class UsageChartView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ChartEntry(val label: String, val value: Long)

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB")
        strokeWidth = dp(1f)
    }

    // NEW PAINT: Soft grid lines behind the histogram bars
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF") // Very subtle translucent white
        strokeWidth = dp(0.8f)
        style = Paint.Style.STROKE
    }

    // NEW PAINT: Compact, dim labels along the left edge
    private val yAxisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#77FFFFFF") // Dim white
        textSize = sp(9f)
        textAlign = Paint.Align.LEFT
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

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        if (contentWidth <= 0 || contentHeight <= 0) return

        val labelMetrics = labelPaint.fontMetrics
        val labelHeight = labelMetrics.bottom - labelMetrics.top

        // Add 32dp left padding to leave explicit room for the new compact Y-axis labels
        val yAxisWidth = dp(32f)

        val chartBottom = height - paddingBottom - labelHeight - dp(12f)
        val chartTop = paddingTop + dp(20f)
        val chartHeight = chartBottom - chartTop
        val chartLeft = paddingLeft + yAxisWidth
        val chartRight = width - paddingRight - dp(4f)

        if (entries.isEmpty()) {
            canvas.drawText("No historical data logs saved", width / 2f, height / 2f, emptyPaint)
            return
        }

        val maxValue = entries.maxOfOrNull { it.value } ?: 0L
        if (maxValue <= 0L || chartHeight <= 0f) {
            canvas.drawText("No historical data logs saved", width / 2f, height / 2f, emptyPaint)
            return
        }

        // Draw the vertical y-axis line boundary
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        // Draw the horizontal x-axis base line
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        // DRAW COMPACT BACKGROUND GRID LINES AND Y-LABELS
        val maxHours = TimeUnit.MILLISECONDS.toHours(maxValue).toInt()
        val intervalHours = when {
            maxHours > 12 -> 4
            maxHours > 6 -> 2
            else -> 1
        }

        // Step through hour lines up to the peak value recorded
        var currentGridHour = intervalHours
        while (currentGridHour * 3600000L <= maxValue) {
            val gridValueMillis = currentGridHour * 3600000L
            val ratio = gridValueMillis / maxValue.toFloat()
            val gridY = chartBottom - (ratio * chartHeight)

            // Draw clean background horizontal line across the entire chart area
            canvas.drawLine(chartLeft, gridY, chartRight, gridY, gridPaint)

            // Draw neat shorthand text layout indicator along the left channel (e.g. "2h")
            canvas.drawText("${currentGridHour}h", paddingLeft + dp(4f), gridY + dp(3f), yAxisLabelPaint)

            currentGridHour += intervalHours
        }

        val entryCount = entries.size
        val availableWidth = chartRight - chartLeft
        val slotWidth = availableWidth / entryCount.coerceAtLeast(1)

        val textScale = when {
            slotWidth < dp(25f) -> 0.7f
            slotWidth < dp(35f) -> 0.8f
            slotWidth < dp(44f) -> 0.9f
            else -> 1.0f
        }

        labelPaint.textSize = sp(10f) * textScale
        valuePaint.textSize = sp(9f) * textScale

        val barWidth = slotWidth * 0.65f
        val linePoints = mutableListOf<Pair<Float, Float>>()

        entries.forEachIndexed { index, entry ->
            val normalized = entry.value / maxValue.toFloat()
            val barHeight = normalized * chartHeight
            val left = chartLeft + slotWidth * index + (slotWidth - barWidth) / 2f
            val right = left + barWidth
            val top = chartBottom - barHeight

            // Render histogram blocks inside layout boundary metrics safely
            if (barHeight > 0) {
                val rect = RectF(left, top, right, chartBottom)
                canvas.drawRoundRect(rect, dp(3f), dp(3f), barPaint)
            }

            val xCenter = (left + right) / 2f

            // DYNAMIC 10-DAY MOVING AVERAGE CALCULATOR
            var sum = 0L
            var count = 0
            for (i in max(0, index - 9)..index) {
                sum += entries[i].value
                count++
            }
            val averageValue = if (count > 0) sum / count else entry.value

            val lineNormalized = averageValue / maxValue.toFloat()
            val lineY = chartBottom - (lineNormalized * chartHeight)
            linePoints.add(Pair(xCenter, lineY))

            // Only overlay numeric texts if there is comfortable structural spacing
            if (slotWidth >= dp(32f)) {
                val valueLabel = formatValue(entry.value, slotWidth < dp(45f))
                val valueBaseline = max(top - dp(4f), chartTop)
                canvas.drawText(valueLabel, xCenter, valueBaseline, valuePaint)
            }

            // Clean x-axis text throttling routine based on total columns
            val moduloCheck = when {
                entryCount > 90 -> 14
                entryCount > 45 -> 7
                entryCount > 20 -> 4
                else -> 1
            }
            if (index % moduloCheck == 0) {
                val labelBaseline = chartBottom - labelMetrics.ascent + dp(4f)
                val displayLabel = truncateLabelIfNeeded(entry.label, slotWidth * moduloCheck)
                canvas.drawText(displayLabel, xCenter, labelBaseline, labelPaint)
            }
        }

        // Overlay the 10-day trending path on top layer
        if (linePoints.isNotEmpty()) {
            val path = Path()
            path.moveTo(linePoints[0].first, linePoints[0].second)
            for (i in 1 until linePoints.size) {
                path.lineTo(linePoints[i].first, linePoints[i].second)
            }
            canvas.drawPath(path, linePaint)
        }
    }

    private fun formatValue(value: Long, compact: Boolean): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(value)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(value) % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(value) % 60

        return when {
            hours >= 10 -> "${hours}h"
            hours >= 1 -> if (compact) "${hours}h" else "${hours}h${minutes}m"
            minutes >= 1 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private fun truncateLabelIfNeeded(label: String, maxWidth: Float): String {
        val textWidth = labelPaint.measureText(label)
        if (textWidth <= maxWidth * 0.95f) return label

        for (length in (label.length - 1) downTo 2) {
            val truncated = label.substring(0, length)
            if (labelPaint.measureText(truncated) <= maxWidth * 0.9f) return truncated
        }
        return label.take(2)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}