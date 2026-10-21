package com.scrollmate.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.scrollmate.app.R
import com.scrollmate.app.ui.components.LoadingState
import com.scrollmate.app.ui.components.StatusChip
import com.scrollmate.app.ui.screens.CoachScreen
import com.scrollmate.app.ui.screens.FocusScreen
import com.scrollmate.app.ui.screens.HistoryScreen
import com.scrollmate.app.ui.screens.NotificationsScreen
import com.scrollmate.app.ui.screens.OnboardingScreen
import com.scrollmate.app.ui.screens.SettingsScreen
import com.scrollmate.app.ui.screens.TodayScreen
import com.scrollmate.core.model.CoachVoice
import kotlinx.coroutines.delay

enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.Home),
    COACH(R.string.tab_coach, Icons.Filled.Face),
    FOCUS(R.string.tab_focus, Icons.Filled.PlayArrow),
    HISTORY(R.string.tab_history, Icons.Filled.DateRange),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/** Callbacks for system screens and permissions, shared by onboarding and settings. */
class SystemActions(
    val openUsageAccess: () -> Unit,
    val requestNotifications: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openOverlaySettings: () -> Unit,
)

@Composable
fun ScrollMateRoot(
    mainViewModel: MainViewModel,
    focusViewModel: FocusViewModel,
    requestedTab: Tab?,
    onRequestedTabConsumed: () -> Unit,
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions and data on every resume, then once a minute while visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                mainViewModel.refresh()
                focusViewModel.refresh()
                delay(60_000)
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        mainViewModel.refresh()
    }
    val actions = SystemActions(
        openUsageAccess = { context.startSafely(mainViewModel.usageAccessIntent()) },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openNotificationSettings = {
            context.startSafely(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        openOverlaySettings = { context.startSafely(mainViewModel.overlaySettingsIntent()) },
    )

    val prefs = state.prefs
    when {
        prefs == null -> LoadingState()
        !prefs.onboardingCompleted -> OnboardingScreen(state = state, viewModel = mainViewModel, actions = actions)
        else -> MainScaffold(mainViewModel, focusViewModel, state, actions, requestedTab, onRequestedTabConsumed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    mainViewModel: MainViewModel,
    focusViewModel: FocusViewModel,
    state: MainUiState,
    actions: SystemActions,
    requestedTab: Tab?,
    onRequestedTabConsumed: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    val unread by mainViewModel.unreadNotifications.collectAsStateWithLifecycle()
    val history by mainViewModel.notificationHistory.collectAsStateWithLifecycle()
    fun closeNotifications() {
        showNotifications = false
        mainViewModel.markNotificationsSeen()
    }
    BackHandler(enabled = showNotifications) { closeNotifications() }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            showNotifications = false
            onRequestedTabConsumed()
        }
    }
    val focusState by focusViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showNotifications) {
                        Text(stringResource(R.string.notif_history_title))
                    } else {
                        val avatar = state.prefs?.voice?.avatar.orEmpty()
                        Text("$avatar ${stringResource(tab.label)}".trim())
                    }
                },
                navigationIcon = {
                    if (showNotifications) {
                        IconButton(onClick = { closeNotifications() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (!showNotifications) {
                        state.snapshot?.let { StatusChip(it.state) }
                        val bellDescription = if (unread > 0) {
                            stringResource(R.string.cd_bell_unread, unread)
                        } else {
                            stringResource(R.string.cd_bell)
                        }
                        IconButton(onClick = {
                            showNotifications = true
                            mainViewModel.markNotificationsSeen()
                        }) {
                            BadgedBox(badge = {
                                if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = bellDescription)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !showNotifications,
                        onClick = {
                            if (showNotifications) closeNotifications()
                            tab = t
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (showNotifications) {
            NotificationsScreen(
                items = history,
                currentVoice = state.prefs?.voice ?: CoachVoice.MOUSE,
                bedtime = state.prefs?.windDown?.bedtime ?: java.time.LocalTime.of(22, 30),
                notificationsAllowed = state.notificationsAllowed,
                modifier = modifier,
            )
            return@Scaffold
        }
        when (tab) {
            Tab.TODAY -> TodayScreen(
                state = state,
                focusState = focusState,
                onGrantAccess = actions.openUsageAccess,
                onUseDemo = { mainViewModel.setDemoMode(true) },
                onRetry = mainViewModel::refresh,
                onOpenFocus = { tab = Tab.FOCUS },
                onOpenCoach = { tab = Tab.COACH },
                modifier = modifier,
            )
            Tab.COACH -> CoachScreen(
                state = state,
                viewModel = mainViewModel,
                onStartFocus = { tab = Tab.FOCUS },
                onOpenSettings = { tab = Tab.SETTINGS },
                modifier = modifier,
            )
            Tab.FOCUS -> FocusScreen(
                viewModel = focusViewModel,
                defaultMinutes = (state.prefs?.focusDuration?.toMinutes() ?: 10L).toInt(),
                restrictionStatus = mainViewModel.restrictionStatus,
                modifier = modifier,
                voice = state.prefs?.voice ?: CoachVoice.MOUSE,
            )
            Tab.HISTORY -> HistoryScreen(
                state = state,
                onGrantAccess = actions.openUsageAccess,
                onUseDemo = { mainViewModel.setDemoMode(true) },
                onRetry = mainViewModel::refresh,
                modifier = modifier,
            )
            Tab.SETTINGS -> SettingsScreen(state = state, viewModel = mainViewModel, actions = actions, modifier = modifier)
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
