package com.scrollmate.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.scrollmate.app.ScrollMateApplication
import com.scrollmate.app.data.toEntity
import com.scrollmate.app.data.toModel
import com.scrollmate.core.focus.FocusSessions
import com.scrollmate.core.model.FocusStatus
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Periodic background check (every 15 minutes – the WorkManager minimum). It refreshes the
 * daily summaries (which also closes yesterday's numbers after midnight), evaluates the rules and
 * posts at most one reminder. Thresholds are therefore detected with up to ~15 minutes delay,
 * longer when the system defers work (Doze, battery saver). The check-in uses the same worker,
 * so it arrives within the same tolerance – no exact-alarm permission is needed.
 */
class CoachCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ScrollMateApplication).container
        return try {
            if (container.settings.current().onboardingCompleted) container.coachEvaluator.run()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Fires once at the planned end of a focus session (inexact; the system may delay it).
 * The in-app timer is exact while ScrollMate is open.
 */
class FocusEndWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ScrollMateApplication).container
        val id = inputData.getString(KEY_SESSION_ID) ?: return Result.success()
        val dao = container.database.focusSessionDao()
        val stored = dao.byId(id)?.toModel() ?: return Result.success()
        if (stored.status != FocusStatus.RUNNING) return Result.success()
        val now = container.clock.instant()
        val refreshed = FocusSessions.refresh(stored, now)
        if (refreshed.status == FocusStatus.COMPLETED) {
            dao.upsert(refreshed.toEntity())
            container.notifier.postFocusEnded(container.coach.focusFinished(refreshed, now))
        }
        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
    }
}

object WorkScheduler {
    private const val PERIODIC_CHECK = "coach_check"
    private const val FOCUS_END = "focus_end"

    fun ensurePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<CoachCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleFocusEnd(context: Context, sessionId: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<FocusEndWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FocusEndWorker.KEY_SESSION_ID to sessionId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FOCUS_END, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelFocusEnd(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FOCUS_END)
    }
}
