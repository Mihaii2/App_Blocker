package com.example.app_blocker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Periodically restarts the tracking services to ensure they stay alive.
 * This acts as a "heartbeat" that keeps the services running even if Android
 * kills them in the background.
 */
class ServiceHeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Heartbeat received - ensuring services are running")

        // Restart ScreenTimeTrackerService (which manages TimerService)
        try {
            val trackerIntent = Intent(context, ScreenTimeTrackerService::class.java)
            context.startService(trackerIntent)
            Log.d(TAG, "ScreenTimeTrackerService restart requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ScreenTimeTrackerService", e)
        }

        // Restart SleepReminderService
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                val reminderIntent = Intent(context, SleepReminderService::class.java)
                ContextCompat.startForegroundService(context, reminderIntent)
                Log.d(TAG, "SleepReminderService restart requested")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart SleepReminderService", e)
        }

        // Schedule the next heartbeat
        scheduleHeartbeat(context)
    }

    companion object {
        private const val TAG = "ServiceHeartbeat"
        private const val REQUEST_CODE = 9001
        const val ACTION_HEARTBEAT = "com.example.app_blocker.ACTION_SERVICE_HEARTBEAT"
        
        // Heartbeat interval: 15 minutes
        // This is frequent enough to catch service deaths quickly,
        // but not so frequent as to drain battery significantly
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        /**
         * Schedules a periodic heartbeat to keep services alive.
         * Should be called from MainActivity when user starts tracking,
         * and from BootReceiver on device boot.
         */
        fun scheduleHeartbeat(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager not available")
                return
            }

            val intent = Intent(context, ServiceHeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Calculate next trigger time
            val triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Use setExactAndAllowWhileIdle for more reliable wakeup on newer devices
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Heartbeat scheduled for ${HEARTBEAT_INTERVAL_MS / 60000} minutes from now")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule exact alarm, falling back to inexact", e)
                try {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to schedule heartbeat alarm", e2)
                }
            }
        }

        /**
         * Cancels the scheduled heartbeat.
         */
        fun cancelHeartbeat(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, ServiceHeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Heartbeat cancelled")
            }
        }

        /**
         * Immediately triggers a heartbeat to start services right now,
         * then schedules the next one.
         */
        fun triggerImmediateHeartbeat(context: Context) {
            Log.d(TAG, "Triggering immediate heartbeat")
            
            // Restart ScreenTimeTrackerService
            try {
                val trackerIntent = Intent(context, ScreenTimeTrackerService::class.java)
                context.startService(trackerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ScreenTimeTrackerService", e)
            }

            // Restart SleepReminderService
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                    val reminderIntent = Intent(context, SleepReminderService::class.java)
                    ContextCompat.startForegroundService(context, reminderIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SleepReminderService", e)
            }

            // Schedule next heartbeat
            scheduleHeartbeat(context)
        }
    }
}
