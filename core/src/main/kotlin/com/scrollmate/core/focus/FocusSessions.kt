package com.scrollmate.core.focus

import com.scrollmate.core.model.FocusSession
import com.scrollmate.core.model.FocusStatus
import java.time.Duration
import java.time.Instant

/**
 * Pure state transitions for a focus session inside ScrollMate.
 * A focus session never blocks other apps; it is a timer plus a short reflection.
 */
object FocusSessions {

    val MIN_DURATION: Duration = Duration.ofMinutes(1)
    val MAX_DURATION: Duration = Duration.ofHours(4)

    fun start(id: String, planned: Duration, now: Instant): FocusSession {
        require(planned >= MIN_DURATION && planned <= MAX_DURATION) { "planned duration out of range" }
        return FocusSession(id = id, plannedDuration = planned, startedAt = now)
    }

    /** Marks a running session as completed once its planned end has passed. Idempotent. */
    fun refresh(session: FocusSession, now: Instant): FocusSession =
        if (session.status == FocusStatus.RUNNING && !now.isBefore(session.plannedEnd)) {
            session.copy(status = FocusStatus.COMPLETED, endedAt = session.plannedEnd)
        } else {
            session
        }

    /** Ends a running session before its planned end. If the end already passed, it is completed regularly. */
    fun endEarly(session: FocusSession, now: Instant): FocusSession {
        val refreshed = refresh(session, now)
        if (refreshed.status != FocusStatus.RUNNING) return refreshed
        val end = if (now.isBefore(session.startedAt)) session.startedAt else now
        return refreshed.copy(status = FocusStatus.ENDED_EARLY, endedAt = end)
    }

    fun remaining(session: FocusSession, now: Instant): Duration {
        if (session.status != FocusStatus.RUNNING) return Duration.ZERO
        val left = Duration.between(now, session.plannedEnd)
        return if (left.isNegative) Duration.ZERO else left
    }

    /** Actual focused time, clamped to [0, planned]. */
    fun elapsed(session: FocusSession, now: Instant): Duration {
        val end = session.endedAt ?: now
        val raw = Duration.between(session.startedAt, end)
        return when {
            raw.isNegative -> Duration.ZERO
            raw > session.plannedDuration -> session.plannedDuration
            else -> raw
        }
    }

    fun withReflection(session: FocusSession, text: String): FocusSession =
        session.copy(reflection = text.trim().take(MAX_REFLECTION).ifEmpty { null })

    const val MAX_REFLECTION = 280
}
