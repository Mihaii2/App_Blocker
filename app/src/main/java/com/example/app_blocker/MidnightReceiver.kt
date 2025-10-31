package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.app_blocker.MIDNIGHT") {
            TimerService.triggerMidnightReset(context)
        }
    }
}