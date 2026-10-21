package com.scrollmate.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeWindowTest {

    private val sleep = TimeWindow("sleep", "Schlafenszeit", LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val study = TimeWindow("study", "Lernen", LocalTime.of(9, 0), LocalTime.of(12, 0))

    @Test
    fun overnightWindowContainsLateEveningAndEarlyMorning() {
        assertTrue(sleep.crossesMidnight)
        assertTrue(sleep.contains(LocalTime.of(22, 0)))
        assertTrue(sleep.contains(LocalTime.of(23, 59)))
        assertTrue(sleep.contains(LocalTime.MIDNIGHT))
        assertTrue(sleep.contains(LocalTime.of(3, 0)))
        assertTrue(sleep.contains(LocalTime.of(6, 59)))
    }

    @Test
    fun overnightWindowExcludesDaytimeAndEnd() {
        assertFalse(sleep.contains(LocalTime.of(7, 0)))
        assertFalse(sleep.contains(LocalTime.of(12, 0)))
        assertFalse(sleep.contains(LocalTime.of(21, 59)))
    }

    @Test
    fun sameDayWindow() {
        assertFalse(study.crossesMidnight)
        assertTrue(study.contains(LocalTime.of(9, 0)))
        assertTrue(study.contains(LocalTime.of(11, 59)))
        assertFalse(study.contains(LocalTime.of(12, 0)))
        assertFalse(study.contains(LocalTime.of(8, 59)))
    }

    @Test
    fun equalStartAndEndMeansWholeDay() {
        val all = TimeWindow("all", "Ganzer Tag", LocalTime.of(8, 0), LocalTime.of(8, 0))
        assertTrue(all.contains(LocalTime.of(3, 0)))
        assertTrue(all.contains(LocalTime.of(20, 0)))
    }
}
