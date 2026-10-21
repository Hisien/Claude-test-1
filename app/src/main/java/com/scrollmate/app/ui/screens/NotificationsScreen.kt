package com.scrollmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.ui.CoachTexts
import com.scrollmate.app.ui.HistoryItem
import com.scrollmate.app.ui.components.EmptyState
import com.scrollmate.app.ui.components.InfoRow
import com.scrollmate.app.ui.components.hhmm
import com.scrollmate.core.model.CoachVoice
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Bell history: every reminder of the last 30 days with time, grouped by day, newest first. */
@Composable
fun NotificationsScreen(
    items: List<HistoryItem>,
    currentVoice: CoachVoice,
    bedtime: LocalTime,
    notificationsAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val grouped = items.groupBy { it.sentAt.atZone(zone).toLocalDate() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.notif_history_desc), style = MaterialTheme.typography.bodySmall)
        }
        if (!notificationsAllowed) {
            item { InfoRow(Icons.Filled.Notifications, stringResource(R.string.coach_notifications_off)) }
        }
        if (items.isEmpty()) {
            item { EmptyState(stringResource(R.string.notif_history_empty)) }
        }
        grouped.forEach { (date, dayItems) ->
            item(key = "header-$date") {
                val label = when (date) {
                    today -> stringResource(R.string.notif_today)
                    today.minusDays(1) -> stringResource(R.string.notif_yesterday)
                    else -> {
                        val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                        "$day, ${date.dayOfMonth}.${date.monthValue}."
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                )
            }
            items(dayItems, key = { it.key }) { item ->
                HistoryRow(item, currentVoice, bedtime)
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, currentVoice: CoachVoice, bedtime: LocalTime) {
    val res = LocalContext.current.resources
    val labels = if (item.message.packageName != null && item.appLabel != null) {
        mapOf(item.message.packageName!! to item.appLabel)
    } else {
        emptyMap()
    }
    // Shown in the voice it was sent in; older entries without a stored voice use the current one.
    val text = CoachTexts.render(res, item.message, labels, item.voice ?: currentVoice, bedtime)
    val time = item.sentAt.atZone(ZoneId.systemDefault()).toLocalTime().hhmm()
    val channel = if (item.asNotification) {
        stringResource(R.string.coach_inbox_as_notification)
    } else {
        stringResource(R.string.coach_inbox_in_app)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    " · $channel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (item.demo) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(R.string.status_demo),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(text.title, style = MaterialTheme.typography.titleSmall)
            Text(text.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
