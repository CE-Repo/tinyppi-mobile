package com.jamal2367.tinyppimobile.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import com.jamal2367.tinyppimobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The shortest a pull is answered in.
 *
 * A box on the same network answers in a few dozen milliseconds, which is
 * faster than the gesture that asked: the spinner would appear and be gone
 * inside the same flick, and a shelf that comes back identical - which is what
 * most pulls find - would read as a pull that did nothing at all. So the
 * answer is held until it can be seen to be one. It is a floor and not a wait:
 * a read that takes longer than this is not delayed by it.
 */
private val PULL_ANSWER = 450.milliseconds

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
    /**
     * Whether somebody is being answered for having pulled the wall down.
     *
     * Apart from [loading], which covers every read there is: this one is the
     * spinner's, and a spinner belongs to the gesture that asked for it rather
     * than to a list quietly reading itself behind the screen.
     */
    val refreshing: Boolean = false,
    /** The film a press is waiting on, until the box says it is playing. */
    val starting: Int? = null,
)

/**
 * The wall of series under the films, and the one show somebody opened.
 *
 * Its own state again, and for the same reason [LibraryUiState] is: a shelf
 * redrawn at the snapshot's cadence is a shelf redrawn five times a second.
 */
data class SeriesUiState(
    val shows: List<LibraryShow> = emptyList(),
    val read: Boolean = false,
    val loading: Boolean = false,
    /** False once the box has answered that it offers no series at all. */
    val offered: Boolean = true,
    /** Whether a pull is being answered; see [LibraryUiState.refreshing]. */
    val refreshing: Boolean = false,
    /** The show whose episodes are on the screen, or null for the wall. */
    val open: OpenShow? = null,
    /** The show a press is waiting on, while its episodes are being read. */
    val opening: Int? = null,
    /**
     * Which seasons of the open show have been unfolded, by season number.
     *
     * Empty whenever a show is opened: a series that has run for nine years is
     * several hundred rows, and a list that opens on all of them opens in the
     * middle of season one. Folded, a whole show is a dozen lines.
     */
    val openSeasons: Set<Int> = emptySet(),
    /** The episode a press is waiting on, until the box says it is playing. */
    val starting: Int? = null,
)

/**
 * The row of films and episodes left half-watched, at the top of both shelves.
 *
 * Its own state for the reason the shelves have theirs, and read on the same
 * occasions they are: when a shelf screen arrives, and whenever the box says
 * its library has moved - which is what a title being stopped looks like.
 */
data class ContinueUiState(
    /** Films and episodes both, the last one seen first. */
    val items: List<ContinueItem> = emptyList(),
    val read: Boolean = false,
    val loading: Boolean = false,
    /**
     * False once the box has said it has no row to offer: neither shelf on
     * offer, or an add-on older than the row.
     */
    val offered: Boolean = true,
    /** The tile a press is waiting on, by [ContinueItem.key]. */
    val starting: String? = null,
)

/** One show, with the episodes that were read when it was opened. */
data class OpenShow(
    val id: Int,
    val title: String,
    /** The tag of the picture the episode list stands under; empty for none. */
    val fanart: String = "",
    val episodes: List<LibraryEpisode> = emptyList(),
)

/**
 * Everything is handed in rather than fetched from the application, so the
 * whole of what the shelves and the commands decide can be run against a
 * made-up box in a unit test. [Factory] is where the real ones come from.
 */
