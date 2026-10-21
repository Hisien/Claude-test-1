package com.scrollmate.core.usage

import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import com.scrollmate.core.time.DayMath
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Platform-independent view of a raw usage event (mapped from Android's UsageEvents.Event in :app). */
enum class UsageEventType {
    /** An activity of the package came to the foreground (ACTIVITY_RESUMED / MOVE_TO_FOREGROUND). */
    FOREGROUND,

    /** An activity of the package left the foreground (ACTIVITY_PAUSED / MOVE_TO_BACKGROUND). */
    BACKGROUND,

    /** Screen off, keyguard shown or device shutdown: nothing is in the foreground any more. */
    DEVICE_INACTIVE,
}

data class UsageEvent(
    val packageName: String?,
    val timestamp: Instant,
    val type: UsageEventType,
)

/**
 * Computes foreground time per local calendar day and app from raw events.
 *
 * Rules:
 * - Only one app is in the foreground at a time; a FOREGROUND event closes any other open session.
 * - A BACKGROUND event for an app that was never seen opened in the range is treated as a session
 *   that started before [rangeStart] (e.g. an app open across midnight) and counted from [rangeStart].
 * - DEVICE_INACTIVE closes every open session.
 * - Sessions still open at [rangeEnd] are counted until [rangeEnd] (pass "now").
 * - Every session is split at local midnight, so a session 23:50–00:20 counts 10 + 20 minutes.
 */
object UsageAggregator {

    fun aggregate(
        events: List<UsageEvent>,
        rangeStart: Instant,
        rangeEnd: Instant,
        zone: ZoneId,
    ): Map<LocalDate, Map<String, Duration>> {
        val totals = LinkedHashMap<LocalDate, MutableMap<String, Duration>>()
        if (!rangeEnd.isAfter(rangeStart)) return totals

        fun add(pkg: String, from: Instant, to: Instant) {
            val start = maxOf(from, rangeStart)
            val end = minOf(to, rangeEnd)
            if (!end.isAfter(start)) return
            DayMath.splitByLocalDay(start, end, zone).forEach { (day, d) ->
                val perApp = totals.getOrPut(day) { LinkedHashMap() }
                perApp[pkg] = (perApp[pkg] ?: Duration.ZERO).plus(d)
            }
        }

        var openPackage: String? = null
        var openSince: Instant? = null
        var anySessionSeen = false

        val sorted = events
            .filter { !it.timestamp.isBefore(rangeStart) && !it.timestamp.isAfter(rangeEnd) }
            .sortedBy { it.timestamp }

        for (event in sorted) {
            when (event.type) {
                UsageEventType.FOREGROUND -> {
                    val pkg = event.packageName ?: continue
                    if (openPackage == pkg) continue
                    if (openPackage != null) add(openPackage!!, openSince!!, event.timestamp)
                    openPackage = pkg
                    openSince = event.timestamp
                    anySessionSeen = true
                }
                UsageEventType.BACKGROUND -> {
                    val pkg = event.packageName ?: continue
                    if (openPackage == pkg) {
                        add(pkg, openSince!!, event.timestamp)
                        openPackage = null
                        openSince = null
                    } else if (!anySessionSeen && openPackage == null) {
                        // App was already in the foreground when the range started.
                        add(pkg, rangeStart, event.timestamp)
                        anySessionSeen = true
                    }
                }
                UsageEventType.DEVICE_INACTIVE -> {
                    if (openPackage != null) add(openPackage!!, openSince!!, event.timestamp)
                    openPackage = null
                    openSince = null
                    anySessionSeen = true
                }
            }
        }
        if (openPackage != null) add(openPackage!!, openSince!!, rangeEnd)
        return totals
    }

    fun toEntries(
        perDay: Map<LocalDate, Map<String, Duration>>,
        source: UsageSource,
    ): List<AppUsageEntry> = perDay.flatMap { (day, apps) ->
        apps.map { (pkg, d) -> AppUsageEntry(pkg, day, d, source) }
    }
}
