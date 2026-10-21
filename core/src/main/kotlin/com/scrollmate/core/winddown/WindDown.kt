package com.scrollmate.core.winddown

import com.scrollmate.core.model.TimeWindow
import com.scrollmate.core.model.WindDownSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** Time logic of the evening wind-down. Pure and testable. */
object WindDown {

    /** First moment of the wind-down: bedtime minus lead time (may wrap past midnight). */
    fun start(settings: WindDownSettings): LocalTime =
        settings.bedtime.minusMinutes(settings.leadMinutes.toLong())

    /** The daily window [start, wakeTime) – usually crosses midnight. */
    fun window(settings: WindDownSettings): TimeWindow =
        TimeWindow("wind_down", "wind_down", start(settings), settings.wakeTime, enabled = settings.enabled)

    /**
     * The "evening" a moment belongs to: times after the window start count for that day,
     * early-morning times (before wake time) still belong to the previous evening.
     * Used for "Heute Abend aussetzen" so a pause at 23:00 also covers 01:00.
     */
    fun eveningOf(now: LocalDateTime, settings: WindDownSettings): LocalDate {
        val w = window(settings)
        return if (w.crossesMidnight && now.toLocalTime().isBefore(settings.wakeTime)) now.toLocalDate().minusDays(1)
        else now.toLocalDate()
    }

    /** True when blocked apps should be covered right now. */
    fun isActive(now: LocalDateTime, settings: WindDownSettings): Boolean {
        if (!settings.enabled || settings.blockedApps.isEmpty()) return false
        if (!window(settings).contains(now.toLocalTime())) return false
        return settings.pausedForDate != eveningOf(now, settings)
    }

    fun isBlocked(packageName: String?, now: LocalDateTime, settings: WindDownSettings): Boolean =
        packageName != null && packageName in settings.blockedApps && isActive(now, settings)

    /** True from wind-down start until bedtime – the moment for the evening reminder. */
    fun isLeadPhase(now: LocalDateTime, settings: WindDownSettings): Boolean {
        if (!settings.enabled) return false
        val lead = TimeWindow("lead", "lead", start(settings), settings.bedtime)
        return settings.leadMinutes > 0 && lead.contains(now.toLocalTime())
    }

    /** How long until the next window start, for sleeping between checks. Zero while active. */
    fun untilNextStart(now: ZonedDateTime, settings: WindDownSettings): Duration {
        if (window(settings).contains(now.toLocalTime())) return Duration.ZERO
        var next = now.with(start(settings))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** Known short-video / social apps suggested as default wind-down targets. */
    val SUGGESTED_PACKAGES: List<String> = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.pinterest",
    )
}
