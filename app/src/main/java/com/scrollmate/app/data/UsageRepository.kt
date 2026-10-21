package com.scrollmate.app.data

import com.scrollmate.app.data.db.UsageSummaryDao
import com.scrollmate.app.data.db.UsageSummaryEntity
import com.scrollmate.core.demo.DemoData
import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import com.scrollmate.core.time.DayMath
import com.scrollmate.core.usage.UsageAggregator
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class UsageRepository(
    private val source: UsageStatsSource,
    private val dao: UsageSummaryDao,
    private val clock: Clock,
) {

    /**
     * Recomputes the last [days] local days from raw events and stores daily summaries.
     * Today is overwritten. For past days we keep the larger value: once the OS has dropped old
     * events, a recomputation would otherwise shrink a finished day.
     */
    suspend fun refreshLive(zone: ZoneId, days: Int = HISTORY_DAYS) {
        val now = clock.instant()
        val today = DayMath.localDate(now, zone)
        val firstDay = today.minusDays((days - 1).toLong())
        val from = DayMath.startOfDay(firstDay, zone)
        val perDay = UsageAggregator.aggregate(source.events(from, now), from, now, zone)

        val existing = dao.since(firstDay.toString(), UsageSource.LIVE.name)
            .associateBy { it.date to it.packageName }
        val updates = perDay.flatMap { (day, apps) ->
            apps.mapNotNull { (pkg, duration) ->
                val old = existing[day.toString() to pkg]
                val keep = day.isBefore(today) && old != null && old.durationMs >= duration.toMillis()
                if (keep) {
                    null
                } else {
                    UsageSummaryEntity(day.toString(), pkg, UsageSource.LIVE.name, duration.toMillis(), now.toEpochMilli())
                }
            }
        }
        if (updates.isNotEmpty()) dao.upsert(updates)
        dao.deleteOlderThan(today.minusDays(RETENTION_DAYS).toString())
    }

    suspend fun liveEntries(fromDate: LocalDate): List<AppUsageEntry> =
        dao.since(fromDate.toString(), UsageSource.LIVE.name).map {
            AppUsageEntry(it.packageName, LocalDate.parse(it.date), Duration.ofMillis(it.durationMs), UsageSource.LIVE)
        }

    fun demoEntries(today: LocalDate): List<AppUsageEntry> = DemoData.entries(today)

    companion object {
        const val HISTORY_DAYS = 7
        const val RETENTION_DAYS = 60L
    }
}
