package com.jamal2367.tinyppimobile.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the live half of the app reads, and everything it can ask for.
 *
 * One view model for three screens - what is playing, the printed readings, the
 * metadata view - because all three are windows onto the same snapshot, and
 * three view models would be three collectors of the same flow with three
 * copies of the same command handling under them.
 */
data class LiveUiState(
    val live: LiveState = LiveState(),
    val settings: AppSettings = AppSettings(),
) {
    val snapshot: Snapshot? get() = live.snapshot

    val isConfigured: Boolean get() = settings.isConfigured

    /** Whether this box will act on a command at all. */
    val canControl: Boolean get() = snapshot?.control == true

    /** Whether there is a film to control, as opposed to a box that will allow it. */
    val canControlPlayback: Boolean get() = canControl && snapshot?.playing == true
}

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TinyPpiApplication).container
    private val repository = container.repository

    val state: StateFlow<LiveUiState> =
        combine(container.liveState, container.settingsRepository.settings) { live, settings ->
            LiveUiState(live = live, settings = settings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LiveUiState(),
        )

    private val _message = MutableStateFlow<String?>(null)

    /**
     * The one line a failed command leaves behind.
     *
     * Only failures: a command that worked is announced by the picture
     * changing, and a snackbar saying so would be one more thing on screen
     * saying what the screen already says.
     */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Where the volume slider is while a finger is on it.
     *
     * The box answers with the level it settled on and the snapshot brings it
     * back five times a second, which is faster than a finger moves - so
     * without this the thumb is dragged out from under the finger by an answer
     * about where it was two frames ago. Cleared when the box catches up.
     */
    private val _pendingVolume = MutableStateFlow<Int?>(null)
    val pendingVolume: StateFlow<Int?> = _pendingVolume.asStateFlow()

    fun playPause() = command { repository.playPause() }

    fun stop() = command { repository.stop() }

    fun seekBy(seconds: Int) = command { repository.seekBy(seconds) }

    fun seekTo(percent: Float) = command { repository.seekTo(percent) }

    fun toggleMute() = command { repository.toggleMute() }

    fun selectAudio(index: Int) = command { repository.selectAudio(index) }

    /** Pick a subtitle track, or pass null to switch them off. */
    fun selectSubtitle(index: Int?) = command { repository.selectSubtitle(index) }

    /** Put the driver into one of the VS10 modes this snapshot offered. */
    fun setMode(mode: String) = command { repository.setMode(mode) }

    /** Where the slider is now, before the box has been told. */
    fun previewVolume(level: Int) {
        _pendingVolume.value = level.coerceIn(0, 100)
    }

    /** Where the finger came off, which is the only level worth sending. */
    fun commitVolume(level: Int) {
        val target = level.coerceIn(0, 100)
        _pendingVolume.value = target
        viewModelScope.launch {
            try {
                repository.setVolume(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                report(failure)
            } finally {
                // Handed back to the box either way: on success its own
                // reading is the one to follow, and on failure the slider must
                // not go on showing a level nothing was ever set to.
                _pendingVolume.value = null
            }
        }
    }

    private fun command(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                report(failure)
            }
        }
    }

    private fun report(failure: Throwable) {
        _message.value = failure.toUserMessage(getApplication())
    }
}
