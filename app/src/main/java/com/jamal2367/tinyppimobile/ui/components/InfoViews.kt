package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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

/**
 * A titled block of related rows - the unit every screen here is built from.
 *
 * A card given a [foldId] can be folded shut by the arrow on its heading, and
 * is found the way it was left on the next launch - the name is what it is
 * remembered by, so it has to outlive a translation and a rename of the
 * heading above it. A card without one is always open.
 *
 * A card given a [containerBrush] is painted with it instead of the flat
 * surface, which is how the live card wears the colour of the poster on it.
 * The brush goes on the inside: the card clips to its own shape, so a gradient
 * laid there follows the rounded corners without being told about them.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    foldId: String? = null,
    containerBrush: Brush? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val folds = LocalCardFolds.current
    val expanded = foldId == null || folds.isExpanded(foldId)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (containerBrush != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .then(if (containerBrush != null) Modifier.background(containerBrush) else Modifier)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    trailing?.invoke()
                    if (foldId != null) {
                        FoldChevron(expanded) { folds.setExpanded(foldId, !expanded) }
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
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
 * Public because the live card folds a part of itself rather than the whole,
 * and it should be the same arrow doing it.
 */
@Composable
fun FoldChevron(
    expanded: Boolean,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = contentDescription ?: stringResource(
            if (expanded) R.string.card_collapse else R.string.card_expand
        ),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            // Back out over the card's own padding: the room the ripple needs
            // is room the arrow would otherwise be indented by.
            .offset(x = 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
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
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
        )
    }
}

/**
 * One reading as a name, a bar and a figure: processor load, cache level, the
 * share of a maximum that a number is.
 *
 * The bar is what makes a number readable at a glance across the room, which
 * is where a phone showing this usually is.
 */
@Composable
fun StatBar(
    label: String,
    count: Int,
    maxCount: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
    val animated by animateFloatAsState(targetValue = fraction, label = "statBar")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(PillShape)
                    .background(color),
            )
        }
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
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
