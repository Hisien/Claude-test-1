package com.scrollmate.core.focus

import com.scrollmate.core.berlin
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.coach.RuleBasedCoach
import com.scrollmate.core.min
import com.scrollmate.core.model.FocusStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class FocusSessionsTest {

    private val t0 = berlin(2026, 10, 21, 14).toInstant()

    @Test
    fun startCreatesRunningSession() {
        val s = FocusSessions.start("s1", min(10), t0)
        assertEquals(FocusStatus.RUNNING, s.status)
        assertNull(s.endedAt)
        assertEquals(t0.plus(min(10)), s.plannedEnd)
        assertEquals(min(10), FocusSessions.remaining(s, t0))
        assertEquals(min(4), FocusSessions.remaining(s, t0.plus(min(6))))
    }

    @Test
    fun regularRunCompletesAtPlannedEnd() {
        val s = FocusSessions.start("s1", min(10), t0)
        assertEquals(FocusStatus.RUNNING, FocusSessions.refresh(s, t0.plus(min(9))).status)
        // App was in background; we refresh much later.
        val done = FocusSessions.refresh(s, t0.plus(min(45)))
        assertEquals(FocusStatus.COMPLETED, done.status)
        assertEquals(s.plannedEnd, done.endedAt)
        assertEquals(min(10), FocusSessions.elapsed(done, t0.plus(min(45))))
        assertEquals(Duration.ZERO, FocusSessions.remaining(done, t0.plus(min(45))))
        // Idempotent
        assertEquals(done, FocusSessions.refresh(done, t0.plus(min(90))))
    }

    @Test
    fun endingEarlyKeepsTheRealDuration() {
        val s = FocusSessions.start("s1", min(25), t0)
        val ended = FocusSessions.endEarly(s, t0.plus(min(7)))
        assertEquals(FocusStatus.ENDED_EARLY, ended.status)
        assertEquals(min(7), FocusSessions.elapsed(ended, t0.plus(min(30))))
    }

    @Test
    fun endingEarlyAfterPlannedEndCountsAsCompleted() {
        val s = FocusSessions.start("s1", min(10), t0)
        val ended = FocusSessions.endEarly(s, t0.plus(min(12)))
        assertEquals(FocusStatus.COMPLETED, ended.status)
        assertEquals(min(10), FocusSessions.elapsed(ended, t0.plus(min(12))))
    }

    @Test
    fun elapsedIsClampedAgainstClockJumps() {
        val s = FocusSessions.start("s1", min(10), t0)
        assertEquals(Duration.ZERO, FocusSessions.elapsed(s, t0.minus(min(5))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        FocusSessions.start("s1", Duration.ZERO, t0)
    }

    @Test
    fun reflectionIsTrimmedAndLimited() {
        val s = FocusSessions.start("s1", min(10), t0)
        assertNull(FocusSessions.withReflection(s, "   ").reflection)
        assertEquals("Spaziergang", FocusSessions.withReflection(s, "  Spaziergang ").reflection)
        assertEquals(FocusSessions.MAX_REFLECTION, FocusSessions.withReflection(s, "x".repeat(500)).reflection!!.length)
    }

    @Test
    fun coachReactsToFinishedSessionWithReflection() {
        val coach = RuleBasedCoach()
        val s = FocusSessions.start("s1", min(10), t0)
        val early = coach.focusFinished(FocusSessions.endEarly(s, t0.plus(min(4))), t0.plus(min(4)))
        assertEquals(MessageKind.FOCUS_ENDED_EARLY, early.kind)
        assertEquals(4, early.minutes)
        val full = coach.focusFinished(FocusSessions.refresh(s, t0.plus(min(11))), t0.plus(min(11)))
        assertEquals(MessageKind.FOCUS_COMPLETED, full.kind)
        assertEquals(CoachAction.REFLECT, full.action)
    }
}
