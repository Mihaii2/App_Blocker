package com.example.app_blocker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var timerText: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var totalUsageToday: Long = 0
    private var sessionStartTime: Long = 0
    private var currentColorState = -1 // 0: green, 1: yellow, 2: orange, 3: red

    private val handler = Handler()
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            val currentTotal = totalUsageToday + elapsed

            val hours = TimeUnit.MILLISECONDS.toHours(currentTotal)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(currentTotal) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(currentTotal) % 60

            timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            updateBackgroundColorIfNeeded(currentTotal)

            handler.postDelayed(this, 1000)
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
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "TimerServiceChannel")
            .setContentTitle("Usage Timer")
            .setContentText("Tracking phone usage")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        sessionStartTime = System.currentTimeMillis()
        handler.post(timerRunnable)

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
        handler.removeCallbacks(timerRunnable)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }

        val elapsed = System.currentTimeMillis() - sessionStartTime
        sharedPreferences.edit().putLong("totalUsageToday", totalUsageToday + elapsed).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "TimerServiceChannel",
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}