package com.fvstexpress.driver.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fvstexpress.driver.data.model.RouteResponse
import com.fvstexpress.driver.data.model.StopDay
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.data.repo.DriverRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TaskView(val api: String, val label: String) { TODAY("today", "Today"), ALL("all", "All"), COMPLETED("completed", "Completed") }

data class TasksState(
    val view: TaskView = TaskView.TODAY,
    val showMap: Boolean = false,
    val days: List<StopDay> = emptyList(),
    val route: RouteResponse? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val unread: Int = 0,
    val online: Boolean = false,
    val message: String? = null,
    /** Stop shown in the map's pin sheet. */
    val pinSheet: com.fvstexpress.driver.data.model.StopDetail? = null,
)

class TasksViewModel(private val repo: DriverRepository, initialOnline: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(TasksState(online = initialOnline))
    val state: StateFlow<TasksState> = _state.asStateFlow()

    init {
        load()
        // No push yet: refresh quietly every minute so dispatch changes show up.
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                load(quiet = true)
            }
        }
    }

    fun load(quiet: Boolean = false, pull: Boolean = false) {
        val s = _state.value
        _state.update { it.copy(loading = !quiet && !pull && it.days.isEmpty(), refreshing = pull, error = null) }
        viewModelScope.launch {
            when (val r = repo.stops(s.view.api)) {
                is ApiResult.Ok -> _state.update { it.copy(days = r.data.days, loading = false, refreshing = false) }
                is ApiResult.Err -> _state.update { it.copy(error = if (quiet && it.days.isNotEmpty()) null else r.message, loading = false, refreshing = false) }
                ApiResult.Queued -> Unit
            }
            if (_state.value.showMap) loadRoute()
            (repo.messages() as? ApiResult.Ok)?.let { m -> _state.update { it.copy(unread = m.data.unread) } }
        }
    }

    private suspend fun loadRoute() {
        (repo.route() as? ApiResult.Ok)?.let { r -> _state.update { it.copy(route = r.data) } }
    }

    fun setView(view: TaskView) {
        if (view == _state.value.view) return
        _state.update { it.copy(view = view, days = emptyList()) }
        load()
    }

    fun toggleMap() {
        _state.update { it.copy(showMap = !it.showMap) }
        if (_state.value.showMap) viewModelScope.launch { loadRoute() }
    }

    /** Returns via [onDone] whether the driver is online now, so the screen can start/stop GPS. */
    fun setOnline(online: Boolean, onDone: (Boolean) -> Unit) {
        _state.update { it.copy(online = online) }
        viewModelScope.launch {
            when (val r = repo.setOnline(online)) {
                is ApiResult.Ok -> { _state.update { it.copy(online = r.data) }; onDone(r.data) }
                is ApiResult.Err -> { _state.update { it.copy(online = !online, message = r.message) }; onDone(!online) }
                ApiResult.Queued -> Unit
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun openPin(key: String) {
        viewModelScope.launch {
            when (val r = repo.stop(key)) {
                is ApiResult.Ok -> _state.update { it.copy(pinSheet = r.data) }
                is ApiResult.Err -> _state.update { it.copy(message = r.message) }
                ApiResult.Queued -> Unit
            }
        }
    }

    fun closePin() = _state.update { it.copy(pinSheet = null) }
}
