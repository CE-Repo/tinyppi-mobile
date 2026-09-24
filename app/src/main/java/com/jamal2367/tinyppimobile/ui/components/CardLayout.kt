package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the reader has put the cards on each screen, which ones they took
 * off it, and which screen - if any - is being rearranged right now.
 *
 * Kept per screen and by the card's own name (the same name its fold is
 * remembered by), never by its heading: a heading is translated, and a card
 * moved to the top in German should still be at the top in English.
 *
 * The order is stored as the reader left it, and only ever applied to the
 * cards a screen actually has at the time (see [arrange]). A card that is
 * not there now - the conversions on a box without VS10, a panel the box only
 * sends for an HDR10 title - keeps its place for when it comes back, and a
 * card this app has never been told about arrives where it would have stood
 * anyway, next to the card it follows by default.
 *
 * Reached through a composition local, the way the folds are, because it is
 * drawn on five screens and nothing about it belongs to their view models.
 */
@Immutable
class CardLayout(
    private val orders: Map<String, List<String>> = emptyMap(),
    private val hidden: Set<String> = emptySet(),
    private val editing: String? = null,
    private val onOrder: (screen: String, order: List<String>) -> Unit = { _, _ -> },
    private val onHidden: (screen: String, id: String, hidden: Boolean) -> Unit = { _, _, _ -> },
    private val onEditing: (screen: String?) -> Unit = {},
    private val onReset: (screen: String) -> Unit = {},
) {
    /** The cards [present] on [screen], in the order the reader left them. */
    fun arrange(screen: String, present: List<String>): List<String> =
        arrangeCards(orders[screen].orEmpty(), present)

    /** The cards [present] on [screen] that are to be drawn, in order. */
    fun visible(screen: String, present: List<String>): List<String> =
        arrange(screen, present).filterNot { isHidden(screen, it) }

    fun isHidden(screen: String, id: String): Boolean = hiddenKey(screen, id) in hidden

    /** Whether any card on [screen] has been taken off it. */
    fun hasHidden(screen: String): Boolean = hidden.any { it.startsWith("$screen$SEPARATOR") }

    /** Whether [screen] is showing its cards to be rearranged. */
    fun isEditing(screen: String): Boolean = editing == screen

    fun edit(screen: String) = onEditing(screen)

    fun done() = onEditing(null)

    /**
     * Move [id] [by] places among the cards [present], writing down the order
     * the whole screen is now in.
     */
    fun move(screen: String, present: List<String>, id: String, by: Int) {
        val moved = moveCard(orders[screen].orEmpty(), present, id, by) ?: return
        onOrder(screen, moved)
    }

    fun setHidden(screen: String, id: String, hidden: Boolean) = onHidden(screen, id, hidden)

    /** Every card on [screen] back where it started, and every one back on it. */
    fun reset(screen: String) = onReset(screen)

    companion object {
        /** What joins a screen to a card in the stored set of hidden ones. */
        const val SEPARATOR = "/"

        fun hiddenKey(screen: String, id: String) = "$screen$SEPARATOR$id"
    }
}

/**
 * The cards [present] in the order [stored] puts them in.
 *
 * [present] is in the order the screen draws them by default. The cards named
 * in [stored] come first in that order; any card not named there is slotted
 * in straight after the card it follows by default, or at the top where it
 * follows nothing - so a card that is new to the reader lands where it would
 * have been had nothing ever been moved around it.
 */
internal fun arrangeCards(stored: List<String>, present: List<String>): List<String> {
    if (stored.isEmpty()) return present.distinct()
    val here = present.toSet()
    val named = stored.toSet()
    val result = stored.filter { it in here }.distinct().toMutableList()
    present.forEachIndexed { index, id ->
        if (id in named || id in result) return@forEachIndexed
        val after = present.subList(0, index).lastOrNull { it in result }
        result.add(if (after == null) 0 else result.indexOf(after) + 1, id)
    }
    return result
}

/**
 * The order to store once [id] has moved [by] places among the cards
 * [present], or null where it cannot move that way.
 *
 * Cards that are in [stored] but not on the screen now are kept, after the
 * ones that are, so a card that comes and goes with what is playing is not
 * forgotten by a move made while it was away.
 */
internal fun moveCard(stored: List<String>, present: List<String>, id: String, by: Int): List<String>? {
    val order = arrangeCards(stored, present).toMutableList()
    val from = order.indexOf(id)
    val to = from + by
    if (from < 0 || to !in order.indices) return null
    order.add(to, order.removeAt(from))
    val away = stored.filterNot { it in order }
    return order + away
}

/**
 * The layout in force here.
 *
 * Every card where it starts and nothing being edited by default, which is
 * what a preview and a test want; the app replaces it at its root.
 */
val LocalCardLayout = staticCompositionLocalOf { CardLayout() }

/**
 * Which screen the cards around here belong to, or null for cards that stay
 * where they are (the settings).
 *
 * Read by a card's heading: a long press on one is how a screen is opened
 * for rearranging, and only a heading on a screen that can be rearranged
 * answers to it.
 */
val LocalCardScreen = compositionLocalOf<String?> { null }
