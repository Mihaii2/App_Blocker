package com.example.app_blocker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class WakeUpReminderService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var reminderView: View
    private lateinit var reminderText: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var viewAdded = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private val visibilityHandler = Handler(Looper.getMainLooper())
    private val visibilityCheckRunnable = object : Runnable {
        override fun run() {
            updateVisibilityForCurrentWindow()
            scheduleVisibilityCheck()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, buildNotification())

        reminderView = LayoutInflater.from(this).inflate(R.layout.floating_wake_up_reminder_layout, null)
        reminderText = reminderView.findViewById(R.id.wakeUpReminderText)

        val defaultText = sharedPreferences.getString(KEY_TEXT, DEFAULT_TEXT)
        reminderText.text = defaultText

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val storedX = sharedPreferences.getInt(KEY_POSITION_X, 0)
        val storedY = sharedPreferences.getInt(KEY_POSITION_Y, 400)
        params.x = storedX
        params.y = storedY

        adjustLayoutSize(params)
        clampPosition(params)
        if (params.x != storedX || params.y != storedY) {
            savePosition(params.x, params.y)
        }
        layoutParams = params

        reminderView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val currentParams = layoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = currentParams.x
                        initialY = currentParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        clampPosition(currentParams)
                        savePosition(currentParams.x, currentParams.y)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        currentParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        clampPosition(currentParams)
                        windowManager.updateViewLayout(reminderView, currentParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeUpReminderService", "onStartCommand: ${intent?.action}")

        // Ensure heartbeat is scheduled to keep services alive
        ServiceHeartbeatReceiver.scheduleHeartbeat(this)

        val text = sharedPreferences.getString(KEY_TEXT, DEFAULT_TEXT) ?: DEFAULT_TEXT
        reminderText.text = text

        layoutParams?.let {
            adjustLayoutSize(it)
            clampPosition(it)
        }

        updateVisibilityForCurrentWindow()
        scheduleVisibilityCheck()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        visibilityHandler.removeCallbacksAndMessages(null)
        removeViewIfNeeded()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureViewAdded() {
        if (!viewAdded && layoutParams != null) {
            runCatching {
                windowManager.addView(reminderView, layoutParams)
                viewAdded = true
            }.onFailure { error ->
                Log.e("WakeUpReminderService", "Failed to add reminder view", error)
            }
        } else if (viewAdded && layoutParams != null) {
            windowManager.updateViewLayout(reminderView, layoutParams)
        }
    }

    private fun removeViewIfNeeded() {
        if (viewAdded) {
            runCatching {
                windowManager.removeView(reminderView)
                viewAdded = false
            }.onFailure { error ->
                Log.e("WakeUpReminderService", "Failed to remove reminder view", error)
            }
        }
    }

    private fun adjustLayoutSize(params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val widthFraction = sharedPreferences.getFloat(KEY_WIDTH_FRACTION, DEFAULT_WIDTH_FRACTION)
            .coerceIn(0.2f, 1f)
        val heightFraction = sharedPreferences.getFloat(KEY_HEIGHT_FRACTION, DEFAULT_HEIGHT_FRACTION)
            .coerceIn(0.2f, 1f)

        val width = (displayMetrics.widthPixels * widthFraction).toInt().coerceAtLeast(1)
        val heightPx = (displayMetrics.heightPixels * heightFraction).toInt()

        params.width = width
        params.height = if (heightPx > 0) heightPx else WindowManager.LayoutParams.WRAP_CONTENT

        val layoutParamsForView = (reminderView.layoutParams
            ?: ViewGroup.LayoutParams(params.width, params.height)).apply {
            this.width = params.width
            if (params.height > 0) {
                this.height = params.height
            }
        }
        reminderView.layoutParams = layoutParamsForView
    }

    private fun savePosition(x: Int, y: Int) {
        sharedPreferences.edit()
            .putInt(KEY_POSITION_X, x)
            .putInt(KEY_POSITION_Y, y)
            .apply()
    }

    private fun isWithinConfiguredWindow(): Boolean {
        val startMinutes = sharedPreferences.getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES)
        val endMinutes = sharedPreferences.getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES)
        val currentMinutes = currentMinutesOfDay()
        return isWithinWindow(currentMinutes, startMinutes, endMinutes)
    }

    private fun calculateNextBoundaryWallClockMillis(): Long? {
        val startMinutes = sharedPreferences.getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES)
        val endMinutes = sharedPreferences.getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES)

        if (startMinutes == endMinutes) {
            return null
        }

        val now = Calendar.getInstance()
        val currentMinutes = currentMinutesOfDay(now)
        val withinWindow = isWithinWindow(currentMinutes, startMinutes, endMinutes)
        val targetMinutes = if (withinWindow) endMinutes else startMinutes

        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, (targetMinutes / 60) % 24)
            set(Calendar.MINUTE, targetMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis
    }

    private fun currentMinutesOfDay(calendar: Calendar = Calendar.getInstance()): Int {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun isWithinWindow(currentMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        if (startMinutes == endMinutes) {
            return true
        }
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val width = when {
            params.width > 0 -> params.width
            reminderView.width > 0 -> reminderView.width
            else -> (metrics.widthPixels * sharedPreferences.getFloat(KEY_WIDTH_FRACTION, DEFAULT_WIDTH_FRACTION)
                .coerceIn(0.2f, 1f)).toInt()
        }
        val height = when {
            params.height > 0 -> params.height
            reminderView.height > 0 -> reminderView.height
            else -> (metrics.heightPixels * sharedPreferences.getFloat(KEY_HEIGHT_FRACTION, DEFAULT_HEIGHT_FRACTION)
                .coerceIn(0.2f, 1f)).toInt()
        }

        val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - height).coerceAtLeast(0)

        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun scheduleVisibilityCheck() {
        visibilityHandler.removeCallbacks(visibilityCheckRunnable)
        val delay = computeNextVisibilityCheckDelay()
        visibilityHandler.postDelayed(visibilityCheckRunnable, delay)
    }

    private fun computeNextVisibilityCheckDelay(): Long {
        val now = System.currentTimeMillis()
        val nextBoundaryMillis = calculateNextBoundaryWallClockMillis()
        val baseAligned = ((now / BASE_VISIBILITY_CHECK_INTERVAL_MS) + 1) * BASE_VISIBILITY_CHECK_INTERVAL_MS
        val target = when {
            nextBoundaryMillis != null -> minOf(nextBoundaryMillis, baseAligned)
            else -> baseAligned
        }
        val rawDelay = target - now
        return rawDelay.coerceIn(MIN_VISIBILITY_CHECK_DELAY_MS, MAX_VISIBILITY_CHECK_DELAY_MS)
    }

    private fun updateVisibilityForCurrentWindow() {
        if (isWithinConfiguredWindow()) {
            ensureViewAdded()
        } else {
            removeViewIfNeeded()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        val restartIntent = Intent(applicationContext, WakeUpReminderService::class.java)
        runCatching {
            ContextCompat.startForegroundService(applicationContext, restartIntent)
        }.onFailure { error ->
            Log.w("WakeUpReminderService", "Immediate restart attempt failed; will rely on alarm", error)
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
            Log.e("WakeUpReminderService", "Failed to schedule reminder restart", error)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.wake_up_reminder_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.wake_up_reminder_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, WakeUpReminderSettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.wake_up_reminder_notification_title))
            .setContentText(getString(R.string.wake_up_reminder_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        const val KEY_TEXT = "wakeUpReminderText"
        const val KEY_START_MINUTES = "wakeUpReminderStartMinutes"
        const val KEY_END_MINUTES = "wakeUpReminderEndMinutes"
        const val KEY_POSITION_X = "wakeUpReminderPositionX"
        const val KEY_POSITION_Y = "wakeUpReminderPositionY"
        const val KEY_WIDTH_FRACTION = "wakeUpReminderWidthFraction"
        const val KEY_HEIGHT_FRACTION = "wakeUpReminderHeightFraction"

        const val DEFAULT_TEXT = "Time to get up!"
        const val DEFAULT_START_MINUTES = 9 * 60   // 9:00 AM
        const val DEFAULT_END_MINUTES = 11 * 60    // 11:00 AM
        const val DEFAULT_WIDTH_FRACTION = 0.7f
        const val DEFAULT_HEIGHT_FRACTION = 0.33f

        private const val NOTIFICATION_ID = 1340
        private const val NOTIFICATION_CHANNEL_ID = "wake_up_reminder_channel"
        private const val RESTART_REQUEST_CODE = 1341
        private const val RESTART_DELAY_MS = 2_000L

        private const val BASE_VISIBILITY_CHECK_INTERVAL_MS = 60_000L
        private const val MIN_VISIBILITY_CHECK_DELAY_MS = 1_000L
        private const val MAX_VISIBILITY_CHECK_DELAY_MS = 60_000L

        fun requestRefresh(context: Context) {
            val intent = Intent(context, WakeUpReminderService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
