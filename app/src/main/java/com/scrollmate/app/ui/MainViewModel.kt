package com.scrollmate.app.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scrollmate.app.AppContainer
import com.scrollmate.app.data.LauncherApp
import com.scrollmate.app.data.UsageSnapshot
import com.scrollmate.app.data.db.ReflectionEntity
import com.scrollmate.app.data.message
import com.scrollmate.app.data.voiceOrNull
import com.scrollmate.core.model.CoachVoice
import com.scrollmate.app.domain.CoachEvaluator
import com.scrollmate.app.winddown.WindDownController
import com.scrollmate.app.work.WorkScheduler
import com.scrollmate.core.coach.MoodAfterScroll
import com.scrollmate.core.coach.ScrollIntent
import com.scrollmate.core.winddown.WindDown
import com.scrollmate.core.coach.CheckInAnswer
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.model.CoachingStyle
import com.scrollmate.core.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class ManualCheckResult { SENT_NOTIFICATION, SENT_IN_APP, NOTHING_DUE, FAILED }

data class InboxItem(val key: String, val sentAt: Instant, val message: CoachMessage, val asNotification: Boolean)

/** One entry of the bell history: what was shown, when, in which voice. */
data class HistoryItem(
    val key: String,
    val sentAt: Instant,
    val message: CoachMessage,
    val asNotification: Boolean,
    val voice: CoachVoice?,
    val demo: Boolean,
    val appLabel: String?,
)

