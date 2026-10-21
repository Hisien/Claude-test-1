package com.scrollmate.core.coach

import java.time.LocalDateTime

/** Quick alternatives to scrolling (impulse control). */
enum class ScrollAlternative { BREATHING_478, SQUATS, WATER, STRETCH, WINDOW }

/** "Fühlst du dich nach dem Scrollen besser oder müder?" */
enum class MoodAfterScroll { BETTER, SAME, TIRED }

/** "Was hast du gerade gesucht?" */
enum class ScrollIntent { DISTRACTION, BOREDOM, INFORMATION, CONNECTION }

/** Topics of the sleep coach dialog. */
enum class SleepTopic { WHY_WORSE_SLEEP, FALL_ASLEEP, RACING_THOUGHTS, NIGHT_WAKING }

object HealthCoach {

    /**
     * Deterministic suggestion that changes every hour, so it feels fresh without randomness.
     * In the evening (from 20:00) calm options are preferred over exercise.
     */
    fun suggestAlternative(now: LocalDateTime): ScrollAlternative {
        val evening = now.hour >= 20 || now.hour < 5
        val pool = if (evening) {
            listOf(ScrollAlternative.BREATHING_478, ScrollAlternative.STRETCH, ScrollAlternative.WATER)
        } else {
            ScrollAlternative.values().toList()
        }
        val index = (now.dayOfYear * 24 + now.hour) % pool.size
        return pool[index]
    }

    /** 4-7-8 breathing: inhale 4 s, hold 7 s, exhale 8 s. */
    val BREATHING_PHASES_SECONDS: List<Int> = listOf(4, 7, 8)
}
