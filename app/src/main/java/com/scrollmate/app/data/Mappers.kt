package com.scrollmate.app.data

import com.scrollmate.app.data.db.FocusSessionEntity
import com.scrollmate.app.data.db.NotificationRecordEntity
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.coach.MessageKind
import com.scrollmate.core.model.CoachVoice
import com.scrollmate.core.model.FocusSession
import com.scrollmate.core.model.FocusStatus
import com.scrollmate.core.model.NotificationRecord
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.rules.CoachTrigger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

fun NotificationRecordEntity.toModel(): NotificationRecord? {
    val rule = RuleId.values().firstOrNull { it.name == ruleId } ?: return null
    return NotificationRecord(
        key = key,
        date = LocalDate.parse(date),
        ruleId = rule,
        threshold = threshold,
        sentAt = Instant.ofEpochMilli(sentAtMs),
        countsTowardsLimit = countsTowardsLimit,
    )
}

fun NotificationRecordEntity.message(): CoachMessage? {
    val kind = MessageKind.values().firstOrNull { it.name == messageKind } ?: return null
    val act = CoachAction.values().firstOrNull { it.name == action } ?: CoachAction.NONE
    return CoachMessage(kind, act, percent, minutes, packageName)
}

fun CoachTrigger.toEntity(
    sentAt: Instant,
    shown: Boolean,
    delivered: Boolean,
    voice: CoachVoice? = null,
    demo: Boolean = false,
): NotificationRecordEntity =
    NotificationRecordEntity(
        key = key,
        date = key.substringBefore('|'),
        ruleId = ruleId.name,
        threshold = threshold,
        sentAtMs = sentAt.toEpochMilli(),
        countsTowardsLimit = countsTowardsLimit,
        shown = shown,
        deliveredAsNotification = delivered,
        messageKind = message.kind.name,
        action = message.action.name,
        percent = message.percent,
        minutes = message.minutes,
        packageName = message.packageName,
        voice = voice?.name,
        demo = demo,
    )

fun FocusSessionEntity.toModel(): FocusSession = FocusSession(
    id = id,
    plannedDuration = Duration.ofMillis(plannedMs),
    startedAt = Instant.ofEpochMilli(startedAtMs),
    endedAt = endedAtMs?.let { Instant.ofEpochMilli(it) },
    status = FocusStatus.values().firstOrNull { it.name == status } ?: FocusStatus.COMPLETED,
    reflection = reflection,
)

fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id,
    plannedMs = plannedDuration.toMillis(),
    startedAtMs = startedAt.toEpochMilli(),
    endedAtMs = endedAt?.toEpochMilli(),
    status = status.name,
    reflection = reflection,
)

fun NotificationRecordEntity.voiceOrNull(): CoachVoice? = CoachVoice.values().firstOrNull { it.name == voice }
