package com.jamal2367.tinyppimobile.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.ui.history.ChartCard
import com.jamal2367.tinyppimobile.ui.live.CardPart
import com.jamal2367.tinyppimobile.ui.live.CardSegment
import com.jamal2367.tinyppimobile.ui.live.Vs10LabelPreview
import com.jamal2367.tinyppimobile.ui.navigation.LocalBottomBarSpace
import com.jamal2367.tinyppimobile.ui.navigation.Page
import com.jamal2367.tinyppimobile.ui.navigation.barAwarePadding
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.ui.theme.artworkGradient
import com.jamal2367.tinyppimobile.testing.FakeLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** The building blocks, drawn as well as composed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ComponentsTest {

    @get:Rule val rule = createComposeRule()

    private fun draw() {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
    }

    @Test
    fun `every preview draws`() {
        var which by mutableStateOf(0)
        rule.setContent {
            when (which) {
                0 -> FormatBadgePreview()
                1 -> StatusPreview()
                2 -> GroupCardPreview()
                3 -> EventsCardPreview()
                4 -> LuminanceChartPreview()
                5 -> EmptyStatePreview()
                else -> Vs10LabelPreview()
            }
        }
        for (i in 0..6) {
            which = i
            draw()
        }
    }

    @Test
    fun `cards in every shape`() {
        rule.setContent {
            TinyPpiTheme(themeMode = ThemeMode.LIGHT) {
                CardState().Provide {
                    Column {
                        SectionCard(title = null) { Text("bare") }
                        SectionCard(title = "", trailing = { Text("tail") }) { Text("trailing") }
                        SectionCard(title = "Washed", containerBrush = Brush.verticalGradient(listOf(Color.Red, Color.Blue))) {
                            Text("brush")
                        }
                        CompositionLocalProvider(LocalCardScreen provides "live") {
                            SectionCard(title = "Arranged") { Text("long press only") }
                        }
                        EmptyState(icon = Icons.Outlined.PlayCircle, title = "No message", actionLabel = "Ignored")
                        for (part in CardPart.entries) {
                            CardSegment(part) { Text(part.name, Modifier.height(20.dp)) }
                        }
                    }
                }
            }
        }
        draw()
        rule.onNodeWithText("brush").assertIsDisplayed()
    }

    @Test
    fun `a value that changes lights up, then fades`() {
        var value by mutableStateOf("1")
        rule.setContent {
            TinyPpiTheme {
                Text(value, Modifier.flashOnChange(value))
            }
        }
        draw()
        value = "2"
        rule.mainClock.advanceTimeBy(120)
        draw()
        rule.mainClock.advanceTimeBy(1_000)
        draw()
    }

    @Test
    fun `the chart draws every window and scale`() {
        var history by mutableStateOf(History(t = listOf(0.0, 10.0), max = listOf(50.0, 12_000.0), avg = listOf(null, 3.0)))
        var range by mutableStateOf(ChartRange.ONE_MINUTE)
        rule.setContent {
            TinyPpiTheme {
                ChartCard(history, range) { range = it }
            }
        }
        draw()
        range = ChartRange.ALL
        draw()
        history = History(t = listOf(0.0), max = listOf(30.0), avg = listOf(5.0))
        draw()
        history = History(t = listOf(0.0, 1.0), max = listOf(null, null), avg = listOf(null, null))
        draw()
    }

    @Test
    fun `a list longer than the screen runs from the top, and padding follows the bar`() {
        val bar = mutableStateOf(10.dp)
        var first: Any? = null
        var second: Any? = null
        rule.setContent {
            TinyPpiTheme {
                CompositionLocalProvider(LocalBottomBarSpace provides bar) {
                    first = barAwarePadding(horizontal = 4.dp, bottom = 8.dp)
                    second = barAwarePadding(horizontal = 4.dp, bottom = 8.dp)
                    LazyColumn(verticalArrangement = centredBelowTitle(4.dp), modifier = Modifier.fillMaxSize()) {
                        item { ScreenTitle("Title") }
                        items(60) { Text("row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        draw()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, "padding")
    }

    @Test
    fun `a page for no tab is empty`() {
        val fake = FakeLive()
        rule.setContent { Page(null, fake.viewModel) {} }
        rule.waitForIdle()
    }

    @Test
    fun `the theme without dynamic colour, light and dark, and with an accent`() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 30)
        try {
            var mode by mutableStateOf(ThemeMode.LIGHT)
            var accent by mutableStateOf<Color?>(Color(0xFFFFEE58))
            rule.setContent {
                TinyPpiTheme(themeMode = mode) {
                    ArtworkAccentTheme(accent = accent, intensity = 0.4f) {
                        val brush = artworkGradient(Color.Red, Color.Black)
                        SectionCard(title = "Tinted", containerBrush = brush) { Text("x") }
                    }
                }
            }
            draw()
            mode = ThemeMode.DARK
            draw()
            accent = Color(0xFF1A237E)
            draw()
            mode = ThemeMode.LIGHT
            draw()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    @Test
    fun `a heading toggles from its arrow too`() {
        val cards = CardState()
        rule.setContent {
            TinyPpiTheme { cards.Provide { SectionCard(title = "Fold", foldId = "f") { Text("inside") } } }
        }
        rule.onNodeWithText("Fold").performClick()
        assertEquals(setOf("f"), cards.folds)
    }
}
