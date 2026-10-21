package com.scrollmate.app.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.scrollmate.app.R
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.model.CoachVoice
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class CoachText(val title: String, val body: String, val actionLabel: String?)

/**
 * Maps the coach's message kinds (decided in :core) to German texts from strings.xml.
 * Reminder-type messages (thresholds, limits, check-in, focus end, wind-down) are spoken in the
 * selected [CoachVoice]; the title always starts with the voice's avatar.
 */
object CoachTexts {

    fun render(
        res: Resources,
        m: CoachMessage,
        labels: Map<String, String> = emptyMap(),
        voice: CoachVoice = CoachVoice.MOUSE,
        bedtime: LocalTime? = null,
    ): CoachText {
        val pct = m.percent ?: 0
        val minutes = m.minutes ?: 10
        val app = m.packageName?.let { labels[it] ?: it } ?: res.getString(R.string.generic_app)
        val time = (bedtime ?: LocalTime.of(22, 30)).format(DateTimeFormatter.ofPattern("HH:mm"))

        val voiced = voicedBody(m.kind, voice)
        val body = if (voiced != null) {
            when (m.kind) {
                MessageKind.THRESHOLD_REACHED -> res.getString(voiced, pct)
                MessageKind.LIMIT_REACHED_SUGGEST_FOCUS, MessageKind.STRICT_REPEAT,
                MessageKind.FOCUS_COMPLETED, MessageKind.FOCUS_ENDED_EARLY -> res.getString(voiced, minutes)
                MessageKind.APP_LIMIT_REACHED -> res.getString(voiced, app)
                MessageKind.WIND_DOWN -> res.getString(voiced, time)
                else -> res.getString(voiced)
            }
        } else {
            neutralBody(res, m, pct, minutes)
        }
        val title = "${voice.avatar} ${res.getString(titleFor(m.kind))}"
        return CoachText(title, body, actionLabel(res, m.action))
    }

    @StringRes
    private fun titleFor(kind: MessageKind): Int = when (kind) {
        MessageKind.THRESHOLD_REACHED -> R.string.coach_title_threshold
        MessageKind.LIMIT_REACHED, MessageKind.LIMIT_REACHED_SUGGEST_FOCUS -> R.string.coach_title_limit
        MessageKind.STRICT_REPEAT -> R.string.coach_title_repeat
        MessageKind.APP_LIMIT_REACHED -> R.string.coach_title_app_limit
        MessageKind.CHECK_IN, MessageKind.CHECK_IN_ON_TRACK,
        MessageKind.CHECK_IN_REPLY_MINDFUL, MessageKind.CHECK_IN_REPLY_AUTOPILOT,
        MessageKind.MOOD_BETTER, MessageKind.MOOD_SAME, MessageKind.MOOD_TIRED,
        MessageKind.INTENT_DISTRACTION, MessageKind.INTENT_BOREDOM,
        MessageKind.INTENT_INFORMATION, MessageKind.INTENT_CONNECTION -> R.string.coach_title_check_in
        MessageKind.DAILY_NO_DATA, MessageKind.DAILY_ON_TRACK, MessageKind.DAILY_NEAR_LIMIT,
        MessageKind.DAILY_OVER_LIMIT, MessageKind.GENTLE_TIP, MessageKind.GENTLE_OVER_LIMIT -> R.string.coach_title_summary
        MessageKind.FOCUS_COMPLETED, MessageKind.FOCUS_ENDED_EARLY -> R.string.coach_title_focus_done
        MessageKind.WIND_DOWN -> R.string.coach_title_wind_down
    }

    /** Texts that do not depend on the voice (in-app summaries and dialog replies). */
    private fun neutralBody(res: Resources, m: CoachMessage, pct: Int, minutes: Int): String = when (m.kind) {
        MessageKind.DAILY_NO_DATA -> res.getString(R.string.coach_body_no_data)
        MessageKind.DAILY_ON_TRACK -> res.getString(R.string.coach_body_on_track, pct)
        MessageKind.DAILY_NEAR_LIMIT -> res.getString(R.string.coach_body_near_limit, pct)
        MessageKind.DAILY_OVER_LIMIT -> res.getString(R.string.coach_body_over_limit, pct, minutes)
        MessageKind.GENTLE_TIP -> res.getString(R.string.coach_body_gentle_tip, pct)
        MessageKind.GENTLE_OVER_LIMIT -> res.getString(R.string.coach_body_gentle_over)
        MessageKind.CHECK_IN_REPLY_MINDFUL -> res.getString(R.string.coach_body_reply_mindful)
        MessageKind.CHECK_IN_REPLY_AUTOPILOT -> res.getString(R.string.coach_body_reply_autopilot, minutes)
        MessageKind.MOOD_BETTER -> res.getString(R.string.mood_reply_better)
        MessageKind.MOOD_SAME -> res.getString(R.string.mood_reply_same)
        MessageKind.MOOD_TIRED -> res.getString(R.string.mood_reply_tired)
        MessageKind.INTENT_DISTRACTION -> res.getString(R.string.intent_reply_distraction)
        MessageKind.INTENT_BOREDOM -> res.getString(R.string.intent_reply_boredom)
        MessageKind.INTENT_INFORMATION -> res.getString(R.string.intent_reply_information)
        MessageKind.INTENT_CONNECTION -> res.getString(R.string.intent_reply_connection)
        // Voiced kinds never reach this branch; keep a sensible fallback anyway.
        else -> res.getString(R.string.coach_body_check_in)
    }

