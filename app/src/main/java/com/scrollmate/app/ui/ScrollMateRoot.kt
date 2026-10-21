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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.scrollmate.app.ui.components.AppIcons
import com.scrollmate.app.ui.components.GlassBackground
import com.scrollmate.app.ui.components.glass
import com.scrollmate.app.ui.theme.LocalGlass
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

enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY(R.string.tab_today, AppIcons.Today, AppIcons.TodayFilled),
    COACH(R.string.tab_coach, AppIcons.Coach, AppIcons.CoachFilled),
    FOCUS(R.string.tab_focus, AppIcons.Focus, AppIcons.FocusFilled),
    HISTORY(R.string.tab_history, AppIcons.History, AppIcons.HistoryFilled),
    SETTINGS(R.string.tab_settings, AppIcons.Settings, AppIcons.SettingsFilled),
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
        prefs == null -> GlassBackground { LoadingState() }
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

    val title = if (showNotifications) stringResource(R.string.notif_history_title) else stringResource(tab.label)
    GlassBackground {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            GlassHeader(
                title = title,
                avatar = state.prefs?.voice?.avatar.orEmpty(),
                status = state.snapshot?.state,
                unread = unread,
                showBack = showNotifications,
                onBack = { closeNotifications() },
                onBell = {
                    showNotifications = true
                    mainViewModel.markNotificationsSeen()
                },
            )
        },
        bottomBar = {
            GlassNavBar(
                selected = if (showNotifications) null else tab,
                onSelect = { t ->
                    if (showNotifications) closeNotifications()
                    tab = t
                },
            )
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
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


@Composable
private fun GlassHeader(
    title: String,
    avatar: String,
    status: com.scrollmate.core.model.DataSourceState?,
    unread: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    onBell: () -> Unit,
) {
    val g = LocalGlass.current
    val date = remember {
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM", java.util.Locale.GERMAN))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).glass(MaterialTheme.shapes.small)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        } else {
            Box(Modifier.size(48.dp).glass(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Text(avatar, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(Modifier.weight(1f)) {
            if (!showBack) {
                Text(date, style = MaterialTheme.typography.labelMedium, color = g.textMuted, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
        if (!showBack) {
            status?.let { StatusChip(it) }
            val bellDescription = if (unread > 0) stringResource(R.string.cd_bell_unread, unread) else stringResource(R.string.cd_bell)
            IconButton(onClick = onBell, modifier = Modifier.size(48.dp).glass(MaterialTheme.shapes.small)) {
                BadgedBox(badge = {
                    if (unread > 0) Badge(containerColor = Color(0xFFF43F5E), contentColor = Color.White) {
                        Text(if (unread > 99) "99+" else unread.toString())
                    }
                }) {
                    Icon(AppIcons.Bell, contentDescription = bellDescription, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Floating glass pill with the five tabs; the active tab glows mint. */
@Composable
private fun GlassNavBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(26.dp))
            .background(g.nav)
            .border(1.dp, g.edge, RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val label = stringResource(t.label)
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .selectable(selected = active, onClick = { onSelect(t) }, role = Role.Tab)
                    .sizeIn(minWidth = 56.dp, minHeight = 52.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (active) t.selectedIcon else t.icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else g.textMuted,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else g.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
