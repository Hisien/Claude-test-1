package com.scrollmate.core.usage

import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import java.time.Duration
import java.time.LocalDate

/** Usage of one local day for the watched apps. */
data class DailyUsage(
    val date: LocalDate,
    val total: Duration,
    /** Sorted by duration, longest first. */
    val perApp: List<Pair<String, Duration>>,
) {
    fun top(n: Int): List<Pair<String, Duration>> = perApp.take(n)

    companion object {
        /**
         * @param watched apps the user selected; empty means "all apps" minus [excluded].
         * @param excluded packages that never count (ScrollMate itself, the home launcher).
         */
        fun of(
            entries: List<AppUsageEntry>,
            date: LocalDate,
            source: UsageSource,
            watched: Set<String>,
            excluded: Set<String> = emptySet(),
        ): DailyUsage {
            val perApp = entries
                .asSequence()
                .filter { it.date == date && it.source == source }
                .filter { isWatched(it.packageName, watched, excluded) }
                .groupBy { it.packageName }
                .mapValues { (_, list) -> list.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) } }
                .filterValues { !it.isZero }
                .toList()
                .sortedByDescending { it.second }
            val total = perApp.fold(Duration.ZERO) { acc, p -> acc.plus(p.second) }
            return DailyUsage(date, total, perApp)
        }

        fun isWatched(pkg: String, watched: Set<String>, excluded: Set<String>): Boolean =
            pkg !in excluded && (watched.isEmpty() || pkg in watched)
    }
}
