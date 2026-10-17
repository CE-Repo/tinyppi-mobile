package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Chapter
import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chapters on the live card's bar: the dots, the chapter named over it,
 * a drag that snaps onto a dot and jumps to it, and the switch that takes them
 * away. Drawn as well as composed, so the dots are painted and not only laid
 * out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp")
class ChapterMarksTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val fake = FakeLive()
    private val sent = mutableListOf<CommandBody>()

    private val chapters = listOf(
        Chapter(1, 0, 0.0, "00:00:00", "Opening"),
        Chapter(2, 2490, 25.0, "00:41:30", "Arrakis"),
        Chapter(3, 4980, 50.0, "01:23:00"),
        Chapter(4, 7470, 75.0, "02:04:30", "Finale"),
    )
    private val film = Screens.film.copy(chapters = chapters, chapter = 2)

    init {
        fake.api.command = { sent += it; CommandAck(ok = true, action = it.action) }
    }

    private fun showLive() = rule.show(CardState().apply { folds = setOf(FOLD_CONTROLS) }) {
        LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel)
    }

    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun drawn() {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
    }

    private val bar get() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))

    @Test
    fun `the chapter playing is named over the bar and its dots are drawn`() {
        fake.playing(film)
        showLive()
        rule.onNodeWithText("Chapter 2 of 4 · Arrakis").assertIsDisplayed()
        drawn()

        // Further on, past a dot that is no longer the one playing.
        fake.playing(film.copy(chapter = 3, metrics = film.metrics.copy(progress = 55.0)))
        rule.until { seen("Chapter 3 of 4") }
        drawn()

        // Before the first chapter has begun, and a file without any.
        fake.playing(film.copy(chapter = 0))
        rule.until { !seen("Chapter 3 of 4") }
        fake.playing(Screens.film)
        drawn()
        assertFalse(seen("Chapter 2 of 4 · Arrakis"))
    }

    @Test
    fun `a drag onto a dot snaps there, names it and jumps to its chapter`() {
        fake.playing(film)
        showLive()
        // The slider keeps the touch slop a drag spends getting started, so
        // the finger goes that much past the middle to bring the bar to it -
        // and then a pixel on, still on the same dot.
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center + Offset(viewConfiguration.touchSlop, 0f))
            moveTo(center + Offset(viewConfiguration.touchSlop + 1f, 0f))
        }
        rule.until { seen("Chapter 3 of 4") }
        drawn()
        bar.performTouchInput { up() }
        rule.until { sent.isNotEmpty() }
        assertEquals(listOf(CommandBody("chapter_goto", 3.0)), sent)
    }

    @Test
    fun `a drag that ends between dots seeks to the spot`() {
        fake.playing(film)
        showLive()
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(Offset(width * 0.37f, centerY))
        }
        rule.until { seen("Chapter 2 of 4 · Arrakis") }
        bar.performTouchInput { up() }
        rule.until { sent.isNotEmpty() }
        assertEquals("seek_percent", sent.single().action)
    }

    @Test
    fun `without control the bar still carries the chapters`() {
        fake.playing(film.copy(control = false))
        showLive()
        rule.onNodeWithText("Chapter 2 of 4 · Arrakis").assertIsDisplayed()
        drawn()
    }

    @Test
    fun `switched off in the settings, the bar is the plain one again`() {
        fake.settings.value = fake.settings.value.copy(showChapters = false)
        fake.playing(film)
        showLive()
        rule.onNodeWithText("Dune: Part Two").assertIsDisplayed()
        assertFalse(seen("Chapter 2 of 4 · Arrakis"))
        drawn()
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center)
        }
        rule.waitForIdle()
        assertFalse(seen("Chapter 3 of 4"))
        bar.performTouchInput { up() }
        rule.until { sent.isNotEmpty() }
        assertEquals("seek_percent", sent.single().action)
    }
}
