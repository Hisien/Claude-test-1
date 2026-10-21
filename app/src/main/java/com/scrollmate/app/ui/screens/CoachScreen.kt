package com.scrollmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmate.app.R
import com.scrollmate.app.ui.CoachTexts
import com.scrollmate.app.ui.MainUiState
import com.scrollmate.app.ui.MainViewModel
import com.scrollmate.app.ui.ManualCheckResult
import com.scrollmate.app.ui.components.DemoNotice
import com.scrollmate.app.ui.components.InfoRow
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.ScreenColumn
import com.scrollmate.app.ui.components.SectionCard
import com.scrollmate.app.ui.components.hhmm
import com.scrollmate.core.coach.CheckInAnswer
import com.scrollmate.core.coach.CoachAction
import com.scrollmate.core.coach.CoachMessage
import java.time.ZoneId

@Composable
fun CoachScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onStartFocus: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        LoadingState(modifier)
        return
    }
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val reflections by viewModel.reflections.collectAsStateWithLifecycle()
    var reflecting by rememberSaveable { mutableStateOf(false) }
    var breakAcknowledged by rememberSaveable { mutableStateOf(false) }
    val focusMinutes = snapshot.prefs.focusDuration.toMinutes().toInt()

    val onAction: (CoachAction) -> Unit = { action ->
        when (action) {
            CoachAction.START_FOCUS -> onStartFocus()
            CoachAction.REFLECT -> reflecting = true
            CoachAction.TAKE_BREAK -> breakAcknowledged = true
            CoachAction.ANSWER_CHECK_IN, CoachAction.NONE -> Unit
        }
    }

    ScreenColumn(modifier) {
        if (snapshot.isDemo) DemoNotice()

        // 1. Daily summary from the rule-based coach.
        val summary = viewModel.coach.dailySummary(snapshot.progress, snapshot.prefs.style, focusMinutes)
        CoachCard(summary, snapshot.labels, onAction)
        if (breakAcknowledged) {
            Text(
                stringResource(R.string.coach_break_ack),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // 2. Check-in: "Scrollst du gerade bewusst?"
        SectionCard(stringResource(R.string.coach_check_in_title)) {
            Text(stringResource(R.string.coach_body_check_in), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.answerCheckIn(CheckInAnswer.MINDFUL) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.check_in_mindful))
                }
                OutlinedButton(onClick = { viewModel.answerCheckIn(CheckInAnswer.AUTOPILOT) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.check_in_autopilot))
                }
            }
            state.checkInReply?.let { reply ->
                HorizontalDivider()
                MessageBody(reply, snapshot.labels, onAction = {
                    viewModel.clearCheckInReply()
                    onAction(it)
                })
            }
        }

        // 3. Optional reflection note.
        if (reflecting || state.reflectionSaved) {
            ReflectionCard(
                saved = state.reflectionSaved,
                onSave = { viewModel.saveReflection(it) },
                onClose = {
                    reflecting = false
                    viewModel.clearReflectionSaved()
                },
            )
        }
        if (reflections.isNotEmpty()) {
            SectionCard(stringResource(R.string.coach_notes_title)) {
                reflections.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        // 4. Reminders delivered today.
        SectionCard(stringResource(R.string.coach_inbox_title)) {
            if (!state.notificationsAllowed) {
                InfoRow(Icons.Filled.Notifications, stringResource(R.string.coach_notifications_off))
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
            }
            if (inbox.isEmpty()) {
                Text(stringResource(R.string.coach_inbox_empty), style = MaterialTheme.typography.bodyMedium)
            }
            inbox.forEach { item ->
                val time = item.sentAt.atZone(ZoneId.systemDefault()).toLocalTime().hhmm()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val channel = if (item.asNotification) {
                        stringResource(R.string.coach_inbox_as_notification)
                    } else {
                        stringResource(R.string.coach_inbox_in_app)
                    }
                    Text("$time · $channel", style = MaterialTheme.typography.labelMedium)
                    MessageBody(item.message, snapshot.labels, onAction)
                }
                HorizontalDivider()
            }
        }

        // 5. Manual rule check (useful in a demo: the background check only runs every ~15 min).
        SectionCard(stringResource(R.string.coach_check_now_title)) {
            InfoRow(Icons.Filled.Info, stringResource(R.string.coach_check_now_body))
            OutlinedButton(onClick = viewModel::runCoachCheckNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_check_rules_now))
            }
            state.manualCheck?.let { result ->
                val text = when (result) {
                    ManualCheckResult.SENT_NOTIFICATION -> stringResource(R.string.check_result_notification)
                    ManualCheckResult.SENT_IN_APP -> stringResource(R.string.check_result_in_app)
                    ManualCheckResult.NOTHING_DUE -> stringResource(R.string.check_result_nothing)
                    ManualCheckResult.FAILED -> stringResource(R.string.check_result_failed)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                LaunchedEffect(result) {
                    kotlinx.coroutines.delay(6_000)
                    viewModel.clearManualCheck()
                }
            }
            snapshot.prefs.reminders.checkInTime?.let {
                Text(stringResource(R.string.coach_daily_check_in_at, it.hhmm()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CoachCard(message: CoachMessage, labels: Map<String, String>, onAction: (CoachAction) -> Unit) {
    val text = CoachTexts.render(LocalContext.current.resources, message, labels)
    SectionCard(text.title) {
        MessageBody(message, labels, onAction, showTitle = false)
    }
}

@Composable
private fun MessageBody(
    message: CoachMessage,
    labels: Map<String, String>,
    onAction: (CoachAction) -> Unit,
    showTitle: Boolean = true,
) {
    val text = CoachTexts.render(LocalContext.current.resources, message, labels)
    if (showTitle) Text(text.title, style = MaterialTheme.typography.titleSmall)
    Text(text.body, style = MaterialTheme.typography.bodyLarge)
    if (text.actionLabel != null && message.action != CoachAction.ANSWER_CHECK_IN) {
        // Exactly one optional next step.
        TextButton(onClick = { onAction(message.action) }) { Text(text.actionLabel) }
    }
}

@Composable
private fun ReflectionCard(saved: Boolean, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    SectionCard(stringResource(R.string.reflection_title)) {
        if (saved) {
            Text(stringResource(R.string.reflection_saved), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 280) text = it },
                label = { Text(stringResource(R.string.reflection_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text(stringResource(R.string.reflection_privacy), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_skip)) }
            }
        }
    }
}
