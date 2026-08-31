package com.jamal2367.tinyppimobile.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HistoryUiState(
    val history: History? = null,
    val snapshot: Snapshot? = null,
    val connection: LiveState.Connection = LiveState.Connection.Connecting,
    val range: ChartRange = ChartRange.TEN_MINUTES,
    val isConfigured: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The playing title's chart and its event list.
 *
 * Fetched rather than streamed. Asking five times a second for three arrays of
 * 3600 numbers would be the most expensive thing this app does, and almost all
 * of it would be the same answer.
 *
 * Two things ask for it. The snapshot's event counter, the moment it moves - it
 * counts events rather than passes, so it moves exactly when something has been
 * written down and a switch or a warning appears without waiting. And a slow
 * beat from the screen, because the chart is the half of this that the counter
 * says nothing about: it gains a sample a second whether anything happens or
 * not, and a film that runs an hour without one event would otherwise leave the
 * chart standing where it was when the last one did.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TinyPpiApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    /** The fetch in flight, if one is. Declared above [init], which starts one. */
    private var fetch: Job? = null

    init {
        container.liveState
            .onEach { live ->
                _state.value = _state.value.copy(
                    snapshot = live.snapshot,
                    connection = live.connection,
                )
            }
            .launchIn(viewModelScope)

        container.settingsRepository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(
                    range = settings.chartRange,
                    isConfigured = settings.isConfigured,
                )
            }
            .launchIn(viewModelScope)

        // What the fetch hangs off: the title that is playing, and how many
        // events it has produced. Either moving is something the history does
        // not yet hold; neither moving is an answer already on screen.
        container.liveState
            .map { live ->
                val snapshot = live.snapshot
                HistoryKey(
                    title = snapshot?.title.orEmpty(),
                    playing = snapshot?.playing == true,
                    events = snapshot?.session?.seq ?: -1,
                )
            }
            .distinctUntilChanged()
            .onEach { key -> if (key.events >= 0) refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * Ask the box for the history again.
     *
     * One at a time: the beat is faster than a request to a box that has gone
     * quiet takes to give up, and a queue of those would each land with an
     * older answer than the one before it.
     */
    fun refresh() {
        if (fetch?.isActive == true) return

        fetch = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val history = repository.history()
                _state.value = _state.value.copy(
                    history = history,
                    loading = false,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    fun setRange(range: ChartRange) {
        viewModelScope.launch { container.settingsRepository.setChartRange(range) }
    }

    /** What a fetch is worth doing for: a different title, or a new event in it. */
    private data class HistoryKey(
        val title: String,
        val playing: Boolean,
        val events: Int,
    )
}
