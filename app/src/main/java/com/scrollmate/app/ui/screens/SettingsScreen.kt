package com.scrollmate.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.ui.MainUiState
import com.scrollmate.app.ui.MainViewModel
import com.scrollmate.app.ui.SystemActions
import com.scrollmate.app.ui.components.InfoRow
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.ScreenColumn
import com.scrollmate.app.ui.components.SectionCard
import com.scrollmate.app.ui.components.Stepper
import com.scrollmate.app.ui.components.durationText
import com.scrollmate.app.ui.components.hhmm
import com.scrollmate.core.model.AppLimit
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.DataSourceState
import com.scrollmate.core.model.RuleId
import com.scrollmate.core.model.TimeWindow
import com.scrollmate.core.model.UserPreferences
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

@Composable
fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    actions: SystemActions,
    modifier: Modifier = Modifier,
) {
    val prefs = state.prefs
    if (prefs == null) {
        LoadingState(modifier)
        return
    }
    val update = viewModel::updatePrefs

    ScreenColumn(modifier) {
        DataSection(state, prefs, viewModel, actions)
        GoalSection(prefs, update)
        SectionCard(stringResource(R.string.settings_style_title)) {
            CoachingStyleOptions(selected = prefs.style, onSelect = { s -> update { it.copy(style = s) } })
        }
        RulesSection(prefs, update)
        TimeWindowsSection(prefs, update)
        AppSelectionSection(state, prefs, viewModel, update)
        DataManagementSection(viewModel)
        SectionCard(stringResource(R.string.settings_about_title)) {
            InfoRow(Icons.Filled.Info, stringResource(R.string.settings_about_body))
        }
    }
}

@Composable
private fun DataSection(state: MainUiState, prefs: UserPreferences, viewModel: MainViewModel, actions: SystemActions) {
    SectionCard(stringResource(R.string.settings_data_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_demo_mode),
            description = stringResource(R.string.settings_demo_mode_desc),
            checked = prefs.demoMode,
            onChange = viewModel::setDemoMode,
        )
        HorizontalDivider()
        // Re-evaluated on every recomposition; the root refreshes on each resume.
        val accessGranted = state.snapshot?.state == DataSourceState.LIVE || viewModel.isUsageAccessGranted()
        Text(
            if (accessGranted) stringResource(R.string.settings_access_granted) else stringResource(R.string.settings_access_missing),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = actions.openUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (accessGranted) stringResource(R.string.action_manage_access) else stringResource(R.string.action_grant_access),
            )
        }
        Text(stringResource(R.string.settings_access_revoke_hint), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(
            if (state.notificationsAllowed) stringResource(R.string.settings_notifications_on)
            else stringResource(R.string.settings_notifications_off),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!state.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_allow_notifications))
            }
        }
        TextButton(onClick = actions.openNotificationSettings) { Text(stringResource(R.string.action_notification_settings)) }
    }
}

