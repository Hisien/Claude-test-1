package com.scrollmate.core.history

import com.scrollmate.core.demo.DemoData
import com.scrollmate.core.min
import com.scrollmate.core.model.AppUsageEntry
import com.scrollmate.core.model.UsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryCalculatorTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun live(daysAgo: Long, minutes: Long) =
        AppUsageEntry("com.feed", today.minusDays(daysAgo), min(minutes), UsageSource.LIVE)

    @Test
    fun alwaysReturnsSevenDaysEndingToday() {
        val s = HistoryCalculator.summarize(emptyList(), today, min(60), UsageSource.LIVE)
        assertEquals(7, s.days.size)
        assertEquals(today, s.days.last().date)
        assertEquals(today.minusDays(6), s.days.first().date)
        assertNull(s.days.first().total)
        assertNull(s.days.first().goalMet)
    }

    @Test
    fun comparisonHiddenWithFourLiveDays() {
        val entries = (1L..4L).map { live(it, 50) } + live(0, 30)
        val s = HistoryCalculator.summarize(entries, today, min(60), UsageSource.LIVE)
        assertEquals(4, s.completedLiveDays)
        assertFalse(s.comparisonAvailable)
        assertNull(s.average)
        assertNull(s.todayVsAverage)
    }

    @Test
    fun comparisonShownFromFiveLiveDays() {
        val entries = listOf(live(1, 40), live(2, 50), live(3, 60), live(4, 70), live(5, 80), live(0, 45))
        val s = HistoryCalculator.summarize(entries, today, min(60), UsageSource.LIVE)
        assertTrue(s.comparisonAvailable)
        assertEquals(min(60), s.average)
        assertEquals(min(-15), s.todayVsAverage)
    }

    @Test
    fun goalMetIncludesExactlyTheLimit() {
        val entries = listOf(live(0, 60), live(1, 61))
        val s = HistoryCalculator.summarize(entries, today, min(60), UsageSource.LIVE)
        assertEquals(true, s.days.last().goalMet)
        assertEquals(false, s.days[5].goalMet)
    }

    @Test
    fun demoDataNeverEnablesComparisonAndIsNotMixedWithLive() {
        val entries = DemoData.entries(today) + live(0, 999)
        val s = HistoryCalculator.summarize(entries, today, min(120), UsageSource.DEMO)
        assertFalse(s.comparisonAvailable)
        assertEquals(min(87), s.days.last().total)
        assertTrue(s.days.all { it.source == UsageSource.DEMO })
    }
}
