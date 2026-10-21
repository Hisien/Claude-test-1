package com.scrollmate.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.scrollmate.core.categories.AppCategory
import com.scrollmate.core.categories.CategoryClassifier
import com.scrollmate.app.ui.theme.ZoneColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.ui.MainUiState
import com.scrollmate.app.ui.components.DemoNotice
import com.scrollmate.app.ui.components.ErrorState
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.PermissionCard
import com.scrollmate.app.ui.components.ScreenColumn
import com.scrollmate.app.ui.components.SectionCard
import com.scrollmate.app.ui.components.durationText
import com.scrollmate.core.history.DayBar
import com.scrollmate.core.history.HistoryCalculator
import com.scrollmate.core.model.DataSourceState
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    state: MainUiState,
    onGrantAccess: () -> Unit,
    onUseDemo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.error -> ScreenColumn(modifier) { ErrorState(onRetry) }
        snapshot == null -> LoadingState(modifier)
        snapshot.state == DataSourceState.PERMISSION_MISSING -> ScreenColumn(modifier) {
            PermissionCard(onGrant = onGrantAccess, onUseDemo = onUseDemo)
        }
        else -> ScreenColumn(modifier) {
            if (snapshot.isDemo) DemoNotice()
            var showCategories by rememberSaveable { mutableStateOf(false) }
            HistoryViewToggle(showCategories) { showCategories = it }
            if (showCategories) {
                CategoriesSection(snapshot)
                return@ScreenColumn
            }
            val history = snapshot.history ?: return@ScreenColumn
            val days = history.days
            val withData = days.count { it.total != null }

            SectionCard(stringResource(R.string.history_chart_title)) {
                if (withData == 0) {
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    WeekChart(days)
                    val met = days.count { it.goalMet == true }
                    Text(stringResource(R.string.history_goal_days, met, withData), style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard(stringResource(R.string.history_average_title)) {
                when {
                    snapshot.isDemo -> Text(stringResource(R.string.history_average_demo), style = MaterialTheme.typography.bodyMedium)
                    !history.comparisonAvailable -> Text(
                        stringResource(
                            R.string.history_average_not_yet,
                            HistoryCalculator.MIN_LIVE_DAYS_FOR_COMPARISON,
                            history.completedLiveDays,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        Text(
                            stringResource(R.string.history_average_value, durationText(history.average!!)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        history.todayVsAverage?.let { diff ->
                            val text = if (diff.isNegative) {
                                stringResource(R.string.history_today_less, durationText(diff.negated()))
                            } else {
                                stringResource(R.string.history_today_more, durationText(diff))
                            }
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.history_days_title)) {
                days.reversed().forEach { DayRow(it) }
            }
        }
    }
}

@Composable
private fun WeekChart(days: List<DayBar>) {
    val primary = ZoneColors.Safe
    val over = ZoneColors.Exceeded
    val limitColor = MaterialTheme.colorScheme.outline
    val maxMinutes = (days.mapNotNull { it.total?.toMinutes() } + days.first().limit.toMinutes())
        .maxOrNull()?.coerceAtLeast(1) ?: 1
    val description = days.joinToString("; ") { day ->
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        "$name ${day.total?.toMinutes() ?: 0} min"
    }
    val chartDescription = stringResource(R.string.cd_week_chart, description)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = chartDescription },
    ) {
        val slot = size.width / days.size
        val barWidth = slot * 0.55f
        days.forEachIndexed { i, day ->
            val minutes = day.total?.toMinutes() ?: 0
            val h = size.height * minutes / maxMinutes.toFloat()
            drawRoundRect(
                color = if (day.goalMet == false) over else primary,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
        val limitY = size.height - size.height * days.first().limit.toMinutes() / maxMinutes.toFloat()
        drawLine(
            color = limitColor,
            start = Offset(0f, limitY),
            end = Offset(size.width, limitY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(stringResource(R.string.history_legend), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DayRow(day: DayBar) {
    val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val date = "$name, ${day.date.dayOfMonth}.${day.date.monthValue}."
    val total = day.total
    val status = when (day.goalMet) {
        true -> stringResource(R.string.history_day_met)
        false -> stringResource(R.string.history_day_over)
        null -> stringResource(R.string.history_day_no_data)
    }
    val value = if (total != null) durationText(total) else "–"
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$date: $value, $status" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (day.goalMet == true) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = if (day.goalMet == false) ZoneColors.Exceeded else ZoneColors.Safe,
        )
        Text(date, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("$value · $status", style = MaterialTheme.typography.bodyMedium)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryViewToggle(showCategories: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showCategories,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.history_view_days)) }
        SegmentedButton(
            selected = showCategories,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.history_view_categories)) }
    }
}

@StringRes
private fun categoryName(c: AppCategory): Int = when (c) {
    AppCategory.SOCIAL -> R.string.cat_social
    AppCategory.ENTERTAINMENT -> R.string.cat_entertainment
    AppCategory.WORK_LEARNING -> R.string.cat_work
    AppCategory.NEWS -> R.string.cat_news
    AppCategory.OTHER -> R.string.cat_other
}

private fun categoryColor(c: AppCategory): Color = when (c) {
    AppCategory.SOCIAL -> Color(0xFF8B5CF6)
    AppCategory.ENTERTAINMENT -> Color(0xFFEC4899)
    AppCategory.WORK_LEARNING -> Color(0xFF3B82F6)
    AppCategory.NEWS -> Color(0xFF14B8A6)
    AppCategory.OTHER -> Color(0xFF94A3B8)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(snapshot: com.scrollmate.app.data.UsageSnapshot) {
    var week by rememberSaveable { mutableStateOf(false) }
    val perApp = if (week) snapshot.weekPerApp else snapshot.today?.perApp?.toMap().orEmpty()
    val shares = CategoryClassifier.breakdown(perApp, snapshot.categoryHints)

    SectionCard(stringResource(R.string.categories_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !week, onClick = { week = false }, label = { Text(stringResource(R.string.categories_today)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = week, onClick = { week = true }, label = { Text(stringResource(R.string.categories_week)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        if (shares.isEmpty()) {
            Text(stringResource(R.string.categories_empty), style = MaterialTheme.typography.bodyMedium)
        }
        shares.forEach { share ->
            val name = stringResource(categoryName(share.category))
            val time = durationText(share.duration)
            val apps = share.packages.take(3).joinToString(", ") { snapshot.labels[it] ?: it }
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "$name: $time, ${share.percent} %"
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.categories_row, time, share.percent), style = MaterialTheme.typography.bodyLarge)
                }
                LinearProgressIndicator(
                    progress = { share.fraction.toFloat() },
                    color = categoryColor(share.category),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Text(stringResource(R.string.categories_apps, apps), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.categories_note), style = MaterialTheme.typography.bodySmall)
    }
}
