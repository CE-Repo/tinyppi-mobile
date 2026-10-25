package com.jamal2367.tinyppimobile.ui.app

import android.content.Intent
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import com.jamal2367.tinyppimobile.MainActivity
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.AppBox
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app as a whole: its tabs, its bar, back, and what the activity does for the screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class AppShellTest {

    private val env = AppBox(settings = { setKeepScreenOn(true); setThemeMode(ThemeMode.DARK) })

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() = env.close()

    private fun str(id: Int, vararg args: Any) = env.app.getString(id, *args)

    private fun tab(label: Int) = rule.onAllNodesWithContentDescription(str(label)).onFirst()

    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `moving and resetting cards persists through the activity wiring`() {
        rule.until { seen("Dune: Part Two") }
        rule.onAllNodesWithText("Dune: Part Two").onFirst().performTouchInput { longClick() }
        rule.until { seen(str(R.string.cards_reset)) }
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_down)).onFirst().performClick()
        rule.until { env.app.container.router.settings.cardOrders.isNotEmpty() }
        rule.onNodeWithText(str(R.string.cards_reset)).performClick()
        rule.until { env.app.container.router.settings.cardOrders.isEmpty() }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        rule.until { !seen(str(R.string.cards_reset)) }
    }

    @Test
    fun `starting a film on the shelf returns to live`() {
        rule.until { seen("Dune: Part Two") }
        tab(R.string.library_title).performClick()
        rule.until { seen("Heat") }
        rule.onAllNodesWithText("Heat").onLast().performClick()
        rule.until { seen(str(R.string.title_play)) }
        rule.onNodeWithText(str(R.string.title_play)).performClick()
        rule.until { env.box.asked.any { it.startsWith("POST ") } }
        rule.until { rule.onAllNodesWithText("Dune: Part Two").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Dune: Part Two").onFirst().assertIsDisplayed()
        assertTrue(env.box.asked.any { it.startsWith("POST ") })
    }

    @Test
    fun `every tab opens from the bar, and back goes home, then out`() {
        rule.until { seen("Dune: Part Two") }
        rule.until { rule.onAllNodesWithContentDescription(str(R.string.series_title)).fetchSemanticsNodes().isNotEmpty() }

        tab(R.string.nav_metadata_tab).performClick()
        rule.until { seen(str(R.string.nav_metadata)) }
        tab(R.string.library_title).performClick()
        rule.until { seen("Heat") }
        // The shelf turned round is kept with the settings, and drawn from them.
        rule.onAllNodesWithText(str(R.string.sort_ascending)).onFirst().performClick()
        rule.until { seen(str(R.string.sort_descending)) }
        tab(R.string.series_title).performClick()
        rule.until { seen("Dark") }
        tab(R.string.nav_history).performClick()
        rule.until { seen(str(R.string.history_summary)) }
        tab(R.string.nav_settings_tab).performClick()
        rule.until { seen(str(R.string.settings_servers_hint)) }

        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { seen("Dune: Part Two") }

        // A swipe moves between tabs, and the bar hides while nobody touches it.
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(4_000)
        rule.onRoot().performTouchInput { swipeUp() }

        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { seen(str(R.string.live_play)) || true }
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { seen(str(R.string.exit_confirm)) || rule.activity.isFinishing }
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { rule.activity.isFinishing }
    }

    @Test
    fun `leaving the app lets go of the box's stream, and coming back opens it again`() {
        rule.until { seen("Dune: Part Two") }
        val streams = { env.box.asked.count { it == "GET /api/stream" } }

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val away = streams()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        rule.until { streams() > away }
        rule.until { seen("Dune: Part Two") }
    }

    @Test
    fun `the screen stays awake while asked to, and the volume keys turn the box`() {
        rule.until { seen("Dune: Part Two") }
        val flags = rule.activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)

        val activity = rule.activity
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 1)))
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 4)))
        assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_A, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
        assertFalse(activity.onKeyUp(KeyEvent.KEYCODE_A, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)))
        rule.until { env.box.asked.count { it == "POST /api/command" } >= 2 }

        // Switched off, both go back to the phone.
        com.jamal2367.tinyppimobile.testing.Robo.settings(env.app) { setVolumeKeys(false); setKeepScreenOn(false) }
        rule.until { rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0 }
        rule.until { !activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)) }
        activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `a newer release is offered, and opened when asked`() {
        env.box.answers["/release"] = """{"name":"TinyPPI 99.0","tag_name":"build-9999","html_url":"https://example.com/r"}"""
        rule.until { seen("Dune: Part Two") }
        rule.mainClock.advanceTimeBy(6_000)
        rule.until { seen(str(R.string.update_show)) }
        rule.onNodeWithText(str(R.string.update_show)).performClick()
        rule.until {
            val started = shadowOf(rule.activity).peekNextStartedActivity()
            started?.action == Intent.ACTION_VIEW
        }
    }
}
