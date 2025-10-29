package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            Log.d("UnlockReceiver", "Screen unlocked")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                Log.d("UnlockReceiver", "Overlay permission is granted, starting service")
                val serviceIntent = Intent(context, TimerService::class.java)
                context.startService(serviceIntent)
            } else {
                Log.d("UnlockReceiver", "Overlay permission is NOT granted")
            }
        }
    }
}