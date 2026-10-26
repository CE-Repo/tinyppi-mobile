package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.*
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsEdgesTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `dismiss gesture ignores side effects resets when scrolling up and consumes no velocity`() {
        lateinit var swipe: NestedScrollConnection
        var closed = 0
        var dismiss by mutableStateOf<() -> Unit>({ closed++; Unit })
        var density by mutableStateOf(1f)
        rule.show {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density),
            ) { swipe = rememberDismissSwipe(dismiss) }
        }
        runBlocking {
            assertEquals(Offset.Zero, swipe.onPostScroll(Offset.Zero, Offset(0f, 10000f), NestedScrollSource.SideEffect))
            assertEquals(Velocity.Zero, swipe.onPreFling(Velocity(0f, 100f)))
            assertEquals(0, closed)
            swipe.onPostScroll(Offset.Zero, Offset(0f, 10000f), NestedScrollSource.UserInput)
            swipe.onPostScroll(Offset(0f, -1f), Offset.Zero, NestedScrollSource.UserInput)
            swipe.onPreFling(Velocity.Zero)
            assertEquals(0, closed)
            swipe.onPostScroll(Offset.Zero, Offset(0f, 10000f), NestedScrollSource.UserInput)
            swipe.onPreFling(Velocity.Zero)
            assertEquals(1, closed)
            swipe.onPreFling(Velocity.Zero)
            assertEquals(1, closed)
        }
        dismiss = { closed += 2 }
        rule.waitForIdle()
        density = 2f
        rule.waitForIdle()
        runBlocking {
            swipe.onPostScroll(Offset.Zero, Offset(0f, 10000f), NestedScrollSource.UserInput)
            swipe.onPreFling(Velocity.Zero)
        }
        assertEquals(3, closed)
    }


    @Test
    fun `returning from seasons preserves the details scroll position`() {
        var covered by mutableStateOf(false)
        val state = DetailsUiState(
            MarkTarget(MarkTarget.Kind.SHOW, 7, "Dark"),
            TitleDetails(plot = "A long plot. ".repeat(1000)),
        )
        rule.show {
            DetailsDialog(
                state, null, false, null,
                over = if (covered) ({ Text("Seasons") }) else null,
                onDismiss = {},
            )
        }
        val page = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 240f) }
        rule.waitForIdle()
        val before = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)
        covered = true
        rule.waitForIdle()
        rule.onNodeWithText("Seasons").assertExists()
        covered = false
        rule.waitForIdle()
        val after = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.5f)
    }

    @Test
    fun `details and seasons replace each other without a transition`() {
        var covered by mutableStateOf(false)
        rule.show {
            DetailsDialog(
                DetailsUiState(
                    MarkTarget(MarkTarget.Kind.SHOW, 7, "Dark"),
                    TitleDetails(plot = "Show details"),
                ),
                null,
                false,
                null,
                over = if (covered) ({ Text("Seasons") }) else null,
                onDismiss = {},
            )
        }
        rule.waitUntil { rule.onAllNodesWithText("Show details").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false

        covered = true
        rule.waitForIdle()
        rule.onNodeWithText("Seasons").assertExists()
        rule.onNodeWithText("Show details").assertDoesNotExist()

        covered = false
        rule.waitForIdle()
        rule.onNodeWithText("Show details").assertExists()
        rule.onNodeWithText("Seasons").assertDoesNotExist()
    }

    @Test
    fun `details actions survive loading and failure and mark a watched title unseen`() {
        val target = MarkTarget(MarkTarget.Kind.MOVIE, 1, "Title")
        var state by mutableStateOf(DetailsUiState(target))
        val marked = mutableListOf<Boolean>()
        var showOpened = 0
        rule.show {
            DetailsDialog(state, null, true, DetailsActions(R.string.title_resume, true,
                watched = true, onMark = { marked += it }, onShow = { showOpened++ }) {}) {}
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.title_show)).assertIsNotEnabled()
        state = DetailsUiState(target, failed = true)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.title_show)).performClick()
        assertEquals(1, showOpened)
        rule.onNodeWithContentDescription(context.getString(R.string.mark_unwatched)).performClick()
        assertEquals(listOf(false), marked)
        state = DetailsUiState(target, details = TitleDetails(watched = true, resume = 1))
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.mark_unwatched)).performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    @Test
    fun `cast fades are drawn at both ends and names without roles fit`() {
        val cast = (1..12).map { CastMember("Actor $it", if (it == 1) "" else "Role $it") }
        rule.show { Box(Modifier.width(280.dp)) { DetailsBody(TitleDetails(cast = cast, castMore = true)) } }
        rule.onRoot().captureToImage()
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst()
            .performScrollToIndex(11)
        rule.waitForIdle()
        rule.onRoot().captureToImage()
        rule.onNodeWithText("Actor 12").assertExists()
    }
    @Test
    fun `seasons window handles missing shelf data and artwork settings`() {
        var show by mutableStateOf<LibraryShow?>(LibraryShow(7, "Dark", poster = "poster"))
        var pictures by mutableStateOf(true)
        var adaptive by mutableStateOf(true)
        var closed = 0
        rule.setContent {
            SeasonsDialog(
                open = OpenShow(7, "Dark", episodes = listOf(LibraryEpisode(70, "Pilot", season = 1))),
                show = show, server = Screens.box, showArtwork = pictures, adaptive = adaptive,
                intensity = 0.5f, starting = null, openSeasons = setOf(1),
                onDismiss = { closed++ }, onSeason = {}, onPlay = {}, onDetails = {},
            )
        }
        rule.onNodeWithText("Pilot").assertExists()
        pictures = false
        adaptive = false
        rule.waitForIdle()
        rule.onNodeWithText("Pilot").assertExists()
        show = null
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.series_back)).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `bleeding an unconstrained child keeps its measured size`() {
        rule.show {
            androidx.compose.ui.layout.Layout(content = { Text("Unbounded", Modifier.bleed(20.dp)) }) { children, _ ->
                val child = children.single().measure(androidx.compose.ui.unit.Constraints())
                layout(child.width, child.height) { child.place(0, 0) }
            }
        }
        val bounds = rule.onNodeWithText("Unbounded").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0f)
        assertTrue(bounds.height > 0f)
    }

    @Test
    fun `details can use the material scheme without an app accent provider`() {
        rule.setContent {
            DetailsDialog(DetailsUiState(MarkTarget(MarkTarget.Kind.MOVIE, 1, "Bare title"),
                TitleDetails(plot = "Bare plot")), null, true, null) {}
        }
        rule.onNodeWithText("Bare plot").assertExists()
    }

}
