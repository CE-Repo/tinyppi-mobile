package com.jamal2367.tinyppimobile.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.InfoRowData
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeBox
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.components.ChartSeries
import com.jamal2367.tinyppimobile.ui.components.GroupCard
import com.jamal2367.tinyppimobile.ui.components.LuminanceChart
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatusLine
import com.jamal2367.tinyppimobile.ui.components.centredBelowTitle
import com.jamal2367.tinyppimobile.ui.history.HistoryScreen
import com.jamal2367.tinyppimobile.ui.library.FilmsScreen
import com.jamal2367.tinyppimobile.ui.library.SeriesScreen
import com.jamal2367.tinyppimobile.ui.metadata.MetadataScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsViewModel
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The last corners: odd data, back while arranging, screens finding their own view models. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h6000dp")
class RemainingCasesTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Before fun reset() {
        Robo.clearSettings(app)
    }

    private fun show(cards: CardState = CardState(), content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { TinyPpiTheme { cards.Provide(content) } }

    @Test
    fun `components with the data the screens rarely send`() {
        show {
            Column {
                StatusLine(LiveState.Connection.NotConfigured, serverLabel = "box:1")
                SectionCard(title = null, foldId = "untitled") { Text("x") }
                GroupCard(InfoGroup(id = "g", title = "G", rows = listOf(InfoRowData(label = "L1 PQ max", value = "3000"))))
                LuminanceChart(series = listOf(ChartSeries("none", Color.Red, emptyList())), windowSeconds = null)
                LuminanceChart(
                    series = listOf(
                        ChartSeries("a", Color.Red, listOf(10.0 to 5.0, 0.0 to 20.0, 5.0 to 3.0, 12.0 to 1.0)),
                        ChartSeries("b", Color.Blue, listOf(1.0 to 2.0)),
                    ),
                    windowSeconds = 6,
                )
                LuminanceChart(series = listOf(ChartSeries("c", Color.Red, listOf(3.0 to 2.0, 1.0 to 9.0))), windowSeconds = 60)
                // A column too short for its contents, and one with only its head.
                Column(Modifier.height(60.dp), verticalArrangement = centredBelowTitle(4.dp)) {
                    ScreenTitle("Over")
                    repeat(4) { Text("row $it", Modifier.height(40.dp)) }
                }
                Column(Modifier.height(60.dp), verticalArrangement = centredBelowTitle(4.dp)) { ScreenTitle("Alone") }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `back while arranging ends the arranging`() {
        val fake = FakeLive()
        fake.playing()
        val cards = CardState()
        show(cards) { com.jamal2367.tinyppimobile.ui.live.LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel) }
        rule.onNodeWithText("Dune: Part Two").performTouchInput { longClick() }
        assertEquals("live", cards.editing)
        rule.until { seen(str(R.string.cards_done)) }
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { cards.editing == null }
    }

    @Test
    fun `shelves of watched films, empty rows, and shows waiting with nothing unseen`() {
        val fake = FakeLive()
        fake.playing(Snapshot(control = true, libraryRevision = 1))
        fake.api.library = { Library(movies = listOf(LibraryFilm(1, "Heat", 1995, watched = true), LibraryFilm(2, "Alien", 1979, watched = true))) }
        fake.api.series = { SeriesLibrary(shows = listOf(LibraryShow(7, "Dark", 2017, unseen = 0), LibraryShow(8, "Silo", 2023, unseen = 2, added = "2026-01-02"), LibraryShow(9, "Andor", 2022, unseen = 1, added = "2026-01-01"))) }
        fake.api.continuing = { ContinueList() }
        var screen by mutableStateOf(0)
        show {
            if (screen == 0) FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel)
            else SeriesScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
        rule.until { seen("Alien") }
        screen = 1
        rule.until { seen("Silo") }
        // A search that leaves only a show with nothing unseen.
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Dark")
        rule.until { !seen("Silo") }
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextClearance()
        // One show opening while the rows show another.
        val gate = kotlinx.coroutines.CompletableDeferred<com.jamal2367.tinyppimobile.data.model.EpisodeList>()
        fake.api.episodes = { gate.await() }
        rule.onAllNodesWithText("Andor").onLast().performClick()
        rule.onNodeWithText(str(R.string.title_open)).performClick()
        rule.waitForIdle()
        gate.complete(com.jamal2367.tinyppimobile.data.model.EpisodeList())
        rule.waitForIdle()
    }

    @Test
    fun `history and metadata find their own view model, and draw what they are given`() {
        FakeBox().use { box ->
            box.stream = listOf("""{"seq":1,"playing":true,"title":" ","control":true,"session":{"seq":2}}""")
            box.answers["/api/history"] = """{"now":5,"t":[0,1],"max":[null,null],"avg":[1,2],"events":[]}"""
            Robo.settings(app) { setPrimary(box.config) }
            rule.until { app.container.router.settings.primary == box.config }
            val fake = FakeLive().apply {
                playing(Screens.film.copy(metadata = listOf(
                    MetadataRow(kind = "wide", name = "", value = "lonely"),
                    MetadataRow(kind = "headings", name = "No cells"),
                )))
            }
            var which by mutableStateOf(0)
            val cards = CardState()
            val idle = com.jamal2367.tinyppimobile.ui.history.HistoryViewModel(app)
            val history = com.jamal2367.tinyppimobile.ui.history.HistoryViewModel(app)
            show(cards) {
                when (which) {
                    0 -> HistoryScreen(onOpenSettings = {}, viewModel = history)
                    1 -> MetadataScreen(onOpenSettings = {}, viewModel = fake.viewModel, historyViewModel = history)
                    else -> HistoryScreen(onOpenSettings = {}, viewModel = idle)
                }
            }
            rule.until { seen(str(R.string.history_summary)) }
            // A change of setting recomposes the screen from its own state.
            Robo.settings(app) { setChartRange(com.jamal2367.tinyppimobile.data.prefs.ChartRange.ALL) }
            rule.waitForIdle()
            which = 1
            rule.until { seen("lonely") }
            cards.hidden = setOf("metadata/history.chart", "metadata/metadata.section0")
            rule.until { seen(str(R.string.cards_all_hidden)) }
            Robo.settings(app) { setChartRange(com.jamal2367.tinyppimobile.data.prefs.ChartRange.ONE_MINUTE) }
            rule.waitForIdle()
            which = 2
            rule.waitForIdle()
        }
    }

    @Test
    fun `settings found by the screen itself, with every odd port`() {
        // Two view models over the one store: the screen drawn by one, then the other.
        val first = SettingsViewModel(app)
        val second = SettingsViewModel(app)
        var current by mutableStateOf(first)
        show { SettingsScreen(current) }
        rule.until { first.state.value.loaded }
        current = second
        rule.until { second.state.value.loaded }
        rule.until { seen(str(R.string.settings_about)) || seen(str(R.string.settings_servers_hint)) }
        val port = rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst()
        for (typed in listOf("0", "70000", "")) {
            port.performTextClearance()
            if (typed.isNotEmpty()) port.performTextInput(typed)
            rule.waitForIdle()
        }
        // Changed from elsewhere: the screen recomposes from its own state.
        Robo.settings(app) { setAdaptiveColor(false) }
        rule.until { !app.container.router.settings.adaptiveColor }
        Robo.settings(app) { setAdaptiveColor(true); setShowArtwork(false) }
        rule.until { !app.container.router.settings.showArtwork }
        Robo.settings(app) { setAdaptiveColorIntensity(0.55f) }
        rule.waitForIdle()
    }
}
