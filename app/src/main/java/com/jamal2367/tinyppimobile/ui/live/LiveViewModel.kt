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
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import com.jamal2367.tinyppimobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
    val canControlPlayback: Boolean get() = snapshot?.run { control && playing } == true
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
    /** The next episode of each show being watched, for the "next up" row. */
    val next: List<ContinueItem> = emptyList(),
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

    /**
     * The box whose shelves were last asked about (see [readOffers]), or null
     * while none is answering: each box is asked once each time it is found,
     * not on every snapshot.
     */
    private var offersFrom: ServerConfig? = null

    /** That box's answer while it is out; let go of when the box is lost or another found. */
    private var offersRead: Job? = null

    val state: StateFlow<LiveUiState> =
        combine(liveState.onEach { noteBox(it); notePlaying(it) }, settings) { live, settings ->
            LiveUiState(live = live, settings = settings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LiveUiState(),
        )

    private val _started = Channel<Unit>(Channel.CONFLATED)

    /**
     * Once for every title the box has agreed to play, from a wall, the
     * episode list or the row of half-watched ones: the shell takes it as the
     * word to go to the live tab, where what was just started is shown.
     */
    val started: Flow<Unit> = _started.receiveAsFlow()

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

    /**
     * Ask a box that has just been found which shelves it offers.
     *
     * Part of reading the live state rather than a watch of its own: the
     * connection is held only while a screen looks (see `AppContainer`), and
     * a collector of its own here would hold it open behind the screen.
     */
    private fun noteBox(live: LiveState) {
        val box = live.server.takeIf { live.isAnswering }
        if (box == offersFrom) return
        offersFrom = box
        // An answer still out from a box that is gone would say what that
        // box offers, not this one.
        offersRead?.cancel()
        offersRead = if (box != null) viewModelScope.launch { readOffers() } else null
    }

    /**
     * Which shelves the box offers, from `/api/hello`, before any shelf has
     * been read: a shelf switched off is gone from the bar at once, and the
     * overview card does not offer the details of a title whose shelf is off
     * (the box would refuse them). A shelf switched on again is read anew.
     *
     * An add-on older than the flags says nothing, and a box that cannot be
     * asked leaves things as they stand: the shelves still find out for
     * themselves when they are refused.
     */
    private suspend fun readOffers() {
        val hello = try {
            repository.hello()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }
        hello.library?.let { offered ->
            val held = _library.value
            if (held.offered != offered) _library.value = held.copy(offered = offered, read = !offered)
        }
        hello.series?.let { offered ->
            val held = _series.value
            if (held.offered != offered) _series.value = held.copy(offered = offered, read = !offered)
        }
    }

    fun playPause() = command { repository.playPause() }

    fun stop() = command { repository.stop() }

    fun previousChapter() = command { repository.previousChapter() }

    fun nextChapter() = command { repository.nextChapter() }

    fun goToChapter(index: Int) = command { repository.goToChapter(index) }

    fun seekBy(seconds: Int) = command { repository.seekBy(seconds) }

    fun seekTo(percent: Float) = command { repository.seekTo(percent) }

    fun toggleMute() = command { repository.toggleMute() }

    fun volumeUp() = command { repository.volumeUp() }

    fun volumeDown() = command { repository.volumeDown() }

    fun selectAudio(index: Int) = command { repository.selectAudio(index) }

    /** Pick a subtitle track, or pass null to switch them off. */
    fun selectSubtitle(index: Int?) = command { repository.selectSubtitle(index) }

    /** Set the audio offset, in seconds. */
    fun setAudioDelay(seconds: Double) = command { repository.setAudioDelay(seconds) }

    /** Set the subtitle offset, in seconds. */
    fun setSubtitleDelay(seconds: Double) = command { repository.setSubtitleDelay(seconds) }

    /** Put the driver into one of the VS10 modes this snapshot offered. */
    fun setMode(mode: String) = command { repository.setMode(mode) }

    private val _details = MutableStateFlow<DetailsUiState?>(null)

    /** The title whose details are open, and what has been read of them; null for none. */
    val details: StateFlow<DetailsUiState?> = _details.asStateFlow()

    private val _underDetails = MutableStateFlow<DetailsUiState?>(null)

    /**
     * The details of the show whose seasons are open over them; null for
     * none. Kept rather than closed when the seasons open, so leaving the
     * seasons finds the page they were opened from exactly as it was, with
     * nothing to read again (see [popDetails]).
     */
    val underDetails: StateFlow<DetailsUiState?> = _underDetails.asStateFlow()

    private var detailsRead: Job = Job()

    /**
     * What was playing when the open details were opened from the overview
     * card (see [playingKey]), or null for details opened anywhere else.
     * Those details are about what is playing, and go when it does.
     */
    private var detailsPlaying: String? = null

    /** The read of the episodes of a show being opened, while it is out. */
    private var opening: Job? = null

    /**
     * A page the open details were led on to from (see [keepDetails]): the
     * page as it was, whether it was the playing title's, and the actions
     * the screen gave it - the screen's own, which the view model only holds.
     */
    class DetailsBack internal constructor(val page: DetailsUiState, internal val playing: String?, val actions: Any?)

    /** The pages the open details were led on to from, the last on top. */
    private val detailsBack = ArrayDeque<DetailsBack>()

    /** Whether the next [openDetails] is a title led on to, which keeps [detailsBack]. */
    private var leadingOn = false

    /**
     * Keep the open page, with the [actions] it shows, before a title it
     * leads on to opens in its place, so going back finds it as it was (see
     * [backDetails]). Nothing while no page is open.
     */
    fun keepDetails(actions: Any?) {
        val page = _details.value ?: return
        detailsBack.addLast(DetailsBack(page, detailsPlaying, actions))
        leadingOn = true
    }

    /**
     * The page the open details were led on to from, back in front as it
     * was, with nothing to read again; null where they were led on to from
     * none, and should close instead.
     */
    fun backDetails(): DetailsBack? {
        val back = detailsBack.removeLastOrNull() ?: return null
        detailsRead.cancel()
        detailsPlaying = back.playing
        _details.value = back.page
        return back
    }

    /**
     * Read everything the library holds about one title, for the dialog that
     * shows it. A failure is the dialog's to show rather than a line along
     * the bottom of the screen.
     */
    fun openDetails(target: MarkTarget, playing: Snapshot? = null) {
        // A title opened anew starts a new trail; one led on to adds to it.
        if (!leadingOn) detailsBack.clear()
        leadingOn = false
        detailsRead.cancel()
        detailsPlaying = playing?.let(::playingKey)
        _details.value = DetailsUiState(target)
        detailsRead = viewModelScope.launch {
            val read = try {
                DetailsUiState(target, details = repository.details(target))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                DetailsUiState(target, failed = true)
            }
            // Into whichever page is showing the title by now: a show's page
            // may have gone under its seasons while it was being read.
            if (_underDetails.value?.target == target) _underDetails.value = read else _details.value = read
        }
    }

    /**
     * The details of a playing title the box hands out none for - a
     * broadcast, a file the library does not know, a box that takes no
     * orders - drawn from what [snapshot] says of it, with nothing to read.
     * Its target has no id: the pictures are the playing title's (see
     * `MediaUrls.details`), and there is nothing to play or mark.
     */
    fun showPlayingDetails(snapshot: Snapshot) {
        detailsBack.clear()
        detailsRead.cancel()
        val media = snapshot.media
        val kind = if (media.item.kind == "episode") MarkTarget.Kind.EPISODE else MarkTarget.Kind.MOVIE
        detailsPlaying = playingKey(snapshot)
        _details.value = DetailsUiState(MarkTarget(kind, 0, snapshot.title), details = media.details(snapshot.art))
    }

    /**
     * The page under the seasons back in front, as the seasons are left.
     * Nothing where there is none.
     */
    fun popDetails() {
        val under = _underDetails.value ?: return
        _details.value = under
        _underDetails.value = null
    }

    /** Close the details, letting go of a read still out. */
    fun closeDetails() {
        detailsBack.clear()
        leadingOn = false
        detailsRead.cancel()
        detailsPlaying = null
        _details.value = null
    }

    /**
     * Close the details of what was playing once it is no longer: stopped,
     * or another title started in its place. A snapshot still to come - the
     * stream being reopened - says nothing either way.
     */
    private fun notePlaying(live: LiveState) {
        val held = detailsPlaying ?: return
        val snapshot = live.snapshot ?: return
        if (playingKey(snapshot) != held) closeDetails()
    }

    /**
     * Which title is playing, or null for none: a title the library holds by
     * its id, anything else - a broadcast, a loose file - by its name.
     */
    private fun playingKey(snapshot: Snapshot): String? {
        if (!snapshot.playing) return null
        val item = snapshot.media.item
        return if (item.id > 0) "${item.kind}:${item.id}" else "title:${snapshot.title}"
    }

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
                _started.trySend(Unit)
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
        opening = viewModelScope.launch {
            try {
                val answer = repository.episodes(show.id)
                // The show's own details, where they are what it was opened
                // from, stay as they are under its seasons rather than close.
                val page = _details.value
                if (page != null && page.target.kind == MarkTarget.Kind.SHOW && page.target.id == show.id) {
                    _underDetails.value = page
                    _details.value = null
                }
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
        // A show left while its episodes are still being read stays left:
        // its seasons do not come up a moment later over the shelf.
        opening?.cancel()
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
                _started.trySend(Unit)
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
                next = answer.next,
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
                next = if (refused) emptyList() else _continuing.value.next,
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
                _started.trySend(Unit)
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
        write(target, { it.copy(watched = watched) }) { repository.setWatched(target, watched) }

    /**
     * Forget where a film or an episode got to, leaving it seen or unseen as
     * it was: it leaves the continue-watching row, and its bar leaves the wall.
     */
    fun clearResume(target: MarkTarget) =
        write(target, { it.copy(resume = 0) }) { repository.clearResume(target) }

    /** One write to the library, and every shelf read again after it. */
    private fun write(target: MarkTarget, update: (TitleDetails) -> TitleDetails, block: suspend () -> Unit) {
        if (_details.value?.busy == true) return
        changeDetails(target) { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                changeDetails(target) { it.copy(busy = false, actionError = describe(failure)) }
                report(failure)
                return@launch
            }
            changeDetails(target) { it.copy(details = update(it.details ?: TitleDetails()), busy = false) }
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

    private fun changeDetails(target: MarkTarget, change: (DetailsUiState) -> DetailsUiState) {
        val current = _details.value
        if (current?.target == target) _details.value = change(current)
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
