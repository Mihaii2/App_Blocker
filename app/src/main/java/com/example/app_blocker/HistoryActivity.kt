package com.example.app_blocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {

    private enum class HistoryMode { DAILY, WEEKLY }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var chartView: UsageChartView

    // UI Binds
    private lateinit var btnDailyMode: TextView
    private lateinit var btnWeeklyMode: TextView
    private lateinit var rangeSeekBar: SeekBar
    private lateinit var rangeValueLabel: TextView

    private var currentMode = HistoryMode.DAILY
    private var currentDisplayLimit = 180

    private val storageDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history)

        // Adjust layout bottom padding dynamically so system navigation bar doesn't overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.historyRootLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        chartView = findViewById(R.id.usageChartView)
        btnDailyMode = findViewById(R.id.btnDailyMode)
        btnWeeklyMode = findViewById(R.id.btnWeeklyMode)
        rangeSeekBar = findViewById(R.id.rangeSeekBar)
        rangeValueLabel = findViewById(R.id.rangeValueLabel)

        // Click logic for the compact [ D ] button
        btnDailyMode.setOnClickListener {
            if (currentMode != HistoryMode.DAILY) {
                currentMode = HistoryMode.DAILY
                toggleButtonStyles()
                updateRangeLabel()
                updateChart()
            }
        }

        // Click logic for the compact [ W ] button
        btnWeeklyMode.setOnClickListener {
            if (currentMode != HistoryMode.WEEKLY) {
                currentMode = HistoryMode.WEEKLY
                toggleButtonStyles()
                updateRangeLabel()
                updateChart()
            }
        }

        // Draggable slider bar adjustment handling logic
        rangeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentDisplayLimit = progress + 1
                updateRangeLabel()
                updateChart()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initial launch drawing trigger
        toggleButtonStyles()
        updateRangeLabel()
        updateChart()
    }

    private fun toggleButtonStyles() {
        if (currentMode == HistoryMode.DAILY) {
            btnDailyMode.setTextColor(Color.parseColor("#00E676")) // Active Green
            btnDailyMode.setBackgroundColor(Color.parseColor("#2D2D2D"))

            btnWeeklyMode.setTextColor(Color.parseColor("#888888")) // Idle Dim Grey
            btnWeeklyMode.setBackgroundColor(Color.parseColor("#1E1E1E"))
        } else {
            btnWeeklyMode.setTextColor(Color.parseColor("#00E676")) // Active Green
            btnWeeklyMode.setBackgroundColor(Color.parseColor("#2D2D2D"))

            btnDailyMode.setTextColor(Color.parseColor("#888888")) // Idle Dim Grey
            btnDailyMode.setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
    }

    private fun updateRangeLabel() {
        val suffix = if (currentMode == HistoryMode.WEEKLY) "w" else "d"
        rangeValueLabel.text = "$currentDisplayLimit$suffix"
    }

    private fun updateChart() {
        val dailyHistory = loadDailyHistory()
        val entries = when (currentMode) {
            HistoryMode.DAILY -> buildDailyChartEntries(dailyHistory)
            HistoryMode.WEEKLY -> buildWeeklyChartEntries(dailyHistory)
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
        return if (records.size > 3650) records.takeLast(3650) else records
    }

    private fun buildDailyChartEntries(periods: List<UsagePeriod>): List<UsageChartView.ChartEntry> {
        if (periods.isEmpty()) return emptyList()
        val recent = periods.takeLast(currentDisplayLimit)
        return recent.map { period ->
            UsageChartView.ChartEntry(toDisplayLabel(period.dateKey), period.durationMillis)
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
        val limitedKeys = if (orderedKeys.size > currentDisplayLimit) orderedKeys.takeLast(currentDisplayLimit) else orderedKeys

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
}