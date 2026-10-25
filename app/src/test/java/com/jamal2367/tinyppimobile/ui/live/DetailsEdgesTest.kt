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
        val dismiss = { closed++; Unit }
        rule.show { swipe = rememberDismissSwipe(dismiss) }
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
    }

    @Test
    fun `details actions survive loading and failure and mark a watched title unseen`() {
        val target = MarkTarget(MarkTarget.Kind.MOVIE, 1, "Title")
        var state by mutableStateOf(DetailsUiState(target))
        val marked = mutableListOf<Boolean>()
        rule.show {
            DetailsDialog(state, null, true, DetailsActions(R.string.title_resume, true,
                watched = true, onMark = { marked += it }) {}) {}
        }
        rule.waitForIdle()
        state = DetailsUiState(target, failed = true)
        rule.waitForIdle()
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
}
