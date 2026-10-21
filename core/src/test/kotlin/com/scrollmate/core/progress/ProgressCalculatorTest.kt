package com.scrollmate.core.progress

import com.scrollmate.core.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ProgressCalculatorTest {

    private val limit = min(100)

    @Test
    fun zeroPercent() {
        val p = ProgressCalculator.calculate(Duration.ZERO, limit)
        assertEquals(0, p.percent)
        assertEquals(limit, p.remaining)
        assertEquals(Duration.ZERO, p.overBy)
        assertFalse(p.limitReached)
        assertEquals(0f, p.ringFraction, 0.0001f)
    }

    @Test
    fun seventyPercent() {
        val p = ProgressCalculator.calculate(min(70), limit)
        assertEquals(70, p.percent)
        assertEquals(min(30), p.remaining)
        assertFalse(p.isOverLimit)
    }

    @Test
    fun exactlyHundredPercent() {
        val p = ProgressCalculator.calculate(min(100), limit)
        assertEquals(100, p.percent)
        assertEquals(Duration.ZERO, p.remaining)
        assertEquals(Duration.ZERO, p.overBy)
        assertTrue(p.limitReached)
        assertFalse(p.isOverLimit)
    }

    @Test
    fun overLimit() {
        val p = ProgressCalculator.calculate(min(130), limit)
        assertEquals(130, p.percent)
        assertEquals(Duration.ZERO, p.remaining)
        assertEquals(min(30), p.overBy)
        assertTrue(p.isOverLimit)
        assertEquals(1f, p.ringFraction, 0.0001f)
    }

    @Test
    fun percentIsFlooredNotRounded() {
        // 69.9 % must not trigger the 70 % threshold.
        val p = ProgressCalculator.calculate(Duration.ofSeconds(69 * 60 + 54), limit)
        assertEquals(69, p.percent)
    }

    @Test
    fun negativeUsageIsTreatedAsZero() {
        assertEquals(0, ProgressCalculator.calculate(min(-5), limit).percent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLimitIsRejected() {
        ProgressCalculator.calculate(min(5), Duration.ZERO)
    }
}
