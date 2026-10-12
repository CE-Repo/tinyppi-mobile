package com.jamal2367.tinyppimobile.ui.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jamal2367.tinyppimobile.MainActivity
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.AppBox
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens.until
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun png(colour: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/**
 * The app painted in the colour of what is playing. On API 27, where the
 * image loader decodes with BitmapFactory - Robolectric has no ImageDecoder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "w411dp-h891dp")
class AppAccentTest {

    private val env = AppBox(settings = { setThemeMode(ThemeMode.DARK) }).apply {
        box.art = png(Color.rgb(200, 40, 40))
        // Two readings of the same film, so the app recomposes with the poster unchanged.
        val film = com.jamal2367.tinyppimobile.testing.Screens.film.copy(libraryRevision = 1)
        stream(film, film.copy(time = "1:11:01"), film.copy(time = "1:11:02"))
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun close() = env.close()

    @Test
    fun `a coloured poster tints the app, a grey one does not, in either theme`() {
        rule.until { rule.onAllNodesWithText("Dune: Part Two").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        Thread.sleep(500)
        rule.waitForIdle()

        Robo.settings(env.app) { setThemeMode(ThemeMode.LIGHT) }
        rule.mainClock.advanceTimeBy(2_000)
        Thread.sleep(500)
        rule.waitForIdle()

        // A poster with no colour in it, under a new address so it is fetched again.
        env.box.art = png(Color.rgb(128, 128, 128))
        env.stream(com.jamal2367.tinyppimobile.testing.Screens.film.copy(art = com.jamal2367.tinyppimobile.data.model.ArtTags(poster = "grey"), libraryRevision = 1))
        env.app.container.reconnect()
        rule.mainClock.advanceTimeBy(5_000)
        Thread.sleep(1_000)
        rule.waitForIdle()
    }
}
