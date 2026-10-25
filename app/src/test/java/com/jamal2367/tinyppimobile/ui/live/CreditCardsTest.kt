package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    @Test
    fun `portrait cards keep names roles and a fallback visible`() {
        rule.show {
            DetailsBody(
                TitleDetails(cast = listOf(CastMember("Actor", "Character", "1234abcd"))),
                com.jamal2367.tinyppimobile.data.prefs.ServerConfig(host = "127.0.0.1", port = 1),
            )
        }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("credit-card").fetchSemanticsNodes().size)
        rule.onNodeWithText("Actor").assertExists()
        rule.onNodeWithText("Character").assertExists()
        rule.onNodeWithText("A").assertExists()
    }

    @Test
    fun `cast without roles keeps its portrait and follows new names and server settings`() {
        var details by mutableStateOf(TitleDetails(cast = listOf(CastMember("Ana", ""))))
        var server by mutableStateOf<com.jamal2367.tinyppimobile.data.prefs.ServerConfig?>(null)
        rule.show { DetailsBody(details, server) }
        rule.onNodeWithText("Ana").assertExists()
        rule.onNodeWithText("A").assertExists()
        server = com.jamal2367.tinyppimobile.testing.Screens.box
        rule.waitForIdle()
        rule.onNodeWithText("Ana").assertExists()
        details = TitleDetails(cast = listOf(CastMember("Bea", "", "portrait")))
        rule.waitForIdle()
        rule.onNodeWithText("Bea").assertExists()
        rule.onNodeWithText("B").assertExists()
        server = null
        rule.waitForIdle()
        rule.onNodeWithText("Bea").assertExists()
    }

    @Test
    fun `cast measurements follow typography and density changes`() {
        var nameSize by mutableStateOf(14)
        var roleSize by mutableStateOf(12)
        var scale by mutableStateOf(1f)
        rule.show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale)) {
                val typography = androidx.compose.material3.MaterialTheme.typography
                androidx.compose.material3.MaterialTheme(typography = typography.copy(
                    labelLarge = typography.labelLarge.copy(fontSize = nameSize.sp, lineHeight = (nameSize + 6).sp),
                    labelMedium = typography.labelMedium.copy(fontSize = roleSize.sp, lineHeight = (roleSize + 4).sp),
                )) {
                    DetailsBody(TitleDetails(cast = listOf(CastMember("Name", "Role"))))
                }
            }
        }
        val before = rule.onAllNodesWithTag("credit-card").fetchSemanticsNodes().single().boundsInRoot.height
        nameSize = 24
        rule.waitForIdle()
        roleSize = 20
        rule.waitForIdle()
        val after = rule.onAllNodesWithTag("credit-card").fetchSemanticsNodes().single().boundsInRoot.height
        assertTrue(after > before)
        scale = 0.9f
        rule.waitForIdle()
        rule.onNodeWithText("Name").assertExists()
        rule.onNodeWithText("Role").assertExists()
    }

    private fun checkCards(fontScale: Float) {
        val names = listOf("Wesley Snipes", "An Actor With A Very Long Name", "Stephen Norrington", "David S. Goyer")
        val crewRoles = listOf("Director", "Writer")
        val roles = listOf("Blade", "A character with a complete and unusually long name")
        rule.show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    DetailsBody(TitleDetails(
                        directors = listOf(names[2]),
                        writers = listOf(names[3]),
                        cast = listOf(CastMember(names[0], roles[0]), CastMember(names[1], roles[1])),
                    ))
                }
            }
        }
        rule.waitForIdle()
        val cards = rule.onAllNodesWithTag("credit-card").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, cards.size)
        assertEquals(1, cards.map { it.width }.distinct().size)
        assertEquals(cards[0].height, cards[1].height, 0f)
        assertEquals(1, cards.map { it.height }.distinct().size)
        for ((index, name) in names.withIndex()) {
            val card = cards[index]
            val initial = rule.onNodeWithText(name.take(1)).fetchSemanticsNode().boundsInRoot
            val lastText = if (index < 2) roles[index] else crewRoles[index - 2]
            val last = rule.onNodeWithText(lastText).fetchSemanticsNode().boundsInRoot
            val contentTop = initial.center.y - card.width * 72f / 136f
            assertEquals("portrait at top: $name", card.top + card.width * 12f / 136f, contentTop, 1f)
            val nameBox = rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
            val pictureBottom = initial.center.y + card.width * 72f / 136f
            assertEquals("text centred below portrait: $name", (pictureBottom + card.bottom) / 2,
                (nameBox.top + last.bottom) / 2, 1f)
            assertTrue("role after name: $name", last.top >= nameBox.bottom)
        }
        for (text in names + crewRoles + roles) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(text, layouts.isNotEmpty())
            assertFalse("full text: $text", layouts.single().hasVisualOverflow)
        }
    }
}
