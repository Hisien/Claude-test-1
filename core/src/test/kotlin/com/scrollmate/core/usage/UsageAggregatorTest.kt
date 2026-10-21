package com.scrollmate.core.usage

import com.scrollmate.core.BERLIN
import com.scrollmate.core.berlin
import com.scrollmate.core.min
import com.scrollmate.core.model.UsageSource
import com.scrollmate.core.time.DayMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class UsageAggregatorTest {

    private val app = "com.example.feed"
    private val other = "com.example.chat"

    private fun fg(pkg: String, t: java.time.ZonedDateTime) = UsageEvent(pkg, t.toInstant(), UsageEventType.FOREGROUND)
    private fun bg(pkg: String, t: java.time.ZonedDateTime) = UsageEvent(pkg, t.toInstant(), UsageEventType.BACKGROUND)

    @Test
    fun sessionAcrossMidnightIsSplitByLocalDay() {
        val events = listOf(fg(app, berlin(2026, 6, 10, 23, 50)), bg(app, berlin(2026, 6, 11, 0, 20)))
        val result = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 11, 12).toInstant(), BERLIN,
        )
        assertEquals(min(10), result[LocalDate.of(2026, 6, 10)]!![app])
        assertEquals(min(20), result[LocalDate.of(2026, 6, 11)]!![app])
    }

    @Test
    fun openSessionIsCountedUntilRangeEnd() {
        val events = listOf(fg(app, berlin(2026, 6, 10, 10, 0)))
        val result = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 10, 25).toInstant(), BERLIN,
        )
        assertEquals(min(25), result[LocalDate.of(2026, 6, 10)]!![app])
    }

    @Test
    fun pauseWithoutResumeCountsFromRangeStart() {
        // App was already open at midnight when the query range started.
        val events = listOf(bg(app, berlin(2026, 6, 10, 0, 15)))
        val result = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 12).toInstant(), BERLIN,
        )
        assertEquals(min(15), result[LocalDate.of(2026, 6, 10)]!![app])
    }

    @Test
    fun foregroundOfAnotherAppClosesThePreviousSession() {
        val events = listOf(
            fg(app, berlin(2026, 6, 10, 10, 0)),
            fg(other, berlin(2026, 6, 10, 10, 10)), // missing pause event for app
            bg(other, berlin(2026, 6, 10, 10, 15)),
        )
        val day = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 12).toInstant(), BERLIN,
        )[LocalDate.of(2026, 6, 10)]!!
        assertEquals(min(10), day[app])
        assertEquals(min(5), day[other])
    }

    @Test
    fun screenOffClosesTheOpenSession() {
        val events = listOf(
            fg(app, berlin(2026, 6, 10, 10, 0)),
            UsageEvent(null, berlin(2026, 6, 10, 10, 7).toInstant(), UsageEventType.DEVICE_INACTIVE),
        )
        val day = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 12).toInstant(), BERLIN,
        )[LocalDate.of(2026, 6, 10)]!!
        assertEquals(min(7), day[app])
    }

    @Test
    fun repeatedResumeOfSameAppDoesNotDoubleCount() {
        val events = listOf(
            fg(app, berlin(2026, 6, 10, 10, 0)),
            fg(app, berlin(2026, 6, 10, 10, 5)), // second activity of the same app
            bg(app, berlin(2026, 6, 10, 10, 20)),
        )
        val day = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 12).toInstant(), BERLIN,
        )[LocalDate.of(2026, 6, 10)]!!
        assertEquals(min(20), day[app])
    }

    @Test
    fun dstEndInOctoberGivesA25HourDayAndCorrectSessionLength() {
        // 2026-10-25 is the last Sunday in October: clocks go back from 03:00 CEST to 02:00 CET.
        val date = LocalDate.of(2026, 10, 25)
        assertEquals(Duration.ofHours(25), DayMath.dayLength(date, BERLIN))

        // 01:30 CEST -> 03:30 CET is three real hours, not two.
        val start = berlin(2026, 10, 25, 1, 30)
        val end = berlin(2026, 10, 25, 3, 30)
        val events = listOf(fg(app, start), bg(app, end))
        val result = UsageAggregator.aggregate(
            events, DayMath.startOfDay(date, BERLIN), DayMath.endOfDay(date, BERLIN), BERLIN,
        )
        assertEquals(Duration.ofHours(3), result[date]!![app])
    }

    @Test
    fun dstStartInMarchGivesA23HourDay() {
        val date = LocalDate.of(2026, 3, 29)
        assertEquals(Duration.ofHours(23), DayMath.dayLength(date, BERLIN))
        // Session over the whole day is split exactly at both midnights.
        val split = DayMath.splitByLocalDay(
            berlin(2026, 3, 28, 23).toInstant(), berlin(2026, 3, 30, 1).toInstant(), BERLIN,
        )
        assertEquals(Duration.ofHours(1), split[LocalDate.of(2026, 3, 28)])
        assertEquals(Duration.ofHours(23), split[date])
        assertEquals(Duration.ofHours(1), split[LocalDate.of(2026, 3, 30)])
    }

    @Test
    fun eventsOutsideRangeAreIgnoredAndEmptyRangeIsEmpty() {
        val events = listOf(fg(app, berlin(2026, 6, 9, 10)), bg(app, berlin(2026, 6, 9, 11)))
        val result = UsageAggregator.aggregate(
            events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 12).toInstant(), BERLIN,
        )
        assertTrue(result.isEmpty())
        assertTrue(
            UsageAggregator.aggregate(events, berlin(2026, 6, 10, 0).toInstant(), berlin(2026, 6, 10, 0).toInstant(), BERLIN)
                .isEmpty(),
        )
    }

    @Test
    fun dailyUsageFiltersSourceSelectionAndExclusions() {
        val d = LocalDate.of(2026, 6, 10)
        val entries = UsageAggregator.toEntries(
            mapOf(d to mapOf(app to min(30), other to min(10), "com.launcher" to min(50))), UsageSource.LIVE,
        )
        val all = DailyUsage.of(entries, d, UsageSource.LIVE, watched = emptySet(), excluded = setOf("com.launcher"))
        assertEquals(min(40), all.total)
        assertEquals(app, all.top(1).single().first)

        val selected = DailyUsage.of(entries, d, UsageSource.LIVE, watched = setOf(other))
        assertEquals(min(10), selected.total)

        val demo = DailyUsage.of(entries, d, UsageSource.DEMO, watched = emptySet())
        assertEquals(Duration.ZERO, demo.total)
        assertNull(demo.perApp.firstOrNull())
    }
}
