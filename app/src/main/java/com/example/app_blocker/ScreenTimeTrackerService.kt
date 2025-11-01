package com.example.app_blocker

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class ScreenTimeTrackerService : Service() {

    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                        val intent = Intent(context, TimerService::class.java).apply {
                            action = TimerService.ACTION_RESUME_TIMER
                        }
                        ContextCompat.startForegroundService(context, intent)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    TimerService.requestStopTimer(context)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        if (shouldResumeTimerNow()) {
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME_TIMER
            }
            ContextCompat.startForegroundService(this, intent)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            receiverRegistered = false
        }
    }

    private fun shouldResumeTimerNow(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return false
        }

        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager

        val isInteractive = when {
            powerManager == null -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH -> powerManager.isInteractive
            else -> powerManager.legacyIsScreenOn()
        }

        if (!isInteractive) {
            return false
        }

        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        return !isLocked
    }

    @Suppress("DEPRECATION")
    private fun PowerManager.legacyIsScreenOn(): Boolean = isScreenOn
}