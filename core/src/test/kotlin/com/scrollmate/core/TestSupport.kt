package com.scrollmate.core

import com.scrollmate.core.model.NotificationRecord
import com.scrollmate.core.rules.CoachTrigger
import com.scrollmate.core.rules.Evaluation
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

fun berlin(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): ZonedDateTime =
    LocalDateTime.of(y, mo, d, h, mi).atZone(BERLIN)

fun min(m: Long): Duration = Duration.ofMinutes(m)

/** Mimics what the app does after an evaluation: persist sent and silently handled triggers. */
fun persist(evaluation: Evaluation, now: ZonedDateTime, into: MutableList<NotificationRecord>) {
    fun rec(t: CoachTrigger) = NotificationRecord(
        key = t.key,
        date = LocalDate.parse(t.key.substringBefore('|')),
        ruleId = t.ruleId,
        threshold = t.threshold,
        sentAt = now.toInstant(),
        countsTowardsLimit = t.countsTowardsLimit,
    )
    evaluation.markHandled.forEach { into += rec(it) }
    evaluation.send?.let { into += rec(it) }
}
