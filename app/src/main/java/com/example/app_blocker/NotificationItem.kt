package com.example.app_blocker

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

    // Converts object into a single delimiter-separated string format like KeywordsActivity
    fun toStorageString(): String {
        val daysJoined = days.joinToString(",")
        return "$id|$enabled|$title|$text|$isInterval|$intervalMinutes|$exactHour|$exactMinute|$daysJoined|$lastTriggeredMillis"
    }

    companion object {
        fun fromStorageString(str: String): NotificationItem {
            val parts = str.split("|")
            if (parts.size < 10) return NotificationItem()

            val id = parts[0].toLongOrNull() ?: System.currentTimeMillis()
            val enabled = parts[1].toBooleanStrictOrNull() ?: true
            val title = parts[2]
            val text = parts[3]
            val isInterval = parts[4].toBooleanStrictOrNull() ?: true
            val intervalMinutes = parts[5].toIntOrNull() ?: 60
            val exactHour = parts[6].toIntOrNull() ?: 12
            val exactMinute = parts[7].toIntOrNull() ?: 0

            val daysSet = mutableSetOf<Int>()
            if (parts[8].isNotBlank()) {
                parts[8].split(",").forEach {
                    it.toIntOrNull()?.let { day -> daysSet.add(day) }
                }
            }

            val lastTriggered = parts[9].toLongOrNull() ?: System.currentTimeMillis()

            return NotificationItem(
                id, enabled, title, text, isInterval,
                intervalMinutes, exactHour, exactMinute, daysSet, lastTriggered
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