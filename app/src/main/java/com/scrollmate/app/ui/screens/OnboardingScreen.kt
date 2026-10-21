package com.scrollmate.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.ui.MainUiState
import com.scrollmate.app.ui.MainViewModel
import com.scrollmate.app.ui.SystemActions
import com.scrollmate.app.ui.components.GlassBackground
import com.scrollmate.app.ui.components.InfoRow
import com.scrollmate.app.ui.components.Stepper
import com.scrollmate.app.ui.components.durationText
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.DataSourceState
import com.scrollmate.core.model.UserPreferences
import java.time.Duration

private const val PAGES = 5

/** Max. 5 screens, skippable at any time. No permission is assumed silently. */
@Composable
fun OnboardingScreen(state: MainUiState, viewModel: MainViewModel, actions: SystemActions) {
    val defaults = UserPreferences()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableLongStateOf(defaults.goal.dailyLimit.toMinutes()) }
    var style by rememberSaveable { mutableStateOf(defaults.style) }
    val accessGranted = state.snapshot?.state == DataSourceState.LIVE

    fun finish(demo: Boolean) = viewModel.completeOnboarding(goal.trim(), limit, style, demo)

    GlassBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_step, page + 1, PAGES),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (page < PAGES - 1) {
                    TextButton(onClick = { finish(demo = false) }) { Text(stringResource(R.string.action_skip)) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> {
                        Title(stringResource(R.string.onboarding_welcome_title))
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
                        InfoRow(Icons.Filled.Favorite, stringResource(R.string.onboarding_welcome_1))
                        InfoRow(Icons.Filled.Person, stringResource(R.string.onboarding_welcome_2))
                        InfoRow(Icons.Filled.Info, stringResource(R.string.onboarding_welcome_3))
                    }
                    1 -> {
                        Title(stringResource(R.string.onboarding_goal_title))
                        OutlinedTextField(
                            value = goal,
                            onValueChange = { if (it.length <= UserPreferences.MAX_GOAL_TEXT) goal = it },
                            label = { Text(stringResource(R.string.onboarding_goal_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Stepper(
                            label = stringResource(R.string.settings_daily_limit),
                            value = durationText(Duration.ofMinutes(limit)),
                            onMinus = { limit = (limit - 15).coerceAtLeast(15) },
                            onPlus = { limit = (limit + 15).coerceAtMost(960) },
                            minusEnabled = limit > 15,
                            plusEnabled = limit < 960,
                        )
                        Text(stringResource(R.string.onboarding_goal_note), style = MaterialTheme.typography.bodySmall)
                    }
                    2 -> {
                        Title(stringResource(R.string.onboarding_style_title))
                        CoachingStyleOptions(selected = style, onSelect = { style = it })
                        if (style == CoachingStyle.FOCUS_WINDOWS) {
                            Text(stringResource(R.string.onboarding_windows_note), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    3 -> {
                        Title(stringResource(R.string.onboarding_data_title))
                        InfoRow(Icons.Filled.Star, stringResource(R.string.onboarding_data_1))
                        InfoRow(Icons.Filled.CheckCircle, stringResource(R.string.onboarding_data_2))
                        InfoRow(Icons.Filled.Info, stringResource(R.string.onboarding_data_3))
                        InfoRow(Icons.Filled.Info, stringResource(R.string.focus_no_blocking))
                    }
                    else -> {
                        Title(stringResource(R.string.onboarding_permission_title))
                        Text(stringResource(R.string.onboarding_permission_body), style = MaterialTheme.typography.bodyLarge)
                        if (accessGranted) {
                            InfoRow(Icons.Filled.CheckCircle, stringResource(R.string.settings_access_granted))
                        } else {
                            Button(onClick = actions.openUsageAccess, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_grant_access))
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (state.notificationsAllowed) {
                                InfoRow(Icons.Filled.Notifications, stringResource(R.string.settings_notifications_on))
                            } else {
                                OutlinedButton(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.action_allow_notifications))
                                }
                                Text(stringResource(R.string.onboarding_notifications_note), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.padding(4.dp))
                        Button(onClick = { finish(demo = false) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (accessGranted) stringResource(R.string.action_start)
                                else stringResource(R.string.action_continue_without_data),
                            )
                        }
                        OutlinedButton(onClick = { finish(demo = true) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_continue_demo))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) { Text(stringResource(R.string.action_back)) }
                } else {
                    Spacer(Modifier)
                }
                if (page < PAGES - 1) {
                    Button(onClick = { page++ }) { Text(stringResource(R.string.action_next)) }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}
