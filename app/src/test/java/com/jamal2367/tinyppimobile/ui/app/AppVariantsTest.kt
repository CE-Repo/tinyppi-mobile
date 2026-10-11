package com.jamal2367.tinyppimobile.ui.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.jamal2367.tinyppimobile.MainActivity
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LastPlayed
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.AppBox
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.until
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shell under conditions the first tour does not meet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class AppVariantsTest {

    private val env = AppBox(
        snapshot = Screens.film.copy(libraryRevision = 1),
        settings = { setThemeMode(ThemeMode.LIGHT); setTabHidden("series", true) },
    ).apply {
        // A poster with a colour in it, for the app to paint itself in.
        val bitmap = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 40)) }
        box.art = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() = env.close()

    private fun str(id: Int, vararg args: Any) = env.app.getString(id, *args)
    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun tabShown(label: Int) = rule.onAllNodesWithContentDescription(str(label)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `tabs come and go with what the box has to show`() {
        rule.until { seen("Dune: Part Two") && tabShown(R.string.nav_metadata_tab) }
        rule.until { !tabShown(R.string.series_title) }

        // On the metadata tab when the film stops being Dolby Vision: back to the one before it.
        rule.onAllNodesWithContentDescription(str(R.string.nav_metadata_tab)).onFirst().performClick()
        rule.until { seen(str(R.string.nav_metadata)) }
        env.stream(Screens.film.copy(hdrType = "hdr10", libraryRevision = 1))
        env.app.container.reconnect()
        rule.until(15_000) { !tabShown(R.string.nav_metadata_tab) }

        // Nothing playing, but something just did: history stays; nothing at all: it goes.
        env.stream(Snapshot(playing = false, last = LastPlayed(title = "Heat"), control = true))
        env.app.container.reconnect()
        rule.until(15_000) { seen(str(R.string.live_idle_title)) && tabShown(R.string.nav_history) }
        env.stream(Snapshot(playing = false, control = true))
        env.app.container.reconnect()
        rule.until(15_000) { !tabShown(R.string.nav_history) }

        // A box that offers no shelves.
        env.box.codes["/api/library"] = 403
        Robo.settings(env.app) { setTabHidden("series", false) }
        env.app.container.reconnect()
        rule.until(15_000) { tabShown(R.string.series_title) }
    }

    @Test
    fun `turned over, the app does not ask about updates again, and an unanswered offer goes`() {
        env.box.answers["/release"] = """{"name":"TinyPPI 99.0","tag_name":"build-9999"}"""
        rule.until { seen("Dune: Part Two") }
        rule.mainClock.advanceTimeBy(6_000)
        rule.until { seen(str(R.string.update_show)) }
        // Back while the offer is up dismisses it for the exit question.
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.until { seen(str(R.string.exit_confirm)) }
        rule.mainClock.advanceTimeBy(15_000)
        rule.until { !seen(str(R.string.exit_confirm)) }

        rule.activityRule.scenario.recreate()
        rule.until { seen("Dune: Part Two") }
        rule.mainClock.advanceTimeBy(15_000)
        rule.waitForIdle()
    }
}

/** A box that asks for the local network permission first, as Android 17 does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AppPermissionTest {

    private val sdk = Build.VERSION.SDK_INT
    private val env = AppBox(settings = { setAdaptiveColor(false) }).also {
        org.robolectric.util.ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 37)
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() {
        org.robolectric.util.ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        env.close()
    }

    @Test
    fun `nothing is drawn until the question is answered`() {
        rule.waitForIdle()
        val shadow = org.robolectric.Shadows.shadowOf(rule.activity)
        val request = shadow.lastRequestedPermission
        rule.activity.onRequestPermissionsResult(
            request.requestCode,
            arrayOf(com.jamal2367.tinyppimobile.util.LocalNetworkAccess.PERMISSION),
            intArrayOf(android.content.pm.PackageManager.PERMISSION_DENIED),
        )
        rule.until { rule.onAllNodesWithText(env.app.getString(R.string.error_local_network_denied)).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Dune: Part Two").fetchSemanticsNodes().isNotEmpty() }
    }
}

/** On a tablet the tabs are a rail down the side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "sw600dp-w900dp-h1200dp")
class AppRailTest {

    private val env = AppBox(settings = { setShowArtwork(false) })

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() = env.close()

    @Test
    fun `the rail opens every tab`() {
        rule.until { rule.onAllNodesWithText("Dune: Part Two").fetchSemanticsNodes().isNotEmpty() }
        for (label in listOf(R.string.nav_settings_tab, R.string.nav_history, R.string.nav_live)) {
            rule.onAllNodesWithText(env.app.getString(label)).onFirst().performClick()
            rule.waitForIdle()
        }
    }
}

/** The app when the box goes away, and the cards folded and hidden from the live screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class AppOfflineTest {

    private val env = AppBox()

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() = env.close()

    private fun str(id: Int) = env.app.getString(id)
    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `cards fold and hide through the app's store, and the shelves leave with the box`() {
        rule.until { seen("Dune: Part Two") }
        rule.until { rule.onAllNodesWithContentDescription(str(R.string.library_title)).fetchSemanticsNodes().isNotEmpty() }

        // A fold, written to the store.
        rule.onAllNodesWithText(str(R.string.live_vs10)).onFirst().performClick()
        rule.until { "live.vs10" in env.app.container.router.settings.cardFolds }

        // A card taken off from the arranging list.
        rule.onAllNodesWithText(str(R.string.live_vs10)).onFirst().performTouchInput { longClick() }
        rule.until { seen(str(R.string.cards_done)) }
        rule.onAllNodesWithContentDescription(str(R.string.cards_hide)).onFirst().performClick()
        rule.until { env.app.container.router.settings.cardHidden.isNotEmpty() }
        rule.onAllNodesWithText(str(R.string.cards_done)).onFirst().performClick()

        // The volume keys while nothing is playing are the phone's.
        env.stream(com.jamal2367.tinyppimobile.data.model.Snapshot(playing = false, control = true))
        env.app.container.reconnect()
        rule.until(15_000) { seen(str(R.string.live_idle_title)) }
        org.junit.Assert.assertFalse(
            rule.activity.onKeyDown(android.view.KeyEvent.KEYCODE_VOLUME_UP, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)),
        )

        // The box goes away: the shelves go with it.
        env.box.close()
        env.app.container.reconnect()
        rule.until(20_000) { rule.onAllNodesWithContentDescription(str(R.string.library_title)).fetchSemanticsNodes().isEmpty() }
    }
}