    @StringRes
    private fun voicedBody(kind: MessageKind, voice: CoachVoice): Int? = when (kind) {
        MessageKind.THRESHOLD_REACHED -> pick(voice, R.string.v_mouse_threshold, R.string.v_harsh_threshold, R.string.v_casual_threshold, R.string.v_neutral_threshold, R.string.v_aggressive_threshold)
        MessageKind.LIMIT_REACHED -> pick(voice, R.string.v_mouse_limit, R.string.v_harsh_limit, R.string.v_casual_limit, R.string.v_neutral_limit, R.string.v_aggressive_limit)
        MessageKind.LIMIT_REACHED_SUGGEST_FOCUS -> pick(voice, R.string.v_mouse_limit_focus, R.string.v_harsh_limit_focus, R.string.v_casual_limit_focus, R.string.v_neutral_limit_focus, R.string.v_aggressive_limit_focus)
        MessageKind.STRICT_REPEAT -> pick(voice, R.string.v_mouse_repeat, R.string.v_harsh_repeat, R.string.v_casual_repeat, R.string.v_neutral_repeat, R.string.v_aggressive_repeat)
        MessageKind.APP_LIMIT_REACHED -> pick(voice, R.string.v_mouse_app_limit, R.string.v_harsh_app_limit, R.string.v_casual_app_limit, R.string.v_neutral_app_limit, R.string.v_aggressive_app_limit)
        MessageKind.CHECK_IN -> pick(voice, R.string.v_mouse_check_in, R.string.v_harsh_check_in, R.string.v_casual_check_in, R.string.v_neutral_check_in, R.string.v_aggressive_check_in)
        MessageKind.CHECK_IN_ON_TRACK -> pick(voice, R.string.v_mouse_on_track, R.string.v_harsh_on_track, R.string.v_casual_on_track, R.string.v_neutral_on_track, R.string.v_aggressive_on_track)
        MessageKind.FOCUS_COMPLETED -> pick(voice, R.string.v_mouse_focus_done, R.string.v_harsh_focus_done, R.string.v_casual_focus_done, R.string.v_neutral_focus_done, R.string.v_aggressive_focus_done)
        MessageKind.FOCUS_ENDED_EARLY -> pick(voice, R.string.v_mouse_focus_early, R.string.v_harsh_focus_early, R.string.v_casual_focus_early, R.string.v_neutral_focus_early, R.string.v_aggressive_focus_early)
        MessageKind.WIND_DOWN -> pick(voice, R.string.v_mouse_wind_down, R.string.v_harsh_wind_down, R.string.v_casual_wind_down, R.string.v_neutral_wind_down, R.string.v_aggressive_wind_down)
        else -> null
    }

    private fun pick(voice: CoachVoice, mouse: Int, harsh: Int, casual: Int, neutral: Int, aggressive: Int): Int = when (voice) {
        CoachVoice.MOUSE -> mouse
        CoachVoice.HARSH -> harsh
        CoachVoice.CASUAL -> casual
        CoachVoice.NEUTRAL -> neutral
        CoachVoice.AGGRESSIVE -> aggressive
    }

    fun actionLabel(res: Resources, action: CoachAction): String? = when (action) {
        CoachAction.START_FOCUS -> res.getString(R.string.action_start_focus)
        CoachAction.TAKE_BREAK -> res.getString(R.string.action_take_break)
        CoachAction.ANSWER_CHECK_IN -> res.getString(R.string.action_answer_check_in)
        CoachAction.REFLECT -> res.getString(R.string.action_reflect)
        CoachAction.TRY_ALTERNATIVE -> res.getString(R.string.action_try_alternative)
        CoachAction.NONE -> null
    }

    @StringRes
    fun voiceName(voice: CoachVoice): Int = when (voice) {
        CoachVoice.MOUSE -> R.string.voice_mouse
        CoachVoice.HARSH -> R.string.voice_harsh
        CoachVoice.CASUAL -> R.string.voice_casual
        CoachVoice.NEUTRAL -> R.string.voice_neutral
        CoachVoice.AGGRESSIVE -> R.string.voice_aggressive
    }

    @StringRes
    fun voiceDescription(voice: CoachVoice): Int = when (voice) {
        CoachVoice.MOUSE -> R.string.voice_mouse_desc
        CoachVoice.HARSH -> R.string.voice_harsh_desc
        CoachVoice.CASUAL -> R.string.voice_casual_desc
        CoachVoice.NEUTRAL -> R.string.voice_neutral_desc
        CoachVoice.AGGRESSIVE -> R.string.voice_aggressive_desc
    }

    /** Example line shown in the voice picker (the 70 % reminder). */
    fun voiceExample(res: Resources, voice: CoachVoice): String =
        res.getString(voicedBody(MessageKind.THRESHOLD_REACHED, voice)!!, 70)
}
