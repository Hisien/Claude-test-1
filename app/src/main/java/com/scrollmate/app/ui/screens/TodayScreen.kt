package com.scrollmate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.data.UsageSnapshot
import com.scrollmate.app.ui.FocusUiState
import com.scrollmate.app.ui.MainUiState
import com.scrollmate.app.ui.components.DemoNotice
import com.scrollmate.app.ui.components.EmptyState
import com.scrollmate.app.ui.components.ErrorState
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.PermissionCard
import com.scrollmate.app.ui.components.GlowCard
import com.scrollmate.app.ui.components.ProgressRing
import com.scrollmate.app.ui.theme.ZoneColors
import com.scrollmate.core.progress.LimitZone
import com.scrollmate.app.ui.components.ScreenColumn
import com.scrollmate.app.ui.components.SectionCard
import com.scrollmate.app.ui.components.durationText
import com.scrollmate.app.ui.components.hhmm
import com.scrollmate.core.model.DataSourceState

@Composable
fun TodayScreen(
    state: MainUiState,
    focusState: FocusUiState,
    onGrantAccess: () -> Unit,
    onUseDemo: () -> Unit,
    onRetry: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenCoach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.error -> ScreenColumn(modifier) { ErrorState(onRetry) }
        snapshot == null -> LoadingState(modifier)
        else -> ScreenColumn(modifier) {
            if (state.error) ErrorState(onRetry)
            if (snapshot.isDemo) DemoNotice()
            if (snapshot.state == DataSourceState.PERMISSION_MISSING) {
                PermissionCard(onGrant = onGrantAccess, onUseDemo = onUseDemo)
            } else {
                ProgressSection(snapshot)
                TopAppsSection(snapshot)
            }
            NextUpSection(snapshot, focusState, onOpenFocus, onOpenCoach)
            if (snapshot.prefs.goal.personalGoal.isNotBlank()) {
                SectionCard(stringResource(R.string.today_your_goal)) {
                    Text(snapshot.prefs.goal.personalGoal, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(snapshot: UsageSnapshot) {
    val progress = snapshot.progress ?: return
    val used = durationText(progress.used)
    val limit = durationText(progress.limit)
    val ringDescription = stringResource(R.string.cd_progress_ring, used, limit, progress.percent)
    val glow = ZoneColors.of(progress.zone)
    val zoneLabel = stringResource(
        when (progress.zone) {
            LimitZone.SAFE -> R.string.zone_safe
            LimitZone.WARNING -> R.string.zone_warning
            LimitZone.EXCEEDED -> R.string.zone_exceeded
        },
    )
    GlowCard(glow) {
        // Text label next to the colour, so the status never depends on colour alone.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { },
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(glow))
            Text(
                zoneLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                fraction = progress.ringFraction,
                color = glow,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = ringDescription },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(used, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.today_of_limit, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        val remainingText = if (progress.isOverLimit) {
            stringResource(R.string.today_over_by, durationText(progress.overBy))
        } else {
            stringResource(R.string.today_remaining, durationText(progress.remaining))
        }
        Text(
            remainingText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress.used.isZero) EmptyState(stringResource(R.string.today_empty))
    }
}

@Composable
private fun TopAppsSection(snapshot: UsageSnapshot) {
    val today = snapshot.today ?: return
    val title = if (snapshot.prefs.selectedApps.isEmpty() || snapshot.isDemo) {
        stringResource(R.string.today_top_apps)
    } else {
        stringResource(R.string.today_top_apps_selected)
    }
    SectionCard(title) {
        val top = today.top(5)
        if (top.isEmpty()) {
            Text(stringResource(R.string.today_top_apps_empty), style = MaterialTheme.typography.bodyMedium)
        }
        val max = top.firstOrNull()?.second?.toMillis()?.coerceAtLeast(1) ?: 1
        top.forEach { (pkg, d) ->
            val label = snapshot.labels[pkg] ?: pkg
            val time = durationText(d)
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: $time" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(time, style = MaterialTheme.typography.bodyLarge)
                }
                LinearProgressIndicator(
                    progress = { d.toMillis().toFloat() / max.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NextUpSection(
    snapshot: UsageSnapshot,
    focusState: FocusUiState,
    onOpenFocus: () -> Unit,
    onOpenCoach: () -> Unit,
) {
    SectionCard(stringResource(R.string.today_next)) {
        val running = focusState.running
        if (running != null) {
            Text(
                stringResource(R.string.today_focus_running, durationText(focusState.remaining)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onOpenFocus, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_focus))
            }
        } else {
            val checkIn = snapshot.prefs.reminders.checkInTime
            val text = when {
                checkIn == null -> stringResource(R.string.today_no_check_in)
                snapshot.now.toLocalTime().isBefore(checkIn) -> stringResource(R.string.today_check_in_today, checkIn.hhmm())
                else -> stringResource(R.string.today_check_in_tomorrow, checkIn.hhmm())
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onOpenCoach, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_check_in_now))
            }
        }
    }
}