data class MainUiState(
    /** Null until DataStore delivered the first value. */
    val prefs: UserPreferences? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val snapshot: UsageSnapshot? = null,
    val notificationsAllowed: Boolean = false,
    val checkInReply: CoachMessage? = null,
    val manualCheck: ManualCheckResult? = null,
    val installedApps: List<LauncherApp>? = null,
    val reflectionSaved: Boolean = false,
    val overlayAllowed: Boolean = false,
    val followUpReply: CoachMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val today = MutableStateFlow(LocalDate.now(c.clock.withZone(c.zone())))

    /** Reminders delivered today (as notification or in-app only). */
    val inbox: StateFlow<List<InboxItem>> = today
        .flatMapLatest { c.database.notificationRecordDao().shownOn(it.toString()) }
        .map { rows ->
            rows.mapNotNull { r ->
                r.message()?.let { InboxItem(r.key, Instant.ofEpochMilli(r.sentAtMs), it, r.deliveredAsNotification) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All reminders of the last 30 days (notification or in-app), newest first. */
    val notificationHistory: StateFlow<List<HistoryItem>> = c.database.notificationRecordDao().recentShown(300)
        .map { rows ->
            withContext(Dispatchers.IO) {
                rows.mapNotNull { r ->
                    r.message()?.let { m ->
                        HistoryItem(
                            key = r.key,
                            sentAt = Instant.ofEpochMilli(r.sentAtMs),
                            message = m,
                            asNotification = r.deliveredAsNotification,
                            voice = r.voiceOrNull(),
                            demo = r.demo,
                            appLabel = m.packageName?.let { c.installedApps.label(it) },
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entries newer than the last time the history was opened. */
    val unreadNotifications: StateFlow<Int> = combine(notificationHistory, c.settings.notificationsSeenAt) { items, seen ->
        items.count { it.sentAt.toEpochMilli() > seen }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markNotificationsSeen() {
        viewModelScope.launch { c.settings.markNotificationsSeen(c.clock.millis()) }
    }

    val reflections: StateFlow<List<ReflectionEntity>> = c.database.reflectionDao().recent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val coach get() = c.coach
    val restrictionStatus get() = c.restrictionService.status()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            c.settings.preferences.collect { p ->
                _state.update { it.copy(prefs = p) }
                reload(p)
                WindDownController.sync(c.appContext, p)
            }
        }
    }

    /** Called on every resume (and once a minute while visible): re-checks permissions and data. */
    fun refresh() {
        val prefs = _state.value.prefs ?: return
        reload(prefs)
        // Permissions may have changed in system settings: start or stop the wind-down accordingly.
        WindDownController.sync(c.appContext, prefs)
        _state.update { it.copy(overlayAllowed = WindDownController.canDrawOverlays(c.appContext)) }
    }

    fun overlaySettingsIntent(): Intent = WindDownController.overlaySettingsIntent(c.appContext)

    /** Turns the wind-down on and pre-selects well-known feed apps that are installed, if none are chosen yet. */
    fun enableWindDown(enabled: Boolean) {
        viewModelScope.launch {
            val installed = if (enabled) withContext(Dispatchers.IO) { c.installedApps.launcherApps().map { it.packageName }.toSet() } else emptySet()
            c.settings.update { p ->
                val blocked = if (enabled && p.windDown.blockedApps.isEmpty()) {
                    WindDown.SUGGESTED_PACKAGES.filter { it in installed }.toSet()
                } else {
                    p.windDown.blockedApps
                }
                p.copy(windDown = p.windDown.copy(enabled = enabled, blockedApps = blocked))
            }
        }
    }

    fun answerMood(mood: MoodAfterScroll) = _state.update { it.copy(followUpReply = c.coach.moodReply(mood)) }

    fun answerIntent(intent: ScrollIntent) = _state.update { it.copy(followUpReply = c.coach.intentReply(intent)) }

    fun clearFollowUp() = _state.update { it.copy(followUpReply = null) }

    private fun reload(prefs: UserPreferences) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.snapshot == null, notificationsAllowed = c.usageAccess.notificationsAllowed()) }
            try {
                val snapshot = c.snapshotLoader.load(prefs)
                today.value = snapshot.date
                _state.update { it.copy(snapshot = snapshot, loading = false, error = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun updatePrefs(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    fun completeOnboarding(goalText: String, limitMinutes: Long, style: CoachingStyle, demo: Boolean) {
        updatePrefs {
            it.copy(
                onboardingCompleted = true,
                goal = it.goal.copy(personalGoal = goalText, dailyLimit = Duration.ofMinutes(limitMinutes)),
                style = style,
                demoMode = demo,
            )
        }
    }

    fun setDemoMode(enabled: Boolean) = updatePrefs { it.copy(demoMode = enabled) }

    fun answerCheckIn(answer: CheckInAnswer) {
        val minutes = (_state.value.prefs ?: UserPreferences()).focusDuration.toMinutes().toInt()
        _state.update { it.copy(checkInReply = c.coach.checkInReply(answer, minutes)) }
    }

    fun clearCheckInReply() = _state.update { it.copy(checkInReply = null) }

    fun runCoachCheckNow() {
        viewModelScope.launch {
            val result = try {
                when (val r = c.coachEvaluator.run()) {
                    is CoachEvaluator.Result.Sent ->
                        if (r.asNotification) ManualCheckResult.SENT_NOTIFICATION else ManualCheckResult.SENT_IN_APP
                    CoachEvaluator.Result.NothingDue -> ManualCheckResult.NOTHING_DUE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ManualCheckResult.FAILED
            }
            _state.update { it.copy(manualCheck = result) }
        }
    }

    fun clearManualCheck() = _state.update { it.copy(manualCheck = null) }

    fun saveReflection(text: String) {
        val clean = text.trim().take(280)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val now = c.clock.instant()
            c.database.reflectionDao().insert(
                ReflectionEntity(date = LocalDate.now(c.clock.withZone(c.zone())).toString(), createdAtMs = now.toEpochMilli(), text = clean),
            )
            _state.update { it.copy(reflectionSaved = true) }
        }
    }

    fun clearReflectionSaved() = _state.update { it.copy(reflectionSaved = false) }

    fun loadInstalledApps() {
        if (_state.value.installedApps != null) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { c.installedApps.launcherApps() }
            _state.update { it.copy(installedApps = apps) }
        }
    }

    fun usageAccessIntent(): Intent = c.usageAccess.settingsIntent()

    fun isUsageAccessGranted(): Boolean = c.usageAccess.isGranted()

    /** P2: CSV export of all locally stored data. */
    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val db = c.database
        buildString {
            appendLine("type,date,package_or_status,minutes,source_or_text")
            db.usageSummaryDao().all().forEach {
                appendLine("usage,${it.date},${csv(it.packageName)},${it.durationMs / 60_000},${it.source}")
            }
            db.focusSessionDao().all().forEach {
                val minutes = ((it.endedAtMs ?: it.startedAtMs) - it.startedAtMs) / 60_000
                val date = Instant.ofEpochMilli(it.startedAtMs).atZone(c.zone()).toLocalDate()
                appendLine("focus,$date,${it.status},$minutes,${csv(it.reflection.orEmpty())}")
            }
            db.reflectionDao().all().forEach {
                appendLine("reflection,${it.date},,,${csv(it.text)}")
            }
        }
    }

    /** P2: deletes every local record and all settings; the app starts with onboarding again. */
    fun deleteAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = c.database
                db.usageSummaryDao().clear()
                db.notificationRecordDao().clear()
                db.focusSessionDao().clear()
                db.reflectionDao().clear()
            }
            WorkScheduler.cancelFocusEnd(c.appContext)
            c.settings.clear()
        }
    }

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(c) }
        }
    }
}
