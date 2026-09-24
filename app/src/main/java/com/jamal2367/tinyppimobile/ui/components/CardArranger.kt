package com.jamal2367.tinyppimobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.navigation.LocalIsCurrentPage

/** One card as the arranging list shows it: its name, and what it is filed by. */
data class ArrangeableCard(val id: String, val label: String)

/**
 * A screen opened for rearranging: a line saying what this is, with the way
 * to put everything back and the way out, and under it every card on the
 * screen as one row each - its name, the arrows that move it, and the eye
 * that takes it off the screen or puts it back.
 *
 * Rows rather than the cards themselves: a card is moved by its place among
 * the others, and a wall of five hundred posters between two arrows would put
 * the card below it a long way down the screen. A card taken off is still
 * listed, faded, so the way back to it is where it was.
 */
internal fun LazyListScope.cardArranger(
    screen: String,
    cards: List<ArrangeableCard>,
    layout: CardLayout,
) {
    val present = cards.map { it.id }
    val byId = cards.associateBy { it.id }
    val order = layout.arrange(screen, present)

    item(key = "arrange-head") {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.cards_edit),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cards_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { layout.reset(screen) }) {
                    Text(stringResource(R.string.cards_reset))
                }
                Button(onClick = layout::done) {
                    Text(stringResource(R.string.cards_done))
                }
            }
        }
    }

    itemsIndexed(items = order, key = { _, id -> "arrange-$id" }) { index, id ->
        val card = byId[id] ?: return@itemsIndexed
        ArrangeRow(
            label = card.label,
            hidden = layout.isHidden(screen, id),
            canMoveUp = index > 0,
            canMoveDown = index < order.lastIndex,
            onMoveUp = { layout.move(screen, present, id, -1) },
            onMoveDown = { layout.move(screen, present, id, 1) },
            onHidden = { layout.setHidden(screen, id, it) },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun ArrangeRow(
    label: String,
    hidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHidden: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (hidden) HIDDEN_ALPHA else 1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hidden) {
                    Text(
                        text = stringResource(R.string.cards_hidden),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cards_move_up),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cards_move_down),
                )
            }
            IconButton(onClick = { onHidden(!hidden) }) {
                Icon(
                    imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (hidden) R.string.cards_show else R.string.cards_hide,
                    ),
                )
            }
        }
    }
}

/**
 * What a screen shows in place of its cards when the reader has taken every
 * one of them off it: a line saying so, and the way back to them - there is
 * no heading left on the screen to be long-pressed.
 *
 * [message] is the line: Live keeps the card of what is playing whatever is
 * taken off it, and says the others are gone rather than all of them.
 */
internal fun LazyListScope.allCardsHidden(
    screen: String,
    layout: CardLayout,
    @StringRes message: Int = R.string.cards_all_hidden,
) {
    item(key = "arrange-all-hidden") {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        ) {
            Text(
                text = stringResource(message),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { layout.edit(screen) }) {
                Text(stringResource(R.string.cards_edit))
            }
        }
    }
}

/**
 * The system's back gesture ends the rearranging, rather than leaving the
 * screen or the app while the list of rows is up. Only on the tab in front,
 * for the reason the series screen gives about its own back handler.
 */
@Composable
internal fun ArrangeBackHandler(screen: String, layout: CardLayout) {
    BackHandler(enabled = layout.isEditing(screen) && LocalIsCurrentPage.current) {
        layout.done()
    }
}

private const val HIDDEN_ALPHA = 0.5f

/** What each screen's cards are arranged under. */
object CardScreens {
    const val LIVE = "live"
    const val METADATA = "metadata"
    const val FILMS = "films"
    const val SERIES = "series"
    const val HISTORY = "history"
}
