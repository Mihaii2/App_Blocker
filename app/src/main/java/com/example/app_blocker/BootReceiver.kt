package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if accessibility service is enabled
            if (isAccessibilityServiceEnabled(context, BlockerService::class.java)) {
                // The service will be started by the system, no need to start it here.
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString != null && prefString.contains(context.packageName + "/" + service.name)
    }
}
