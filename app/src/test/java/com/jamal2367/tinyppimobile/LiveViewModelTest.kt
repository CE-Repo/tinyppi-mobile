package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.WatchedBody
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * What the live screens and the shelves decide, against a made-up box.
 *
 * Mostly about when the shelves are read and when they are left alone - the
 * part of the view model with rules in it, and the part a wrong guess makes
 * expensive: a wall of several hundred posters read again for nothing, or a
 * wall that goes on saying a film is unwatched after it has been watched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val api = FakeApi()
    private val live = MutableStateFlow(LiveState())
    private val settings = MutableStateFlow(
        AppSettings(primary = ServerConfig(enabled = true, host = "192.168.1.10")),
    )
    private var reconnects = 0

    private fun viewModel() = LiveViewModel(
        repository = PlayerRepository(api, FakeApi.json),
        liveState = live,
        settings = settings,
        reconnectLive = { reconnects++ },
        describe = { failure -> "failed: ${(failure as? ApiFailure.Api)?.status ?: failure::class.simpleName}" },
    )

    private val blade = LibraryFilm(id = 1, title = "Blade Runner")
    private val heat = LibraryFilm(id = 2, title = "Heat")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Keeps [LiveViewModel.state] collected, the way a screen would. */
    private fun TestScope.watch(vm: LiveViewModel) {
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `the state is the live reading and the settings together`() = runTest(dispatcher) {
        val vm = viewModel()
        watch(vm)
        live.value = LiveState(
            connection = LiveState.Connection.Streaming,
            snapshot = Snapshot(playing = true, control = true),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.isConfigured)
        assertTrue(vm.state.value.canControlPlayback)
    }

    @Test
    fun `a box that allows control but plays nothing has nothing to control`() = runTest(dispatcher) {
        val vm = viewModel()
        watch(vm)
        live.value = LiveState(snapshot = Snapshot(playing = false, control = true))
        advanceUntilIdle()

        assertTrue(vm.state.value.canControl)
        assertFalse(vm.state.value.canControlPlayback)
    }

    @Test
    fun `a command goes to the box as the action it names`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.volumeUp()
        vm.playPause()
        advanceUntilIdle()

        assertEquals(listOf("command:volume_up", "command:playpause"), api.calls)
        assertNull(vm.message.value)
    }

    @Test
    fun `a refused command leaves one line behind, until it has been shown`() = runTest(dispatcher) {
        api.command = { throw ApiFailure.Api(403, "control disabled") }
        val vm = viewModel()
        vm.stop()
        advanceUntilIdle()

        assertEquals("failed: 403", vm.message.value)
        vm.consumeMessage()
        assertNull(vm.message.value)
    }

    @Test
    fun `reconnecting is handed to the session`() = runTest(dispatcher) {
        viewModel().reconnect()
        assertEquals(1, reconnects)
    }

    @Test
    fun `the film wall is read once and not again for nothing`() = runTest(dispatcher) {
        api.library = { Library(movies = listOf(blade, heat)) }
        val vm = viewModel()

        vm.refreshLibrary()
        advanceUntilIdle()
        vm.refreshLibrary()
        advanceUntilIdle()

        assertEquals(listOf(blade, heat), vm.library.value.films)
        assertTrue(vm.library.value.read)
        assertEquals(1, api.count("library"))
    }

    @Test
    fun `the first library version is a mark, and a later one marks every shelf unread`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refreshLibrary()
        vm.refreshSeries()
        vm.refreshContinuing()
        advanceUntilIdle()

        vm.noteLibraryVersion(7)
        assertTrue("where the box happens to stand is not a move", vm.library.value.read)

        vm.noteLibraryVersion(7)
        assertTrue(vm.library.value.read)

        vm.noteLibraryVersion(8)
        assertFalse(vm.library.value.read)
        assertFalse(vm.series.value.read)
        assertFalse(vm.continuing.value.read)

        vm.refreshLibrary()
        advanceUntilIdle()
        assertEquals(2, api.count("library"))
    }

    @Test
    fun `a box that offers no library is taken at its word`() = runTest(dispatcher) {
        api.library = { throw ApiFailure.Api(403, "library disabled") }
        val vm = viewModel()

        vm.refreshLibrary()
        advanceUntilIdle()
        vm.refreshLibrary(force = true)
        advanceUntilIdle()

        assertFalse(vm.library.value.offered)
        assertEquals(1, api.count("library"))
        assertNull("the wall is an offer, and its failure is not reported", vm.message.value)
    }

    @Test
    fun `a box that cannot be reached is asked again next time`() = runTest(dispatcher) {
        var fail = true
        api.library = { if (fail) throw ApiFailure.Unreachable(null, true, null) else Library(listOf(blade)) }
        val vm = viewModel()

        vm.refreshLibrary()
        advanceUntilIdle()
        assertFalse(vm.library.value.read)
        assertTrue(vm.library.value.offered)

        fail = false
        vm.refreshLibrary()
        advanceUntilIdle()
        assertEquals(listOf(blade), vm.library.value.films)
    }

    @Test
    fun `a pull overrules a box that said no, and reports what went wrong`() = runTest(dispatcher) {
        api.library = { throw ApiFailure.Api(403, "library disabled") }
        val vm = viewModel()
        vm.refreshLibrary()
        advanceUntilIdle()

        api.library = { throw ApiFailure.Api(500, "boom") }
        vm.pullLibrary()
        advanceUntilIdle()

        assertEquals(2, api.count("library"))
        assertFalse(vm.library.value.refreshing)
        assertEquals("failed: 500", vm.message.value)
    }

    @Test
    fun `a pressed film holds the wall until it plays`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        api.play = { started.await(); CommandAck(ok = true) }
        val vm = viewModel()

        vm.playFilm(blade)
        advanceUntilIdle()
        assertEquals(1, vm.library.value.starting)

        vm.playFilm(heat)
        advanceUntilIdle()
        assertEquals("a second press is ignored while the first waits", 1, api.count("play"))

        started.complete(Unit)
        advanceUntilIdle()
        vm.filmStarted()
        assertNull(vm.library.value.starting)
    }

    @Test
    fun `a film that will not start gives the wall back and says why`() = runTest(dispatcher) {
        api.play = { throw ApiFailure.Api(409, "busy") }
        val vm = viewModel()

        vm.playFilm(blade)
        advanceUntilIdle()

        assertNull(vm.library.value.starting)
        assertEquals("failed: 409", vm.message.value)
    }

    @Test
    fun `an open show stays open across a re-read that still has it`() = runTest(dispatcher) {
        val show = LibraryShow(id = 5, title = "Severance")
        val gone = LibraryShow(id = 6, title = "Lost")
        api.series = { SeriesLibrary(shows = listOf(show, gone)) }
        api.episodes = { EpisodeList(tvshowid = it, episodes = listOf(LibraryEpisode(id = 50))) }
        val vm = viewModel()

        vm.refreshSeries()
        advanceUntilIdle()
        vm.openShow(show)
        advanceUntilIdle()
        vm.toggleSeason(1)

        vm.refreshSeries(force = true)
        advanceUntilIdle()

        assertEquals(5, vm.series.value.open?.id)
        assertEquals("the unfolded seasons are left alone", setOf(1), vm.series.value.openSeasons)
        assertEquals("its episodes are read again in place", 2, api.count("episodes"))
    }

    @Test
    fun `an open show that has gone from the shelf goes back to the wall`() = runTest(dispatcher) {
        val show = LibraryShow(id = 5, title = "Severance")
        api.series = { SeriesLibrary(shows = listOf(show)) }
        val vm = viewModel()
        vm.refreshSeries()
        advanceUntilIdle()
        vm.openShow(show)
        advanceUntilIdle()

        api.series = { SeriesLibrary(shows = emptyList()) }
        vm.refreshSeries(force = true)
        advanceUntilIdle()

        assertNull(vm.series.value.open)
        assertTrue(vm.series.value.openSeasons.isEmpty())
    }

    @Test
    fun `seasons fold and unfold one at a time`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleSeason(1)
        vm.toggleSeason(2)
        vm.toggleSeason(1)
        assertEquals(setOf(2), vm.series.value.openSeasons)
    }

    @Test
    fun `an add-on without the continue row is not asked for it again`() = runTest(dispatcher) {
        api.continuing = { throw ApiFailure.Api(404, null) }
        val vm = viewModel()

        vm.refreshContinuing()
        advanceUntilIdle()
        vm.refreshContinuing(force = true)
        advanceUntilIdle()

        assertFalse(vm.continuing.value.offered)
        assertTrue(vm.continuing.value.items.isEmpty())
        assertEquals(1, api.count("continuing"))
    }

    @Test
    fun `resuming an episode from the row starts the episode, not a film`() = runTest(dispatcher) {
        val item = ContinueItem(kind = "episode", id = 42, title = "Pilot")
        api.continuing = { ContinueList(items = listOf(item)) }
        val vm = viewModel()
        vm.refreshContinuing()
        advanceUntilIdle()

        vm.playContinuing(item)
        advanceUntilIdle()

        assertEquals(1, api.count("playEpisode"))
        assertEquals(0, api.count("play"))
        assertFalse("the row is worth reading again", vm.continuing.value.read)
    }

    @Test
    fun `marking a film sends its id and reads the shelves again`() = runTest(dispatcher) {
        val vm = viewModel()
        var sent: WatchedBody? = null
        api.setWatched = { body -> sent = body; CommandAck(ok = true) }
        api.library = { Library(movies = listOf(blade.copy(watched = true), heat)) }
        vm.refreshLibrary()
        advanceUntilIdle()
        assertEquals(1, api.count("library"))

        vm.setWatched(MarkTarget(MarkTarget.Kind.MOVIE, blade.id, blade.title), watched = false)
        advanceUntilIdle()

        assertEquals(WatchedBody(movieid = blade.id, watched = false), sent)
        assertEquals(2, api.count("library"))
        assertEquals(1, api.count("series"))
        assertEquals(1, api.count("continuing"))
        assertNull(vm.message.value)
    }

    @Test
    fun `a series is marked by its show id alone`() = runTest(dispatcher) {
        val vm = viewModel()
        var sent: WatchedBody? = null
        api.setWatched = { body -> sent = body; CommandAck(ok = true) }

        vm.setWatched(MarkTarget(MarkTarget.Kind.SHOW, 7, "Dark"), watched = true)
        advanceUntilIdle()

        assertEquals(WatchedBody(tvshowid = 7, watched = true), sent)
        val encoded = FakeApi.json.encodeToString(WatchedBody.serializer(), sent!!)
        assertEquals("""{"tvshowid":7,"watched":true}""", encoded)
    }

    @Test
    fun `a mark the box refuses is said out loud and reads nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        api.setWatched = { throw ApiFailure.Api(400, "update failed") }

        vm.setWatched(MarkTarget(MarkTarget.Kind.EPISODE, 3, "S01E01"), watched = true)
        advanceUntilIdle()

        assertEquals("failed: 400", vm.message.value)
        assertEquals(0, api.count("library"))
    }
}