class LiveViewModel(
    private val repository: PlayerRepository,
    liveState: Flow<LiveState>,
    settings: Flow<AppSettings>,
    private val reconnectLive: () -> Unit,
    /** How a failure is put into words for the snackbar. */
    private val describe: (Throwable) -> String,
) : ViewModel() {

    val state: StateFlow<LiveUiState> =
        combine(liveState, settings) { live, settings ->
            LiveUiState(live = live, settings = settings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LiveUiState(),
        )

    private val _library = MutableStateFlow(LibraryUiState())

    /**
     * The films the box has. Empty until something asks for them, which the
     * films screen does on arrival and again whenever the box says its library
     * has moved (see [refreshLibrary] and [noteLibraryVersion]).
     */
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _series = MutableStateFlow(SeriesUiState())

    /** The series it has, read on the same two occasions the films are. */
    val series: StateFlow<SeriesUiState> = _series.asStateFlow()

    private val _continuing = MutableStateFlow(ContinueUiState())

    /** The row of titles left half-watched, read on the same occasions. */
    val continuing: StateFlow<ContinueUiState> = _continuing.asStateFlow()

    /**
     * Which version of the box's shelves the two lists above were read at, or
     * null before a snapshot has said.
     *
     * The first number a session is told is where the box happens to stand and
     * not a move, so it is taken as the mark rather than acted on. After that
     * every change means the same thing: a film has been watched to the end or
     * switched off in the middle, or a scan has moved something, and what is
     * held here is the old answer.
     */
    private var libraryVersion: Long? = null

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
        reconnectLive()
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * What the box says its shelves are at now, left here by whichever screen
     * is showing one.
     *
     * Nothing is read from the network here. Both lists are simply marked
     * unread, and the screen that is actually showing one reads it because it
     * watches that mark - a shelf nobody has open costs nothing until somebody
     * opens it, which is the whole reason the lists are held rather than asked
     * for per visit.
     *
     * Before this the app had no way of hearing that a library had moved at
     * all: a wall read this afternoon went on drawing tonight's film as
     * unwatched until the app was started again.
     */
    fun noteLibraryVersion(revision: Long) {
        val held = libraryVersion
        if (held == revision) return
        libraryVersion = revision
        if (held == null) return
        _library.value = _library.value.copy(read = false)
        _series.value = _series.value.copy(read = false)
        _continuing.value = _continuing.value.copy(read = false)
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
     * Asked for when the screen arrives, and again whenever the box says its
     * library has moved - which is what [noteLibraryVersion] marks and what a
     * film ending looks like from here. Without that mark a list already read
     * is left alone: the wall is the same wall, and re-reading it on every
     * visit to the screen would send a library across the network to draw what
     * is already drawn.
     *
     * A failure is not reported to the reader. The wall is an offer rather
     * than an answer to something they asked for, and a box that cannot make
     * it has already said so through the connection line at the top of the
     * screen.
     */
    fun refreshLibrary(force: Boolean = false) {
        val current = _library.value
        if (current.loading || !current.offered) return
        // Not while a tile is waiting on the film it was pressed on: the wall
        // arrives without that press on it and the tile would stop showing it.
        // Nothing is lost by waiting - the list stays marked unread, and the
        // screen reads it again as soon as the press is given back.
        if (current.starting != null) return
        if (current.read && !force) return

        _library.value = current.copy(loading = true)
        viewModelScope.launch { readLibrary() }
    }

    /**
     * Read it now, because somebody pulled the wall down to ask.
     *
     * Everything [refreshLibrary] holds back for is overruled here. A pull is
     * a person asking, and each of those guards is an assumption about what
     * they already know: that the list is current, that a press is still
     * coming, that a box which once said it offers no library still does not -
     * and the reason to pull is usually that one of them has stopped being
     * true. The setting can be switched on in Kodi while the app is looking at
     * the shelf it switched off.
     *
     * It is also the one read whose failure is said out loud. Everywhere else
     * the wall is an offer, and an offer that cannot be made is not worth
     * interrupting anybody over; this one was asked for, and a gesture that is
     * answered with nothing at all is a gesture somebody makes again.
     */
    fun pullLibrary() {
        if (_library.value.refreshing) return
        _library.value = _library.value.copy(refreshing = true, loading = true)
        viewModelScope.launch {
            val since = TimeSource.Monotonic.markNow()
            val failure = readLibrary()
            // The row above the wall is part of what was pulled.
            readContinuing()
            delay(PULL_ANSWER - since.elapsedNow())
            _library.value = _library.value.copy(refreshing = false)
            if (failure != null) report(failure)
        }
    }

    /**
     * The read itself, the caller having already marked it in flight; what
     * comes back is what went wrong, or null.
     */
    private suspend fun readLibrary(): Throwable? = try {
        val answer = repository.library()
        _library.value = _library.value.copy(
            films = answer.movies,
            read = true,
            loading = false,
            // It answered, so it offers one however it answered last time.
            offered = true,
            starting = null,
        )
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // A box saying it offers no library has answered; anything else -
        // unreachable, a token refused - is worth asking again the next time
        // the screen finds it idle, so it does not count as having been read.
        val refused = (failure as? ApiFailure.Api)?.isControlDisabled == true
        _library.value = _library.value.copy(
            loading = false,
            read = refused,
            offered = !refused,
            starting = null,
        )
        failure
    }

    /**
     * Put one of those films on the television.
     *
     * The tile stays pressed until the box says the film is on, which is what
     * [LibraryUiState.starting] carries; a film that never starts gives the
     * wall back when the next read does (see the live screen).
     */
    fun playFilm(film: LibraryFilm, fromStart: Boolean = false) {
        if (_library.value.starting != null) return
        _library.value = _library.value.copy(starting = film.id)
        viewModelScope.launch {
            try {
                repository.playFilm(film.id, fromStart)
                // Where the box got to in this film has just moved, so the
                // list is worth reading again the next time nothing is on.
                _library.value = _library.value.copy(read = false)
                _continuing.value = _continuing.value.copy(read = false)
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

    /**
     * Read the box's series, on the same two occasions the films are read.
     *
     * A failure is not reported, for the same reason a failed film wall is
     * not: the shelf is an offer rather than an answer to something somebody
     * asked for.
     */
    fun refreshSeries(force: Boolean = false) {
        val current = _series.value
        if (current.loading || !current.offered) return
        if (current.starting != null || current.opening != null) return  // as above
        if (current.read && !force) return

        _series.value = current.copy(loading = true)
        viewModelScope.launch { readSeries() }
    }

    /** The shelf read because somebody pulled it down; see [pullLibrary]. */
    fun pullSeries() {
        if (_series.value.refreshing) return
        _series.value = _series.value.copy(refreshing = true, loading = true)
        viewModelScope.launch {
            val since = TimeSource.Monotonic.markNow()
            val failure = readSeries()
            readContinuing()
            delay(PULL_ANSWER - since.elapsedNow())
            _series.value = _series.value.copy(refreshing = false)
            if (failure != null) report(failure)
        }
    }

    /** The read itself; see [readLibrary]. */
    private suspend fun readSeries(): Throwable? = try {
        val answer = repository.series()
        // A shelf that has just been read again may no longer hold the show
        // somebody was inside, and where it does not the screen comes back to
        // the wall. Where it does, they are left where they were: this is read
        // again every time an episode ends now, and a screen that threw
        // whoever was watching a series back out to the wall each time would
        // be a screen nobody could watch a series from.
        val open = _series.value.open
        val held = open?.takeIf { show -> answer.shows.any { it.id == show.id } }
        _series.value = _series.value.copy(
            shows = answer.shows,
            read = true,
            loading = false,
            offered = true,
            open = held,
            openSeasons = if (held != null) _series.value.openSeasons else emptySet(),
            opening = null,
            starting = null,
        )
        // The episodes of that show are a list of their own, and the one that
        // has just been watched is a row in it.
        if (held != null) reread(held)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        val refused = (failure as? ApiFailure.Api)?.isControlDisabled == true
        _series.value = _series.value.copy(
            loading = false,
            read = refused,
            offered = !refused,
            opening = null,
            starting = null,
        )
        failure
    }

    /**
     * Open one of those series, which is to read its episodes.
     *
     * Read here and not with the shelf: a house with ninety series in it would
     * otherwise be sent every episode of all of them to draw a wall of ninety
     * posters, and it is the wall that is being looked at.
     */
    fun openShow(show: LibraryShow) {
        val current = _series.value
        if (current.opening != null || current.starting != null) return

        _series.value = current.copy(opening = show.id)
        viewModelScope.launch {
            try {
                val answer = repository.episodes(show.id)
                _series.value = _series.value.copy(
                    open = OpenShow(show.id, show.title, show.fanart, answer.episodes),
                    opening = null,
                    openSeasons = emptySet(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // The shelf may have moved under the screen -- the show
                // scanned away while it was being looked at -- so it is worth
                // reading again the next time the box is found idle.
                _series.value = _series.value.copy(opening = null, read = false)
                report(failure)
            }
        }
    }

    /**
     * Read the open show's episodes again, in place.
     *
     * The rows carry a resume bar and a watched tick, and both move the moment
     * an episode is stopped; without this they would go on saying it was never
     * watched for as long as the screen stayed open on that show.
     *
     * In place, and quietly: which seasons are unfolded is left alone, and a
     * box that cannot answer leaves the episodes that are drawn where they
     * are - the shelf above them has just been read, so the screen is not
     * showing a show that is gone.
     */
    private suspend fun reread(show: OpenShow) {
        val episodes = try {
            repository.episodes(show.id).episodes
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }
        val current = _series.value.open ?: return
        if (current.id != show.id) return
        _series.value = _series.value.copy(open = current.copy(episodes = episodes))
    }

    /** Back out of a show, to the wall it was opened from. */
    fun closeShow() {
        if (_series.value.open != null || _series.value.opening != null) {
            _series.value = _series.value.copy(
                open = null,
                opening = null,
                openSeasons = emptySet(),
            )
        }
    }

    /** Unfold one season of the open show, or fold it again. */
    fun toggleSeason(season: Int) {
        val unfolded = _series.value.openSeasons
        _series.value = _series.value.copy(
            openSeasons = if (season in unfolded) {
                unfolded - season
            } else {
                unfolded + season
            },
        )
    }

    /** Put one episode of the open show on the television. */
    fun playEpisode(episode: LibraryEpisode, fromStart: Boolean = false) {
        if (_series.value.starting != null) return
        _series.value = _series.value.copy(starting = episode.id)
        viewModelScope.launch {
            try {
                repository.playEpisode(episode.id, fromStart)
                // What has been watched is about to move, on this episode and
                // on the count its show's tile wears.
                _series.value = _series.value.copy(read = false)
                _continuing.value = _continuing.value.copy(read = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _series.value = _series.value.copy(starting = null)
                report(failure)
            }
        }
    }

    /** Give the list back, once the episode that was pressed is playing. */
    fun episodeStarted() {
        if (_series.value.starting != null) {
            _series.value = _series.value.copy(starting = null)
        }
    }

    /**
     * Read the row of titles left half-watched.
     *
     * Quietly, the way the shelves are read: the row is an offer, and a box
     * that cannot make it simply has no row.
     */
    fun refreshContinuing(force: Boolean = false) {
        val current = _continuing.value
        if (current.loading || !current.offered) return
        if (current.starting != null) return  // as with the walls
        if (current.read && !force) return

        _continuing.value = current.copy(loading = true)
        viewModelScope.launch { readContinuing() }
    }

    /** The read itself; see [readLibrary]. */
    private suspend fun readContinuing() {
        try {
            val answer = repository.continuing()
            _continuing.value = _continuing.value.copy(
                items = answer.items,
                read = true,
                loading = false,
                offered = true,
                starting = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Neither shelf on offer (403), or an add-on that has no row at
            // all (404): settled answers, not asked again until a pull.
            val status = (failure as? ApiFailure.Api)?.status
            val refused = status == 403 || status == 404
            _continuing.value = _continuing.value.copy(
                loading = false,
                read = refused,
                offered = !refused,
                items = if (refused) emptyList() else _continuing.value.items,
                starting = null,
            )
        }
    }

    /**
     * Resume one of the titles on the row, where it was.
     *
     * The same two calls the walls start things through; the box resumes it
     * because the library holds a point to resume from.
     */
    fun playContinuing(item: ContinueItem, fromStart: Boolean = false) {
        if (_continuing.value.starting != null) return
        _continuing.value = _continuing.value.copy(starting = item.key)
        viewModelScope.launch {
            try {
                if (item.isEpisode) {
                    repository.playEpisode(item.id, fromStart)
                    _series.value = _series.value.copy(read = false)
                } else {
                    repository.playFilm(item.id, fromStart)
                    _library.value = _library.value.copy(read = false)
                }
                _continuing.value = _continuing.value.copy(read = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _continuing.value = _continuing.value.copy(starting = null)
                report(failure)
            }
        }
    }

    /** Give the row back, once the title that was pressed is playing. */
    fun continuingStarted() {
        if (_continuing.value.starting != null) {
            _continuing.value = _continuing.value.copy(starting = null)
        }
    }

    /**
     * Mark a film, a series or an episode as seen or unseen, as the dialog a
     * press on it opens asked.
     *
     * Nothing is drawn from here: the box drops what it holds the moment it
     * has written, and every list the shelves are drawn from is read again at
     * once - the walls, the row, and the episodes of whichever show is open -
     * so the tick that appears is the library's and not a guess of this app's.
     * A failure is said out loud, because this one was asked for.
     */
    fun setWatched(target: MarkTarget, watched: Boolean) =
        write { repository.setWatched(target, watched) }

    /**
     * Forget where a film or an episode got to, leaving it seen or unseen as
     * it was: it leaves the continue-watching row, and its bar leaves the wall.
     */
    fun clearResume(target: MarkTarget) = write { repository.clearResume(target) }

    /** One write to the library, and every shelf read again after it. */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                report(failure)
                return@launch
            }
            _library.value = _library.value.copy(read = false)
            _series.value = _series.value.copy(read = false)
            _continuing.value = _continuing.value.copy(read = false)
            // Read here rather than left to the screens that watch the marks:
            // the screen showing the press may be one that is not watching
            // every list the title stands on.
            refreshLibrary()
            refreshSeries()
            refreshContinuing()
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
        _message.value = describe(failure)
    }

    companion object {
        /**
         * The one every screen asks for, wired to the app's own graph.
         *
         * Every caller passes it with the activity as the owner, so the three
         * live screens and the two shelves share one instance - which is what
         * lets the shelves be read once rather than once per screen.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TinyPpiApplication
                val container = application.container
                LiveViewModel(
                    repository = container.repository,
                    liveState = container.liveState,
                    settings = container.settingsRepository.settings,
                    reconnectLive = container::reconnect,
                    describe = { it.toUserMessage(application) },
                )
            }
        }
    }
}
