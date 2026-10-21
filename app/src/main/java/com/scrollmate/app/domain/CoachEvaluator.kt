package com.scrollmate.app.domain

import com.scrollmate.app.data.SnapshotLoader
import com.scrollmate.app.data.db.FocusSessionDao
import com.scrollmate.app.data.db.NotificationRecordDao
import com.scrollmate.app.data.toEntity
import com.scrollmate.app.data.toModel
import com.scrollmate.app.notifications.Notifier
import com.scrollmate.core.focus.FocusSessions
import com.scrollmate.core.model.FocusStatus
import com.scrollmate.core.rules.CoachTrigger
import com.scrollmate.core.rules.RuleContext
import com.scrollmate.core.rules.RuleEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * Runs the rule engine once: used by the periodic background worker and by the
 * "Jetzt prüfen" button. A Room insert with IGNORE is the idempotency check, so a reminder
 * can never be delivered twice even if two runs overlap or the process restarts.
 */
class CoachEvaluator(
    private val loader: SnapshotLoader,
    private val engine: RuleEngine,
    private val notificationDao: NotificationRecordDao,
    private val focusDao: FocusSessionDao,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    sealed interface Result {
        data class Sent(val trigger: CoachTrigger, val asNotification: Boolean) : Result
        data object NothingDue : Result
    }

    suspend fun run(): Result = mutex.withLock {
        val snapshot = loader.load()
        val now = clock.instant()
        val focusRunning = focusDao.running()?.toModel()
            ?.let { FocusSessions.refresh(it, now) }
            ?.status == FocusStatus.RUNNING

        val records = notificationDao.since(snapshot.date.minusDays(1).toString()).mapNotNull { it.toModel() }
        val evaluation = engine.evaluate(
            RuleContext(
                now = snapshot.now,
                prefs = snapshot.prefs,
                totalUsage = snapshot.today?.total,
                appUsage = snapshot.appUsageToday,
                records = records,
                focusSessionRunning = focusRunning,
            ),
        )

        evaluation.markHandled.forEach {
            notificationDao.insert(it.toEntity(now, shown = false, delivered = false))
        }
        val trigger = evaluation.send ?: return@withLock Result.NothingDue
        val canPost = notifier.canPost()
        val inserted = notificationDao.insert(trigger.toEntity(now, shown = true, delivered = canPost)) != -1L
        if (!inserted) return@withLock Result.NothingDue
        if (canPost) notifier.postCoach(trigger, snapshot.labels, snapshot.isDemo, snapshot.prefs.voice, snapshot.prefs.windDown.bedtime)
        notificationDao.deleteOlderThan(snapshot.date.minusDays(30).toString())
        Result.Sent(trigger, canPost)
    }
}
