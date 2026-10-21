package com.scrollmate.core.history

import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import java.time.Duration
import java.time.LocalDate

data class DayBar(
    val date: LocalDate,
    /** Null when there is no data for that day. */
    val total: Duration?,
    val limit: Duration,
    val source: UsageSource?,
) {
    val goalMet: Boolean? get() = total?.let { it <= limit }
}

data class HistorySummary(
    val days: List<DayBar>,
    /** Number of completed past days (today excluded) with live data in the window. */
    val completedLiveDays: Int,
    val comparisonAvailable: Boolean,
    /** Average of completed live days; null unless [comparisonAvailable]. */
    val average: Duration?,
    /** Today minus average; negative means less than usual. Null unless [comparisonAvailable]. */
    val todayVsAverage: Duration?,
)

object HistoryCalculator {

    const val MIN_LIVE_DAYS_FOR_COMPARISON = 5

    /**
     * Builds the last [days] days ending with [today]. Only entries of [source] are used, so demo
     * numbers never mix with live ones. The personal average is only shown after at least five
     * completed days of live data – with less data an average would mostly be noise.
     */
    fun summarize(
        entries: List<AppUsageEntry>,
        today: LocalDate,
        limit: Duration,
        source: UsageSource,
        days: Int = 7,
        includePackage: (String) -> Boolean = { true },
    ): HistorySummary {
        val relevant = entries.filter { it.source == source && includePackage(it.packageName) }
        val byDay = relevant.groupBy { it.date }
        val bars = (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dayEntries = byDay[date]
            val total = dayEntries?.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) }
            DayBar(date, total, limit, if (dayEntries == null) null else source)
        }
        val completedLive = if (source == UsageSource.LIVE) {
            bars.filter { it.date.isBefore(today) && it.total != null }
        } else {
            emptyList()
        }
        val available = completedLive.size >= MIN_LIVE_DAYS_FOR_COMPARISON
        val average = if (available) {
            completedLive.fold(Duration.ZERO) { acc, b -> acc.plus(b.total!!) }.dividedBy(completedLive.size.toLong())
        } else {
            null
        }
        val todayTotal = bars.last().total
        return HistorySummary(
            days = bars,
            completedLiveDays = completedLive.size,
            comparisonAvailable = available,
            average = average,
            todayVsAverage = if (average != null && todayTotal != null) todayTotal.minus(average) else null,
        )
    }
}
