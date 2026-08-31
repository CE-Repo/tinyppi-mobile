package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.accentText

/**
 * A block of related rows, usually titled - the unit every screen is built from.
 *
 * A card given no [title] is a card whose contents say what they are: the live
 * card is the one, and a word over a poster with the film's own name beside it
 * would be a label on a label. Where there is nothing to put on a heading at
 * all - no title, no arrow, nothing trailing - the heading is left out rather
 * than drawn empty, and the card starts where its contents start.
 *
 * A card given a [foldId] can be folded shut by the arrow on its heading, and
 * is found the way it was left on the next launch - the name is what it is
 * remembered by, so it has to outlive a translation and a rename of the
 * heading above it. A card without one is always open.
 *
 * [foldOpenByDefault] is where that fold starts, for a reader who has never
 * touched it. Cards start open; the transport starts shut, because what is
 * playing is what the screen is opened for and the buttons are wanted a good
 * deal less often than they take up room.
 *
 * A card given a [containerBrush] is painted with it instead of the flat
 * surface, which is how the live card wears the colour of the poster on it.
 * The brush goes on the inside: the card clips to its own shape, so a gradient
 * laid there follows the rounded corners without being told about them.
 *
 * Every card is outlined. The cards are the only thing on these screens and
 * they are stacked one under the next in the same colour as each other - a
 * hairline is what says where one ends and the next begins, on a dark theme
 * where the difference in fill is a shade or two.
 */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    foldId: String? = null,
    foldOpenByDefault: Boolean = true,
    containerBrush: Brush? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val folds = LocalCardFolds.current
    val expanded = foldId == null || folds.isExpanded(foldId, foldOpenByDefault)
    val heading = !title.isNullOrBlank() || trailing != null || foldId != null

    // The whole heading answers to a finger, and the arrow is what lights up
    // for it: one interaction source, held by the row and drawn by the arrow.
    // A card that only opens on its arrow feels broken along the rest of the
    // line, and a heading that flashes end to end reads as though the title
    // itself did something.
    val press = remember { MutableInteractionSource() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (containerBrush != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        // The card's own ground, one step lighter - not the outline colour,
        // which the poster's accent is mixed into: a hairline is there to say
        // where a card ends, and a coloured one says rather more.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .then(if (containerBrush != null) Modifier.background(containerBrush) else Modifier)
                .padding(16.dp),
        ) {
            if (heading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (foldId != null) {
                                Modifier.clickable(
                                    interactionSource = press,
                                    indication = null,
                                ) {
                                    folds.setExpanded(foldId, !expanded, foldOpenByDefault)
                                }
                            } else {
                                Modifier
                            }
                        ),
                    // Ends apart where there is a heading, and the arrow alone
                    // at the right where there is not.
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!title.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            // The one piece of the accent a heading wears: a
                            // tick down its left edge, the way the dashboard
                            // marks a section. It says what colour the screen
                            // is in without colouring the words.
                            Box(
                                modifier = Modifier
                                    .width(TICK_WIDTH)
                                    .height(TICK_HEIGHT)
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.accentText),
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                // Match the film title's accent colour.
                                color = MaterialTheme.colorScheme.accentText,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        trailing?.invoke()
                        if (foldId != null) {
                            FoldChevron(expanded, interactionSource = press) {
                                folds.setExpanded(foldId, !expanded, foldOpenByDefault)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = if (heading) 12.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * The arrow at the end of a heading, turned over while what it holds is open.
 *
 * The target is the arrow and a hair of room around it, clipped to a circle so
 * the ripple is the arrow lighting up rather than the whole heading flashing.
 *
 * Built out of the icon rather than out of an icon button, because a button
 * there reserves the height of a finger and pushes the heading down with it. A
 * heading is a line of text, and the arrow beside it has to sit on that line.
 *
 * Hand it the [interactionSource] of the row it sits on and the arrow lights up
 * for a press anywhere along that row, which is where the whole of the heading
 * gets to be a target without the whole of it flashing.
 *
 * Public because the live card folds a part of itself rather than the whole,
 * and it should be the same arrow doing it.
 */
@Composable
fun FoldChevron(
    expanded: Boolean,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    val press = interactionSource ?: remember { MutableInteractionSource() }

    Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = contentDescription ?: stringResource(
            if (expanded) R.string.card_collapse else R.string.card_expand
        ),
        // Match the heading and the film title's accent colour.
        tint = MaterialTheme.colorScheme.accentText,
        modifier = Modifier
            // Back out over the card's own padding: the room the ripple needs
            // is room the arrow would otherwise be indented by.
            .offset(x = 4.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = press,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(4.dp)
            .rotate(rotation),
    )
}

/**
 * One fact: what it is on the left, what it says on the right.
 *
 * Nothing is drawn for a value the add-on left empty - it leaves a reading out
 * rather than sending it blank, and a row drawn for one would claim a
 * measurement that was never taken.
 */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    valueColor: Color? = null,
) {
    if (value.isNullOrBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
        )
    }
}

/** A single number with its caption - the tiles across the top of a screen. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Card(
        modifier = modifier.height(88.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The accent tick down the left of a card's heading. */
private val TICK_WIDTH = 3.dp
private val TICK_HEIGHT = 16.dp
