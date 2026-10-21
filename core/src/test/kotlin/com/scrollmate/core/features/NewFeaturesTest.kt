package com.scrollmate.core.features

import com.scrollmate.core.berlin
import com.scrollmate.core.categories.AppCategory
import com.scrollmate.core.categories.CategoryClassifier
import com.scrollmate.core.categories.SystemCategoryHint
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.HealthCoach
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.coach.MoodAfterScroll
import com.scrollmate.core.coach.RuleBasedCoach
import com.scrollmate.core.coach.ScrollAlternative
import com.scrollmate.core.coach.ScrollIntent
import com.scrollmate.core.demo.DemoData
import com.scrollmate.core.min
import com.scrollmate.core.model.CoachVoice
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.NotificationRecord
import com.scrollmate.core.model.ReminderSettings
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.UserPreferences
import com.scrollmate.core.model.WindDownSettings
import com.scrollmate.core.persist
import com.scrollmate.core.progress.LimitZone
import com.scrollmate.core.progress.ProgressCalculator
import com.scrollmate.core.rules.RuleContext
import com.scrollmate.core.rules.RuleEngine
import com.scrollmate.core.winddown.WindDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NewFeaturesTest {

    private fun at(h: Int, m: Int = 0, day: Int = 21) = LocalDateTime.of(2026, 10, day, h, m)

    private val wd = WindDownSettings(
        enabled = true,
        bedtime = LocalTime.of(22, 30),
        wakeTime = LocalTime.of(6, 30),
        leadMinutes = 30,
        blockedApps = setOf("com.instagram.android"),
    )

    // --- Wind-down ---

    @Test
    fun windDownStartsThirtyMinutesBeforeBedtimeAndRunsOvernight() {
        assertEquals(LocalTime.of(22, 0), WindDown.start(wd))
        assertFalse(WindDown.isActive(at(21, 59), wd))
        assertTrue(WindDown.isActive(at(22, 0), wd))
        assertTrue(WindDown.isActive(at(23, 59), wd))
        assertTrue(WindDown.isActive(at(3, 0, day = 22), wd))
        assertFalse(WindDown.isActive(at(6, 30, day = 22), wd))
        assertFalse(WindDown.isActive(at(12, 0), wd))
    }

    @Test
    fun onlySelectedAppsAreBlocked() {
        assertTrue(WindDown.isBlocked("com.instagram.android", at(23, 0), wd))
        assertFalse(WindDown.isBlocked("com.whatsapp", at(23, 0), wd))
        assertFalse(WindDown.isBlocked("com.instagram.android", at(23, 0), wd.copy(enabled = false)))
        assertFalse(WindDown.isBlocked(null, at(23, 0), wd))
    }

    @Test
    fun bedtimeAfterMidnightWrapsCorrectly() {
        val late = wd.copy(bedtime = LocalTime.of(0, 15))
        assertEquals(LocalTime.of(23, 45), WindDown.start(late))
        assertTrue(WindDown.isActive(at(23, 50), late))
        assertTrue(WindDown.isActive(at(0, 10, day = 22), late))
        assertTrue(WindDown.isLeadPhase(at(0, 10, day = 22), late))
        assertFalse(WindDown.isLeadPhase(at(0, 20, day = 22), late))
    }

    @Test
    fun pausingTonightAlsoCoversAfterMidnightButNotTomorrow() {
        val paused = wd.copy(pausedForDate = LocalDate.of(2026, 10, 21))
        assertFalse(WindDown.isActive(at(23, 0), paused))
        assertFalse(WindDown.isActive(at(2, 0, day = 22), paused))
        assertTrue(WindDown.isActive(at(22, 30, day = 22), paused))
        assertEquals(LocalDate.of(2026, 10, 21), WindDown.eveningOf(at(2, 0, day = 22), wd))
    }

    @Test
    fun untilNextStart() {
        assertEquals(Duration.ofHours(2), WindDown.untilNextStart(berlin(2026, 10, 21, 20), wd))
        assertEquals(Duration.ZERO, WindDown.untilNextStart(berlin(2026, 10, 21, 23), wd))
        assertEquals(Duration.ofHours(15), WindDown.untilNextStart(berlin(2026, 10, 22, 7), wd))
    }

    @Test
    fun engineSendsEveningReminderOncePerEvening() {
        val engine = RuleEngine(RuleBasedCoach())
        val prefs = UserPreferences(
            style = CoachingStyle.GENTLE,
            reminders = ReminderSettings(checkInTime = null),
            windDown = wd,
        )
        val records = mutableListOf<NotificationRecord>()
        val before = berlin(2026, 10, 21, 21, 50)
        assertNull(engine.evaluate(RuleContext(before, prefs, min(10), records = records)).send)

        val lead = berlin(2026, 10, 21, 22, 5)
        val e = engine.evaluate(RuleContext(lead, prefs, min(10), records = records))
        assertEquals(RuleId.WIND_DOWN, e.send?.ruleId)
        assertEquals(MessageKind.WIND_DOWN, e.send?.message?.kind)
        persist(e, lead, records)
        assertNull(engine.evaluate(RuleContext(lead.plusMinutes(15), prefs, min(10), records = records)).send)

        // Paused for tonight: no reminder.
        val paused = prefs.copy(windDown = wd.copy(pausedForDate = LocalDate.of(2026, 10, 21)))
        assertNull(engine.evaluate(RuleContext(lead, paused, min(10))).send)
    }

    // --- Limit zones ---

    @Test
    fun limitZones() {
        assertEquals(LimitZone.SAFE, ProgressCalculator.calculate(min(69), min(100)).zone)
        assertEquals(LimitZone.WARNING, ProgressCalculator.calculate(min(70), min(100)).zone)
        assertEquals(LimitZone.WARNING, ProgressCalculator.calculate(min(99), min(100)).zone)
        assertEquals(LimitZone.EXCEEDED, ProgressCalculator.calculate(min(100), min(100)).zone)
        assertEquals(LimitZone.EXCEEDED, ProgressCalculator.calculate(min(150), min(100)).zone)
    }

    // --- Categories ---

    @Test
    fun classifierUsesKnownAppsThenSystemHint() {
        assertEquals(AppCategory.SOCIAL, CategoryClassifier.classify("com.whatsapp"))
        assertEquals(AppCategory.ENTERTAINMENT, CategoryClassifier.classify("com.google.android.youtube", SystemCategoryHint.SOCIAL))
        assertEquals(AppCategory.WORK_LEARNING, CategoryClassifier.classify("notion.id"))
        assertEquals(AppCategory.NEWS, CategoryClassifier.classify("x.y", SystemCategoryHint.NEWS))
        assertEquals(AppCategory.ENTERTAINMENT, CategoryClassifier.classify("x.game", SystemCategoryHint.GAME))
        assertEquals(AppCategory.OTHER, CategoryClassifier.classify("x.unknown"))
    }

    @Test
    fun breakdownSumsAndSorts() {
        val shares = CategoryClassifier.breakdown(
            mapOf(
                "com.instagram.android" to min(30),
                "com.whatsapp" to min(10),
                "com.google.android.youtube" to min(50),
                "notion.id" to min(10),
                "zero.app" to Duration.ZERO,
            ),
        )
        assertEquals(listOf(AppCategory.ENTERTAINMENT, AppCategory.SOCIAL, AppCategory.WORK_LEARNING), shares.map { it.category })
        assertEquals(min(40), shares[1].duration)
        assertEquals(40, shares[1].percent)
        assertEquals(100, shares.sumOf { it.percent })
        assertTrue(CategoryClassifier.breakdown(emptyMap()).isEmpty())
    }

    @Test
    fun demoAppsHaveCategories() {
        val perApp = DemoData.entries(LocalDate.of(2026, 10, 21)).groupBy { it.packageName }
            .mapValues { (_, l) -> l.fold(Duration.ZERO) { a, e -> a.plus(e.duration) } }
        val cats = CategoryClassifier.breakdown(perApp).map { it.category }.toSet()
        assertEquals(setOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.NEWS), cats)
    }

    // --- Health coach & voices ---

    @Test
    fun alternativesAreCalmInTheEvening() {
        val calm = setOf(ScrollAlternative.BREATHING_478, ScrollAlternative.STRETCH, ScrollAlternative.WATER)
        (20..23).forEach { h -> assertTrue(HealthCoach.suggestAlternative(at(h)) in calm) }
        assertEquals(HealthCoach.suggestAlternative(at(10)), HealthCoach.suggestAlternative(at(10, 45)))
        assertEquals(listOf(4, 7, 8), HealthCoach.BREATHING_PHASES_SECONDS)
    }

    @Test
    fun followUpRepliesOfferAtMostOneAction() {
        val coach = RuleBasedCoach()
        assertEquals(CoachAction.NONE, coach.moodReply(MoodAfterScroll.BETTER).action)
        assertEquals(CoachAction.TRY_ALTERNATIVE, coach.moodReply(MoodAfterScroll.TIRED).action)
        assertEquals(MessageKind.INTENT_BOREDOM, coach.intentReply(ScrollIntent.BOREDOM).kind)
        assertEquals(CoachAction.NONE, coach.intentReply(ScrollIntent.INFORMATION).action)
    }

    @Test
    fun voicesHaveDistinctAvatarsAndMouseIsDefault() {
        assertEquals(5, CoachVoice.values().map { it.avatar }.toSet().size)
        assertEquals(CoachVoice.MOUSE, UserPreferences().voice)
    }
}
