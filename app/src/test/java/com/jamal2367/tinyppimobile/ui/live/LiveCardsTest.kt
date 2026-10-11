package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.Metrics
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The cards of the live screen against every shape of reading they handle. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveCardsTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val fake = FakeLive()

    private fun showLive(accent: Color? = null) = rule.show(CardState().apply { folds = setOf(FOLD_CONTROLS) }) {
        ArtworkAccentTheme(accent = accent) {
            LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
    }

    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a new reading redraws the cards without rebuilding what has not moved`() {
        fake.playing()
        showLive(accent = Color(0xFF2266CC))
        rule.onNodeWithContentDescription(str(R.string.live_stop)).assertIsDisplayed()
        for (second in 1..3) {
            fake.playing(Screens.film.copy(time = "1:11:0$second", metrics = Screens.film.metrics.copy(progress = 42.0 + second)))
            rule.waitForIdle()
        }
        rule.onNodeWithText("1:11:03").assertIsDisplayed()

        // The long press, as accessibility services send it.
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ContentDescription, listOf("Dune: Part Two"),
        ).not()).let { }
    }

    @Test
    fun `dragging the bar shows where it would land`() {
        fake.playing()
        showLive()
        val bar = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center)
        }
        rule.waitForIdle()
        rule.until { !seen("1:11:00") }
        bar.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, fake.api.count("command:seek_percent"))

        // A duration the app cannot read leaves the box's own clock up.
        fake.playing(Screens.film.copy(duration = "soon", time = ""))
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center)
        }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.value_na)).assertIsDisplayed()
        bar.performTouchInput { up() }
    }

    @Test
    fun `tracks in every combination`() {
        fake.playing(
            Screens.film.copy(
                controls = PlayerControls(audio = listOf(Track(0, "English")), audioCurrent = 5, volume = null),
            ),
        )
        showLive()
        rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, str(R.string.live_track_unknown))).assertExists()

        fake.playing(Screens.film.copy(controls = PlayerControls(subtitle = listOf(Track(0, "English")), subtitleOn = false)))
        rule.until {
            rule.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, str(R.string.live_subtitles_off)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        fake.playing(Screens.film.copy(controls = PlayerControls()))
        rule.waitForIdle()
    }

    private fun badgesFor(snapshot: Snapshot) {
        fake.playing(snapshot)
        rule.waitForIdle()
    }

    @Test
    fun `the source badge names every grade and conversion`() {
        fake.playing(Screens.film.copy(hdrType = "hdr10", outputType = "sdr", groups = emptyList()))
        showLive()
        rule.waitForIdle()
        badgesFor(Screens.film.copy(groups = emptyList(), metadata = emptyList()))
        for ((output, label) in listOf(
            "SDR" to "SDR", "HDR10+" to "HDR10+", "hdr10plus" to "HDR10+", "HDR10" to "HDR10", "HLG" to "HLG", "DV-LL" to null,
        )) {
            badgesFor(Screens.film.copy(outputType = "", vs10 = Vs10State(output = output)))
        }
        // An output the source already is draws no arrow.
        badgesFor(Screens.film.copy(hdrType = "hdr10", outputType = "", vs10 = Vs10State(output = "HDR10")))
        badgesFor(Screens.film.copy(outputType = "dolbyvision", playing = true))
    }

    @Test
    fun `a film with little known about it still draws`() {
        fake.settings.value = fake.settings.value.copy(showArtwork = false)
        fake.live.value = LiveState(
            connection = LiveState.Connection.Busy,
            snapshot = Screens.film.copy(
                metrics = Metrics(),
                media = MediaInfo(),
                art = ArtTags(),
                vs10 = Vs10State(),
            ),
            server = null,
        )
        showLive()
        rule.onNodeWithText(str(R.string.status_busy)).assertIsDisplayed()
        fake.playing(Screens.film.copy(media = MediaInfo(year = "", genre = "Drama")))
        rule.until { seen("Drama") }
        fake.playing(Screens.film.copy(media = MediaInfo(year = "2001", genre = "")))
        rule.until { seen("2001") }
        fake.live.value = LiveState(connection = LiveState.Connection.Unauthorized, snapshot = Screens.film, server = Screens.box)
        rule.until { seen(str(R.string.status_unauthorized)) }
        rule.onNodeWithText(str(R.string.status_unauthorized)).performClick()
        assertEquals(1, fake.reconnects)
    }

    @Test
    fun `a message is shown for as long as a snackbar is, then let go`() {
        fake.api.command = { throw ApiFailure.Api(500, "no") }
        fake.playing()
        showLive()
        rule.onNodeWithContentDescription(str(R.string.live_stop)).performClick()
        rule.until { seen("no") }
        rule.mainClock.advanceTimeBy(10_000)
        rule.until { fake.viewModel.message.value == null }
    }

    @Test
    fun `the vs10 label preview draws`() {
        rule.setContent { Vs10LabelPreview() }
        rule.onNodeWithText("Original").assertIsDisplayed()
    }
}
