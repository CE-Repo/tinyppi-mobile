package com.jamal2367.tinyppimobile.ui.library

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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

        // A film from the wall, resumable: every option of the dialog.
        text("Dune").performClick()
        rule.onNodeWithText(str(R.string.title_restart)).performClick()
        settle()
        text("Dune").performClick()
        rule.onNodeWithText(str(R.string.title_resume)).performClick()
        settle()
        text("Dune").performClick()
        rule.onNodeWithText(str(R.string.mark_watched)).performClick()
        text("Dune").performClick()
        rule.onNodeWithText(str(R.string.mark_unwatched)).performClick()
        text("Dune").performClick()
        rule.onNodeWithText(str(R.string.resume_clear)).performClick()
        text("Heat").performClick()
        rule.onNodeWithText(str(R.string.title_play)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mark_cancel)).performClick()
        // From the continue row.
        rule.onAllNodesWithText("Arrival").onFirst().performClick()
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
        text("Gravity").performClick()
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

        text("Dark").performClick()
        rule.onNodeWithText(str(R.string.title_open)).performClick()
        rule.waitUntil { rule.onAllNodesWithText(str(R.string.series_back)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.series_specials)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        text("S01E02").performClick()
        rule.onNodeWithText(str(R.string.title_restart)).performClick()
        settle()
        text("Loose").performClick()
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.waitForIdle()
        assertEquals(2, fake.api.count("playEpisode"))
        rule.onNodeWithText(str(R.string.series_back)).performClick()

        // A show from the unseen row, a search, and the continue row.
        rule.waitUntil { rule.onAllNodesWithText("Silo").fetchSemanticsNodes().isNotEmpty() }
        text("Silo").performClick()
        rule.onNodeWithText(str(R.string.mark_cancel)).performClick()
        text("Dark").performClick()
        rule.onNodeWithText(str(R.string.mark_watched)).performClick()
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
    fun `the series cards are arranged and a pull reads the shelf again`() {
        val cards = CardState()
        showSeries(cards)
        rule.waitUntil { rule.onAllNodesWithText("Chernobyl").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.recent_title)).performTouchInput { longClick() }
        assertEquals("series", cards.editing)
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        cards.hidden = setOf("series/shelf.series.continue", "series/shelf.series.recent", "series/shelf.series.unseen", "series/shelf.series")
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
}
