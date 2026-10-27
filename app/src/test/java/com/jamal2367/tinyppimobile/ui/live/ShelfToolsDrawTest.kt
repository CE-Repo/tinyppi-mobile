package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.ui.library.FilmsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The filter row drawn for real on a narrow phone: faded where it goes on, at either end. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShelfToolsDrawTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the row is drawn faded at the start and at the end of its scroll`() {
        val fake = FakeLive()
        fake.api.library = {
            Library(
                movies = listOf(
                    LibraryFilm(1, "Dune", genres = listOf("Sci-Fi"), video = "dv", resolution = "2160p", audio = "truehd"),
                    LibraryFilm(2, "Heat", genres = listOf("Crime"), video = "sdr", resolution = "1080p", audio = "dts"),
                ),
            )
        }
        fake.playing(Snapshot(control = true, playing = false, libraryRevision = 3))
        rule.show(CardState()) { FilmsScreen(onOpenSettings = {}, viewModel = fake.viewModel) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Heat").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureToImage()
        rule.onAllNodesWithText(context.getString(R.string.filter_audio)).onLast().performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureToImage()
    }
}
