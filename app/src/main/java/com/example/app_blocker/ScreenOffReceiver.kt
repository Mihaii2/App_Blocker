package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            val serviceIntent = Intent(context, TimerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}