package com.jamal2367.tinyppimobile.ui.library

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.data.model.CastMember
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The films and series shelves: their cards, the search, the dialog and the arranging. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class LibraryScreensTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val fake = FakeLive()
    private var settingsOpened = 0

    private val films = listOf(
        LibraryFilm(1, "Dune", 2024, "p1", 9960, 8.5, "imdb", watched = false, resume = 4260, added = "2026-10-01 20:00:00"),
        LibraryFilm(2, "Heat", 1995, "p2", 10200, 8.3, "tmdb", watched = true, added = "2026-09-01 20:00:00"),
        LibraryFilm(3, "Alien", 0, "", 0, 0.0, "", watched = false),
        LibraryFilm(4, "Arrival", 2016, "p4", 3600, 7.9, "other", added = "2026-08-01 20:00:00"),
        LibraryFilm(5, "Gravity", 2013, "p5", 1800, 0.0, "", added = "2026-08-01 20:00:00"),
    )

    private val shows = listOf(
        LibraryShow(7, "Dark", 2017, "s7", "f7", episodes = 26, unseen = 4, rating = 8.7, ratingFrom = "imdb", added = "2026-10-01 20:00:00"),
        LibraryShow(8, "Chernobyl", 2019, "s8", "", episodes = 5, unseen = 0, watched = true),
        LibraryShow(9, "Silo", 0, "", "", episodes = 10, unseen = 10),
    )

    private val episodes = listOf(
        LibraryEpisode(70, "Pilot special", season = 0, episode = 1, thumb = "t70", duration = 3000),
        LibraryEpisode(71, "Secrets", season = 1, episode = 1, thumb = "t71", duration = 3120, watched = true),
        LibraryEpisode(72, "", season = 1, episode = 2, thumb = "", duration = 0, resume = 600),
        LibraryEpisode(73, "Lies", season = 2, episode = 1, duration = 3600),
        LibraryEpisode(74, "Loose", season = -1, episode = -1),
    )

    private val continueItems = listOf(
        ContinueItem("movie", 1, "Dune", 2024, "p1", 9960, 4260, rating = 8.5, ratingFrom = "imdb"),
        ContinueItem("movie", 4, "Arrival", 0, "", 0, 100),
        ContinueItem("episode", 72, "Lies", poster = "s7", duration = 3120, resume = 600, show = "Dark", season = 1, episode = 2),
        ContinueItem("episode", 75, "Untitled", duration = 3120, resume = 600, show = "", season = -1, episode = 3),
    )

    init {
        fake.api.library = { Library(movies = films, count = films.size) }
        fake.api.series = { SeriesLibrary(shows = shows, count = shows.size) }
        fake.api.continuing = { ContinueList(items = continueItems) }
        fake.api.episodes = { EpisodeList(tvshowid = it, episodes = episodes) }
        fake.playing(Snapshot(control = true, playing = false, libraryRevision = 3))
    }

    private fun showFilms(cards: CardState = CardState()) = rule.show(cards) {
        FilmsScreen(onOpenSettings = { settingsOpened++ }, viewModel = fake.viewModel)
    }

    private fun showSeries(cards: CardState = CardState()) = rule.show(cards) {
        SeriesScreen(onOpenSettings = { settingsOpened++ }, viewModel = fake.viewModel)
    }

    private fun text(value: String) = rule.onAllNodesWithText(value).onLast()

    /** Long enough for a press to be given back, as when the box never says it started. */
    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(7_000)
        rule.waitForIdle()
    }

    @Test
    fun `an unconfigured shelf points at the settings`() {
        fake.settings.value = AppSettings(primary = Screens.box.copy(host = ""))
        showFilms()
        rule.onNodeWithText(str(R.string.action_open_settings)).performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun `an empty shelf says so`() {
        fake.api.library = { Library() }
        fake.api.continuing = { ContinueList() }
        showFilms()
        rule.onNodeWithText(str(R.string.library_empty_title)).assertIsDisplayed()
    }

    @Test
    fun `the film shelf shows its rows, searches and asks about a film`() {
        showFilms()
        rule.waitUntil { rule.onAllNodesWithText("Gravity").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.continue_count, 2)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.recent_title)).assertIsDisplayed()

        // Every action stays in one row beside the primary playback button.
        text("Dune").performTouchInput { longClick() }
        val playBounds = rule.onNodeWithText(str(R.string.title_resume)).fetchSemanticsNode().boundsInRoot
        for (label in listOf(R.string.title_restart, R.string.resume_clear, R.string.mark_watched)) {
            val toolBounds = rule.onNodeWithContentDescription(str(label)).fetchSemanticsNode().boundsInRoot
            assertTrue("action is beside playback", toolBounds.left >= playBounds.right)
            assertEquals(playBounds.center.y, toolBounds.center.y, 1f)
        }
        rule.onNodeWithContentDescription(str(R.string.title_restart)).performClick()
        settle()
        assertTrue(said(str(R.string.mark_watched)))
        rule.onNodeWithText(str(R.string.title_resume)).performClick()
        settle()
        text("Dune").performTouchInput { longClick() }
        rule.onNodeWithContentDescription(str(R.string.mark_watched)).performClick()
        rule.waitUntil { said(str(R.string.mark_unwatched)) }
        assertTrue(!said(str(R.string.mark_watched)))
        rule.onNodeWithContentDescription(str(R.string.mark_unwatched)).performClick()
        rule.waitUntil { said(str(R.string.mark_watched)) }
        rule.onNodeWithContentDescription(str(R.string.resume_clear)).performClick()
        rule.waitUntil { !said(str(R.string.resume_clear)) }
        assertTrue(has(str(R.string.title_play)))
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        text("Heat").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_play)).assertIsDisplayed()
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        // From the continue row.
        rule.onAllNodesWithText("Arrival").onFirst().performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_resume)).performClick()
        settle()
        assertTrue(fake.api.calls.containsAll(listOf("play", "setWatched", "clearResume")))

        // The search narrows the wall, and is cleared again.
        rule.onNode(hasSetTextAction()).performTextInput("19")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.onNodeWithText(str(R.string.library_all, 1)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.onNodeWithText(str(R.string.library_no_match)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.library_search_clear)).performClick()

        // Each card folds away and comes back.
        for (title in listOf(
            str(R.string.continue_count, 2), str(R.string.recent_title),
            str(R.string.library_unseen, 4), str(R.string.library_all, 5),
        )) {
            rule.onNodeWithText(title).performClick()
            rule.onNodeWithText(title).performClick()
        }
    }

    @Test
    fun `a pull reads the shelf again and a failure is said`() {
        showFilms()
        rule.waitUntil { rule.onAllNodesWithText("Gravity").fetchSemanticsNodes().isNotEmpty() }
        fake.api.library = { throw ApiFailure.Api(500, "shelf broke") }
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeDown() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("shelf broke").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(10_000)
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }
    }

    @Test
    fun `a pressed film waits for the box, then gives the wall back`() {
        val gate = CompletableDeferred<Unit>()
        fake.api.play = { gate.await(); com.jamal2367.tinyppimobile.data.model.CommandAck(ok = true) }
        showFilms()
        rule.waitUntil { rule.onAllNodesWithText("Gravity").fetchSemanticsNodes().isNotEmpty() }
        text("Gravity").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        gate.complete(Unit)
        rule.mainClock.advanceTimeBy(7_000)
        rule.waitForIdle()
        assertEquals(null, fake.viewModel.library.value.starting)
    }

    @Test
    fun `the film cards are arranged, hidden and brought back`() {
        val cards = CardState()
        showFilms(cards)
        rule.waitUntil { rule.onAllNodesWithText("Gravity").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.recent_title)).performTouchInput { longClick() }
        assertEquals("films", cards.editing)
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_down)).onFirst().performClick()
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_up)).onFirst().performClick()
        repeat(4) { rule.onAllNodesWithContentDescription(str(R.string.cards_hide)).onFirst().performClick() }
        rule.onAllNodesWithContentDescription(str(R.string.cards_show)).onFirst().performClick()
        rule.onNodeWithText(str(R.string.cards_reset)).performClick()
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        assertEquals(listOf("films"), cards.resets)

        cards.hidden = setOf("films/shelf.films.continue", "films/shelf.films.recent", "films/shelf.films.unseen", "films/shelf.films")
        rule.onNodeWithText(str(R.string.cards_all_hidden)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cards_edit)).performClick()
        assertEquals("films", cards.editing)
    }

    @Test
    fun `the series shelf opens a show into its seasons and plays an episode`() {
        showSeries()
        rule.waitUntil { rule.onAllNodesWithText("Chernobyl").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.series_unseen_all, 2)).assertIsDisplayed()

        text("Dark").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_open)).performClick()
        rule.waitUntil { said(str(R.string.series_back)) }
        rule.onNodeWithText(str(R.string.series_specials)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        text("S01E02").performTouchInput { longClick() }
        rule.onNodeWithContentDescription(str(R.string.title_restart)).performClick()
        settle()
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        text("Loose").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.waitForIdle()
        assertEquals(2, fake.api.count("playEpisode"))
        rule.onNodeWithContentDescription(str(R.string.series_back)).performClick()
        // Its way out goes back to the show's details it was opened over.
        rule.waitUntil { has(str(R.string.title_open)) }
        assertTrue(!said(str(R.string.series_back)))
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodes(isRoot()).fetchSemanticsNodes().size)

        // A show from the unseen row, a search, and the continue row.
        rule.waitUntil { rule.onAllNodesWithText("Silo").fetchSemanticsNodes().isNotEmpty() }
        text("Silo").performTouchInput { longClick() }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        text("Dark").performTouchInput { longClick() }
        rule.onNodeWithContentDescription(str(R.string.mark_watched)).performClick()
        rule.waitUntil { said(str(R.string.mark_unwatched)) }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.onNodeWithText(str(R.string.series_no_match)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.library_search_clear)).performClick()
        for (title in listOf(
            str(R.string.continue_count, 2), str(R.string.recent_title),
            str(R.string.series_unseen_all, 2), str(R.string.series_all, 3),
        )) {
            rule.onNodeWithText(title).performClick()
            rule.onNodeWithText(title).performClick()
        }
        rule.onAllNodesWithText("Untitled").onFirst().performClick()
        rule.onNodeWithText(str(R.string.title_resume)).performClick()
        settle()
    }

    @Test
    fun `an episode tap opens its details and playback closes them`() {
        fake.api.details = { kind, id ->
            assertEquals("episode", kind)
            assertEquals(72, id)
            TitleDetails(kind = kind, id = id, plot = "Episode details.")
        }
        showSeries()
        rule.waitUntil { has("Dark") }
        text("Dark").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.title_open)).performClick()
        rule.waitUntil { said(str(R.string.series_back)) }
        // The seasons are the show's details page turned over, in the same
        // window over the shelf: no window closes and none opens for them.
        assertEquals(2, rule.onAllNodes(isRoot()).fetchSemanticsNodes().size)
        assertEquals(1, fake.api.count("details"))
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()

        text("S01E02").performClick()
        rule.waitUntil { has("Episode details.") }
        assertTrue(!has(str(R.string.mark_cancel)))
        assertEquals(0, fake.api.count("playEpisode"))
        rule.onNodeWithText(str(R.string.title_resume)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.title_restart)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.title_resume)).performClick()
        settle()
        assertTrue(!has("Episode details."))
        assertTrue(said(str(R.string.series_back)))
        assertEquals(1, fake.api.count("playEpisode"))

        // A pull down past the top of the seasons closes them, back to the
        // show's details.
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitUntil { has(str(R.string.title_open)) }
        assertTrue(!said(str(R.string.series_back)))
        // The show's page was there all along: nothing was read again for it.
        assertEquals(2, fake.api.count("details"))
    }

    @Test
    fun `the series cards are arranged and a pull reads the shelf again`() {
        val cards = CardState()
        showSeries(cards)
        rule.waitUntil { rule.onAllNodesWithText("Chernobyl").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.recent_title)).performTouchInput { longClick() }
        assertEquals("series", cards.editing)
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        cards.hidden = setOf("series/shelf.series.continue", "series/shelf.series.next", "series/shelf.series.recent", "series/shelf.series.unseen", "series/shelf.series")
        rule.onNodeWithText(str(R.string.cards_all_hidden)).assertIsDisplayed()
        cards.hidden = emptySet()
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue(fake.api.count("series") >= 2)
    }

    @Test
    fun `a shelf with every show seen has no unseen row`() {
        fake.api.series = { SeriesLibrary(shows = shows.filter { it.watched }, count = 1) }
        showSeries()
        rule.waitUntil { rule.onAllNodesWithText("Chernobyl").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText(str(R.string.series_unseen_all, 0)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `with posters off and nothing to control the shelves only list`() {
        fake.settings.value = fake.settings.value.copy(showArtwork = false)
        fake.live.value = LiveState(connection = LiveState.Connection.Streaming, snapshot = Snapshot(control = false), server = null)
        showSeries()
        rule.onNodeWithText(str(R.string.library_empty_title)).assertIsDisplayed()
    }

    private fun has(value: String) = rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the film shelf sorts and filters by what the films carry`() {
        fake.api.continuing = { ContinueList() }
        fake.api.library = {
            Library(
                movies = listOf(
                    LibraryFilm(1, "Dune", 2024, genres = listOf("Sci-Fi"), video = "dv", dvProfile = "8.1", resolution = "2160p", audio = "truehd"),
                    LibraryFilm(2, "Heat", 1995, genres = listOf("Crime"), video = "sdr", resolution = "1080p", audio = "dts"),
                    LibraryFilm(3, "Alien", 1979, genres = listOf("Sci-Fi", "Horror"), video = "dv", dvProfile = "5", resolution = "2160p", audio = "weird"),
                ),
            )
        }
        val cards = CardState()
        showFilms(cards)
        rule.waitUntil { has("Heat") }
        rule.onNodeWithContentDescription(str(R.string.shelf_sort, str(R.string.sort_title))).performClick()
        text(str(R.string.sort_year)).performClick()
        rule.waitUntil {
            rule.onAllNodesWithContentDescription(str(R.string.shelf_sort, str(R.string.sort_year))).fetchSemanticsNodes().isNotEmpty()
        }
        // The newest first, and kept for the next launch; a press turns it round.
        assertEquals("YEAR:down", cards.shelfOrders["films"])
        text(str(R.string.sort_descending)).performClick()
        rule.waitUntil { has(str(R.string.sort_ascending)) }
        assertEquals("YEAR", cards.shelfOrders["films"])

        text(str(R.string.filter_genre)).performClick()
        text("Crime").performClick()
        rule.waitUntil { !has("Dune") }
        assertTrue(has("Heat"))
        text("Crime").performClick()
        text(str(R.string.filter_genre_all)).performClick()
        rule.waitUntil { has("Dune") }

        text(str(R.string.filter_video)).performClick()
        text("SDR").performClick()
        rule.waitUntil { !has("Alien") }
        // Each Dolby Vision profile on the shelf is a choice of its own.
        text("SDR").performClick()
        text("Dolby Vision P5").performClick()
        rule.waitUntil { has("Alien") && !has("Dune") }
        text("Dolby Vision P5").performClick()
        text(str(R.string.filter_video_all)).performClick()
        text(str(R.string.filter_resolution)).performScrollTo().performClick()
        text("4K").performClick()
        rule.waitUntil { !has("Heat") }
        text("4K").performClick()
        text(str(R.string.filter_resolution_all)).performClick()
        // Only the sound the app has a name for counts as a choice; one choice is no filter.
        text(str(R.string.filter_audio)).performScrollTo().performClick()
        text("DTS").performClick()
        rule.waitUntil { !has("Dune") && has("Heat") }
        // One press puts every filter back.
        text(str(R.string.filter_reset)).performScrollTo().performClick()
        rule.waitUntil { has("Dune") && has(str(R.string.filter_audio)) }
    }

    @Test
    fun `the series shelf sorts and filters by genre, and offers what comes next`() {
        fake.api.series = {
            SeriesLibrary(shows = shows.mapIndexed { index, show -> show.copy(genres = listOf(listOf("Drama", "History", "Sci-Fi")[index])) })
        }
        fake.api.continuing = {
            ContinueList(next = listOf(ContinueItem("episode", 76, "Ghosts", poster = "s7", duration = 3000, show = "Dark", season = 2, episode = 1)))
        }
        // An order a series cannot be put in, kept by some other build, is the shelf's own.
        showSeries(CardState().apply { shelfOrders = mapOf("series" to "RUNTIME:down") })
        rule.waitUntil { has("Chernobyl") }
        assertTrue(has(str(R.string.sort_ascending)))
        rule.onNodeWithContentDescription(str(R.string.shelf_sort, str(R.string.sort_title))).performClick()
        // A series has no running time to be put in order by.
        assertTrue(!has(str(R.string.sort_runtime)))
        text(str(R.string.sort_rating)).performClick()
        text(str(R.string.filter_genre)).performClick()
        text("History").performClick()
        rule.waitUntil { !has("Silo") }
        text(str(R.string.filter_reset)).performScrollTo().performClick()
        rule.waitUntil { has("Silo") }
        text(str(R.string.filter_genre)).performClick()
        text("History").performClick()
        rule.waitUntil { !has("Silo") }

        rule.onNodeWithText(str(R.string.next_up_count, 1)).assertIsDisplayed()
        // The show's tile on the next-up row, ahead of its poster among what arrived last.
        rule.onAllNodesWithText("Dark").onFirst().performClick()
        // Nothing of it has been seen: play, without the two resume answers.
        assertTrue(!said(str(R.string.title_restart)))
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.waitForIdle()
        assertEquals(1, fake.api.count("playEpisode"))
        settle()
        text(str(R.string.next_up_count, 1)).performClick()
        text(str(R.string.next_up_count, 1)).performClick()
    }

    @Test
    fun `the shelves keep still while what holds them is drawn again`() {
        var pass by mutableIntStateOf(0)
        rule.show(CardState()) {
            // Read here, so a change runs the caller again with the same screens in it.
            Text("pass $pass")
            FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel)
            SeriesScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
        rule.waitForIdle()
        pass++
        rule.waitUntil { has("pass 1") }
    }

    private fun said(description: String) =
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a title's details stand on a page of their own, and a failure says so`() {
        fake.api.details = { kind, id ->
            TitleDetails(
                kind = kind, id = id, title = "Heat", plot = "A heist.", tagline = "A Los Angeles crime saga",
                year = 1995, duration = 10200, mpaa = "Rated R", age = "mpa_r", ageLevel = "R", genres = listOf("Crime"),
                directors = listOf("Michael Mann"), writers = listOf("M. Mann"), studios = listOf("Warner"),
                cast = listOf(CastMember("Al Pacino", "Vincent"), CastMember("Val Kilmer", "")), castMore = true,
                ratings = listOf(SourceRating("imdb", 8.3)), userrating = 9, top250 = 120,
                poster = "p2", fanart = "f2", video = "dv", dvProfile = "7 FEL", resolution = "2160p", audio = "weird",
            )
        }
        showFilms()
        rule.waitUntil { has("Heat") }
        text("Heat").performClick()
        rule.waitUntil { has("A heist.") }
        // The screen moving under the open page leaves it as it is.
        fake.playing(Snapshot(control = true, playing = false, libraryRevision = 3, title = "Something else"))
        rule.waitForIdle()
        for (shown in listOf(
            "1995 · 2 h 50 min", "Dolby Vision P7 FEL", "4K", "weird", "A Los Angeles crime saga",
            "Crime", "Michael Mann", "M. Mann", "Warner", "Al Pacino", "Vincent", "Val Kilmer", "\u2026",
        )) {
            assertTrue(shown, has(shown))
        }
        // The ratings as the overview card's shields, the age rating first.
        for (spoken in listOf("Rated R", "IMDb 8.3", "${str(R.string.live_rating_mine)} 9", str(R.string.live_rating_top250, 120))) {
            assertTrue(spoken, said(spoken))
        }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue(!has("A heist."))

        fake.api.details = { _, _ -> throw ApiFailure.Api(404, "no such title") }
        text("Gravity").performClick()
        rule.waitUntil { has(str(R.string.details_failed)) }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue(!has(str(R.string.details_failed)))
    }

    @Test
    fun `a series with little to say, and an episode under its still`() {
        fake.api.details = { kind, id ->
            if (kind == "tvshow") TitleDetails(kind = kind, id = id, title = "Chernobyl")
            else TitleDetails(kind = kind, id = id, title = "Ghosts", thumb = "t76", plot = "It begins.")
        }
        fake.api.continuing = {
            ContinueList(next = listOf(ContinueItem("episode", 76, "Ghosts", poster = "s7", duration = 3000, show = "Dark", season = 2, episode = 1)))
        }
        showSeries()
        rule.waitUntil { has("Chernobyl") }
        text("Chernobyl").performClick()
        rule.waitForIdle()
        assertTrue(!has(str(R.string.details_genre)) && !has(str(R.string.live_credit_cast)))
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.onAllNodesWithText("Dark").onFirst().performClick()
        rule.waitUntil { has("It begins.") }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        assertEquals(2, fake.api.count("details"))
    }

    @Test
    fun `the details page plays, resumes or starts over, and opens a series`() {
        fake.api.details = { kind, id -> TitleDetails(kind = kind, id = id, title = "Dune", plot = "Spice.") }
        showFilms()
        rule.waitUntil { has("Dune") }
        // A film with a point to resume from: resume, or start over.
        text("Dune").performClick()
        rule.waitUntil { has("Spice.") }
        rule.onAllNodesWithContentDescription(str(R.string.title_restart)).onLast().performClick()
        settle()
        assertTrue(has("Spice."))
        rule.onAllNodesWithText(str(R.string.title_resume)).onLast().performClick()
        settle()
        assertEquals(listOf(false, null), fake.api.plays.map { it.resume })
        // A film not begun: only play.
        text("Heat").performClick()
        rule.waitUntil { has("Spice.") }
        assertTrue(!said(str(R.string.title_restart)))
        rule.onAllNodesWithText(str(R.string.title_play)).onLast().performClick()
        settle()
        assertEquals(3, fake.api.plays.size)
    }

    @Test
    fun `status changes keep details open and only switch the mark after success`() {
        fake.api.details = { kind, id ->
            TitleDetails(kind = kind, id = id, title = "Dune", watched = false, resume = 600)
        }
        val gate = CompletableDeferred<Unit>()
        fake.api.setWatched = {
            gate.await()
            com.jamal2367.tinyppimobile.data.model.CommandAck(ok = true)
        }
        showFilms()
        rule.waitUntil { has("Dune") }
        text("Dune").performClick()
        rule.waitUntil { fake.viewModel.details.value?.details != null }
        rule.onNodeWithContentDescription(str(R.string.mark_watched)).performClick()
        rule.waitUntil { fake.viewModel.details.value?.busy == true }
        rule.onNodeWithContentDescription(str(R.string.mark_watched)).assertIsNotEnabled()
        fake.viewModel.setWatched(fake.viewModel.details.value!!.target, true)
        gate.complete(Unit)
        rule.waitUntil { said(str(R.string.mark_unwatched)) }
        assertEquals(1, fake.api.count("setWatched"))
        assertTrue(!said(str(R.string.mark_watched)))

        fake.api.setWatched = { throw ApiFailure.Api(500, "mark failed") }
        rule.onNodeWithContentDescription(str(R.string.mark_unwatched)).performClick()
        rule.waitUntil { has("mark failed") }
        assertTrue(said(str(R.string.mark_unwatched)))
        assertEquals(true, fake.viewModel.details.value?.details?.watched)

        rule.onNodeWithContentDescription(str(R.string.resume_clear)).performClick()
        rule.waitUntil { !said(str(R.string.resume_clear)) }
        assertTrue(has(str(R.string.title_play)))
        assertEquals(null, fake.viewModel.details.value?.actionError)
        assertEquals(0, fake.viewModel.details.value?.details?.resume)
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.waitUntil { fake.viewModel.details.value == null }
    }

    @Test
    fun `pulling down at the top closes details`() {
        fake.api.details = { kind, id -> TitleDetails(kind = kind, id = id, plot = "Swipe details.") }
        showFilms()
        rule.waitUntil { has("Heat") }
        text("Heat").performClick()
        rule.waitUntil { has("Swipe details.") }
        assertTrue(!said(str(R.string.details_close)))
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitUntil { !has("Swipe details.") }
    }

    @Test
    fun `an episode to go on with offers the way to its show, and a film or a lost show none`() {
        fake.api.continuing = {
            ContinueList(items = listOf(
                ContinueItem("episode", 72, "Lies", duration = 3120, resume = 600, show = "Dark", tvshowid = 7, season = 1, episode = 2),
                ContinueItem("episode", 76, "Gone", duration = 3120, resume = 600, show = "Lost", tvshowid = 99, season = 4, episode = 8),
            ))
        }
        showSeries()
        rule.waitUntil { rule.onAllNodesWithText("S01E02", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("S01E02", substring = true).onFirst().performClick()
        rule.waitUntil { said(str(R.string.title_show)) }
        rule.onNodeWithContentDescription(str(R.string.title_show)).performClick()
        // The episode's page gives way to the show's, where its seasons open.
        rule.waitUntil { has(str(R.string.title_open)) }
        assertTrue(!said(str(R.string.title_show)))
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        rule.waitForIdle()

        rule.onAllNodesWithText("S04E08", substring = true).onFirst().performClick()
        rule.waitUntil { has(str(R.string.title_resume)) }
        assertTrue(!said(str(R.string.title_show)))
    }

    @Test
    fun `a details page slides out when it is closed rather than vanishing`() {
        fake.api.details = { kind, id -> TitleDetails(kind = kind, id = id, plot = "Sliding.") }
        showFilms()
        rule.waitUntil { has("Heat") }
        text("Heat").performClick()
        rule.waitUntil { has("Sliding.") }
        rule.mainClock.autoAdvance = false
        fake.viewModel.closeDetails()
        rule.mainClock.advanceTimeBy(48)
        // Still on the way out.
        assertTrue(has("Sliding."))
        rule.mainClock.autoAdvance = true
        rule.waitUntil { !has("Sliding.") }
        assertEquals(1, rule.onAllNodes(isRoot()).fetchSemanticsNodes().size)
    }

    @Test
    fun `a series' details page opens it, and stays under its seasons until they are left`() {
        fake.api.details = { kind, id -> TitleDetails(kind = kind, id = id, title = "Chernobyl", plot = "1986.") }
        showSeries()
        rule.waitUntil { has("Chernobyl") }
        text("Chernobyl").performClick()
        rule.waitUntil { has("1986.") }
        rule.onAllNodesWithText(str(R.string.title_open)).onLast().performClick()
        rule.waitUntil { said(str(R.string.series_back)) }
        // The page has slid over to the seasons, in the same window.
        assertTrue(!has("1986."))
        assertEquals(2, rule.onAllNodes(isRoot()).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription(str(R.string.series_back)).performClick()
        rule.waitForIdle()
        // And back to the very page it came from, read once and only once.
        assertTrue(!said(str(R.string.series_back)))
        assertTrue(has("1986."))
        assertEquals(1, fake.api.count("details"))
    }

    @Test
    fun `the seasons slide in while their episodes are read, and leaving then opens nothing later`() {
        val gate = CompletableDeferred<EpisodeList>()
        fake.api.episodes = { gate.await() }
        fake.api.details = { kind, id -> TitleDetails(kind = kind, id = id, title = "Chernobyl", plot = "1986.") }
        showSeries()
        rule.waitUntil { has("Chernobyl") }
        text("Chernobyl").performClick()
        rule.waitUntil { has("1986.") }
        rule.onAllNodesWithText(str(R.string.title_open)).onLast().performClick()
        // Already there, waiting for its episodes.
        rule.waitUntil { said(str(R.string.series_back)) }
        assertTrue(!has("1986."))
        rule.onNodeWithContentDescription(str(R.string.series_back)).performClick()
        rule.waitUntil { has("1986.") }
        // The read that was still out comes to nothing.
        gate.complete(EpisodeList(episodes = episodes))
        rule.waitForIdle()
        assertTrue(!said(str(R.string.series_back)))
        assertEquals(null, fake.viewModel.series.value.open)
    }
}
