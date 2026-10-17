package com.jamal2367.tinyppimobile.viewmodel

import com.jamal2367.tinyppimobile.FakeApi
import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import com.jamal2367.tinyppimobile.ui.live.LiveUiState
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.live.OpenShow
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The guards and failure paths of the live screens' view model. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelEdgeTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeApi()
    private val sent = mutableListOf<String>()

    private fun viewModel() = LiveViewModel(
        repository = PlayerRepository(api, FakeApi.json),
        liveState = MutableStateFlow(LiveState()),
        settings = MutableStateFlow(AppSettings()),
        reconnectLive = {},
        describe = { failure -> "failed: ${(failure as? ApiFailure.Api)?.status ?: failure.message}" },
    )

    private val film = LibraryFilm(id = 1, title = "Heat")
    private val show = LibraryShow(id = 7, title = "Dark", fanart = "f")
    private val episode = LibraryEpisode(id = 70, title = "Secrets", season = 1, episode = 1)
    private val movieItem = ContinueItem(kind = "movie", id = 1, title = "Heat")
    private val episodeItem = ContinueItem(kind = "episode", id = 70, title = "Secrets")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    init {
        api.command = { sent += it.action + (it.value?.let { v -> "=$v" } ?: ""); CommandAck(ok = true) }
    }

    @Test
    fun `control needs a reading that allows it, and playback something playing`() {
        assertFalse(LiveUiState().canControl)
        assertFalse(LiveUiState(live = LiveState(snapshot = Snapshot(control = false))).canControl)
        assertFalse(LiveUiState().canControlPlayback)
        val allowed = LiveUiState(live = LiveState(snapshot = Snapshot(control = true)))
        assertTrue(allowed.canControl)
        assertFalse(allowed.canControlPlayback)
        assertTrue(LiveUiState(live = LiveState(snapshot = Snapshot(control = true, playing = true))).canControlPlayback)
        assertFalse(LiveUiState(live = LiveState(snapshot = Snapshot(control = false, playing = true))).canControlPlayback)
        assertEquals(OpenShow(1, "x", "", emptyList()), OpenShow(1, "x"))
    }

    @Test
    fun `every transport button sends its command`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.previousChapter()
        vm.nextChapter()
        vm.goToChapter(3)
        vm.seekBy(10)
        vm.seekTo(50f)
        vm.toggleMute()
        vm.volumeUp()
        vm.volumeDown()
        vm.selectAudio(1)
        vm.selectSubtitle(null)
        vm.setMode("hdr10")
        advanceUntilIdle()
        assertEquals(
            listOf("chapter_previous", "chapter_next", "chapter_goto=3.0", "seek=10.0", "seek_percent=50.0", "mute", "volume_up",
                "volume_down", "audio=1.0", "subtitle=-1.0"),
            sent,
        )
        assertTrue(api.calls.contains("mode"))
    }

    @Test
    fun `the film wall is not read twice at once, nor while a press is pending`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Library>()
        api.library = { gate.await() }
        val vm = viewModel()
        vm.refreshLibrary()
        vm.refreshLibrary()
        advanceUntilIdle()
        assertEquals(1, api.count("library"))
        gate.complete(Library(movies = listOf(film)))
        advanceUntilIdle()
        vm.refreshLibrary()
        advanceUntilIdle()
        assertEquals("read once is enough", 1, api.count("library"))
        vm.refreshLibrary(force = true)
        advanceUntilIdle()
        assertEquals(2, api.count("library"))

        val playing = CompletableDeferred<CommandAck>()
        api.play = { playing.await() }
        vm.playFilm(film)
        vm.playFilm(film)
        vm.refreshLibrary(force = true)
        advanceUntilIdle()
        assertEquals(2, api.count("library"))
        assertEquals(1, api.count("play"))
        playing.complete(CommandAck(ok = true))
        advanceUntilIdle()
        vm.filmStarted()
        vm.filmStarted()
        assertNull(vm.library.value.starting)

        // A refused library is settled, and no longer asked for until pulled.
        api.library = { throw ApiFailure.Api(403, null) }
        vm.refreshLibrary(force = true)
        advanceUntilIdle()
        assertFalse(vm.library.value.offered)
        vm.refreshLibrary(force = true)
        advanceUntilIdle()
        assertEquals(3, api.count("library"))
    }

    @Test
    fun `a pull that answers says nothing, and a second pull waits for it`() = runTest(dispatcher) {
        val vm = viewModel()
        val gate = CompletableDeferred<Library>()
        api.library = { gate.await() }
        vm.pullLibrary()
        vm.pullLibrary()
        advanceUntilIdle()
        assertEquals(1, api.count("library"))
        gate.complete(Library())
        vm.pullLibrary()
        vm.pullSeries()
        advanceUntilIdle()
        assertNull(vm.message.value)
        assertFalse(vm.library.value.refreshing)
        assertFalse(vm.series.value.refreshing)
    }

    @Test
    fun `the series shelf has the same guards, and a pull reports its failure`() = runTest(dispatcher) {
        val gate = CompletableDeferred<SeriesLibrary>()
        api.series = { gate.await() }
        val vm = viewModel()
        vm.refreshSeries()
        vm.refreshSeries()
        vm.pullSeries()
        vm.pullSeries()
        advanceUntilIdle()
        assertEquals(2, api.count("series"))
        gate.complete(SeriesLibrary(shows = listOf(show)))
        advanceUntilIdle()
        assertFalse(vm.series.value.refreshing)
        vm.refreshSeries()
        advanceUntilIdle()
        assertEquals(2, api.count("series"))

        api.series = { throw IOException("down") }
        vm.pullSeries()
        advanceUntilIdle()
        assertEquals("failed: down", vm.message.value)
        assertTrue(vm.series.value.offered)
        assertFalse(vm.series.value.read)

        api.series = { throw ApiFailure.Api(500, null) }
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        assertTrue(vm.series.value.offered)

        api.series = { throw ApiFailure.Api(403, null) }
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        assertFalse(vm.series.value.offered)
        assertTrue(vm.series.value.read)
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        assertEquals(5, api.count("series"))
    }

    @Test
    fun `a show is opened once at a time and its failure is said`() = runTest(dispatcher) {
        val gate = CompletableDeferred<EpisodeList>()
        api.episodes = { gate.await() }
        val vm = viewModel()
        vm.openShow(show)
        vm.openShow(show)
        vm.refreshSeries()
        advanceUntilIdle()
        assertEquals(1, api.count("episodes"))
        assertEquals(0, api.count("series"))
        gate.complete(EpisodeList(tvshowid = 7, episodes = listOf(episode)))
        advanceUntilIdle()
        assertEquals("f", vm.series.value.open?.fanart)

        vm.closeShow()
        vm.closeShow()
        assertNull(vm.series.value.open)

        api.episodes = { throw IOException("gone") }
        vm.openShow(show)
        advanceUntilIdle()
        assertEquals("failed: gone", vm.message.value)
        assertNull(vm.series.value.opening)

        // Closing while it is still opening drops the opening too.
        api.episodes = { awaitCancellation() }
        vm.openShow(show)
        advanceUntilIdle()
        vm.closeShow()
        assertNull(vm.series.value.opening)
    }

    @Test
    fun `an open show is re-read with its shelf, unless it went or changed meanwhile`() = runTest(dispatcher) {
        api.series = { SeriesLibrary(shows = listOf(show, show.copy(id = 8))) }
        api.episodes = { EpisodeList(tvshowid = it, episodes = listOf(episode)) }
        val vm = viewModel()
        vm.openShow(show)
        advanceUntilIdle()

        // The re-read of the episodes fails: the list is left as it was.
        api.episodes = { throw IOException("x") }
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        assertEquals(listOf(episode), vm.series.value.open?.episodes)

        // The show is closed while its episodes are being read again.
        val reread = CompletableDeferred<EpisodeList>()
        api.episodes = { reread.await() }
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        vm.closeShow()
        reread.complete(EpisodeList())
        advanceUntilIdle()
        assertNull(vm.series.value.open)

        // Another show is opened while the first one's episodes are read again.
        api.episodes = { EpisodeList(tvshowid = it, episodes = listOf(episode)) }
        vm.openShow(show)
        advanceUntilIdle()
        val second = CompletableDeferred<EpisodeList>()
        api.episodes = { if (it == 7) second.await() else EpisodeList(tvshowid = it) }
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        vm.closeShow()
        vm.openShow(show.copy(id = 8))
        advanceUntilIdle()
        second.complete(EpisodeList(episodes = listOf(episode, episode)))
        advanceUntilIdle()
        assertEquals(8, vm.series.value.open?.id)
        assertEquals(emptyList<LibraryEpisode>(), vm.series.value.open?.episodes)

        // The second show on the shelf is found past the first.
        vm.refreshSeries(force = true)
        advanceUntilIdle()
        assertEquals(8, vm.series.value.open?.id)
    }

    @Test
    fun `episodes start once at a time and give the press back on failure`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CommandAck>()
        api.playEpisode = { gate.await() }
        val vm = viewModel()
        vm.playEpisode(episode)
        vm.playEpisode(episode)
        vm.refreshSeries()
        vm.openShow(show)
        advanceUntilIdle()
        assertEquals(1, api.count("playEpisode"))
        assertEquals(0, api.count("episodes"))
        gate.complete(CommandAck(ok = true))
        advanceUntilIdle()
        assertFalse(vm.series.value.read)
        vm.episodeStarted()
        vm.episodeStarted()
        assertNull(vm.series.value.starting)

        api.playEpisode = { throw ApiFailure.Api(500, "no") }
        vm.playEpisode(episode, fromStart = true)
        advanceUntilIdle()
        assertEquals("failed: 500", vm.message.value)
        assertNull(vm.series.value.starting)
    }

    @Test
    fun `the continue row has its guards and settles on refusals`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ContinueList>()
        api.continuing = { gate.await() }
        val vm = viewModel()
        vm.refreshContinuing()
        vm.refreshContinuing()
        advanceUntilIdle()
        gate.complete(ContinueList(items = listOf(movieItem)))
        advanceUntilIdle()
        vm.refreshContinuing()
        advanceUntilIdle()
        assertEquals(1, api.count("continuing"))
        vm.refreshContinuing(force = true)
        advanceUntilIdle()
        assertEquals(2, api.count("continuing"))

        val playing = CompletableDeferred<CommandAck>()
        api.play = { playing.await() }
        vm.playContinuing(movieItem)
        vm.playContinuing(movieItem)
        vm.refreshContinuing(force = true)
        advanceUntilIdle()
        assertEquals(1, api.count("play"))
        assertEquals(2, api.count("continuing"))
        playing.complete(CommandAck(ok = true))
        advanceUntilIdle()
        assertFalse(vm.library.value.read)
        vm.continuingStarted()
        vm.continuingStarted()
        assertNull(vm.continuing.value.starting)

        api.playEpisode = { throw IOException("x") }
        vm.playContinuing(episodeItem, fromStart = true)
        advanceUntilIdle()
        assertEquals("failed: x", vm.message.value)
        assertNull(vm.continuing.value.starting)

        api.continuing = { throw ApiFailure.Api(500, null) }
        vm.refreshContinuing(force = true)
        advanceUntilIdle()
        assertEquals(listOf(movieItem), vm.continuing.value.items)
        assertTrue(vm.continuing.value.offered)

        api.continuing = { throw IOException("x") }
        vm.refreshContinuing(force = true)
        advanceUntilIdle()
        assertTrue(vm.continuing.value.offered)

        api.continuing = { throw ApiFailure.Api(404, null) }
        vm.refreshContinuing(force = true)
        advanceUntilIdle()
        assertFalse(vm.continuing.value.offered)
        assertEquals(emptyList<ContinueItem>(), vm.continuing.value.items)
        vm.refreshContinuing(force = true)
        advanceUntilIdle()

        api.continuing = { throw ApiFailure.Api(403, null) }
        vm.pullLibrary()
        advanceUntilIdle()
        assertFalse(vm.continuing.value.offered)
    }

    @Test
    fun `marks that answer read the shelves and those that fail are said`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.clearResume(MarkTarget(MarkTarget.Kind.MOVIE, 1, "Heat"))
        advanceUntilIdle()
        assertEquals(1, api.count("library"))
        api.setWatched = { throw IOException("no") }
        vm.setWatched(MarkTarget(MarkTarget.Kind.MOVIE, 1, "Heat"), true)
        advanceUntilIdle()
        assertEquals("failed: no", vm.message.value)
    }

    @Test
    fun `everything in flight is dropped with the view model`() = runTest(dispatcher) {
        api.library = { awaitCancellation() }
        api.series = { awaitCancellation() }
        api.continuing = { awaitCancellation() }
        api.episodes = { awaitCancellation() }
        api.play = { awaitCancellation() }
        api.playEpisode = { awaitCancellation() }
        api.setWatched = { awaitCancellation() }
        api.command = { awaitCancellation() }
        val vm = viewModel()
        vm.refreshLibrary()
        vm.refreshSeries()
        vm.refreshContinuing()
        vm.openShow(show)
        vm.playFilm(film)
        vm.playEpisode(episode)
        vm.playContinuing(episodeItem)
        vm.setWatched(MarkTarget(MarkTarget.Kind.SHOW, 7, "Dark"), true)
        vm.playPause()
        advanceUntilIdle()

        // The episodes of an open show, being read again.
        val reread = viewModel()
        api.series = { SeriesLibrary(shows = listOf(show)) }
        api.episodes = { EpisodeList(episodes = listOf(episode)) }
        reread.openShow(show)
        advanceUntilIdle()
        api.episodes = { awaitCancellation() }
        reread.refreshSeries(force = true)
        advanceUntilIdle()

        vm.viewModelScope.cancel()
        reread.viewModelScope.cancel()
        advanceUntilIdle()
        assertNull(vm.message.value)
        assertNull(reread.message.value)
    }
}
