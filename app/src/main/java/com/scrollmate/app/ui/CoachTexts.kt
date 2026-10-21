package com.scrollmate.app.ui

import android.content.res.Resources
import com.scrollmate.app.R
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.coach.MessageKind

data class CoachText(val title: String, val body: String, val actionLabel: String?)

/** Maps the coach's message kinds (decided in :core) to German texts from strings.xml. */
object CoachTexts {

    fun render(res: Resources, m: CoachMessage, labels: Map<String, String> = emptyMap()): CoachText {
        val pct = m.percent ?: 0
        val minutes = m.minutes ?: 10
        val (title, body) = when (m.kind) {
            MessageKind.THRESHOLD_REACHED ->
                res.getString(R.string.coach_title_threshold) to res.getString(R.string.coach_body_threshold, pct)
            MessageKind.LIMIT_REACHED ->
                res.getString(R.string.coach_title_limit) to res.getString(R.string.coach_body_limit)
            MessageKind.LIMIT_REACHED_SUGGEST_FOCUS ->
                res.getString(R.string.coach_title_limit) to res.getString(R.string.coach_body_limit_focus, minutes)
            MessageKind.STRICT_REPEAT ->
                res.getString(R.string.coach_title_repeat) to res.getString(R.string.coach_body_repeat, minutes)
            MessageKind.APP_LIMIT_REACHED -> {
                val app = m.packageName?.let { labels[it] ?: it } ?: res.getString(R.string.generic_app)
                res.getString(R.string.coach_title_app_limit) to res.getString(R.string.coach_body_app_limit, app)
            }
            MessageKind.CHECK_IN ->
                res.getString(R.string.coach_title_check_in) to res.getString(R.string.coach_body_check_in)
            MessageKind.CHECK_IN_ON_TRACK ->
                res.getString(R.string.coach_title_check_in) to res.getString(R.string.coach_body_check_in_on_track)
            MessageKind.DAILY_NO_DATA ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_no_data)
            MessageKind.DAILY_ON_TRACK ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_on_track, pct)
            MessageKind.DAILY_NEAR_LIMIT ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_near_limit, pct)
            MessageKind.DAILY_OVER_LIMIT ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_over_limit, pct, minutes)
            MessageKind.GENTLE_TIP ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_gentle_tip, pct)
            MessageKind.GENTLE_OVER_LIMIT ->
                res.getString(R.string.coach_title_summary) to res.getString(R.string.coach_body_gentle_over)
            MessageKind.CHECK_IN_REPLY_MINDFUL ->
                res.getString(R.string.coach_title_check_in) to res.getString(R.string.coach_body_reply_mindful)
            MessageKind.CHECK_IN_REPLY_AUTOPILOT ->
                res.getString(R.string.coach_title_check_in) to res.getString(R.string.coach_body_reply_autopilot, minutes)
            MessageKind.FOCUS_COMPLETED ->
                res.getString(R.string.coach_title_focus_done) to res.getString(R.string.coach_body_focus_done, minutes)
            MessageKind.FOCUS_ENDED_EARLY ->
                res.getString(R.string.coach_title_focus_done) to res.getString(R.string.coach_body_focus_early, minutes)
        }
        return CoachText(title, body, actionLabel(res, m.action))
    }

    fun actionLabel(res: Resources, action: CoachAction): String? = when (action) {
        CoachAction.START_FOCUS -> res.getString(R.string.action_start_focus)
        CoachAction.TAKE_BREAK -> res.getString(R.string.action_take_break)
        CoachAction.ANSWER_CHECK_IN -> res.getString(R.string.action_answer_check_in)
        CoachAction.REFLECT -> res.getString(R.string.action_reflect)
        CoachAction.NONE -> null
    }
}
