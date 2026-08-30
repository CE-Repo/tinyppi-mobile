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
 * Fetched rather than streamed, and only when there is something new in it. The
 * snapshot carries an event counter for exactly this: it counts events, not
 * passes, so a number that has not moved means nothing has been written down
 * and the hour of samples already on screen is still the whole story. Asking
 * five times a second for three arrays of 3600 numbers would be the most
 * expensive thing this app does, and almost all of it would be the same answer.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TinyPpiApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

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

    fun refresh() {
        viewModelScope.launch {
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
