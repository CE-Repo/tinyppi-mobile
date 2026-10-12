package com.jamal2367.tinyppimobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.library.FilmsScreen
import com.jamal2367.tinyppimobile.ui.library.SeriesScreen
import com.jamal2367.tinyppimobile.ui.live.LiveScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsViewModel
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The corners of the screens the main tours leave out. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h6000dp")
class ScreenExtrasTest {

    @get:Rule val rule = createComposeRule()

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Before fun reset() {
        Robo.clearSettings(app)
    }

    @Test
    fun `settings under an accent, with odd ports and tabs brought back`() {
        Robo.settings(app) { setTabHidden("films", true); setAdaptiveColor(false) }
        val vm = SettingsViewModel(app)
        rule.show { ArtworkAccentTheme(accent = Color(0xFF00897B)) { SettingsScreen(vm) } }
        rule.until { vm.state.value.settings.hiddenTabs.isNotEmpty() }

        val port = rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst()
        port.performTextClearance()
        rule.onAllNodesWithText(str(R.string.settings_unsaved)).fetchSemanticsNodes()
        port.performTextInput("99999")
        rule.waitForIdle()

        rule.onNodeWithText(str(R.string.library_title)).performClick()
        rule.until { "films" !in app.container.router.settings.hiddenTabs }
        rule.onNodeWithText(str(R.string.settings_adaptive_color)).performClick()
        rule.until { app.container.router.settings.adaptiveColor }
        rule.onNodeWithText(str(R.string.settings_artwork)).performClick()
        rule.until { !app.container.router.settings.showArtwork }

        // The strength set from elsewhere moves the slider with it.
        Robo.settings(app) { setShowArtwork(true); setAdaptiveColorIntensity(0.7f) }
        rule.until { seen(str(R.string.settings_adaptive_color_intensity_value, 70)) }
    }

    @Test
    fun `shelves without posters, and a show that is opening`() {
        val fake = FakeLive()
        fake.settings.value = fake.settings.value.copy(showArtwork = false)
        fake.playing(Snapshot(control = true, libraryRevision = 1))
        fake.api.library = { Library(movies = listOf(LibraryFilm(1, "Heat", 1995, resume = 5, duration = 10, added = "2026-01-01"))) }
        fake.api.series = { SeriesLibrary(shows = listOf(LibraryShow(7, "Dark", 2017, unseen = 2, added = "2026-01-01"), LibraryShow(8, "Silo", 0, watched = true, unseen = 3))) }
        fake.api.continuing = {
            ContinueList(items = listOf(
                ContinueItem("movie", 1, "Heat", resume = 5, duration = 10),
                ContinueItem("episode", 9, "Pilot", show = "Dark", season = -1, episode = -1, resume = 5, duration = 10),
            ))
        }
        val gate = CompletableDeferred<EpisodeList>()
        fake.api.episodes = { gate.await() }
        var screen by mutableStateOf(0)
        rule.show {
            if (screen == 0) FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel)
            else SeriesScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
        rule.until { seen(str(R.string.library_unseen, 1)) }
        screen = 1
        rule.until { seen("Silo") }
        rule.onAllNodesWithText("Dark").onLast().performClick()
        rule.onNodeWithText(str(R.string.title_open)).performClick()
        rule.waitForIdle()
        assertEquals(7, fake.viewModel.series.value.opening)
        gate.complete(EpisodeList(episodes = listOf(LibraryEpisode(71, "Lies", season = 1, episode = 1, duration = 60))))
        rule.until { seen(str(R.string.series_back)) }
        rule.onNodeWithText(str(R.string.series_season, 1)).performClick()
        rule.until { seen("Lies") }
        rule.onAllNodesWithText("Pilot").fetchSemanticsNodes()
    }

    @Test
    fun `the live screen with a new view model, and its card pressed long from a screen reader`() {
        val first = FakeLive().apply { playing() }
        val second = FakeLive().apply { playing() }
        val cards = CardState().apply { folds = setOf("live.controls") }
        var current by mutableStateOf(first)
        rule.show(cards) { LiveScreen(onOpenSettings = {}, viewModel = current.viewModel) }
        rule.until { seen("Dune: Part Two") }
        current = second
        rule.waitForIdle()
        second.playing(Screens.film.copy(controls = Screens.film.controls.copy(volume = 50)))
        rule.waitForIdle()

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and hasAnyDescendant(hasText("Dune: Part Two")))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals("live", cards.editing)
        cards.editing = null
        rule.waitForIdle()

        // Setting the bar to where it already is moves nothing.
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        rule.waitForIdle()
        assertTrue(second.api.calls.count { it == "command:seek_percent" } <= 1)
    }
}
