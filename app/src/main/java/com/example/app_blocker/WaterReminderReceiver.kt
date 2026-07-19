package com.example.app_blocker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // <-- ADD THIS IMPORT
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Calendar

class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        val isManualTest = (intent.action == ACTION_WATER_ALARM) && (!sharedPrefs.getBoolean("waterReminderEnabled", false))

        if (!sharedPrefs.getBoolean("waterReminderEnabled", false) && !isManualTest) return

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val startHour = sharedPrefs.getInt("waterStartHour", 10)
        val endHour = sharedPrefs.getInt("waterEndHour", 22)

        if (isManualTest || (currentHour in startHour until endHour)) {
            triggerNotification(context, sharedPrefs)
        }

        if (sharedPrefs.getBoolean("waterReminderEnabled", false)) {
            scheduleNextReminder(context)
        }
    }

    private fun triggerNotification(context: Context, sharedPrefs: SharedPreferences) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "water_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Water Reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // READ CUSTOM CONTENT STRINGS LIVE FROM PREFERENCES CACHE
        val customTitle = sharedPrefs.getString("waterCustomTitle", "")?.trim()
        val customText = sharedPrefs.getString("waterCustomText", "Water")?.trim()
        val fallbackText = if (customText.isNullOrEmpty()) "Water" else customText

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Handle structural empty fields cleanly
        if (!customTitle.isNullOrEmpty()) {
            builder.setContentTitle(customTitle)
            builder.setContentText(fallbackText)
        } else {
            // If no title is supplied, push text up to act as primary line single header
            builder.setContentTitle(fallbackText)
        }

        notificationManager.notify(NOTIFICATION_WATER_ID, builder.build())
    }

    companion object {
        private const val NOTIFICATION_WATER_ID = 2001
        const val ACTION_WATER_ALARM = "com.example.app_blocker.ACTION_WATER_ALARM"

        fun scheduleNextReminder(context: Context) {
            val sharedPrefs = context.getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, WaterReminderReceiver::class.java).apply {
                action = ACTION_WATER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val intervalMinutes = sharedPrefs.getInt("waterIntervalMinutes", 60).coerceAtLeast(1)
            val triggerAt = SystemClock.elapsedRealtime() + (intervalMinutes * 60 * 1000L)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WaterReminderReceiver::class.java).apply {
                action = ACTION_WATER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}