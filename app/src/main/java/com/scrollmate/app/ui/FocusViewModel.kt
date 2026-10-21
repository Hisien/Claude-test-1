package com.scrollmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scrollmate.app.AppContainer
import com.scrollmate.app.data.toEntity
import com.scrollmate.app.data.toModel
import com.scrollmate.app.work.WorkScheduler
import com.scrollmate.core.coach.CoachMessage
import com.scrollmate.core.focus.FocusSessions
import com.scrollmate.core.model.FocusSession
import com.scrollmate.core.model.FocusStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.UUID

data class FocusUiState(
    val loaded: Boolean = false,
    val running: FocusSession? = null,
    val remaining: Duration = Duration.ZERO,
    /** The session that just ended and waits for the optional reflection. */
    val finished: FocusSession? = null,
    val finishedMessage: CoachMessage? = null,
    val reflectionSaved: Boolean = false,
)

/** Focus sessions inside ScrollMate. Other apps are never blocked. */
class FocusViewModel(private val c: AppContainer) : ViewModel() {

    private val dao = c.database.focusSessionDao()
    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    val recent: StateFlow<List<FocusSession>> = dao.recent(5)
        .map { list -> list.map { it.toModel() }.filter { it.status != FocusStatus.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val running = dao.running()?.toModel()
            if (running != null) handle(running) else _state.update { it.copy(loaded = true) }
        }
    }

    fun start(minutes: Int) {
        if (_state.value.running != null) return
        viewModelScope.launch {
            val session = FocusSessions.start(UUID.randomUUID().toString(), Duration.ofMinutes(minutes.toLong()), c.clock.instant())
            dao.upsert(session.toEntity())
            WorkScheduler.scheduleFocusEnd(c.appContext, session.id, session.plannedDuration)
            _state.update { it.copy(finished = null, finishedMessage = null, reflectionSaved = false) }
            handle(session)
        }
    }

    fun endEarly() {
        val running = _state.value.running ?: return
        viewModelScope.launch {
            val ended = FocusSessions.endEarly(running, c.clock.instant())
            dao.upsert(ended.toEntity())
            WorkScheduler.cancelFocusEnd(c.appContext)
            handle(ended)
        }
    }

    fun saveReflection(text: String) {
        val finished = _state.value.finished ?: return
        viewModelScope.launch {
            val updated = FocusSessions.withReflection(finished, text)
            dao.upsert(updated.toEntity())
            _state.update { it.copy(finished = updated, reflectionSaved = true) }
        }
    }

    fun dismissFinished() = _state.update { it.copy(finished = null, finishedMessage = null, reflectionSaved = false) }

    /** Re-evaluate after the app was in the background (the session may have ended meanwhile). */
    fun refresh() {
        _state.value.running?.let { viewModelScope.launch { handle(it) } }
    }

    private suspend fun handle(session: FocusSession) {
        val now = c.clock.instant()
        val current = FocusSessions.refresh(session, now)
        if (current.status == FocusStatus.RUNNING) {
            _state.update { it.copy(loaded = true, running = current, remaining = FocusSessions.remaining(current, now)) }
            startTicker()
        } else {
            // The ticker stops on its own once `running` is null (it may be the caller of this function).
            if (current != session) dao.upsert(current.toEntity())
            if (current.status == FocusStatus.COMPLETED) WorkScheduler.cancelFocusEnd(c.appContext)
            _state.update {
                it.copy(
                    loaded = true,
                    running = null,
                    remaining = Duration.ZERO,
                    finished = current,
                    finishedMessage = c.coach.focusFinished(current, now),
                )
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val running = _state.value.running ?: break
                val now = c.clock.instant()
                if (FocusSessions.refresh(running, now).status != FocusStatus.RUNNING) {
                    handle(running)
                    break
                }
                _state.update {
                    if (it.running?.id == running.id) it.copy(remaining = FocusSessions.remaining(running, now)) else it
                }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FocusViewModel(c) }
        }
    }
}
