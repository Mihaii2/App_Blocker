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
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(11f)
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
        val chartTop = paddingTop + dp(8f)
        val chartHeight = chartBottom - chartTop
        val chartLeft = paddingLeft + dp(8f)
        val chartRight = width - paddingRight - dp(8f)

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

        val slotWidth = (chartRight - chartLeft) / entries.size.coerceAtLeast(1)
        val barWidth = slotWidth * 0.6f
        val axisY = chartBottom
        canvas.drawLine(chartLeft, axisY, chartRight, axisY, axisPaint)

        entries.forEachIndexed { index, entry ->
            val normalized = entry.value / maxValue.toFloat()
            val barHeight = normalized * chartHeight
            val left = chartLeft + slotWidth * index + (slotWidth - barWidth) / 2f
            val right = left + barWidth
            val top = axisY - barHeight
            val rect = RectF(left, top, right, axisY)
            canvas.drawRoundRect(rect, dp(6f), dp(6f), barPaint)

            val xCenter = (left + right) / 2f
            val valueLabel = formatValue(entry.value)
            val valueBaseline = max(top - dp(6f), chartTop + valuePaint.textSize)
            canvas.drawText(valueLabel, xCenter, valueBaseline, valuePaint)

            val labelBaseline = axisY - labelMetrics.ascent + dp(4f)
            canvas.drawText(entry.label, xCenter, labelBaseline, labelPaint)
        }
    }

    private fun formatValue(value: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(value)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(value) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(value) % 60
        return when {
            hours >= 1 -> if (minutes >= 1) "${hours}h ${minutes}m" else "${hours}h"
            minutes >= 1 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
