package com.example.app_blocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {

    private enum class HistoryMode { DAILY, WEEKLY }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var chartView: UsageChartView
    private lateinit var modeSpinner: Spinner
    private lateinit var summaryText: TextView

    private val storageDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        chartView = findViewById(R.id.usageChartView)
        modeSpinner = findViewById(R.id.modeSpinner)
        summaryText = findViewById(R.id.summaryText)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.history_timeframes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val mode = if (position == 1) HistoryMode.WEEKLY else HistoryMode.DAILY
                updateChart(mode)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                updateChart(HistoryMode.DAILY)
            }
        }

        modeSpinner.setSelection(0)
        updateChart(HistoryMode.DAILY)
    }

    private fun updateChart(mode: HistoryMode) {
        val dailyHistory = loadDailyHistory()
        val entries = when (mode) {
            HistoryMode.DAILY -> buildDailyChartEntries(dailyHistory)
            HistoryMode.WEEKLY -> buildWeeklyChartEntries(dailyHistory)
        }

        if (entries.isEmpty()) {
            summaryText.text = getString(R.string.history_empty_message)
        } else {
            val total = entries.sumOf { it.value }
            val average = if (entries.isNotEmpty()) total / entries.size else 0L
            val max = entries.maxOf { it.value }
            summaryText.text = getString(
                R.string.history_summary_format,
                entries.size,
                formatDuration(average),
                formatDuration(max)
            )
        }

        chartView.setData(entries)
    }

    private data class UsagePeriod(val dateKey: String, val durationMillis: Long)

    private fun loadDailyHistory(): List<UsagePeriod> {
        val historyString = sharedPreferences.getString("usageHistory", "[]")
        val historyArray = try {
            JSONArray(historyString)
        } catch (e: Exception) {
            JSONArray()
        }

        val records = mutableListOf<UsagePeriod>()
        for (i in 0 until historyArray.length()) {
            val obj = historyArray.optJSONObject(i) ?: continue
            val date = obj.optString("date")
            if (date.isNullOrBlank()) continue
            val duration = obj.optLong("durationMillis", 0L).coerceAtLeast(0L)
            records.add(UsagePeriod(date, duration))
        }

        records.sortBy { it.dateKey }

        val todayKey = storageDateFormat.format(Date())
        val todayDuration = sharedPreferences.getLong("totalUsageToday", 0L).coerceAtLeast(0L)
        val existingIndex = records.indexOfFirst { it.dateKey == todayKey }
        if (existingIndex >= 0) {
            records[existingIndex] = records[existingIndex].copy(durationMillis = todayDuration)
        } else {
            records.add(UsagePeriod(todayKey, todayDuration))
        }

        records.sortBy { it.dateKey }
        return if (records.size > 90) records.takeLast(90) else records
    }

    private fun buildDailyChartEntries(periods: List<UsagePeriod>): List<UsageChartView.ChartEntry> {
        if (periods.isEmpty()) return emptyList()
        val recent = periods.takeLast(14)
        return recent.map { period ->
            val label = toDisplayLabel(period.dateKey)
            UsageChartView.ChartEntry(label, period.durationMillis)
        }
    }

    private fun buildWeeklyChartEntries(periods: List<UsagePeriod>): List<UsageChartView.ChartEntry> {
        if (periods.isEmpty()) return emptyList()
        val sortedPeriods = periods.sortedBy { it.dateKey }
        val weeklyTotals = linkedMapOf<String, Long>()

        for (period in sortedPeriods) {
            val date = try {
                storageDateFormat.parse(period.dateKey)
            } catch (e: Exception) {
                null
            } ?: continue

            val weekStart = startOfWeek(date)
            val weekKey = storageDateFormat.format(weekStart)
            weeklyTotals[weekKey] = (weeklyTotals[weekKey] ?: 0L) + period.durationMillis
        }

        val orderedKeys = weeklyTotals.keys.sorted()
        val limitedKeys = if (orderedKeys.size > 12) orderedKeys.takeLast(12) else orderedKeys

        return limitedKeys.map { key ->
            val labelDate = try {
                storageDateFormat.parse(key)
            } catch (e: Exception) {
                null
            }
            val label = labelDate?.let { "Wk ${displayDateFormat.format(it)}" } ?: "Week $key"
            UsageChartView.ChartEntry(label, weeklyTotals[key] ?: 0L)
        }
    }

    private fun startOfWeek(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val offset = (dayOfWeek - Calendar.MONDAY + 7) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -offset)
        return calendar.time
    }

    private fun toDisplayLabel(dateKey: String): String {
        return try {
            val date = storageDateFormat.parse(dateKey)
            if (date != null) displayDateFormat.format(date) else dateKey
        } catch (e: Exception) {
            dateKey
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        return when {
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            minutes > 0 -> if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
