package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.ui.components.CardLayout
import com.jamal2367.tinyppimobile.ui.components.arrangeCards
import com.jamal2367.tinyppimobile.ui.components.moveCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the cards on a screen come out in the order the reader left them, and
 * that the cards a screen has today and did not have yesterday - a panel the
 * box sends only for some titles, a card a later build adds - land where they
 * would have stood had nothing been moved.
 */
class CardLayoutTest {

    private val live = listOf("now", "controls", "vs10", "video", "audio")

    @Test
    fun nothingStoredIsTheDefaultOrder() {
        assertEquals(live, arrangeCards(emptyList(), live))
    }

    @Test
    fun theStoredOrderIsApplied() {
        val stored = listOf("audio", "now", "controls", "vs10", "video")
        assertEquals(stored, arrangeCards(stored, live))
    }

    @Test
    fun aCardNotOnTheScreenNowIsLeftOut() {
        val stored = listOf("audio", "hdr", "now", "controls", "vs10", "video")
        assertEquals(listOf("audio", "now", "controls", "vs10", "video"), arrangeCards(stored, live))
    }

    @Test
    fun aNewCardFollowsTheCardItFollowsByDefault() {
        // "hdr" has never been seen: it arrives straight after "audio", its
        // neighbour in the order the box sends the panels in, wherever the
        // reader has put that one.
        val stored = listOf("audio", "now", "controls", "vs10", "video")
        val present = live + "hdr"
        assertEquals(
            listOf("audio", "hdr", "now", "controls", "vs10", "video"),
            arrangeCards(stored, present),
        )
    }

    @Test
    fun aNewCardAtTheTopStaysAtTheTop() {
        val stored = listOf("controls", "now")
        assertEquals(listOf("first", "controls", "now"), arrangeCards(stored, listOf("first", "now", "controls")))
    }

    @Test
    fun movingSwapsWithTheNeighbour() {
        assertEquals(
            listOf("now", "vs10", "controls", "video", "audio"),
            moveCard(emptyList(), live, "vs10", -1),
        )
        assertEquals(
            listOf("controls", "now", "vs10", "video", "audio"),
            moveCard(emptyList(), live, "now", 1),
        )
    }

    @Test
    fun movingPastEitherEndIsRefused() {
        assertNull(moveCard(emptyList(), live, "now", -1))
        assertNull(moveCard(emptyList(), live, "audio", 1))
        assertNull(moveCard(emptyList(), live, "missing", 1))
    }

    @Test
    fun aCardAwayFromTheScreenIsRememberedThroughAMove() {
        // Moved during a Dolby Vision title, with the HDR10 panel absent: the
        // place the reader gave that panel earlier is not thrown away.
        val stored = listOf("hdr", "now", "controls", "vs10", "video", "audio")
        val moved = moveCard(stored, live, "audio", -1)!!
        assertTrue("hdr" in moved)
        assertEquals(listOf("now", "controls", "vs10", "audio", "video"), moved.filter { it in live })
    }

    @Test
    fun hiddenCardsAreKeptPerScreen() {
        val layout = CardLayout(hidden = setOf(CardLayout.hiddenKey("films", "shelf.films")))
        assertTrue(layout.isHidden("films", "shelf.films"))
        assertFalse(layout.isHidden("series", "shelf.films"))
        assertTrue(layout.hasHidden("films"))
        assertFalse(layout.hasHidden("series"))
        assertEquals(
            listOf("shelf.films.continue"),
            layout.visible("films", listOf("shelf.films.continue", "shelf.films")),
        )
    }

    @Test
    fun editingIsOneScreenAtATime() {
        val layout = CardLayout(editing = "live")
        assertTrue(layout.isEditing("live"))
        assertFalse(layout.isEditing("films"))
    }
}
