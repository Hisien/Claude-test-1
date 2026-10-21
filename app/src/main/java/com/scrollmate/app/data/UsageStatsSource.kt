package com.scrollmate.app.data

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.scrollmate.core.usage.UsageEvent
import com.scrollmate.core.usage.UsageEventType
import java.time.Instant

/**
 * Reads raw usage events via [UsageStatsManager.queryEvents]. We deliberately do not use the
 * aggregated buckets of queryUsageStats: their boundaries do not reliably match local days.
 * No package names or durations are ever logged.
 */
class UsageStatsSource(private val context: Context) {

    @SuppressLint("InlinedApi")
    fun events(from: Instant, to: Instant): List<UsageEvent> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val raw = try {
            usm.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        } catch (e: SecurityException) {
            return emptyList()
        } ?: return emptyList()

        val result = ArrayList<UsageEvent>()
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            val type = when (event.eventType) {
                // ACTIVITY_RESUMED (API 29+) has the same value as the older MOVE_TO_FOREGROUND.
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.FOREGROUND
                // ACTIVITY_PAUSED (API 29+) has the same value as the older MOVE_TO_BACKGROUND.
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.BACKGROUND
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_INACTIVE
                else -> null
            } ?: continue
            result += UsageEvent(event.packageName, Instant.ofEpochMilli(event.timeStamp), type)
        }
        return result
    }
}
