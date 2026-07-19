package com.example.app_blocker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    private var lastPersistedSecond: Long = -1
    private var isPausedForScreenOff = false
    private var lastResetDate: String = ""
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isTimerRunning) {
                return
            }

            ensureDateConsistency()
            if (!isTimerRunning) {
                return
            }

            val elapsed = System.currentTimeMillis() - sessionStartTime
            val currentTotal = totalUsageToday + elapsed

            updateTimerDisplay(currentTotal)
            persistUsageProgressIfNeeded(currentTotal)

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
        isPausedForScreenOff = sharedPreferences.getBoolean(PREF_KEY_PAUSED_FOR_SCREEN_OFF, false)
        val today = currentDateString()
        val hasStoredResetDate = sharedPreferences.contains(PREF_KEY_LAST_RESET_DATE)
        lastResetDate = sharedPreferences.getString(PREF_KEY_LAST_RESET_DATE, today) ?: today
        if (!hasStoredResetDate) {
            sharedPreferences.edit().putString(PREF_KEY_LAST_RESET_DATE, lastResetDate).apply()
        }
        val needsInitialReset = lastResetDate != today
        val transparency = sharedPreferences.getFloat("timerTransparency", 0.8f)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_timer_layout, null)
        timerText = floatingView.findViewById(R.id.timerText)
        floatingView.alpha = transparency

        startForeground(NOTIFICATION_ID, buildNotification())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val storedX = sharedPreferences.getInt("timer_position_x", 0)
        val storedY = sharedPreferences.getInt("timer_position_y", 100)
        params.x = storedX
        params.y = storedY
        clampPosition(params)
        if (params.x != storedX || params.y != storedY) {
            sharedPreferences.edit()
                .putInt("timer_position_x", params.x)
                .putInt("timer_position_y", params.y)
                .apply()
        }

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e("TimerService", "Error adding view to window manager", e)
        }

        if (needsInitialReset) {
            Log.d("TimerService", "Date mismatch on create; forcing midnight reset")
            performMidnightReset()
        } else {
            updateTimerDisplay(totalUsageToday)
            scheduleAutoResetAlarm()
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
                        clampPosition(params)
                        sharedPreferences.edit()
                            .putInt("timer_position_x", params.x)
                            .putInt("timer_position_y", params.y)
                            .apply()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        clampPosition(params)
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

        var actionHandled = false
        when (intent?.action) {
            ACTION_MIDNIGHT_RESET -> {
                val today = currentDateString()
                if (lastResetDate == today) {
                    Log.d("TimerService", "Handling ACTION_MIDNIGHT_RESET but already reset for today; scheduling next alarm")
                    scheduleAutoResetAlarm()
                } else {
                    Log.d("TimerService", "Handling ACTION_MIDNIGHT_RESET")
                    performMidnightReset()
                }
                actionHandled = true
            }
            ACTION_STOP_TIMER -> {
                Log.d("TimerService", "Handling ACTION_STOP_TIMER")
                stopTimer(saveProgress = true)
                isPausedForScreenOff = true
                sharedPreferences.edit().putBoolean(PREF_KEY_PAUSED_FOR_SCREEN_OFF, true).apply()
                return START_STICKY
            }
            ACTION_RESUME_TIMER -> {
                Log.d("TimerService", "Handling ACTION_RESUME_TIMER")
                isPausedForScreenOff = false
                sharedPreferences.edit().putBoolean(PREF_KEY_PAUSED_FOR_SCREEN_OFF, false).apply()
            }
            else -> {
                // No special action; fall through to start logic below.
            }
        }

        if (!actionHandled) {
            ensureDateConsistency()
        }

        if (!isPausedForScreenOff) {
            startTimerIfNeeded()
        }

        return START_STICKY
    }

    private fun updateBackgroundColorIfNeeded(millis: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val newState = when {
            minutes < 60 -> 0
            minutes < 120 -> 1
            minutes < 180 -> 2
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        if (midnightResetReceiverRegistered) {
            unregisterReceiver(midnightResetReceiver)
            midnightResetReceiverRegistered = false
        }
        cancelAutoResetAlarm()
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
            lastPersistedSecond = TimeUnit.MILLISECONDS.toSeconds(totalUsageToday)
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
        val shouldResumeAfterReset = !isPausedForScreenOff
        stopTimer(saveProgress = false)
        val previousDayUsage = totalUsageToday
        recordUsageForPreviousDay(previousDayUsage)
        totalUsageToday = 0L
        sharedPreferences.edit().putLong("totalUsageToday", totalUsageToday).apply()
        lastPersistedSecond = TimeUnit.MILLISECONDS.toSeconds(totalUsageToday)
        updateTimerDisplay(totalUsageToday)
        handler.removeCallbacks(timerRunnable)
        if (shouldResumeAfterReset) {
            sessionStartTime = System.currentTimeMillis()
            isTimerRunning = true
            handler.post(timerRunnable)
        } else {
            isTimerRunning = false
            sharedPreferences.edit().putBoolean(PREF_KEY_PAUSED_FOR_SCREEN_OFF, true).apply()
        }
        lastResetDate = currentDateString()
        sharedPreferences.edit().putString(PREF_KEY_LAST_RESET_DATE, lastResetDate).apply()
        scheduleAutoResetAlarm()
    }

    private fun recordUsageForPreviousDay(durationMillis: Long) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dateKey = formatDate(calendar.time)

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

        // ONLY MODIFICATION TO BASE LAYER: Allow storing up to 10 years of days so 180 weeks aggregate perfectly
        while (entries.size > 3650) {
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
        ContextCompat.registerReceiver(
            this,
            midnightResetReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        midnightResetReceiverRegistered = true
        Log.d("TimerService", "Midnight reset receiver registered")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopTimer(saveProgress = true)
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        val restartIntent = Intent(applicationContext, TimerService::class.java)
        runCatching {
            ContextCompat.startForegroundService(applicationContext, restartIntent)
        }.onFailure { error ->
            Log.w("TimerService", "Immediate restart attempt failed; will rely on alarm", error)
        }
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        runCatching {
            alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }.onFailure { error ->
            Log.e("TimerService", "Failed to schedule timer restart", error)
        }
    }

    private fun ensureDateConsistency() {
        val today = currentDateString()
        if (lastResetDate != today) {
            Log.d("TimerService", "Detected day rollover (last=$lastResetDate, today=$today); performing reset")
            performMidnightReset()
        }
    }

    private fun scheduleAutoResetAlarm() {
        cancelAutoResetAlarm()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, TimerService::class.java).apply {
            action = ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = PendingIntent.getService(
            this,
            RESET_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val triggerAt = computeNextResetTriggerMillis()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d("TimerService", "Scheduled auto reset alarm for ${Date(triggerAt)}")
        }.onFailure { error ->
            Log.e("TimerService", "Failed to schedule auto reset alarm", error)
            runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Log.d("TimerService", "Scheduled inexact fallback alarm for ${Date(triggerAt)}")
            }.onFailure { fallbackError ->
                Log.e("TimerService", "Fallback auto reset alarm scheduling also failed", fallbackError)
            }
        }
    }

    private fun cancelAutoResetAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getService(
            this,
            RESET_ALARM_REQUEST_CODE,
            Intent(this, TimerService::class.java).apply { action = ACTION_MIDNIGHT_RESET },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun computeNextResetTriggerMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 0)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, RESET_GRACE_PERIOD_MINUTES)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun currentDateString(): String = formatDate(Date())

    private fun formatDate(date: Date): String = synchronized(dayFormatter) {
        dayFormatter.timeZone = TimeZone.getDefault()
        dayFormatter.format(date)
    }

    private fun persistUsageProgressIfNeeded(currentTotal: Long) {
        val currentSecond = TimeUnit.MILLISECONDS.toSeconds(currentTotal)
        if (currentSecond != lastPersistedSecond) {
            sharedPreferences.edit().putLong("totalUsageToday", currentTotal).apply()
            lastPersistedSecond = currentSecond
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.timer_service_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.timer_service_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.timer_service_notification_title))
            .setContentText(getString(R.string.timer_service_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        var viewWidth = when {
            floatingView.width > 0 -> floatingView.width
            floatingView.measuredWidth > 0 -> floatingView.measuredWidth
            else -> 0
        }
        var viewHeight = when {
            floatingView.height > 0 -> floatingView.height
            floatingView.measuredHeight > 0 -> floatingView.measuredHeight
            else -> 0
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            floatingView.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST)
            )
            if (viewWidth <= 0) {
                viewWidth = floatingView.measuredWidth
            }
            if (viewHeight <= 0) {
                viewHeight = floatingView.measuredHeight
            }
        }

        if (viewWidth <= 0) {
            viewWidth = metrics.widthPixels / 2
        }
        if (viewHeight <= 0) {
            viewHeight = metrics.heightPixels / 6
        }

        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)

        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    companion object {
        const val ACTION_MIDNIGHT_RESET = "com.example.app_blocker.timer.ACTION_MIDNIGHT_RESET"
        const val ACTION_STOP_TIMER = "com.example.app_blocker.timer.ACTION_STOP"
        const val ACTION_RESUME_TIMER = "com.example.app_blocker.timer.ACTION_RESUME"
        private const val RESTART_REQUEST_CODE = 1001
        private const val RESTART_DELAY_MS = 2_000L
        private const val NOTIFICATION_ID = 1337
        private const val NOTIFICATION_CHANNEL_ID = "timer_service_channel"
        private const val PREF_KEY_PAUSED_FOR_SCREEN_OFF = "timerPausedForScreenOff"
        private const val PREF_KEY_LAST_RESET_DATE = "timerLastResetDate"
        private const val RESET_ALARM_REQUEST_CODE = 1101
        private const val RESET_GRACE_PERIOD_MINUTES = 2

        fun triggerMidnightReset(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_MIDNIGHT_RESET
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestStopTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP_TIMER
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}