package com.example.app_blocker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Calendar

class NotificationSchedulerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        val notifications = loadNotifications(sharedPrefs).toMutableList()
        if (notifications.isEmpty()) return

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val nowMillis = System.currentTimeMillis()

        val globalStartHour = sharedPrefs.getInt("globalStartHour", 10)
        val globalEndHour = sharedPrefs.getInt("globalEndHour", 22)
        val isWithinGlobalHours = currentHour in globalStartHour until globalEndHour

        var listModified = false

        for (item in notifications) {
            if (!item.enabled) continue

            val matchesDay = item.days.isEmpty() || item.days.contains(currentDayOfWeek)
            if (!matchesDay) continue

            var shouldTrigger = false

            if (item.isInterval) {
                if (isWithinGlobalHours) {
                    val requiredIntervalMillis = item.intervalMinutes * 60 * 1000L
                    if (nowMillis - item.lastTriggeredMillis >= requiredIntervalMillis) {
                        shouldTrigger = true
                    }
                }
            } else {
                if (item.exactHour == currentHour && Math.abs(item.exactMinute - currentMinute) <= 1) {
                    if (nowMillis - item.lastTriggeredMillis >= 120_000L) {
                        shouldTrigger = true
                    }
                }
            }

            if (shouldTrigger) {
                triggerNotification(context, item)
                item.lastTriggeredMillis = nowMillis
                listModified = true

                if (item.days.isEmpty()) {
                    item.enabled = false
                }
            }
        }

        if (listModified) {
            saveNotifications(sharedPrefs, notifications)
        }

        scheduleNextCheck(context)
    }

    private fun triggerNotification(context: Context, item: NotificationItem) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "scheduled_notifications_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Scheduled Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (item.title.isNotBlank()) {
            builder.setContentTitle(item.title)
            builder.setContentText(item.text)
        } else {
            builder.setContentTitle(item.text)
        }

        notificationManager.notify(item.id.toInt(), builder.build())
    }

    companion object {
        const val ACTION_SCHEDULER_ALARM = "com.example.app_blocker.ACTION_SCHEDULER_ALARM"

        // NOW SAVES AND LOADS USING `StringSet` EXACTLY LIKE KEYWORDS ACTIVITY
        fun loadNotifications(prefs: SharedPreferences): List<NotificationItem> {
            val stringSet = prefs.getStringSet("notifications", emptySet()) ?: emptySet()
            val list = mutableListOf<NotificationItem>()
            for (str in stringSet) {
                try {
                    list.add(NotificationItem.fromStorageString(str))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return list
        }

        fun saveNotifications(prefs: SharedPreferences, list: List<NotificationItem>) {
            val stringSet = list.map { it.toStorageString() }.toSet()
            prefs.edit().putStringSet("notifications", stringSet).apply()
        }

        fun scheduleNextCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NotificationSchedulerReceiver::class.java).apply {
                action = ACTION_SCHEDULER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAt = SystemClock.elapsedRealtime() + 60_000L

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }
}