@Composable
private fun GoalSection(prefs: UserPreferences, update: ((UserPreferences) -> UserPreferences) -> Unit) {
    val limitMinutes = prefs.goal.dailyLimit.toMinutes()
    var goalText by rememberSaveable(prefs.goal.personalGoal) { mutableStateOf(prefs.goal.personalGoal) }
    SectionCard(stringResource(R.string.settings_goal_title)) {
        Stepper(
            label = stringResource(R.string.settings_daily_limit),
            value = durationText(prefs.goal.dailyLimit),
            onMinus = { update { it.copy(goal = it.goal.copy(dailyLimit = Duration.ofMinutes((limitMinutes - 15).coerceAtLeast(15)))) } },
            onPlus = { update { it.copy(goal = it.goal.copy(dailyLimit = Duration.ofMinutes((limitMinutes + 15).coerceAtMost(960)))) } },
            minusEnabled = limitMinutes > 15,
            plusEnabled = limitMinutes < 960,
        )
        OutlinedTextField(
            value = goalText,
            onValueChange = { if (it.length <= UserPreferences.MAX_GOAL_TEXT) goalText = it },
            label = { Text(stringResource(R.string.onboarding_goal_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (goalText != prefs.goal.personalGoal) {
            Button(onClick = { update { it.copy(goal = it.goal.copy(personalGoal = goalText.trim())) } }) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

/** Style picker used in onboarding and settings. */
@Composable
fun CoachingStyleOptions(selected: CoachingStyle, onSelect: (CoachingStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CoachingStyle.entries.forEach { style ->
            val (title, desc) = when (style) {
                CoachingStyle.GENTLE -> stringResource(R.string.style_gentle) to stringResource(R.string.style_gentle_desc)
                CoachingStyle.ACTIVE -> stringResource(R.string.style_active) to stringResource(R.string.style_active_desc)
                CoachingStyle.STRICT -> stringResource(R.string.style_strict) to stringResource(R.string.style_strict_desc)
                CoachingStyle.FOCUS_WINDOWS -> stringResource(R.string.style_windows) to stringResource(R.string.style_windows_desc)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = style == selected, onClick = { onSelect(style) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = style == selected, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesSection(prefs: UserPreferences, update: ((UserPreferences) -> UserPreferences) -> Unit) {
    val r = prefs.reminders
    var pickCheckIn by remember { mutableStateOf(false) }

    fun toggleRule(id: RuleId, enabled: Boolean) = update {
        val set = if (enabled) it.reminders.disabledRules - id else it.reminders.disabledRules + id
        it.copy(reminders = it.reminders.copy(disabledRules = set))
    }

    SectionCard(stringResource(R.string.settings_rules_title)) {
        InfoRow(Icons.Filled.Info, stringResource(R.string.settings_delay_notice))

        Text(stringResource(R.string.settings_thresholds), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 70, 80, 90, 100).forEach { t ->
                val on = t in r.thresholdsPercent
                FilterChip(
                    selected = on,
                    onClick = {
                        update {
                            val list = if (on) it.reminders.thresholdsPercent - t else it.reminders.thresholdsPercent + t
                            it.copy(reminders = it.reminders.copy(thresholdsPercent = list))
                        }
                    },
                    label = { Text("$t %") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        SwitchRow(stringResource(R.string.rule_threshold_total), null, RuleId.THRESHOLD_TOTAL !in r.disabledRules) {
            toggleRule(RuleId.THRESHOLD_TOTAL, it)
        }
        SwitchRow(stringResource(R.string.rule_threshold_app), null, RuleId.THRESHOLD_APP !in r.disabledRules) {
            toggleRule(RuleId.THRESHOLD_APP, it)
        }
        SwitchRow(stringResource(R.string.rule_strict_repeat), stringResource(R.string.rule_strict_repeat_desc), RuleId.STRICT_REPEAT !in r.disabledRules) {
            toggleRule(RuleId.STRICT_REPEAT, it)
        }
        if (prefs.style == CoachingStyle.STRICT) {
            val m = r.strictRepeatInterval.toMinutes()
            Stepper(
                label = stringResource(R.string.settings_repeat_interval),
                value = stringResource(R.string.duration_min, m.toInt()),
                onMinus = { update { it.copy(reminders = it.reminders.copy(strictRepeatInterval = Duration.ofMinutes((m - 10).coerceAtLeast(10)))) } },
                onPlus = { update { it.copy(reminders = it.reminders.copy(strictRepeatInterval = Duration.ofMinutes((m + 10).coerceAtMost(120)))) } },
                minusEnabled = m > 10,
                plusEnabled = m < 120,
            )
        }
        Stepper(
            label = stringResource(R.string.settings_max_reminders),
            value = if (r.maxRemindersPerDay == 0) stringResource(R.string.settings_none) else r.maxRemindersPerDay.toString(),
            onMinus = { update { it.copy(reminders = it.reminders.copy(maxRemindersPerDay = it.reminders.maxRemindersPerDay - 1)) } },
            onPlus = { update { it.copy(reminders = it.reminders.copy(maxRemindersPerDay = it.reminders.maxRemindersPerDay + 1)) } },
            minusEnabled = r.maxRemindersPerDay > 0,
            plusEnabled = r.maxRemindersPerDay < 20,
        )
        HorizontalDivider()

        val checkInOn = r.checkInTime != null && RuleId.CHECK_IN !in r.disabledRules
        SwitchRow(stringResource(R.string.rule_check_in), null, checkInOn) { on ->
            update {
                val time = if (on) it.reminders.checkInTime ?: LocalTime.of(20, 0) else it.reminders.checkInTime
                it.copy(
                    reminders = it.reminders.copy(
                        checkInTime = time,
                        disabledRules = if (on) it.reminders.disabledRules - RuleId.CHECK_IN else it.reminders.disabledRules + RuleId.CHECK_IN,
                    ),
                )
            }
        }
        if (checkInOn) {
            OutlinedButton(onClick = { pickCheckIn = true }) {
                Text(stringResource(R.string.settings_check_in_time, r.checkInTime!!.hhmm()))
            }
        }
        HorizontalDivider()
        val f = prefs.focusDuration.toMinutes()
        Stepper(
            label = stringResource(R.string.settings_focus_duration),
            value = stringResource(R.string.duration_min, f.toInt()),
            onMinus = { update { it.copy(focusDuration = Duration.ofMinutes((f - 5).coerceAtLeast(5))) } },
            onPlus = { update { it.copy(focusDuration = Duration.ofMinutes((f + 5).coerceAtMost(120))) } },
            minusEnabled = f > 5,
            plusEnabled = f < 120,
        )
    }

    if (pickCheckIn) {
        TimeDialog(
            title = stringResource(R.string.rule_check_in),
            initial = r.checkInTime ?: LocalTime.of(20, 0),
            onDismiss = { pickCheckIn = false },
            onConfirm = { t ->
                pickCheckIn = false
                update { it.copy(reminders = it.reminders.copy(checkInTime = t)) }
            },
        )
    }
}

@Composable
private fun TimeWindowsSection(prefs: UserPreferences, update: ((UserPreferences) -> UserPreferences) -> Unit) {
    // (window id, true = editing start, false = editing end)
    var editing by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val studyLabel = stringResource(R.string.window_study)
    val sleepLabel = stringResource(R.string.window_sleep)

    fun replace(id: String, transform: (TimeWindow) -> TimeWindow) = update { p ->
        p.copy(timeWindows = p.timeWindows.map { if (it.id == id) transform(it) else it })
    }

    SectionCard(stringResource(R.string.settings_windows_title)) {
        Text(stringResource(R.string.settings_windows_desc), style = MaterialTheme.typography.bodySmall)
        if (prefs.timeWindows.isEmpty()) {
            Text(stringResource(R.string.settings_windows_empty), style = MaterialTheme.typography.bodyMedium)
        }
        prefs.timeWindows.forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = w.enabled, onCheckedChange = { on -> replace(w.id) { it.copy(enabled = on) } })
                    IconButton(onClick = { update { p -> p.copy(timeWindows = p.timeWindows.filterNot { it.id == w.id }) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_window, w.label))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = w.id to true }) {
                        Text(stringResource(R.string.window_from, w.start.hhmm()))
                    }
                    OutlinedButton(onClick = { editing = w.id to false }) {
                        Text(stringResource(R.string.window_to, w.end.hhmm()))
                    }
                }
            }
            HorizontalDivider()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                update { it.copy(timeWindows = it.timeWindows + TimeWindow(UUID.randomUUID().toString(), studyLabel, LocalTime.of(9, 0), LocalTime.of(12, 0))) }
            }) { Text(stringResource(R.string.action_add_window, studyLabel)) }
            OutlinedButton(onClick = {
                update { it.copy(timeWindows = it.timeWindows + TimeWindow(UUID.randomUUID().toString(), sleepLabel, LocalTime.of(22, 0), LocalTime.of(7, 0))) }
            }) { Text(stringResource(R.string.action_add_window, sleepLabel)) }
        }
    }

    editing?.let { (id, isStart) ->
        val window = prefs.timeWindows.firstOrNull { it.id == id }
        if (window == null) {
            editing = null
        } else {
            TimeDialog(
                title = window.label,
                initial = if (isStart) window.start else window.end,
                onDismiss = { editing = null },
                onConfirm = { t ->
                    editing = null
                    replace(id) { if (isStart) it.copy(start = t) else it.copy(end = t) }
                },
            )
        }
    }
}

@Composable
private fun AppSelectionSection(
    state: MainUiState,
    prefs: UserPreferences,
    viewModel: MainViewModel,
    update: ((UserPreferences) -> UserPreferences) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.settings_apps_title)) {
        Text(
            if (prefs.selectedApps.isEmpty()) stringResource(R.string.settings_apps_all)
            else stringResource(R.string.settings_apps_count, prefs.selectedApps.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (prefs.demoMode) Text(stringResource(R.string.settings_apps_demo_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            expanded = !expanded
            if (expanded) viewModel.loadInstalledApps()
        }) {
            Text(if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_choose_apps))
        }
        if (expanded) {
            val apps = state.installedApps
            if (apps == null) {
                Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyMedium)
            } else if (apps.isEmpty()) {
                Text(stringResource(R.string.settings_apps_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                apps.forEach { app ->
                    val selected = app.packageName in prefs.selectedApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    update {
                                        it.copy(selectedApps = if (on) it.selectedApps + app.packageName else it.selectedApps - app.packageName)
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (selected) {
                        AppLimitStepper(app.label, prefs.goal.appLimits.firstOrNull { it.packageName == app.packageName }) { minutes ->
                            update { p ->
                                val others = p.goal.appLimits.filterNot { it.packageName == app.packageName }
                                val limits = if (minutes > 0) others + AppLimit(app.packageName, Duration.ofMinutes(minutes)) else others
                                p.copy(goal = p.goal.copy(appLimits = limits))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLimitStepper(label: String, current: AppLimit?, onChange: (Long) -> Unit) {
    val m = current?.limit?.toMinutes() ?: 0L
    Stepper(
        label = stringResource(R.string.settings_app_limit, label),
        value = if (m == 0L) stringResource(R.string.settings_app_limit_off) else stringResource(R.string.duration_min, m.toInt()),
        onMinus = { onChange((m - 15).coerceAtLeast(0)) },
        onPlus = { onChange((m + 15).coerceAtMost(240)) },
        minusEnabled = m > 0,
        plusEnabled = m < 240,
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun DataManagementSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Boolean?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                exportResult = try {
                    val csv = viewModel.exportCsv()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    SectionCard(stringResource(R.string.settings_manage_title)) {
        Text(stringResource(R.string.settings_manage_desc), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { exportLauncher.launch("scrollmate-export.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_export))
        }
        exportResult?.let {
            Text(
                if (it) stringResource(R.string.export_done) else stringResource(R.string.export_failed),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_delete_all))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAllData()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
