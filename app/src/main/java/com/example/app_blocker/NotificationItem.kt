package com.example.app_blocker

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class NotificationItem(
    val id: Long = System.currentTimeMillis(),
    var enabled: Boolean = true,
    var title: String = "",
    var text: String = "Notification",
    var isInterval: Boolean = true,
    var intervalMinutes: Int = 60,
    var exactHour: Int = 12,
    var exactMinute: Int = 0,
    var days: MutableSet<Int> = mutableSetOf(),
    var lastTriggeredMillis: Long = System.currentTimeMillis()
) : Comparable<NotificationItem> {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("enabled", enabled)
            put("title", title)
            put("text", text)
            put("isInterval", isInterval)
            put("intervalMinutes", intervalMinutes)
            put("exactHour", exactHour)
            put("exactMinute", exactMinute)
            put("days", JSONArray(days))
            put("lastTriggeredMillis", lastTriggeredMillis)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationItem {
            val daysSet = mutableSetOf<Int>()
            val daysArray = json.optJSONArray("days")
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    daysSet.add(daysArray.getInt(i))
                }
            }

            return NotificationItem(
                id = json.optLong("id", System.currentTimeMillis()),
                enabled = json.optBoolean("enabled", true),
                title = json.optString("title", ""),
                text = json.optString("text", "Notification"),
                isInterval = json.optBoolean("isInterval", true),
                intervalMinutes = json.optInt("intervalMinutes", 60),
                exactHour = json.optInt("exactHour", 12),
                exactMinute = json.optInt("exactMinute", 0),
                days = daysSet,
                lastTriggeredMillis = json.optLong("lastTriggeredMillis", System.currentTimeMillis())
            )
        }
    }

    override fun compareTo(other: NotificationItem): Int {
        return if (!this.isInterval && other.isInterval) {
            -1
        } else if (this.isInterval && !other.isInterval) {
            1
        } else if (!this.isInterval && !other.isInterval) {
            val thisTime = this.exactHour * 60 + this.exactMinute
            val otherTime = other.exactHour * 60 + other.exactMinute
            thisTime.compareTo(otherTime)
        } else {
            this.intervalMinutes.compareTo(other.intervalMinutes)
        }
    }

    fun getDaysSummary(): String {
        if (!enabled && days.isEmpty()) return "Never"
        if (days.isEmpty()) return "Once"
        if (days.size == 7) return "Daily"
        if (days.size == 5 && !days.contains(1) && !days.contains(7)) return "Mon - Fri"
        if (days.size == 2 && days.contains(1) && days.contains(7)) return "Weekends"

        val names = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
        return days.sorted().mapNotNull { names[it] }.joinToString(", ")
    }

    fun getTimeSummary(): String {
        return if (isInterval) {
            if (intervalMinutes >= 60) {
                val hours = intervalMinutes / 60
                val remainingMins = intervalMinutes % 60
                val timeStr = if (remainingMins > 0) "${hours}h ${remainingMins}m" else "${hours}h"
                "Every ${intervalMinutes}m ($timeStr)"
            } else {
                "Every ${intervalMinutes}m"
            }
        } else {
            String.format(Locale.US, "%02d:%02d", exactHour, exactMinute)
        }
    }
}