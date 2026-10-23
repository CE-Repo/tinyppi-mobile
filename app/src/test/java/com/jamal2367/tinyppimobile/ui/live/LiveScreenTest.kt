package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.LastPlayed
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The live screen, in each state a box can be in, and every button on it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val fake = FakeLive()
    private var settingsOpened = 0

    private fun showLive(cards: CardState = CardState()) = rule.show(cards) {
        LiveScreen(onOpenSettings = { settingsOpened++ }, viewModel = fake.viewModel)
    }

    @Test
    fun `with nothing filled in it points at the settings`() {
        fake.settings.value = AppSettings(primary = Screens.box.copy(host = ""))
        fake.live.value = LiveState(connection = LiveState.Connection.NotConfigured)
        showLive()
        rule.onNodeWithText(str(R.string.live_not_configured_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_open_settings)).performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun `before the first reading it waits, and offline it offers to reconnect`() {
        fake.live.value = LiveState(connection = LiveState.Connection.Offline)
        showLive()
        rule.onNodeWithText(str(R.string.live_waiting_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.status_offline)).performClick()
        assertEquals(1, fake.reconnects)
    }

    @Test
    fun `with nothing playing it says so over the box's name`() {
        fake.live.value = LiveState(
            connection = LiveState.Connection.Polling,
            snapshot = Snapshot(last = LastPlayed(title = "Heat")),
            server = Screens.box,
        )
        showLive()
        rule.onNodeWithText(str(R.string.live_idle_title)).assertIsDisplayed()
        rule.onNodeWithText(Screens.box.label).assertIsDisplayed()
    }

    @Test
    fun `every button on a playing film sends its command`() {
        fake.playing()
        showLive()
        rule.onNodeWithText("Dune: Part Two").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.live_transport)).performClick()

        for (description in listOf(
            context.resources.getQuantityString(R.plurals.live_seek_back_minutes, 10, 10),
            context.resources.getQuantityString(R.plurals.live_seek_back_minutes, 1, 1),
            context.resources.getQuantityString(R.plurals.live_seek_back, 10, 10),
            context.resources.getQuantityString(R.plurals.live_seek_forward, 10, 10),
            context.resources.getQuantityString(R.plurals.live_seek_forward_minutes, 1, 1),
            context.resources.getQuantityString(R.plurals.live_seek_forward_minutes, 10, 10),
            str(R.string.live_chapter_previous),
            str(R.string.live_pause),
            str(R.string.live_stop),
            str(R.string.live_chapter_next),
        )) {
            rule.onNodeWithContentDescription(description).performClick()
        }
        rule.onNodeWithContentDescription(str(R.string.live_volume)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_volume_down)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_mute)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_volume_up)).performClick()

        rule.onNodeWithContentDescription(str(R.string.live_audio_track)).performClick()
        rule.onNodeWithText("Deutsch DD+").performClick()
        rule.onNodeWithContentDescription(str(R.string.live_subtitles)).performClick()
        rule.onNodeWithText("English").performClick()
        rule.onNodeWithContentDescription(str(R.string.live_subtitles)).performClick()
        rule.onNodeWithText(str(R.string.live_subtitles_off)).performClick()

        rule.onNodeWithContentDescription(str(R.string.a11y_conversion, "DV", "HDR10")).performClick()
        rule.onNodeWithText("Original").performClick()

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        rule.waitForIdle()

        assertEquals(
            listOf(
                "command:seek", "command:seek", "command:seek", "command:seek", "command:seek", "command:seek",
                "command:chapter_previous", "command:playpause", "command:stop", "command:chapter_next",
                "command:volume_down", "command:mute", "command:volume_up",
                "command:audio", "command:subtitle", "command:subtitle", "mode", "mode", "command:seek_percent",
            ),
            fake.api.calls,
        )
    }

    @Test
    fun `a paused, muted film with nothing else known still draws`() {
        fake.playing(
            Screens.film.copy(
                paused = true,
                title = "",
                time = "",
                duration = "",
                finish = "",
                filename = "",
                controls = PlayerControls(subtitle = listOf(Track(0, "English")), muted = true, volume = 3),
                media = Screens.film.media.copy(show = "Dark", season = "1", episode = "2"),
            ),
        )
        showLive()
        rule.onNodeWithText(str(R.string.live_untitled)).assertIsDisplayed()
        rule.onNodeWithText("Dark · S01E02 · 2024 · Science Fiction").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.live_transport)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_play)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.live_volume)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_unmute)).assertIsDisplayed()
    }

    @Test
    fun `the studio stands on a line of its own under the year and the genre`() {
        fake.playing(
            Screens.film.copy(
                media = Screens.film.media.copy(studios = listOf("Syncopy", "Atlas Entertainment"), studiosMore = true),
            ),
        )
        showLive()
        val genre = rule.onNodeWithText("2024 · Science Fiction").fetchSemanticsNode().boundsInRoot
        val studio = rule.onNodeWithText("Syncopy, Atlas Entertainment, …").fetchSemanticsNode().boundsInRoot
        assertEquals(true, studio.top >= genre.bottom)
        assertEquals(genre.left, studio.left, 0.5f)
    }

    // Drawn for real: the measuring the stand-in graphics do sets every word
    // too tall for the column to leave any room beside the poster.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `who directed it stands in the middle between the lines above and the badges`() {
        fun named(count: Int) = context.resources.getQuantityString(R.plurals.live_credit_directors, count)
        fun box(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        // A title alone over it, so the poster beside the column leaves it
        // room to stand in.
        fake.playing(Screens.film.copy(media = MediaInfo(directors = listOf("Christopher Nolan"))))
        showLive()
        val title = box("Dune: Part Two")
        val director = box("${named(1)} Christopher Nolan")
        // The badges' words sit inside their pills: two rows are a pill and
        // the rows' gap apart, which says how far in.
        val first = box("UHD")
        val second = box("TrueHD")
        val inset = (second.top - first.top - 10f * context.resources.displayMetrics.density - first.height) / 2
        val over = director.top - title.bottom
        assertEquals(over, first.top - inset - director.bottom, 1.5f)
        assertEquals(true, over > 8f * context.resources.displayMetrics.density)

        // Under the studio, where there is one, and level with it.
        fake.playing(
            Screens.film.copy(
                media = Screens.film.media.copy(studios = listOf("Syncopy"), directors = listOf("Christopher Nolan")),
            ),
        )
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Syncopy").fetchSemanticsNodes().isNotEmpty() }
        val studio = box("Syncopy")
        val under = box("${named(1)} Christopher Nolan")
        assertEquals(true, under.top >= studio.bottom)
        assertEquals(studio.left, under.left, 0.5f)

        // Named for more where the add-on cut the list, with an ellipsis.
        fake.playing(
            Screens.film.copy(
                media = Screens.film.media.copy(directors = listOf("Joel Coen"), directorsMore = true),
            ),
        )
        rule.waitUntil(5_000) { rule.onAllNodesWithText("${named(2)} Joel Coen, …").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `without control the readings are shown and nothing can be pressed`() {
        fake.playing(Screens.film.copy(control = false, controls = PlayerControls()))
        showLive()
        rule.onNodeWithText(str(R.string.live_control_disabled)).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.live_transport)).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun `a refused command is said once`() {
        fake.api.command = { throw ApiFailure.Api(500, "broken") }
        fake.playing()
        showLive()
        rule.onNodeWithText(str(R.string.live_transport)).performClick()
        rule.onNodeWithContentDescription(str(R.string.live_stop)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("broken").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a long press on the playing card arranges the screen's cards`() {
        val cards = CardState()
        fake.playing()
        showLive(cards)
        rule.onNodeWithText("Dune: Part Two").performTouchInput { longClick() }
        assertEquals("live", cards.editing)
        rule.onNodeWithText(str(R.string.cards_edit)).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.cards_move_down)).fetchSemanticsNodes()
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        assertEquals(null, cards.editing)
    }
}
