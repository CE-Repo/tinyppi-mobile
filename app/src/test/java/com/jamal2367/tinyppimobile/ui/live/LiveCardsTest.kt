package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.components.CardLayout
import com.jamal2367.tinyppimobile.ui.components.CardScreens
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

    /** The film with what its library says about it. */
    private val told = Screens.film.copy(
        media = Screens.film.media.copy(tagline = "Long live the fighters.", plot = "Paul joins the Fremen."),
    )

    @Test
    fun `the plot card says the plot, with its slogan where there is one`() {
        fake.playing(told)
        showLive()
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onNodeWithText("Long live the fighters.").assertIsDisplayed()
        rule.onNodeWithText("Paul joins the Fremen.").assertIsDisplayed()

        // An episode: a plot without a slogan.
        fake.playing(told.copy(media = MediaInfo(plot = "An episode.")))
        rule.until { seen("An episode.") }
        assertEquals(false, seen("Long live the fighters."))

        // A slogan without a plot: too little for a card.
        fake.playing(told.copy(media = MediaInfo(tagline = "Only this.")))
        rule.until { !seen(str(R.string.live_plot)) }
        assertEquals(false, seen("Only this."))

        // A plot again, and the card is back.
        fake.playing(told)
        rule.until { seen("Paul joins the Fremen.") }

        // A file the library does not know: no card at all.
        fake.playing(told.copy(media = MediaInfo()))
        rule.until { !seen(str(R.string.live_plot)) }
    }

    @Test
    fun `the plot card carries every rating the library holds, before the slogan and the plot`() {
        fake.playing(
            told.copy(
                media = told.media.copy(
                    ratings = listOf(
                        SourceRating("imdb", 8.5, 612_345),
                        SourceRating("tmdb", 8.2, 1),
                        SourceRating("rotten tomatoes", 9.2, 0),
                    ),
                    userRating = 9,
                    top250 = 27,
                    mpaa = "FSK 12",
                ),
            ),
        )
        showLive()
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onNodeWithText("IMDb").assertIsDisplayed()
        rule.onNodeWithText("8.5").assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.live_rating_votes, 612_345, "612,345")).assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.live_rating_votes, 1, "1")).assertIsDisplayed()
        rule.onNodeWithText("Rotten tomatoes").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.live_rating_mine)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.live_rating_top250, 27)).assertIsDisplayed()
        rule.onNodeWithText("FSK 12").assertIsDisplayed()
        // The ratings stand above the slogan.
        val badges = rule.onNodeWithText("IMDb").fetchSemanticsNode().boundsInRoot
        val slogan = rule.onNodeWithText("Long live the fighters.").fetchSemanticsNode().boundsInRoot
        assertEquals(true, badges.bottom <= slogan.top)

        // A score alone - no age rating, no own rating, no place - from a
        // source the library names nothing: its figure and its star.
        fake.playing(told.copy(media = told.media.copy(ratings = listOf(SourceRating("", 5.0)))))
        rule.until { seen("5.0") }
        assertEquals(false, seen("FSK 12"))
        assertEquals(false, seen(str(R.string.live_rating_mine)))

        // Ratings without a plot: the card for them alone, and no slogan.
        fake.playing(told.copy(media = MediaInfo(tagline = "Only this.", mpaa = "FSK 16")))
        rule.until { seen("FSK 16") }
        assertEquals(true, seen(str(R.string.live_plot)))
        assertEquals(false, seen("Only this."))
    }

    @Test
    fun `the plot card folds, moves and comes off the screen like the others`() {
        val cards = CardState().apply { folds = setOf(FOLD_CONTROLS) }
        fake.playing(told)
        rule.show(cards) { LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel) }

        // Folded from its heading, and open again from it.
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { FOLD_PLOT in cards.folds && !seen("Paul joins the Fremen.") }
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { FOLD_PLOT !in cards.folds && seen("Paul joins the Fremen.") }

        // First among the cards that can be arranged, under the playing one.
        cards.editing = CardScreens.LIVE
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_down)).onFirst().performClick()
        rule.until { cards.orders[CardScreens.LIVE]?.take(2) == listOf(FOLD_CONTROLS, FOLD_PLOT) }

        // Taken off the screen, it is gone from it once the arranging is done.
        rule.onAllNodesWithContentDescription(str(R.string.cards_hide))[1].performClick()
        rule.until { CardLayout.hiddenKey(CardScreens.LIVE, FOLD_PLOT) in cards.hidden }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        rule.until { cards.editing == null && !seen("Paul joins the Fremen.") }
    }

    @Test
    fun `the vs10 label preview draws`() {
        rule.setContent { Vs10LabelPreview() }
        rule.onNodeWithText("Original").assertIsDisplayed()
    }
}
