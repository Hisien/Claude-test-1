package com.scrollmate.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** How the coach behaves. See README for the user-facing description of each style. */
enum class CoachingStyle {
    /** Positive feedback and optional tips only; no active reminders except the daily check-in. */
    GENTLE,

    /** Reminders at the configured thresholds plus short check-ins. */
    ACTIVE,

    /** Like ACTIVE, plus a direct focus-session suggestion at 100 % and a repeat after a chosen interval. No real blocking. */
    STRICT,

    /** Threshold rules only apply inside user-defined time windows. */
    FOCUS_WINDOWS,
}

/** Where a usage number came from. */
enum class UsageSource { LIVE, DEMO, MANUAL }

/** What the app is currently showing. Exactly one of these is visible in the status chip. */
enum class DataSourceState { LIVE, PERMISSION_MISSING, DEMO }

/** Whether a real, system-wide restriction of other apps is possible. The MVP always reports UNAVAILABLE. */
enum class RestrictionStatus { UNAVAILABLE, AVAILABLE, ACTIVE }

/** Identifiers of the coaching rules. Used in idempotency keys, so names must stay stable. */
enum class RuleId {
    THRESHOLD_TOTAL,
    THRESHOLD_APP,
    STRICT_REPEAT,
    CHECK_IN,
}

data class AppUsageEntry(
    val packageName: String,
    val date: LocalDate,
    val duration: Duration,
    val source: UsageSource,
) {
    init {
        require(!duration.isNegative) { "duration must not be negative" }
    }
}

data class AppLimit(val packageName: String, val limit: Duration) {
    init {
        require(!limit.isNegative && !limit.isZero) { "app limit must be positive" }
    }
}

/**
 * A daily recurring time window, e.g. "Lernen 09:00–12:00" or "Schlafenszeit 22:00–07:00".
 * If [end] is before or equal to [start] the window crosses midnight; start == end means the whole day.
 */
data class TimeWindow(
    val id: String,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val enabled: Boolean = true,
) {
    val crossesMidnight: Boolean get() = end < start

    /** Half-open check: [start, end). */
    fun contains(time: LocalTime): Boolean = when {
        start == end -> true
        start < end -> !time.isBefore(start) && time.isBefore(end)
        else -> !time.isBefore(start) || time.isBefore(end)
    }
}

data class DailyGoal(
    val dailyLimit: Duration = Duration.ofMinutes(120),
    val appLimits: List<AppLimit> = emptyList(),
    /** Free text, e.g. "Abends weniger am Handy". Never interpreted, only shown back to the user. */
    val personalGoal: String = "",
)

data class ReminderSettings(
    val thresholdsPercent: List<Int> = DEFAULT_THRESHOLDS,
    /** Local time of the daily check-in; null disables it. */
    val checkInTime: LocalTime? = LocalTime.of(20, 0),
    /** Cap for threshold and repeat reminders per local day. The check-in does not count. */
    val maxRemindersPerDay: Int = 5,
    /** Only used by [CoachingStyle.STRICT]. */
    val strictRepeatInterval: Duration = Duration.ofMinutes(30),
    val disabledRules: Set<RuleId> = emptySet(),
) {
    companion object {
        val DEFAULT_THRESHOLDS = listOf(70, 90, 100)
    }
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val goal: DailyGoal = DailyGoal(),
    val style: CoachingStyle = CoachingStyle.ACTIVE,
    val reminders: ReminderSettings = ReminderSettings(),
    val timeWindows: List<TimeWindow> = emptyList(),
    val focusDuration: Duration = Duration.ofMinutes(10),
    val demoMode: Boolean = false,
    /** Apps the user wants to watch. Empty means: all apps except ScrollMate and the launcher. */
    val selectedApps: Set<String> = emptySet(),
) {
    /** Returns a copy with all values clamped to sane ranges, so bad input can never break the rule engine. */
    fun sanitized(): UserPreferences = copy(
        goal = goal.copy(
            dailyLimit = goal.dailyLimit.coerceIn(MIN_LIMIT, MAX_LIMIT),
            appLimits = goal.appLimits
                .filter { it.limit >= MIN_APP_LIMIT }
                .distinctBy { it.packageName },
            personalGoal = goal.personalGoal.take(MAX_GOAL_TEXT),
        ),
        reminders = reminders.copy(
            thresholdsPercent = reminders.thresholdsPercent.filter { it in 1..200 }.distinct().sorted(),
            maxRemindersPerDay = reminders.maxRemindersPerDay.coerceIn(0, 20),
            strictRepeatInterval = reminders.strictRepeatInterval.coerceIn(Duration.ofMinutes(5), Duration.ofHours(4)),
        ),
        focusDuration = focusDuration.coerceIn(Duration.ofMinutes(1), Duration.ofHours(4)),
    )

    companion object {
        val MIN_LIMIT: Duration = Duration.ofMinutes(5)
        val MAX_LIMIT: Duration = Duration.ofHours(16)
        val MIN_APP_LIMIT: Duration = Duration.ofMinutes(5)
        const val MAX_GOAL_TEXT = 140
    }
}

/** Persisted record of a reminder that was sent (as notification or in-app). Used for idempotency. */
data class NotificationRecord(
    val key: String,
    val date: LocalDate,
    val ruleId: RuleId,
    val threshold: Int?,
    val sentAt: Instant,
    val countsTowardsLimit: Boolean,
)

enum class FocusStatus { RUNNING, COMPLETED, ENDED_EARLY }

data class FocusSession(
    val id: String,
    val plannedDuration: Duration,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val status: FocusStatus = FocusStatus.RUNNING,
    val reflection: String? = null,
) {
    val plannedEnd: Instant get() = startedAt.plus(plannedDuration)
}

internal fun Duration.coerceIn(min: Duration, max: Duration): Duration = when {
    this < min -> min
    this > max -> max
    else -> this
}
