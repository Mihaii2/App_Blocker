package com.example.app_blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed - starting services")
            
            // Start the heartbeat to keep services alive
            ServiceHeartbeatReceiver.triggerImmediateHeartbeat(context)
            
            // Check if accessibility service is enabled
            if (isAccessibilityServiceEnabled(context, BlockerService::class.java)) {
                // The service will be started by the system, no need to start it here.
                Log.d("BootReceiver", "Accessibility service is enabled")
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString != null && prefString.contains(context.packageName + "/" + service.name)
    }
}
