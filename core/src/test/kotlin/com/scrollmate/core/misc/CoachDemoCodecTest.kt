package com.scrollmate.core.misc

import com.scrollmate.core.coach.CheckInAnswer
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.coach.RuleBasedCoach
import com.scrollmate.core.demo.DemoData
import com.scrollmate.core.min
import com.scrollmate.core.model.AppLimit
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.RestrictionStatus
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.TimeWindow
import com.scrollmate.core.model.UsageSource
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.prefs.PreferencesCodec
import com.scrollmate.core.progress.ProgressCalculator
import com.scrollmate.core.restriction.NoOpRestrictionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class CoachDemoCodecTest {

    private val coach = RuleBasedCoach()

    @Test
    fun dailySummaryPerStyle() {
        val low = ProgressCalculator.calculate(min(30), min(100))
        val near = ProgressCalculator.calculate(min(80), min(100))
        val over = ProgressCalculator.calculate(min(130), min(100))
        assertEquals(MessageKind.DAILY_NO_DATA, coach.dailySummary(null, CoachingStyle.ACTIVE, 10).kind)
        assertEquals(MessageKind.DAILY_ON_TRACK, coach.dailySummary(low, CoachingStyle.STRICT, 10).kind)
        assertEquals(MessageKind.DAILY_NEAR_LIMIT, coach.dailySummary(near, CoachingStyle.ACTIVE, 10).kind)
        assertEquals(MessageKind.GENTLE_TIP, coach.dailySummary(near, CoachingStyle.GENTLE, 10).kind)
        assertEquals(MessageKind.DAILY_OVER_LIMIT, coach.dailySummary(over, CoachingStyle.ACTIVE, 10).kind)
        // Gentle never pushes a focus session.
        val gentleOver = coach.dailySummary(over, CoachingStyle.GENTLE, 10)
        assertEquals(MessageKind.GENTLE_OVER_LIMIT, gentleOver.kind)
        assertTrue(gentleOver.action != CoachAction.START_FOCUS)
    }

    @Test
    fun checkInReplies() {
        assertEquals(CoachAction.NONE, coach.checkInReply(CheckInAnswer.MINDFUL, 10).action)
        val autopilot = coach.checkInReply(CheckInAnswer.AUTOPILOT, 15)
        assertEquals(CoachAction.START_FOCUS, autopilot.action)
        assertEquals(15, autopilot.minutes)
    }

    @Test
    fun demoDataIsDeterministicSevenDaysFiveApps() {
        val today = LocalDate.of(2026, 10, 21)
        val a = DemoData.entries(today)
        assertEquals(a, DemoData.entries(today))
        assertEquals(35, a.size)
        assertEquals(7, a.map { it.date }.distinct().size)
        assertEquals(5, a.map { it.packageName }.distinct().size)
        assertTrue(a.all { it.source == UsageSource.DEMO && DemoData.isDemoPackage(it.packageName) })
        assertEquals(today, a.maxOf { it.date })
        assertEquals("Kurzvideos", DemoData.labelFor("demo.shortvideo"))
    }

    @Test
    fun codecRoundTrips() {
        val limits = listOf(AppLimit("com.a.b", min(30)), AppLimit("org.x;y=z", min(45)))
        assertEquals(limits, PreferencesCodec.decodeAppLimits(PreferencesCodec.encodeAppLimits(limits)))

        val windows = listOf(
            TimeWindow("w1", "Schlafenszeit | ; spät", LocalTime.of(22, 0), LocalTime.of(7, 0)),
            TimeWindow("w2", "Lernen", LocalTime.of(9, 30), LocalTime.of(12, 0), enabled = false),
        )
        assertEquals(windows, PreferencesCodec.decodeTimeWindows(PreferencesCodec.encodeTimeWindows(windows)))

        val rules = setOf(RuleId.CHECK_IN, RuleId.STRICT_REPEAT)
        assertEquals(rules, PreferencesCodec.decodeRuleIds(PreferencesCodec.encodeRuleIds(rules)))

        assertEquals(listOf(70, 90, 100), PreferencesCodec.decodeThresholds("70,90,100"))
        assertEquals(emptyList<Int>(), PreferencesCodec.decodeThresholds(""))
        assertNull(PreferencesCodec.decodeThresholds(null))

        val apps = setOf("com.a", "com.b")
        assertEquals(apps, PreferencesCodec.decodeStringSet(PreferencesCodec.encodeStringSet(apps)))
    }

    @Test
    fun codecSkipsGarbage() {
        assertEquals(emptyList<AppLimit>(), PreferencesCodec.decodeAppLimits("garbage;=5;x=abc;y=-3"))
        assertEquals(emptyList<TimeWindow>(), PreferencesCodec.decodeTimeWindows("a|b|25:00|07:00|1;broken"))
    }

    @Test
    fun sanitizeClampsValues() {
        val raw = UserPreferences(
            goal = UserPreferences().goal.copy(dailyLimit = Duration.ofMinutes(1)),
            reminders = UserPreferences().reminders.copy(thresholdsPercent = listOf(100, 70, 70, 0, 500), maxRemindersPerDay = 99),
        )
        val s = raw.sanitized()
        assertEquals(UserPreferences.MIN_LIMIT, s.goal.dailyLimit)
        assertEquals(listOf(70, 100), s.reminders.thresholdsPercent)
        assertEquals(20, s.reminders.maxRemindersPerDay)
    }

    @Test
    fun restrictionIsHonestlyUnavailable() {
        assertEquals(RestrictionStatus.UNAVAILABLE, NoOpRestrictionService().status())
    }
}
