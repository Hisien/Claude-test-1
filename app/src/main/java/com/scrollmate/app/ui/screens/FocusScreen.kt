package com.scrollmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmate.app.R
import com.scrollmate.app.ui.CoachTexts
import com.scrollmate.app.ui.FocusViewModel
import com.scrollmate.app.ui.components.InfoRow
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.ScreenColumn
import com.scrollmate.app.ui.components.SectionCard
import com.scrollmate.app.ui.components.Stepper
import com.scrollmate.app.ui.components.hhmm
import com.scrollmate.core.focus.FocusSessions
import com.scrollmate.core.model.FocusSession
import com.scrollmate.core.model.FocusStatus
import com.scrollmate.core.model.RestrictionStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    defaultMinutes: Int,
    restrictionStatus: RestrictionStatus,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    if (!state.loaded) {
        LoadingState(modifier)
        return
    }

    ScreenColumn(modifier) {
        // Always visible, honest statement of what a focus session is.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding16(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(Icons.Filled.Info, stringResource(R.string.focus_no_blocking))
                if (restrictionStatus == RestrictionStatus.UNAVAILABLE) {
                    Text(stringResource(R.string.focus_restriction_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val running = state.running
        val finished = state.finished
        when {
            running != null -> RunningCard(running, state.remaining, onEnd = viewModel::endEarly)
            finished != null -> FinishedCard(
                finished = finished,
                message = state.finishedMessage,
                saved = state.reflectionSaved,
                onSave = viewModel::saveReflection,
                onDone = viewModel::dismissFinished,
            )
            else -> StartCard(defaultMinutes, onStart = viewModel::start)
        }

        SectionCard(stringResource(R.string.focus_recent_title)) {
            if (recent.isEmpty()) Text(stringResource(R.string.focus_recent_empty), style = MaterialTheme.typography.bodyMedium)
            recent.forEach { session ->
                SessionRow(session)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StartCard(defaultMinutes: Int, onStart: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(defaultMinutes.coerceIn(5, 120)) }
    SectionCard(stringResource(R.string.focus_start_title)) {
        Text(stringResource(R.string.focus_start_body), style = MaterialTheme.typography.bodyMedium)
        Stepper(
            label = stringResource(R.string.focus_duration),
            value = stringResource(R.string.duration_min, minutes),
            onMinus = { minutes = (minutes - 5).coerceAtLeast(5) },
            onPlus = { minutes = (minutes + 5).coerceAtMost(120) },
            minusEnabled = minutes > 5,
            plusEnabled = minutes < 120,
        )
        Button(onClick = { onStart(minutes) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_start_focus))
        }
        Text(stringResource(R.string.focus_end_tolerance), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RunningCard(session: FocusSession, remaining: Duration, onEnd: () -> Unit) {
    val total = remaining.seconds.coerceAtLeast(0)
    val clock = "%02d:%02d".format(total / 60, total % 60)
    val description = stringResource(R.string.cd_focus_remaining, (total + 59) / 60)
    SectionCard(stringResource(R.string.focus_running_title)) {
        Text(
            clock,
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = description
                heading()
            },
        )
        val planned = session.plannedDuration.seconds.toFloat().coerceAtLeast(1f)
        LinearProgressIndicator(
            progress = { 1f - total.toFloat() / planned },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.focus_running_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_end_focus))
        }
    }
}

@Composable
private fun FinishedCard(
    finished: FocusSession,
    message: com.scrollmate.core.coach.CoachMessage?,
    saved: Boolean,
    onSave: (String) -> Unit,
    onDone: () -> Unit,
) {
    var text by rememberSaveable(finished.id) { mutableStateOf(finished.reflection.orEmpty()) }
    val rendered = message?.let { CoachTexts.render(LocalContext.current.resources, it) }
    SectionCard(rendered?.title ?: stringResource(R.string.coach_title_focus_done)) {
        rendered?.let { Text(it.body, style = MaterialTheme.typography.bodyLarge) }
        if (saved) {
            Text(stringResource(R.string.reflection_saved), style = MaterialTheme.typography.bodyMedium)
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= FocusSessions.MAX_REFLECTION) text = it },
                label = { Text(stringResource(R.string.focus_reflection_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!saved) {
                Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
            }
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    val start = session.startedAt.atZone(ZoneId.systemDefault())
    val minutes = FocusSessions.elapsed(session, session.endedAt ?: Instant.now()).toMinutes()
    val status = when (session.status) {
        FocusStatus.COMPLETED -> stringResource(R.string.focus_status_completed)
        FocusStatus.ENDED_EARLY -> stringResource(R.string.focus_status_early)
        FocusStatus.RUNNING -> stringResource(R.string.focus_status_running)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.focus_session_row, start.toLocalDate().dayOfMonth, start.toLocalDate().monthValue, start.toLocalTime().hhmm(), minutes.toInt(), status),
            style = MaterialTheme.typography.bodyLarge,
        )
        session.reflection?.let { Text("„$it“", style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun Modifier.padding16(): Modifier = this.then(Modifier.padding(16.dp))
