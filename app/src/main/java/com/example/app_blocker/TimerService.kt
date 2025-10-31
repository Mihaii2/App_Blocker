package com.example.app_blocker

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class TimerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var timerText: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var totalUsageToday: Long = 0
    private var sessionStartTime: Long = 0
    private var currentColorState = -1 // 0: green, 1: yellow, 2: orange, 3: red
    private var isTimerRunning = false
    private var midnightResetReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isTimerRunning) {
                return
            }

            val elapsed = System.currentTimeMillis() - sessionStartTime
            val currentTotal = totalUsageToday + elapsed

            updateTimerDisplay(currentTotal)

            handler.postDelayed(this, 1000)
        }
    }

    private val midnightResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("TimerService", "Midnight reset broadcast received inside service")
            performMidnightReset()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        Log.d("TimerService", "onCreate")

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        totalUsageToday = sharedPreferences.getLong("totalUsageToday", 0L)
        val transparency = sharedPreferences.getFloat("timerTransparency", 0.8f)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_timer_layout, null)
        timerText = floatingView.findViewById(R.id.timerText)
        floatingView.alpha = transparency

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = sharedPreferences.getInt("timer_position_x", 0)
        params.y = sharedPreferences.getInt("timer_position_y", 100)

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e("TimerService", "Error adding view to window manager", e)
        }

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        sharedPreferences.edit()
                            .putInt("timer_position_x", params.x)
                            .putInt("timer_position_y", params.y)
                            .apply()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TimerService", "onStartCommand")

        if (intent?.action == ACTION_MIDNIGHT_RESET) {
            Log.d("TimerService", "Handling ACTION_MIDNIGHT_RESET")
            performMidnightReset()
            return START_STICKY
        }

        startTimerIfNeeded()

        return START_STICKY
    }

    private fun updateBackgroundColorIfNeeded(millis: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val newState = when {
            minutes < 30 -> 0
            minutes < 60 -> 1
            minutes < 120 -> 2
            else -> 3
        }

        if (newState != currentColorState) {
            currentColorState = newState
            val color = when (newState) {
                0 -> Color.parseColor("#388E3C") // Dark Green
                1 -> Color.parseColor("#FBC02D") // Amber
                2 -> Color.parseColor("#F57C00") // Orange
                else -> Color.parseColor("#D32F2F") // Red
            }

            if (newState == 1) { // Amber
                timerText.setTextColor(Color.BLACK)
            } else {
                timerText.setTextColor(Color.WHITE)
            }

            val background = DrawableCompat.wrap(floatingView.background).mutate()
            DrawableCompat.setTint(background, color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TimerService", "onDestroy")
        stopTimer(saveProgress = true)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        if (midnightResetReceiverRegistered) {
            unregisterReceiver(midnightResetReceiver)
            midnightResetReceiverRegistered = false
        }
    }

    private fun startTimerIfNeeded() {
        if (isTimerRunning) {
            return
        }

        totalUsageToday = sharedPreferences.getLong("totalUsageToday", 0L)
        updateTimerDisplay(totalUsageToday)

        sessionStartTime = System.currentTimeMillis()
        isTimerRunning = true
        handler.post(timerRunnable)
        ensureMidnightResetReceiver()
    }

    private fun stopTimer(saveProgress: Boolean) {
        if (!isTimerRunning) {
            if (saveProgress) {
                totalUsageToday = sharedPreferences.getLong("totalUsageToday", 0L)
            }
            return
        }

        handler.removeCallbacks(timerRunnable)
        val elapsed = System.currentTimeMillis() - sessionStartTime
        totalUsageToday += elapsed
        if (saveProgress) {
            sharedPreferences.edit().putLong("totalUsageToday", totalUsageToday).apply()
        }
        isTimerRunning = false
        updateTimerDisplay(totalUsageToday)
    }

    private fun updateTimerDisplay(millis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        updateBackgroundColorIfNeeded(millis)
    }

    private fun performMidnightReset() {
        Log.d("TimerService", "performMidnightReset invoked")
        stopTimer(saveProgress = false)
        val previousDayUsage = totalUsageToday
        recordUsageForPreviousDay(previousDayUsage)
        totalUsageToday = 0L
        sharedPreferences.edit().putLong("totalUsageToday", totalUsageToday).apply()
        updateTimerDisplay(totalUsageToday)
        sessionStartTime = System.currentTimeMillis()
        isTimerRunning = true
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    private fun recordUsageForPreviousDay(durationMillis: Long) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        val historyString = sharedPreferences.getString("usageHistory", "[]")
        val historyArray = try {
            JSONArray(historyString)
        } catch (e: Exception) {
            Log.e("TimerService", "Failed to parse usage history JSON", e)
            JSONArray()
        }

        val entries = mutableListOf<JSONObject>()
        for (i in 0 until historyArray.length()) {
            val obj = historyArray.optJSONObject(i)
            if (obj != null) {
                entries.add(obj)
            }
        }

        val newEntry = JSONObject().apply {
            put("date", dateKey)
            put("durationMillis", durationMillis.coerceAtLeast(0L))
        }

        var replaced = false
        for (i in entries.indices) {
            if (entries[i].optString("date") == dateKey) {
                entries[i] = newEntry
                replaced = true
                break
            }
        }

        if (!replaced) {
            entries.add(newEntry)
        }

        entries.sortBy { it.optString("date") }
        while (entries.size > 60) {
            entries.removeAt(0)
        }

        val updatedArray = JSONArray()
        entries.forEach { updatedArray.put(it) }
        sharedPreferences.edit().putString("usageHistory", updatedArray.toString()).apply()
    }

    private fun ensureMidnightResetReceiver() {
        if (midnightResetReceiverRegistered) {
            return
        }
        val filter = IntentFilter(ACTION_MIDNIGHT_RESET)
        registerReceiver(midnightResetReceiver, filter)
        midnightResetReceiverRegistered = true
        Log.d("TimerService", "Midnight reset receiver registered")
    }

    companion object {
        const val ACTION_MIDNIGHT_RESET = "com.example.app_blocker.timer.ACTION_MIDNIGHT_RESET"

        fun triggerMidnightReset(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_MIDNIGHT_RESET
            }
            context.startService(intent)
        }
    }

}