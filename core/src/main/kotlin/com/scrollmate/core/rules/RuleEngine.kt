package com.scrollmate.core.rules

import com.scrollmate.core.coach.Coach
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.NotificationRecord
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.progress.ProgressCalculator
import java.time.Duration
import java.time.ZonedDateTime

/** Static description of a coaching rule: which styles use it, its priority and whether it counts toward the daily cap. */
data class CoachingRule(
    val id: RuleId,
    val styles: Set<CoachingStyle>,
    /** Lower value wins when several rules fire in the same evaluation. */
    val priority: Int,
    val countsTowardsLimit: Boolean,
) {
    companion object {
        private val REMINDER_STYLES = setOf(CoachingStyle.ACTIVE, CoachingStyle.STRICT, CoachingStyle.FOCUS_WINDOWS)

        val ALL: List<CoachingRule> = listOf(
            CoachingRule(RuleId.STRICT_REPEAT, setOf(CoachingStyle.STRICT), priority = 0, countsTowardsLimit = true),
            CoachingRule(RuleId.THRESHOLD_TOTAL, REMINDER_STYLES, priority = 1, countsTowardsLimit = true),
            CoachingRule(RuleId.THRESHOLD_APP, REMINDER_STYLES, priority = 2, countsTowardsLimit = true),
            CoachingRule(RuleId.CHECK_IN, CoachingStyle.values().toSet(), priority = 3, countsTowardsLimit = false),
        )

        fun of(id: RuleId): CoachingRule = ALL.first { it.id == id }
    }
}

data class RuleContext(
    /** Current time in the device time zone. Its local date decides "today". */
    val now: ZonedDateTime,
    val prefs: UserPreferences,
    /** Usage today, or null when no data source is available (permission missing). */
    val totalUsage: Duration?,
    val appUsage: Map<String, Duration> = emptyMap(),
    /** All persisted records; the engine filters to today itself. */
    val records: List<NotificationRecord> = emptyList(),
    val focusSessionRunning: Boolean = false,
)

data class CoachTrigger(
    val ruleId: RuleId,
    val threshold: Int?,
    val key: String,
    val message: CoachMessage,
    val countsTowardsLimit: Boolean,
)

/**
 * Result of one evaluation.
 * [send]: at most one reminder per evaluation, so a delayed background check never produces a burst.
 * [markHandled]: lower thresholds that were overtaken (e.g. 70 % when 90 % is already reached);
 * they are persisted without being shown so they never arrive late.
 */
data class Evaluation(
    val send: CoachTrigger?,
    val markHandled: List<CoachTrigger>,
)

class RuleEngine(private val coach: Coach) {

    fun evaluate(ctx: RuleContext): Evaluation {
        val prefs = ctx.prefs.sanitized()
        val today = ctx.now.toLocalDate()
        val todays = ctx.records.filter { it.date == today }
        val sentKeys = todays.mapTo(HashSet()) { it.key }
        val remindersToday = todays.count { it.countsTowardsLimit }
        val disabled = prefs.reminders.disabledRules
        val focusMinutes = prefs.focusDuration.toMinutes().toInt()

        val candidates = mutableListOf<CoachTrigger>()
        val handled = mutableListOf<CoachTrigger>()

        fun enabled(id: RuleId): Boolean = id !in disabled && prefs.style in CoachingRule.of(id).styles

        val remindersAllowedNow = when (prefs.style) {
            CoachingStyle.GENTLE -> false
            CoachingStyle.ACTIVE, CoachingStyle.STRICT -> true
            CoachingStyle.FOCUS_WINDOWS -> prefs.timeWindows.any { it.enabled && it.contains(ctx.now.toLocalTime()) }
        } && !ctx.focusSessionRunning

        val progress = ctx.totalUsage?.let { ProgressCalculator.calculate(it, prefs.goal.dailyLimit) }

        // Total-usage thresholds.
        if (progress != null && remindersAllowedNow && enabled(RuleId.THRESHOLD_TOTAL)) {
            val reachedUnsent = prefs.reminders.thresholdsPercent
                .filter { progress.percent >= it }
                .map { t -> t to NotificationKeys.key(today, RuleId.THRESHOLD_TOTAL, t) }
                .filter { (_, key) -> key !in sentKeys }
            if (reachedUnsent.isNotEmpty()) {
                val rule = CoachingRule.of(RuleId.THRESHOLD_TOTAL)
                reachedUnsent.forEachIndexed { index, (t, key) ->
                    val trigger = CoachTrigger(
                        RuleId.THRESHOLD_TOTAL, t, key,
                        coach.thresholdReached(t, prefs.style, focusMinutes),
                        rule.countsTowardsLimit,
                    )
                    // Overtaken thresholds are stored silently and do not count toward the daily cap.
                    if (index == reachedUnsent.lastIndex) candidates += trigger
                    else handled += trigger.copy(countsTowardsLimit = false)
                }
            }
        }

        // Strict style: repeat the focus suggestion after the chosen interval while over the limit.
        if (progress != null && progress.limitReached && remindersAllowedNow && enabled(RuleId.STRICT_REPEAT)) {
            val previous = todays.filter {
                (it.ruleId == RuleId.THRESHOLD_TOTAL && (it.threshold ?: 0) >= 100) || it.ruleId == RuleId.STRICT_REPEAT
            }
            val last = previous.maxByOrNull { it.sentAt }
            if (last != null && !ctx.now.toInstant().isBefore(last.sentAt.plus(prefs.reminders.strictRepeatInterval))) {
                val n = todays.count { it.ruleId == RuleId.STRICT_REPEAT } + 1
                val key = NotificationKeys.key(today, RuleId.STRICT_REPEAT, 100, "n$n")
                if (key !in sentKeys) {
                    candidates += CoachTrigger(RuleId.STRICT_REPEAT, 100, key, coach.strictRepeat(focusMinutes), true)
                }
            }
        }

        // Per-app limits (only the 100 % mark, to keep things calm).
        if (remindersAllowedNow && enabled(RuleId.THRESHOLD_APP)) {
            prefs.goal.appLimits.forEach { limit ->
                val used = ctx.appUsage[limit.packageName] ?: Duration.ZERO
                if (used >= limit.limit) {
                    val key = NotificationKeys.key(today, RuleId.THRESHOLD_APP, 100, limit.packageName)
                    if (key !in sentKeys) {
                        candidates += CoachTrigger(
                            RuleId.THRESHOLD_APP, 100, key, coach.appLimitReached(limit.packageName), true,
                        )
                    }
                }
            }
        }

        // Daily check-in (all styles, not affected by the reminder cap).
        val checkInTime = prefs.reminders.checkInTime
        if (checkInTime != null && enabled(RuleId.CHECK_IN) && !ctx.now.toLocalTime().isBefore(checkInTime)) {
            val key = NotificationKeys.key(today, RuleId.CHECK_IN)
            if (key !in sentKeys) {
                candidates += CoachTrigger(RuleId.CHECK_IN, null, key, coach.checkIn(progress), false)
            }
        }

        val send = candidates
            .sortedBy { CoachingRule.of(it.ruleId).priority }
            .firstOrNull { !it.countsTowardsLimit || remindersToday < prefs.reminders.maxRemindersPerDay }

        return Evaluation(send, handled)
    }
}
