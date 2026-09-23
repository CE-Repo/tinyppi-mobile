@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle
import com.jamal2367.tinyppimobile.ui.components.SectionHeading
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge

/**
 * The walls of posters the box's library is offered as, and everything they
 * are drawn out of.
 *
 * Three screens draw these: the live screen while the box is playing nothing,
 * and the two shelves in the navigation bar, which are the same walls reached
 * on purpose rather than come across. Only the drawing of them is here - the
 * state they read is the live view model's, because a shelf read on one screen
 * is the same shelf on the next, and holding it twice would be asking the box
 * twice.
 */

/**
 * One shelf as a card: its heading and the wall a row at a time.
 *
 * One builder for both shelves. The films and the series are the same offer
 * made twice, and a second card drawn differently would read as a different
 * screen rather than a second shelf.
 *
 * The box that narrows it down is not in here: it sits at the very top of the
 * screen, above the continue-watching row as well (see [shelfTop]), because
 * it is where somebody who came to look for one title starts.
 */
internal fun <T> LazyListScope.shelfCard(
    key: String,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: @Composable () -> String,
    noMatch: @Composable () -> String,
    tiles: List<T>,
    columns: Int,
    rowKey: (List<T>) -> String,
    tile: @Composable RowScope.(T) -> Unit,
) {
    cardTop(key, gapAbove, expanded, onToggle, title)
    if (!expanded) return

    if (tiles.isEmpty()) {
        // A search nothing answers. The library itself being empty is handled
        // before the card is drawn at all, where the screen has room to say so
        // properly.
        item(key = "$key-empty") {
            CardSegment(CardPart.MIDDLE) {
                Text(
                    text = noMatch(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                )
            }
        }
    } else {
        items(items = tiles.chunked(columns), key = rowKey) { row ->
            CardSegment(CardPart.MIDDLE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FILM_GAP),
                    modifier = Modifier.padding(top = FILM_GAP),
                ) {
                    for (entry in row) tile(entry)
                    // The last row is rarely full. Without this its tiles
                    // would be spread across the width instead of standing
                    // under the ones above them.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    cardBottom(key)
}

/**
 * The top of a card on a shelf: the gap that parts it from the card above,
 * and the heading - the same heading every other card in the app wears, the
 * accent tick, the title and the arrow that folds it.
 *
 * Folded, this is the whole card, rounded at the bottom as well.
 */
internal fun LazyListScope.cardTop(
    key: String,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: @Composable () -> String,
) {
    if (gapAbove) {
        item(key = "$key-gap") { Spacer(Modifier.height(CardGap)) }
    }
    item(key = "$key-heading") {
        CardSegment(if (expanded) CardPart.TOP else CardPart.WHOLE) {
            Box(
                modifier = Modifier.padding(
                    top = CARD_PADDING,
                    bottom = if (expanded) 0.dp else CARD_PADDING,
                ),
            ) {
                SectionHeading(
                    title = title(),
                    expanded = expanded,
                    onToggle = onToggle,
                )
            }
        }
    }
}

/** The foot of an open card: its padding, and its rounded bottom edge. */
internal fun LazyListScope.cardBottom(key: String) {
    item(key = "$key-foot") {
        CardSegment(CardPart.BOTTOM) {
            Spacer(Modifier.height(CARD_PADDING))
        }
    }
}

/** Which part of a card a [CardSegment] is. */
internal enum class CardPart { TOP, MIDDLE, BOTTOM, WHOLE }

/**
 * One slice of a card that is laid out a row at a time.
 *
 * A shelf cannot be one `SectionCard`: a card is laid out in one piece, and
 * one holding five hundred posters would lay out five hundred posters. So the
 * card is drawn in slices, each an item of the screen's own lazy list, and
 * each slice paints the part of the card that falls inside it - the rounded
 * top, the two sides, the rounded bottom - out of one card shape drawn
 * taller than the slice and cut to it. The slices meet with no gap between
 * them, so what the eye sees is one card in the same colour, with the same
 * hairline and the same corners as every card elsewhere in the app.
 */
@Composable
internal fun CardSegment(
    part: CardPart,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val fill = MaterialTheme.colorScheme.surfaceContainerLow
    val line = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                // Well past any corner, so the corner of a slice that is not
                // meant to have one is always outside it.
                val beyond = 64.dp.toPx()
                val stroke = 1.dp.toPx()
                val (top, height) = when (part) {
                    CardPart.TOP -> 0f to size.height + beyond
                    CardPart.MIDDLE -> -beyond to size.height + beyond * 2
                    CardPart.BOTTOM -> -beyond to size.height + beyond
                    CardPart.WHOLE -> 0f to size.height
                }
                // The hairline inside the edge, the way a card's border is.
                val outline = shape.createOutline(
                    Size(size.width - stroke, height - stroke),
                    layoutDirection,
                    this,
                )
                translate(left = stroke / 2, top = top + stroke / 2) {
                    drawOutline(outline, fill)
                    drawOutline(outline, line, style = Stroke(stroke))
                }
            }
            .padding(horizontal = CARD_PADDING),
        content = content,
    )
}

/**
 * The head of a shelf screen: its name, and under it the box the shelf is
 * narrowed down with - above everything else on the screen, the
 * continue-watching row included, because it is where somebody who came to
 * look for one title starts.
 */
internal fun LazyListScope.shelfTop(
    title: @Composable () -> String,
    searchLabel: @Composable () -> String,
    search: String,
    onSearch: (String) -> Unit,
) {
    item(key = "shelf-title") {
        ScreenTitle(text = title(), modifier = Modifier.padding(bottom = 14.dp))
    }
    item(key = "shelf-search") {
        WallSearch(
            label = searchLabel(),
            search = search,
            onSearch = onSearch,
            modifier = Modifier.padding(bottom = CardGap),
        )
    }
}

/**
 * The box a shelf is narrowed down with: a pill with a magnifier in it, in
 * the colour of the cards under it.
 *
 * The cross empties it, and only while there is something to empty: over a
 * field nobody has typed in it is a control that does nothing. It puts the
 * whole shelf back and then gets out of the way - field and keyboard both.
 * Somebody who clears a search is done with it; one who meant to search for
 * something else can press the field again, which is one press against the
 * screenful of posters a keyboard would otherwise go on covering.
 */
@Composable
private fun WallSearch(
    label: String,
    search: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The cards' own ground and hairline, so the box reads as one more piece
    // of the same screen rather than a control laid on top of it.
    val fill = MaterialTheme.colorScheme.surfaceContainerLow
    val line = MaterialTheme.colorScheme.surfaceContainerHigh

    TextField(
        value = search,
        onValueChange = onSearch,
        singleLine = true,
        shape = PillShape,
        placeholder = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (search.isEmpty()) {
            null
        } else {
            {
                IconButton(
                    onClick = {
                        onSearch("")
                        dismissSearch(focus, keyboard)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.library_search_clear),
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        // The key the keyboard offers instead of a newline, and what it does:
        // nothing but close, because the wall narrowed itself as the letters
        // arrived.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { dismissSearch(focus, keyboard) },
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, line, PillShape),
    )
}

/**
 * Let the search field go, and the keyboard with it.
 *
 * Clearing the focus is usually enough to put the keyboard away, but only
 * usually: a field disposed from under it - which is what scrolling a wall
 * does to the heading it sits in - can leave the keyboard up over a screen
 * with nothing left to type into. Both, then, and neither costs anything when
 * there was nothing focused to begin with.
 */
internal fun dismissSearch(focus: FocusManager, keyboard: SoftwareKeyboardController?) {
    focus.clearFocus()
    keyboard?.hide()
}

/**
 * The films whose name - or year - answers what has been typed.
 *
 * Matched here rather than by the box: the whole library is already on the
 * phone, and a round trip per keystroke to narrow a list this app is holding
 * would be a slower answer to a question already in front of it.
 */
internal fun matching(films: List<LibraryFilm>, search: String): List<LibraryFilm> =
    matching(films, search, { it.title }, { it.year })

/**
 * The same, for any shelf whose tiles carry a name and a year.
 *
 * Written once rather than once per shelf: the films and the series are
 * narrowed by the same question, and two copies of it would be two answers to
 * keep the same.
 */
internal fun <T> matching(
    items: List<T>,
    search: String,
    title: (T) -> String,
    year: (T) -> Int,
): List<T> {
    val needle = search.trim()
    if (needle.isEmpty()) return items
    return items.filter { item ->
        title(item).contains(needle, ignoreCase = true) ||
            (year(item) > 0 && year(item).toString().contains(needle))
    }
}

/**
 * How many posters go across, from how wide the screen is.
 *
 * A count rather than a width, because the row has to divide the screen
 * exactly: tiles that each take a fixed width leave a ragged edge down the
 * right of every row. Three on a phone, more on a tablet or a phone held
 * sideways - worked out from the same poster width the card at the top of this
 * screen uses.
 */
@Composable
internal fun filmColumns(): Int {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    // Inside a card now, so its padding comes off both sides as well.
    val usable = width - ScreenEdge * 2 - CARD_PADDING * 2
    return (usable / (FILM_TILE_MIN + FILM_GAP)).toInt().coerceIn(3, 6)
}

/**
 * How wide one tile of a wall of [columns] is, for a row that has to match it
 * without being one of the wall's rows.
 */
@Composable
internal fun filmTileWidth(columns: Int): Dp {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val usable = width - ScreenEdge * 2 - CARD_PADDING * 2
    return (usable - FILM_GAP * (columns - 1)) / columns
}

/**
 * The air between two posters.
 *
 * The card gap, because the rows of the wall are items of the screen's own
 * list and are spaced by it from above and below: a narrower gap across than
 * down would have drawn a grid with two different rhythms in it.
 */
internal val FILM_GAP = CardGap

/** A shelf card's padding, and the room between its heading and what it holds - a `SectionCard`'s. */
private val CARD_PADDING = 16.dp
internal val CARD_INNER_GAP = 12.dp
private val FILM_TILE_MIN = 104.dp
internal val RESUME_BAR = 3.dp
