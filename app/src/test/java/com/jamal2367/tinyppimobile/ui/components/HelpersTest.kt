package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.ui.live.dismissSearch
import com.jamal2367.tinyppimobile.ui.live.matching
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination.FILMS
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination.HISTORY
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination.LIVE
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination.METADATA
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination.SETTINGS
import com.jamal2367.tinyppimobile.ui.navigation.pageKey
import com.jamal2367.tinyppimobile.ui.pageAfterChange
import com.jamal2367.tinyppimobile.ui.settledOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain functions under the screens. */
class HelpersTest {

    private val row = listOf(LIVE, FILMS, HISTORY, SETTINGS)

    @Test
    fun `a settled page names its tab, or keeps the one open`() {
        assertEquals(FILMS, settledOn(row, 1, LIVE))
        assertEquals(HISTORY, settledOn(row, 9, HISTORY))
    }

    @Test
    fun `a tab that leaves the row hands over to the one before it`() {
        assertEquals(2, pageAfterChange(row, HISTORY))
        assertEquals(0, pageAfterChange(row, METADATA))
        assertEquals(1, pageAfterChange(row, TopLevelDestination.SERIES))
    }

    @Test
    fun `a page is keyed by its route, or its index once gone`() {
        assertEquals("films", pageKey(row, 1))
        assertEquals(7, pageKey(row, 7))
    }

    @Test
    fun `cards keep the stored order and slot new ones in`() {
        assertEquals(listOf("a", "b"), arrangeCards(emptyList(), listOf("a", "b", "a")))
        assertEquals(listOf("b", "c", "a"), arrangeCards(listOf("b", "a", "b"), listOf("a", "b", "c", "c")))
        assertEquals(listOf("x", "b"), arrangeCards(listOf("b"), listOf("x", "b")))
        assertNull(moveCard(emptyList(), listOf("a", "b"), "a", -1))
        assertNull(moveCard(emptyList(), listOf("a", "b"), "z", 1))
        assertEquals(listOf("b", "a", "gone"), moveCard(listOf("a", "b", "gone"), listOf("a", "b"), "a", 1))

        val moved = mutableListOf<List<String>>()
        val layout = CardLayout(onOrder = { _, order -> moved += order })
        layout.move("live", listOf("a", "b"), "a", -1)
        layout.move("live", listOf("a", "b"), "a", 1)
        assertEquals(listOf(listOf("b", "a")), moved)
        assertTrue(CardLayout(hidden = setOf("live/a")).hasHidden("live"))
        // The defaults do nothing.
        CardLayout().apply { setHidden("live", "a", true); edit("live"); done(); reset("live") }
        CardFolds().setExpanded("x", false)
    }

    @Test
    fun `a search matches the title or the year, and a cleared one keeps the keyboard question open`() {
        val films = listOf(LibraryFilm(1, "Heat", 1995), LibraryFilm(2, "Alien", 0), LibraryFilm(3, "Dune", 2024))
        assertEquals(films, matching(films, " "))
        assertEquals(listOf(films[0]), matching(films, "heat"))
        assertEquals(listOf(films[0]), matching(films, "199"))
        assertEquals(emptyList<LibraryFilm>(), matching(films, "zzz"))
        assertEquals(listOf(films[2]), matching(films, "202"))

        var cleared = 0
        val focus = object : FocusManager {
            override fun clearFocus(force: Boolean) { cleared++ }
            override fun moveFocus(focusDirection: FocusDirection): Boolean = false
        }
        dismissSearch(focus, null)
        assertEquals(1, cleared)
    }
}
