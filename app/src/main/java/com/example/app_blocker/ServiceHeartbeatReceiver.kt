package com.example.app_blocker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class ServiceHeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Heartbeat received - ensuring services are running")

        // Keep core background screen analytics alive exclusively
        try {
            val trackerIntent = Intent(context, ScreenTimeTrackerService::class.java)
            context.startService(trackerIntent)
            Log.d(TAG, "ScreenTimeTrackerService restart requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ScreenTimeTrackerService", e)
        }

        scheduleHeartbeat(context)
    }

    companion object {
        private const val TAG = "ServiceHeartbeat"
        private const val REQUEST_CODE = 9001
        const val ACTION_HEARTBEAT = "com.example.app_blocker.ACTION_SERVICE_HEARTBEAT"
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L

        fun scheduleHeartbeat(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ServiceHeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                try {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to schedule heartbeat alarm", e2)
                }
            }
        }

        fun triggerImmediateHeartbeat(context: Context) {
            try {
                context.startService(Intent(context, ScreenTimeTrackerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ScreenTimeTrackerService", e)
            }
            scheduleHeartbeat(context)
        }
    }
}