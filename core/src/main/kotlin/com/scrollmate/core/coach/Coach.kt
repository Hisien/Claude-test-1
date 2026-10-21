package com.scrollmate.core.coach

import com.scrollmate.core.focus.FocusSessions
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.FocusSession
import com.scrollmate.core.model.FocusStatus
import com.scrollmate.core.progress.Progress
import java.time.Instant

/** The one optional next step a coach message offers. NONE is only used for plain acknowledgements. */
enum class CoachAction { START_FOCUS, TAKE_BREAK, ANSWER_CHECK_IN, REFLECT, NONE }

/**
 * Message kinds. The :app module maps each kind to a German string in strings.xml, so the
 * wording lives in resources while the decision logic stays here and is unit-testable.
 */
enum class MessageKind {
    THRESHOLD_REACHED,
    LIMIT_REACHED,
    LIMIT_REACHED_SUGGEST_FOCUS,
    STRICT_REPEAT,
    APP_LIMIT_REACHED,
    CHECK_IN,
    CHECK_IN_ON_TRACK,
    DAILY_NO_DATA,
    DAILY_ON_TRACK,
    DAILY_NEAR_LIMIT,
    DAILY_OVER_LIMIT,
    GENTLE_TIP,
    GENTLE_OVER_LIMIT,
    CHECK_IN_REPLY_MINDFUL,
    CHECK_IN_REPLY_AUTOPILOT,
    FOCUS_COMPLETED,
    FOCUS_ENDED_EARLY,
}

data class CoachMessage(
    val kind: MessageKind,
    val action: CoachAction,
    val percent: Int? = null,
    val minutes: Int? = null,
    val packageName: String? = null,
)

enum class CheckInAnswer { MINDFUL, AUTOPILOT }

/**
 * Coach contract. The MVP ships [RuleBasedCoach]; an AI coach could implement the same interface later.
 * Messages are friendly, offer exactly one optional next step, never diagnose and never guess
 * moods or reasons behind the numbers.
 */
interface Coach {
    fun thresholdReached(percent: Int, style: CoachingStyle, focusMinutes: Int): CoachMessage
    fun appLimitReached(packageName: String): CoachMessage
    fun strictRepeat(focusMinutes: Int): CoachMessage
    fun checkIn(progress: Progress?): CoachMessage
    fun dailySummary(progress: Progress?, style: CoachingStyle, focusMinutes: Int): CoachMessage
    fun checkInReply(answer: CheckInAnswer, focusMinutes: Int): CoachMessage
    fun focusFinished(session: FocusSession, now: Instant): CoachMessage
}

class RuleBasedCoach : Coach {

    override fun thresholdReached(percent: Int, style: CoachingStyle, focusMinutes: Int): CoachMessage = when {
        percent < 100 -> CoachMessage(MessageKind.THRESHOLD_REACHED, CoachAction.TAKE_BREAK, percent = percent)
        style == CoachingStyle.STRICT ->
            CoachMessage(MessageKind.LIMIT_REACHED_SUGGEST_FOCUS, CoachAction.START_FOCUS, percent = percent, minutes = focusMinutes)
        else -> CoachMessage(MessageKind.LIMIT_REACHED, CoachAction.TAKE_BREAK, percent = percent)
    }

    override fun appLimitReached(packageName: String): CoachMessage =
        CoachMessage(MessageKind.APP_LIMIT_REACHED, CoachAction.TAKE_BREAK, packageName = packageName)

    override fun strictRepeat(focusMinutes: Int): CoachMessage =
        CoachMessage(MessageKind.STRICT_REPEAT, CoachAction.START_FOCUS, minutes = focusMinutes)

    override fun checkIn(progress: Progress?): CoachMessage =
        if (progress != null && !progress.limitReached) {
            CoachMessage(MessageKind.CHECK_IN_ON_TRACK, CoachAction.REFLECT, percent = progress.percent)
        } else {
            CoachMessage(MessageKind.CHECK_IN, CoachAction.ANSWER_CHECK_IN, percent = progress?.percent)
        }

    override fun dailySummary(progress: Progress?, style: CoachingStyle, focusMinutes: Int): CoachMessage {
        if (progress == null) return CoachMessage(MessageKind.DAILY_NO_DATA, CoachAction.NONE)
        val gentle = style == CoachingStyle.GENTLE
        return when {
            progress.percent < NEAR_LIMIT_PERCENT ->
                CoachMessage(MessageKind.DAILY_ON_TRACK, CoachAction.REFLECT, percent = progress.percent)
            !progress.limitReached && gentle ->
                CoachMessage(MessageKind.GENTLE_TIP, CoachAction.TAKE_BREAK, percent = progress.percent)
            !progress.limitReached ->
                CoachMessage(MessageKind.DAILY_NEAR_LIMIT, CoachAction.TAKE_BREAK, percent = progress.percent)
            gentle ->
                CoachMessage(MessageKind.GENTLE_OVER_LIMIT, CoachAction.REFLECT, percent = progress.percent)
            else ->
                CoachMessage(MessageKind.DAILY_OVER_LIMIT, CoachAction.START_FOCUS, percent = progress.percent, minutes = focusMinutes)
        }
    }

    override fun checkInReply(answer: CheckInAnswer, focusMinutes: Int): CoachMessage = when (answer) {
        CheckInAnswer.MINDFUL -> CoachMessage(MessageKind.CHECK_IN_REPLY_MINDFUL, CoachAction.NONE)
        CheckInAnswer.AUTOPILOT ->
            CoachMessage(MessageKind.CHECK_IN_REPLY_AUTOPILOT, CoachAction.START_FOCUS, minutes = focusMinutes)
    }

    override fun focusFinished(session: FocusSession, now: Instant): CoachMessage {
        val minutes = FocusSessions.elapsed(session, now).toMinutes().toInt()
        val kind = if (session.status == FocusStatus.ENDED_EARLY) MessageKind.FOCUS_ENDED_EARLY else MessageKind.FOCUS_COMPLETED
        return CoachMessage(kind, CoachAction.REFLECT, minutes = minutes)
    }

    companion object {
        const val NEAR_LIMIT_PERCENT = 70
    }
}
