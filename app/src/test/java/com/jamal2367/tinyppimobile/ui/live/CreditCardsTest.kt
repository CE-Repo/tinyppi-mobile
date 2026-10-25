package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.data.model.CastMember
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.testing.Screens.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp")
class CreditCardsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `cards align and show full names and roles`() = checkCards(1f)

    @Test
    fun `larger text grows every card without clipping`() = checkCards(1.5f)

    private fun checkCards(fontScale: Float) {
        val names = listOf("Stephen Norrington", "David S. Goyer", "Wesley Snipes", "An Actor With A Very Long Name")
        val crewRoles = listOf("Director", "Writer")
        val roles = listOf("Blade", "A character with a complete and unusually long name")
        rule.show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    DetailsBody(TitleDetails(
                        directors = listOf(names[0]),
                        writers = listOf(names[1]),
                        cast = listOf(CastMember(names[2], roles[0]), CastMember(names[3], roles[1])),
                    ))
                }
            }
        }
        rule.waitForIdle()
        val cards = rule.onAllNodesWithTag("credit-card").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, cards.size)
        assertEquals(1, cards.map { it.width }.distinct().size)
        assertEquals(cards[0].height, cards[1].height, 0f)
        assertEquals(cards[2].height, cards[3].height, 0f)
        for ((index, name) in names.withIndex()) {
            val card = cards[index]
            val initial = rule.onNodeWithText(name.take(1)).fetchSemanticsNode().boundsInRoot
            val lastText = if (index < 2) crewRoles[index] else roles[index - 2]
            val last = rule.onNodeWithText(lastText).fetchSemanticsNode().boundsInRoot
            val contentTop = initial.center.y - card.width * 22f / 136f
            assertEquals("centred content: $name", card.center.y, (contentTop + last.bottom) / 2, 1f)
        }
        for (text in names + crewRoles + roles) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(text, layouts.isNotEmpty())
            assertFalse("full text: $text", layouts.single().hasVisualOverflow)
        }
    }
}
