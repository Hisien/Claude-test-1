package com.scrollmate.core.rules

import com.scrollmate.core.berlin
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.coach.RuleBasedCoach
import com.scrollmate.core.min
import com.scrollmate.core.model.AppLimit
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.DailyGoal
import com.scrollmate.core.model.NotificationRecord
import com.scrollmate.core.model.ReminderSettings
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.TimeWindow
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.persist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {

    private val engine = RuleEngine(RuleBasedCoach())

    private fun prefs(
        style: CoachingStyle,
        limit: Long = 100,
        checkIn: LocalTime? = LocalTime.of(20, 0),
        windows: List<TimeWindow> = emptyList(),
        disabled: Set<RuleId> = emptySet(),
        max: Int = 5,
        appLimits: List<AppLimit> = emptyList(),
    ) = UserPreferences(
        onboardingCompleted = true,
        goal = DailyGoal(dailyLimit = min(limit), appLimits = appLimits),
        style = style,
        reminders = ReminderSettings(checkInTime = checkIn, disabledRules = disabled, maxRemindersPerDay = max),
        timeWindows = windows,
    )

    private fun ctx(
        now: ZonedDateTime,
        p: UserPreferences,
        used: Long?,
        records: List<NotificationRecord> = emptyList(),
        apps: Map<String, Duration> = emptyMap(),
        focus: Boolean = false,
    ) = RuleContext(now, p, used?.let { min(it) }, apps, records, focus)

    private val noon = berlin(2026, 10, 21, 12)

    // --- thresholds per style ---

    @Test
    fun gentleSendsNoThresholdReminders() {
        val e = engine.evaluate(ctx(noon, prefs(CoachingStyle.GENTLE), used = 150))
        assertNull(e.send)
    }

    @Test
    fun gentleStillSendsTheDailyCheckIn() {
        val e = engine.evaluate(ctx(berlin(2026, 10, 21, 20, 5), prefs(CoachingStyle.GENTLE), used = 150))
        assertEquals(RuleId.CHECK_IN, e.send?.ruleId)
        assertFalse(e.send!!.countsTowardsLimit)
    }

    @Test
    fun activeBelowFirstThresholdSendsNothing() {
        assertNull(engine.evaluate(ctx(noon, prefs(CoachingStyle.ACTIVE), used = 69)).send)
    }

    @Test
    fun activeAtSeventyPercentSendsSeventy() {
        val send = engine.evaluate(ctx(noon, prefs(CoachingStyle.ACTIVE), used = 70)).send!!
        assertEquals(RuleId.THRESHOLD_TOTAL, send.ruleId)
        assertEquals(70, send.threshold)
        assertEquals(MessageKind.THRESHOLD_REACHED, send.message.kind)
        assertEquals(CoachAction.TAKE_BREAK, send.message.action)
    }

    @Test
    fun delayedCheckSendsOnlyHighestThresholdAndSilentlyHandlesLowerOnes() {
        val e = engine.evaluate(ctx(noon, prefs(CoachingStyle.ACTIVE), used = 95))
        assertEquals(90, e.send?.threshold)
        assertEquals(listOf(70), e.markHandled.map { it.threshold })
        assertFalse(e.markHandled.single().countsTowardsLimit)
    }

    @Test
    fun activeAtHundredSuggestsBreakNotFocus() {
        val send = engine.evaluate(ctx(noon, prefs(CoachingStyle.ACTIVE), used = 100)).send!!
        assertEquals(MessageKind.LIMIT_REACHED, send.message.kind)
    }

    @Test
    fun strictAtHundredSuggestsFocusSession() {
        val send = engine.evaluate(ctx(noon, prefs(CoachingStyle.STRICT), used = 101)).send!!
        assertEquals(100, send.threshold)
        assertEquals(MessageKind.LIMIT_REACHED_SUGGEST_FOCUS, send.message.kind)
        assertEquals(CoachAction.START_FOCUS, send.message.action)
        assertEquals(10, send.message.minutes)
    }

    @Test
    fun strictRepeatsAfterIntervalAndNotBefore() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.STRICT)
        persist(engine.evaluate(ctx(noon, p, used = 120)), noon, records)
        assertEquals(3, records.size) // 100 sent, 70 and 90 handled silently
        val after15 = noon.plusMinutes(15)
        assertNull(engine.evaluate(ctx(after15, p, used = 125, records = records)).send)

        val after30 = noon.plusMinutes(30)
        val repeat = engine.evaluate(ctx(after30, p, used = 130, records = records)).send!!
        assertEquals(RuleId.STRICT_REPEAT, repeat.ruleId)
        assertEquals(MessageKind.STRICT_REPEAT, repeat.message.kind)
        persist(engine.evaluate(ctx(after30, p, used = 130, records = records)), after30, records)

        // Next repeat only after another interval.
        assertNull(engine.evaluate(ctx(after30.plusMinutes(10), p, used = 135, records = records)).send)
        assertEquals(
            RuleId.STRICT_REPEAT,
            engine.evaluate(ctx(after30.plusMinutes(30), p, used = 140, records = records)).send?.ruleId,
        )
    }

    @Test
    fun activeNeverRepeats() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.ACTIVE)
        persist(engine.evaluate(ctx(noon, p, used = 120)), noon, records)
        assertNull(engine.evaluate(ctx(noon.plusHours(2), p, used = 180, records = records)).send)
    }

    @Test
    fun focusWindowsOnlyRemindInsideAWindow() {
        val window = TimeWindow("sleep", "Schlafenszeit", LocalTime.of(22, 0), LocalTime.of(7, 0))
        val p = prefs(CoachingStyle.FOCUS_WINDOWS, windows = listOf(window), checkIn = null)
        assertNull(engine.evaluate(ctx(noon, p, used = 90)).send)
        assertEquals(90, engine.evaluate(ctx(berlin(2026, 10, 21, 23, 30), p, used = 90)).send?.threshold)
        assertEquals(90, engine.evaluate(ctx(berlin(2026, 10, 22, 6, 0), p, used = 90)).send?.threshold)
    }

    @Test
    fun focusWindowsIgnoresDisabledWindows() {
        val window = TimeWindow("study", "Lernen", LocalTime.of(9, 0), LocalTime.of(17, 0), enabled = false)
        val p = prefs(CoachingStyle.FOCUS_WINDOWS, windows = listOf(window), checkIn = null)
        assertNull(engine.evaluate(ctx(noon, p, used = 90)).send)
    }

    // --- priorities, caps, disabled rules ---

    @Test
    fun thresholdHasPriorityOverCheckInAndCheckInFollowsLater() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.ACTIVE)
        val evening = berlin(2026, 10, 21, 20, 10)
        val first = engine.evaluate(ctx(evening, p, used = 75))
        assertEquals(RuleId.THRESHOLD_TOTAL, first.send?.ruleId)
        persist(first, evening, records)
        val second = engine.evaluate(ctx(evening.plusMinutes(15), p, used = 76, records = records))
        assertEquals(RuleId.CHECK_IN, second.send?.ruleId)
    }

    @Test
    fun appLimitIsReportedAndHasLowerPriorityThanTotal() {
        val p = prefs(CoachingStyle.ACTIVE, appLimits = listOf(AppLimit("com.feed", min(30))), checkIn = null)
        val apps = mapOf("com.feed" to min(31))
        val withTotal = engine.evaluate(ctx(noon, p, used = 72, apps = apps))
        assertEquals(RuleId.THRESHOLD_TOTAL, withTotal.send?.ruleId)
        val onlyApp = engine.evaluate(ctx(noon, p, used = 40, apps = apps)).send!!
        assertEquals(RuleId.THRESHOLD_APP, onlyApp.ruleId)
        assertEquals("com.feed", onlyApp.message.packageName)
    }

    @Test
    fun disabledRuleNeverFires() {
        val p = prefs(CoachingStyle.ACTIVE, disabled = setOf(RuleId.THRESHOLD_TOTAL), checkIn = null)
        assertNull(engine.evaluate(ctx(noon, p, used = 150)).send)
        val noCheckIn = prefs(CoachingStyle.GENTLE, disabled = setOf(RuleId.CHECK_IN))
        assertNull(engine.evaluate(ctx(berlin(2026, 10, 21, 21), noCheckIn, used = 10)).send)
    }

    @Test
    fun dailyCapStopsRemindersButNotTheCheckIn() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.ACTIVE, max = 1)
        persist(engine.evaluate(ctx(noon, p, used = 70)), noon, records)
        assertNull(engine.evaluate(ctx(noon.plusHours(1), p, used = 95, records = records)).send)
        val evening = engine.evaluate(ctx(berlin(2026, 10, 21, 20, 1), p, used = 95, records = records))
        assertEquals(RuleId.CHECK_IN, evening.send?.ruleId)
    }

    @Test
    fun runningFocusSessionSuppressesThresholdReminders() {
        assertNull(engine.evaluate(ctx(noon, prefs(CoachingStyle.STRICT), used = 120, focus = true)).send)
    }

    @Test
    fun missingDataOnlyAllowsCheckIn() {
        val p = prefs(CoachingStyle.ACTIVE)
        assertNull(engine.evaluate(ctx(noon, p, used = null)).send)
        val checkIn = engine.evaluate(ctx(berlin(2026, 10, 21, 20, 0), p, used = null)).send!!
        assertEquals(MessageKind.CHECK_IN, checkIn.message.kind)
    }

    @Test
    fun checkInUnderLimitIsPositive() {
        val send = engine.evaluate(ctx(berlin(2026, 10, 21, 20, 0), prefs(CoachingStyle.GENTLE), used = 40)).send!!
        assertEquals(MessageKind.CHECK_IN_ON_TRACK, send.message.kind)
        assertEquals(CoachAction.REFLECT, send.message.action)
    }

    // --- idempotency ---

    @Test
    fun sameThresholdFiresOnlyOncePerDayAndAgainNextDay() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.ACTIVE, checkIn = null)
        val first = engine.evaluate(ctx(noon, p, used = 72, records = records))
        assertEquals(70, first.send?.threshold)
        persist(first, noon, records)

        // Simulated restart: same persisted records, later the same day, still at 7x %.
        assertNull(engine.evaluate(ctx(noon.plusMinutes(15), p, used = 75, records = records)).send)
        assertNull(engine.evaluate(ctx(noon.plusHours(3), p, used = 89, records = records)).send)

        val nextDay = berlin(2026, 10, 22, 12)
        assertEquals(70, engine.evaluate(ctx(nextDay, p, used = 72, records = records)).send?.threshold)
    }

    @Test
    fun timeZoneChangeOnSameLocalDateDoesNotResend() {
        val records = mutableListOf<NotificationRecord>()
        val p = prefs(CoachingStyle.ACTIVE, checkIn = null)
        persist(engine.evaluate(ctx(noon, p, used = 72)), noon, records)
        // Travel to Lisbon (one hour behind): still 2026-10-21 locally.
        val lisbon = noon.plusHours(2).withZoneSameInstant(ZoneId.of("Europe/Lisbon"))
        assertEquals(noon.toLocalDate(), lisbon.toLocalDate())
        assertNull(engine.evaluate(ctx(lisbon, p, used = 74, records = records)).send)
    }

    @Test
    fun keysContainDateRuleAndThreshold() {
        val key = NotificationKeys.key(noon.toLocalDate(), RuleId.THRESHOLD_TOTAL, 90)
        assertEquals("2026-10-21|THRESHOLD_TOTAL|90", key)
        assertTrue(NotificationKeys.key(noon.toLocalDate(), RuleId.CHECK_IN).endsWith("|-"))
        assertNotNull(NotificationKeys.key(noon.toLocalDate(), RuleId.THRESHOLD_APP, 100, "com.feed"))
    }
}
