package com.scrollmate.app.data

import com.scrollmate.core.categories.SystemCategoryHint
import com.scrollmate.core.datasource.DataSourceResolver
import com.scrollmate.core.history.HistoryCalculator
import com.scrollmate.core.history.HistorySummary
import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.DataSourceState
import com.scrollmate.core.model.UsageSource
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.progress.Progress
import com.scrollmate.core.progress.ProgressCalculator
import com.scrollmate.core.usage.DailyUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Everything the screens and the background check need, computed in one place. */
data class UsageSnapshot(
    val state: DataSourceState,
    val now: ZonedDateTime,
    val prefs: UserPreferences,
    /** Null when no data source is available (permission missing). */
    val today: DailyUsage?,
    val progress: Progress?,
    /** Usage per app today, for per-app limits (not filtered by the selection). */
    val appUsageToday: Map<String, Duration>,
    val history: HistorySummary?,
    val labels: Map<String, String>,
    /** Watched apps summed over the last 7 days, for the category analysis. */
    val weekPerApp: Map<String, Duration> = emptyMap(),
    val categoryHints: Map<String, SystemCategoryHint> = emptyMap(),
) {
    val date: LocalDate get() = now.toLocalDate()
    val isDemo: Boolean get() = state == DataSourceState.DEMO
}

class SnapshotLoader(
    private val settings: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val usageAccess: UsageAccess,
    private val installedApps: InstalledApps,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {

    suspend fun load(prefs: UserPreferences? = null): UsageSnapshot = withContext(Dispatchers.IO) {
        val p = prefs ?: settings.current()
        val z = zone()
        val now = ZonedDateTime.now(clock.withZone(z))
        val date = now.toLocalDate()
        val state = DataSourceResolver.resolve(p.demoMode, usageAccess.isGranted())

        val entries: List<AppUsageEntry> = when (state) {
            DataSourceState.DEMO -> usageRepository.demoEntries(date)
            DataSourceState.LIVE -> {
                usageRepository.refreshLive(z)
                usageRepository.liveEntries(date.minusDays(UsageRepository.HISTORY_DAYS.toLong() - 1))
            }
            DataSourceState.PERMISSION_MISSING -> emptyList()
        }
        val source = DataSourceResolver.visibleSource(state)
        if (source == null) {
            return@withContext UsageSnapshot(state, now, p, null, null, emptyMap(), null, emptyMap())
        }

        val excluded = if (source == UsageSource.LIVE) installedApps.excludedPackages() else emptySet()
        val watched = if (source == UsageSource.DEMO) emptySet() else p.selectedApps
        val today = DailyUsage.of(entries, date, source, watched, excluded)
        val history = HistoryCalculator.summarize(entries, date, p.goal.dailyLimit, source) {
            DailyUsage.isWatched(it, watched, excluded)
        }
        val appUsage = entries.filter { it.date == date }
            .groupBy { it.packageName }
            .mapValues { (_, l) -> l.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) } }
        val weekPerApp = entries
            .filter { it.source == source && DailyUsage.isWatched(it.packageName, watched, excluded) }
            .groupBy { it.packageName }
            .mapValues { (_, l) -> l.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) } }
        val labels = (today.perApp.map { it.first } + p.goal.appLimits.map { it.packageName } + weekPerApp.keys)
            .distinct()
            .associateWith { installedApps.label(it) }
        val hints = if (source == UsageSource.LIVE) weekPerApp.keys.associateWith { installedApps.categoryHint(it) } else emptyMap()

        UsageSnapshot(
            state = state,
            now = now,
            prefs = p,
            today = today,
            progress = ProgressCalculator.calculate(today.total, p.goal.dailyLimit),
            appUsageToday = appUsage,
            history = history,
            labels = labels,
            weekPerApp = weekPerApp,
            categoryHints = hints,
        )
    }
}
