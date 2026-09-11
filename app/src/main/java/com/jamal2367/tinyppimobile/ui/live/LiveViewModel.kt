package com.jamal2367.tinyppimobile.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
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

/**
 * The wall of films the screen shows while nothing is playing.
 *
 * Its own state rather than part of [LiveUiState]: the snapshot arrives five
 * times a second and this changes when a library does, and folding the two
 * together would redraw a wall of five hundred posters at the snapshot's
 * cadence.
 */
data class LibraryUiState(
    val films: List<LibraryFilm> = emptyList(),
    /** Whether the list has been read at least once, however it turned out. */
    val read: Boolean = false,
    val loading: Boolean = false,
    /**
     * Whether this box offers a library at all.
     *
     * False once it has answered that it does not - the card switched off in
     * the add-on's settings, or a box that will not be told what to play. That
     * is a settled answer rather than a failure, so it is not asked again.
     */
    val offered: Boolean = true,
    /** The film a press is waiting on, until the box says it is playing. */
    val starting: Int? = null,
)

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

    private val _library = MutableStateFlow(LibraryUiState())

    /**
     * The films the box has. Empty until something asks for them, which the
     * live screen does whenever it finds the box idle (see [refreshLibrary]).
     */
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /**
     * The one line a failed command leaves behind.
     *
     * Only failures: a command that worked is announced by the picture
     * changing, and a snackbar saying so would be one more thing on screen
     * saying what the screen already says.
     */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Connect again now.
     *
     * Not a command to the box - it does not reach one - so it does not go
     * through [command] and cannot fail: what it does is tear down whatever
     * this app has open and start over, which is the answer when the box is
     * back and the session is still sitting on its back-off.
     */
    fun reconnect() {
        container.reconnect()
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun playPause() = command { repository.playPause() }

    fun stop() = command { repository.stop() }

    fun previousChapter() = command { repository.previousChapter() }

    fun nextChapter() = command { repository.nextChapter() }

    fun seekBy(seconds: Int) = command { repository.seekBy(seconds) }

    fun seekTo(percent: Float) = command { repository.seekTo(percent) }

    fun toggleMute() = command { repository.toggleMute() }

    fun volumeUp() = command { repository.volumeUp() }

    fun volumeDown() = command { repository.volumeDown() }

    fun selectAudio(index: Int) = command { repository.selectAudio(index) }

    /** Pick a subtitle track, or pass null to switch them off. */
    fun selectSubtitle(index: Int?) = command { repository.selectSubtitle(index) }

    /** Put the driver into one of the VS10 modes this snapshot offered. */
    fun setMode(mode: String) = command { repository.setMode(mode) }

    /**
     * Read the box's film library.
     *
     * Asked for when the screen finds the box idle and again when a film ends
     * - which is [force], because what the library says about the film that
     * has just finished has changed. Without it a list already read is left
     * alone: the wall is the same wall, and re-reading it on every visit to
     * the screen would send a library across the network to draw what is
     * already drawn.
     *
     * A failure is not reported to the reader. The wall is an offer rather
     * than an answer to something they asked for, and a box that cannot make
     * it has already said so through the connection line at the top of the
     * screen.
     */
    fun refreshLibrary(force: Boolean = false) {
        val current = _library.value
        if (current.loading || !current.offered) return
        if (current.read && !force) return

        _library.value = current.copy(loading = true)
        viewModelScope.launch {
            try {
                val answer = repository.library()
                _library.value = LibraryUiState(films = answer.movies, read = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // A box saying it offers no library has answered; anything
                // else - unreachable, a token refused - is worth asking again
                // the next time the screen finds it idle, so it does not count
                // as having been read.
                val refused = (failure as? ApiFailure.Api)?.isControlDisabled == true
                _library.value = _library.value.copy(
                    loading = false,
                    read = refused,
                    offered = !refused,
                    starting = null,
                )
            }
        }
    }

    /**
     * Put one of those films on the television.
     *
     * The tile stays pressed until the box says the film is on, which is what
     * [LibraryUiState.starting] carries; a film that never starts gives the
     * wall back when the next read does (see the live screen).
     */
    fun playFilm(film: LibraryFilm) {
        if (_library.value.starting != null) return
        _library.value = _library.value.copy(starting = film.id)
        viewModelScope.launch {
            try {
                repository.playFilm(film.id)
                // Where the box got to in this film has just moved, so the
                // list is worth reading again the next time nothing is on.
                _library.value = _library.value.copy(read = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _library.value = _library.value.copy(starting = null)
                report(failure)
            }
        }
    }

    /** Give the wall back, once the film that was pressed is playing. */
    fun filmStarted() {
        if (_library.value.starting != null) {
            _library.value = _library.value.copy(starting = null)
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
