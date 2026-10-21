package com.scrollmate.app

import android.content.Context
import com.scrollmate.app.data.InstalledApps
import com.scrollmate.app.data.SettingsRepository
import com.scrollmate.app.data.SnapshotLoader
import com.scrollmate.app.data.UsageAccess
import com.scrollmate.app.data.UsageRepository
import com.scrollmate.app.data.UsageStatsSource
import com.scrollmate.app.data.db.ScrollMateDatabase
import com.scrollmate.app.domain.CoachEvaluator
import com.scrollmate.app.notifications.Notifier
import com.scrollmate.core.coach.Coach
import com.scrollmate.core.coach.RuleBasedCoach
import com.scrollmate.core.restriction.AppRestrictionService
import com.scrollmate.core.restriction.NoOpRestrictionService
import com.scrollmate.core.rules.RuleEngine
import java.time.Clock
import java.time.ZoneId

/** Manual constructor injection: one place that wires everything together. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** System clock; the zone is read on every use so time-zone changes are picked up immediately. */
    val clock: Clock = Clock.systemUTC()
    fun zone(): ZoneId = ZoneId.systemDefault()

    val database: ScrollMateDatabase = ScrollMateDatabase.create(appContext)
    val settings = SettingsRepository.create(appContext)
    val usageAccess = UsageAccess(appContext)
    val installedApps = InstalledApps(appContext)
    val usageRepository = UsageRepository(UsageStatsSource(appContext), database.usageSummaryDao(), clock)

    val coach: Coach = RuleBasedCoach()
    val ruleEngine = RuleEngine(coach)
    val restrictionService: AppRestrictionService = NoOpRestrictionService()

    val notifier = Notifier(appContext)

    val snapshotLoader = SnapshotLoader(
        settings = settings,
        usageRepository = usageRepository,
        usageAccess = usageAccess,
        installedApps = installedApps,
        clock = clock,
        zone = ::zone,
    )

    val coachEvaluator = CoachEvaluator(
        loader = snapshotLoader,
        engine = ruleEngine,
        notificationDao = database.notificationRecordDao(),
        focusDao = database.focusSessionDao(),
        notifier = notifier,
        clock = clock,
    )
}
