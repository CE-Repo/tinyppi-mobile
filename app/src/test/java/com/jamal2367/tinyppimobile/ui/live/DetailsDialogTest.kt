package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.activity.ComponentDialog
import org.robolectric.shadows.ShadowDialog
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isRoot
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.ui.library.FilmsScreen
import com.jamal2367.tinyppimobile.ui.library.SeriesScreen
import com.jamal2367.tinyppimobile.ui.navigation.LocalIsCurrentPage
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The details dialog on its own, and the read behind it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailsDialogTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val film = MarkTarget(MarkTarget.Kind.MOVIE, 1, "One")

    private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun closeShown() =
        has("One")

    @Test
    fun `the dialog shows the read as it goes, ignores swiping and closes on Back`() {
        var state by mutableStateOf(DetailsUiState(film))
        var closed = 0
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.LIGHT) { DetailsDialog(state, null, true, null) { closed++ } }
        }
        rule.waitUntil { has("One") }
        state = DetailsUiState(film, failed = true)
        rule.waitUntil { has(context.getString(R.string.details_failed)) }
        state = DetailsUiState(film, details = TitleDetails(plot = "Plot of one"))
        rule.waitUntil { has("Plot of one") }
        assertTrue(has(context.getString(R.string.details_description)))
        state = DetailsUiState(film, details = TitleDetails(tagline = "Only a slogan"))
        rule.waitUntil { has("Only a slogan") }
        assertTrue(has(context.getString(R.string.details_description)))
        // Each kind of rating brings the row on its own.
        for ((said, rated) in listOf(
            "FSK 12" to TitleDetails(mpaa = "FSK 12"),
            context.getString(R.string.live_rating_mine) + " 7" to TitleDetails(userrating = 7),
            context.getString(R.string.live_rating_top250, 3) to TitleDetails(top250 = 3),
            "Emmy " + context.getString(R.string.live_award_won, 1) to TitleDetails(
                awards = listOf(com.jamal2367.tinyppimobile.data.model.Award("emmy", listOf("Drama"))),
            ),
        )) {
            state = DetailsUiState(film, details = rated)
            rule.waitUntil { rule.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(!has(context.getString(R.string.details_description)))
        }
        // A cast with no more to it ends without an ellipsis, and stays as it
        // is while the rest of the page changes around it.
        val cast = listOf(com.jamal2367.tinyppimobile.data.model.CastMember("Ana", "Joi"))
        state = DetailsUiState(film, details = TitleDetails(cast = cast, plot = "One"))
        rule.waitUntil { has("Ana") }
        state = DetailsUiState(film, details = TitleDetails(cast = cast, plot = "Two"))
        rule.waitUntil { has("Two") }
        assertTrue(!has("\u2026"))
        // Another cast in its place is drawn anew.
        state = DetailsUiState(film, details = TitleDetails(cast = cast + com.jamal2367.tinyppimobile.data.model.CastMember("Ben", ""), castMore = true))
        rule.waitUntil { has("Ben") }
        // Only the ellipsis going, then only the names changing.
        state = DetailsUiState(film, details = TitleDetails(cast = cast + com.jamal2367.tinyppimobile.data.model.CastMember("Ben", "")))
        rule.waitUntil { !has("\u2026") }
        state = DetailsUiState(film, details = TitleDetails(cast = cast))
        rule.waitUntil { !has("Ben") }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { swipeDown() }
        assertEquals(0, closed)
        rule.runOnIdle {
            (ShadowDialog.getShownDialogs().last { it.isShowing } as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        assertEquals(1, closed)
    }

    @Test
    fun `pictures are drawn only where the reader has them on`() {
        var artwork by mutableStateOf(true)
        val details = TitleDetails(poster = "p", fanart = "f", plot = "With pictures")
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.DARK) {
                DetailsDialog(DetailsUiState(film, details), com.jamal2367.tinyppimobile.testing.Screens.box, artwork, null) {}
            }
        }
        rule.waitUntil { has("With pictures") }
        artwork = false
        rule.waitForIdle()
        assertTrue(has("With pictures"))
    }

    @Test
    fun `enriched crew keeps portraits and translates roles with legacy fallback`() {
        val director = com.jamal2367.tinyppimobile.data.model.CastMember("Alex Director", "director", "director-tag")
        val writer = com.jamal2367.tinyppimobile.data.model.CastMember("Sam Writer", "writer", "writer-tag")
        var details by mutableStateOf(TitleDetails(crew = listOf(director, writer)))
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.DARK) {
                DetailsDialog(DetailsUiState(film, details), com.jamal2367.tinyppimobile.testing.Screens.box, true, null) {}
            }
        }
        rule.waitUntil { has("Alex Director") && has("Sam Writer") }
        assertTrue(has(context.getString(R.string.live_credit_director)))
        assertTrue(has(context.getString(R.string.details_writer)))
        details = TitleDetails(directors = listOf("Legacy Director"), writers = listOf("Legacy Writer"))
        rule.waitUntil { has("Legacy Director") && has("Legacy Writer") }
        assertTrue(!has("Alex Director"))
    }

    @Test
    fun `under a poster's colour the page starts again from the app's own, and takes its title's`() {
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.DARK) {
                ArtworkAccentTheme(accent = androidx.compose.ui.graphics.Color.Red) {
                    DetailsDialog(
                        DetailsUiState(film, TitleDetails(plot = "Painted", poster = "p")),
                        com.jamal2367.tinyppimobile.testing.Screens.box,
                        showArtwork = true,
                        actions = DetailsActions(R.string.title_play, false) {},
                        adaptive = true,
                        intensity = 0.5f,
                    ) {}
                }
            }
        }
        rule.waitUntil { has("Painted") }
        rule.onNodeWithText(context.getString(R.string.title_play)).assertExists()
    }

    @Test
    fun `the keys follow the actions they are given`() {
        val pressed = mutableListOf<String>()
        var plot by mutableStateOf("Keys")
        var actions by mutableStateOf(DetailsActions(R.string.title_resume, true) { pressed += "first $it" })
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.LIGHT) {
                DetailsDialog(DetailsUiState(film, TitleDetails(plot = plot)), null, true, actions) {}
            }
        }
        rule.waitUntil { has("Keys") }
        // The page drawn again around the same actions keeps its keys.
        plot = "Keys again"
        rule.waitUntil { has("Keys again") }
        actions = DetailsActions(R.string.title_resume, true) { pressed += "second $it" }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.title_resume)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.title_restart)).performClick()
        assertEquals(listOf("second false", "second true"), pressed)
    }

    @Test
    fun `a new read lets go of the one before, and closing lets go of the one still out`() {
        val fake = FakeLive()
        val first = CompletableDeferred<TitleDetails>()
        val asked = mutableListOf<Int>()
        fake.api.details = { _, id ->
            asked += id
            if (id == 1) first.await() else TitleDetails(id = id, plot = "Plot $id")
        }
        fake.viewModel.openDetails(film)
        rule.waitForIdle()
        fake.viewModel.openDetails(MarkTarget(MarkTarget.Kind.EPISODE, 2, "Two"))
        rule.waitUntil { fake.viewModel.details.value?.details != null }
        assertTrue(first.isActive)
        assertEquals("Plot 2", fake.viewModel.details.value?.details?.plot)

        fake.viewModel.openDetails(film)
        rule.waitForIdle()
        fake.viewModel.closeDetails()
        assertNull(fake.viewModel.details.value)
        assertEquals(listOf(1, 2, 1), asked)
    }

    @Test
    fun `details read while the shelf stands open go from waiting to shown, and the shelves keep still around them`() {
        val fake = FakeLive()
        val answer = CompletableDeferred<TitleDetails>()
        fake.api.details = { _, _ -> answer.await() }
        var pass by mutableIntStateOf(0)
        rule.show(CardState()) {
            // Read here, so a change runs the caller again with the same screens in it.
            Text("pass $pass")
            FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel)
            SeriesScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
        rule.waitForIdle()
        pass++
        rule.waitForIdle()
        fake.viewModel.openDetails(film)
        rule.waitUntil { closeShown() }
        answer.complete(TitleDetails(plot = "Plot of one"))
        rule.waitUntil { has("Plot of one") }
        fake.viewModel.closeDetails()
        rule.waitForIdle()
        assertTrue(!has("Plot of one"))
    }

    @Test
    fun `closing details nothing has asked for is nothing`() {
        val fake = FakeLive()
        fake.viewModel.closeDetails()
        assertNull(fake.viewModel.details.value)
    }

    @Test
    fun `details already open when a shelf comes up are shown on it`() {
        val fake = FakeLive()
        fake.api.details = { _, _ -> TitleDetails(plot = "Plot of one") }
        fake.viewModel.openDetails(film)
        rule.show(CardState()) { FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel) }
        rule.waitUntil { has("Plot of one") }
    }

    @Test
    fun `details open on a page behind are not shown there`() {
        val fake = FakeLive()
        fake.viewModel.openDetails(film)
        rule.show(CardState()) {
            CompositionLocalProvider(LocalIsCurrentPage provides false) {
                FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel)
            }
        }
        rule.waitForIdle()
        assertTrue(!closeShown())
    }
}
