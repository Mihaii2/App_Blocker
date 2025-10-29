package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.app_blocker.MIDNIGHT") {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putLong("totalUsageToday", 0L).apply()
        }
    }
}