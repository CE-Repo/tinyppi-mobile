package com.jamal2367.tinyppimobile.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeBox
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.history.HistoryScreen
import com.jamal2367.tinyppimobile.ui.history.HistoryViewModel
import com.jamal2367.tinyppimobile.ui.metadata.MetadataScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The history and metadata screens, against a box answering what each case needs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class HistoryScreensTest {

    @get:Rule val rule = createComposeRule()

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private val box = FakeBox()

    private val playing = """{"seq":1,"playing":true,"title":"Dune","control":true,"session":{"seq":4,"switches":2,"warnings":3}}"""

    private val history = """{"now":905,"step":2,"t":[0,2,4,6],"max":[100,null,15000,300],"avg":[20,30,null,40],
        "events":[
          {"t":1,"pos":"0:00:01","kind":"vs10","from":"DV","to":"HDR10"},
          {"t":2,"pos":"","kind":"subtitle","from":"English","to":"__off__"},
          {"t":3,"pos":"0:00:03","kind":"audio","from":null,"to":null},
          {"t":4,"pos":"0:00:04","kind":"temperature","value":77.6},
          {"t":5,"pos":"0:00:05","kind":"temperature"},
          {"t":6,"pos":"0:00:06","kind":"cpu","value":93.2},
          {"t":7,"pos":"0:00:07","kind":"cpu"},
          {"t":8,"pos":"0:00:08","kind":"fps","value":23.976},
          {"t":9,"pos":"0:00:09","kind":"mode","from":"SDR","to":"DV"},
          {"t":10,"pos":"0:00:10","kind":"strange"},
          {"t":11,"pos":"0:00:11","kind":"cache_low"}
        ],"seq":4,"switches":2}"""

    @Before fun start() {
        Robo.clearSettings(app)
    }

    @After fun stop() = box.close()

    private fun configure(config: ServerConfig = box.config) {
        Robo.settings(app) { setPrimary(config) }
        rule.until { app.container.router.settings.primary == config }
    }

    @Test
    fun `nothing configured points at the settings`() {
        var opened = 0
        rule.show { HistoryScreen(onOpenSettings = { opened++ }, viewModel = HistoryViewModel(app)) }
        rule.onNodeWithText(str(R.string.action_open_settings)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a box with no history yet says so, and why`() {
        box.stream = listOf(playing)
        box.codes["/api/history"] = 500
        box.answers["/api/history"] = """{"error":"no history here"}"""
        configure()
        rule.show { HistoryScreen(onOpenSettings = {}, viewModel = HistoryViewModel(app)) }
        rule.until { rule.onAllNodesWithText("no history here").fetchSemanticsNodes().isNotEmpty() }
        box.codes.remove("/api/history")
        box.answers["/api/history"] = "{}"
        rule.until { rule.onAllNodesWithText(str(R.string.history_empty_text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a history shows its summary and every kind of event, and its cards are arranged`() {
        box.stream = listOf(playing)
        box.answers["/api/history"] = history
        configure()
        val cards = CardState()
        rule.show(cards) { HistoryScreen(onOpenSettings = {}, viewModel = HistoryViewModel(app)) }
        rule.until { rule.onAllNodesWithText(str(R.string.history_summary)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Dune").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.event_unknown)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.live_subtitles_off)).assertIsDisplayed()
        // The heartbeat asks again while the title plays.
        rule.mainClock.advanceTimeBy(2_500)

        rule.onNodeWithText(str(R.string.history_summary)).performTouchInput { longClick() }
        assertEquals("history", cards.editing)
        rule.until { rule.onAllNodesWithText(str(R.string.cards_done)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        cards.hidden = setOf("history/history.summary", "history/history.events")
        rule.until { rule.onAllNodesWithText(str(R.string.cards_all_hidden)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a history outlives the reading it was fetched for`() {
        box.stream = listOf(playing)
        box.answers["/api/history"] = history
        configure()
        val vm = HistoryViewModel(app)
        rule.show { HistoryScreen(onOpenSettings = {}, viewModel = vm) }
        rule.until { rule.onAllNodesWithText(str(R.string.history_summary)).fetchSemanticsNodes().isNotEmpty() }
        box.close()
        app.container.reconnect()
        rule.until(20_000) { vm.state.value.snapshot == null }
        rule.waitForIdle()
    }

    @Test
    fun `a history with neither peaks nor events still draws`() {
        box.stream = listOf("""{"seq":1,"playing":false,"last":{"title":"Heat"}}""")
        box.answers["/api/history"] = """{"now":10,"t":[1],"max":[],"avg":[],"events":[],"switches":0}"""
        configure()
        rule.show { HistoryScreen(onOpenSettings = {}, viewModel = HistoryViewModel(app)) }
        rule.until { rule.onAllNodesWithText(str(R.string.history_events_empty)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the metadata screen lists its sections and draws the chart`() {
        box.stream = listOf(playing)
        box.answers["/api/history"] = history
        configure()
        val fake = FakeLive()
        fake.playing(
            Screens.film.copy(
                metadata = listOf(
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "row", name = "Loose", value = ""),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "section", name = "", value = "Level 2"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "headings", name = "Target", cells = listOf("100", "600", "1000", "")),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "columns", name = "Slope", cells = listOf("2.1k", "")),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "space"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "wide", name = "Note", value = "trimmed"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "section", name = "Level 2"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "unknown", name = "Extra", value = "1"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "section", name = "Empty"),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "section", name = ""),
                    com.jamal2367.tinyppimobile.data.model.MetadataRow(kind = "row", name = "Untitled", value = "x"),
                ),
            ),
        )
        val history = HistoryViewModel(app)
        val cards = CardState()
        rule.show(cards) { MetadataScreen(onOpenSettings = {}, viewModel = fake.viewModel, historyViewModel = history) }
        rule.until { rule.onAllNodesWithText(str(R.string.history_chart)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("2100").assertIsDisplayed()
        for ((label, range) in listOf(
            R.string.history_range_1m to ChartRange.ONE_MINUTE,
            R.string.history_range_all to ChartRange.ALL,
            R.string.history_range_10m to ChartRange.TEN_MINUTES,
        )) {
            rule.onNodeWithText(str(label)).performClick()
            rule.until { history.state.value.range == range }
        }
        rule.onNodeWithText(str(R.string.history_chart)).performTouchInput { longClick() }
        assertEquals("metadata", cards.editing)
        rule.until { rule.onAllNodesWithText(str(R.string.cards_done)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        cards.hidden = (listOf("history.chart", "metadata.section0", "metadata.Level 2", "metadata.Level 2#1", "metadata.section3") +
            listOf()).map { "metadata/$it" }.toSet()
        rule.waitForIdle()
    }

    @Test
    fun `metadata waits for a title that has some, and asks for settings without a box`() {
        val fake = FakeLive()
        fake.playing(Snapshot(playing = true, title = "SDR film"))
        var opened = 0
        rule.show { MetadataScreen(onOpenSettings = { opened++ }, viewModel = fake.viewModel, historyViewModel = HistoryViewModel(app)) }
        rule.onNodeWithText(str(R.string.metadata_empty_title)).assertIsDisplayed()
        fake.settings.value = fake.settings.value.copy(primary = ServerConfig())
        rule.onNodeWithText(str(R.string.action_open_settings)).performClick()
        assertEquals(1, opened)
    }
}
