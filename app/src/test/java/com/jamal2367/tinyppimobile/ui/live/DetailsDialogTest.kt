package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test
    fun `the dialog shows the read as it goes, and closes on its button`() {
        var state by mutableStateOf(DetailsUiState(film))
        var closed = 0
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.LIGHT) { DetailsDialog(state) { closed++ } }
        }
        rule.waitUntil { has("One") }
        state = DetailsUiState(film, failed = true)
        rule.waitUntil { has(context.getString(R.string.details_failed)) }
        state = DetailsUiState(film, details = TitleDetails(plot = "Plot of one"))
        rule.waitUntil { has("Plot of one") }
        rule.onNodeWithText(context.getString(R.string.details_close)).performClick()
        assertEquals(1, closed)
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
        rule.waitUntil { has(context.getString(R.string.details_close)) }
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
        assertTrue(!has(context.getString(R.string.details_close)))
    }
}
