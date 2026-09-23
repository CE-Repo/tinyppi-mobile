@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.ui.theme.neutralTonalButtonColors

/**
 * The conversions this source can be put through.
 *
 * The buttons are the box's own: it sends the group that applies to the grade
 * that is playing, and the same set the on-screen dialog offers. HDR10+ and
 * HLG carry none, and then the whole card is left out rather than offering a
 * conversion nothing else offers either.
 *
 * Tonal and neutral: a filled button is the accent at full strength, and two of
 * those shouting from a card near the bottom of the screen outrank the play
 * button they are sitting under. Even in the tonal shade they were two more
 * coloured pills on a screen that had a dozen. These wear what the transport
 * wears throughout - the card's own ground, the same corner, the same height
 * and the same air between them - so the two cards read as one set of keys in
 * two groups rather than as two ideas of what a button is.
 *
 * Two to a line, sharing the width equally. They are alternatives to each
 * other, so one drawn wider than the next would be saying something about it
 * that is not true - and a box that offers four of them would otherwise put
 * four names in the width of one, each cut to nothing. An odd one at the end
 * takes the line to itself.
 */
@Composable
internal fun Vs10Card(
    vs10: Vs10State,
    canControl: Boolean,
    viewModel: LiveViewModel,
) {
    SectionCard(title = stringResource(R.string.live_vs10), foldId = FOLD_VS10) {
        if (!canControl) {
            Text(
                text = stringResource(R.string.live_control_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            vs10.options.chunked(VS10_PER_ROW).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { option ->
                        FilledTonalButton(
                            onClick = withKeyTick { viewModel.setMode(option.mode) },
                            shape = TRANSPORT_SHAPE,
                            colors = neutralTonalButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .height(TRANSPORT_BUTTON),
                        ) {
                            Vs10Label(option.label)
                        }
                    }
                }
            }
        }
    }
}

/** How many conversions the VS10 card puts on one line. */
private const val VS10_PER_ROW = 2

/**
 * A conversion, as `DV → SDR`, with the arrow drawn rather than typed.
 *
 * The box writes the label with an arrow character in it, and a character is at
 * the mercy of the font that has to draw it: the one the system picks here sets
 * it thin, small, and off the line of the words either side. Drawn as an icon
 * it takes the weight and the colour of the text around it and sits where an
 * arrow between two words should sit.
 *
 * A label with no arrow in it - or one written some other way - is set as it
 * came. Nothing here needs the split to succeed.
 */
@Composable
private fun Vs10Label(label: String) {
    val sides = ARROW.split(shortened(label)).map { it.trim() }

    if (sides.size != 2) {
        Text(
            text = shortened(label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    // Drawn, the arrow says nothing to a screen reader, which read the two
    // halves as "DV SDR" - so the pair is put back into a sentence for it.
    val spoken = stringResource(R.string.a11y_conversion, sides[0], sides[1])

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text = sides[0], maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(text = sides[1], maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A conversion's name, short enough for half a card.
 *
 * The box spells Dolby Vision out, and two buttons side by side have room for
 * about eight characters each before a name has to be cut. `DV` is what the
 * badge on the card above calls it anyway.
 */
private fun shortened(label: String): String =
    label.replace("Dolby Vision", "DV", ignoreCase = true)

/** However the box wrote the arrow between the two halves of a conversion. */
private val ARROW = Regex("""\s*(?:->|=>|\u2192|\u27F6)\s*""")

/** The labels the box sends, in the buttons they are drawn on. */
@PreviewLightDark
@Composable
private fun Vs10LabelPreview() {
    TinyPpiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
            ) {
                listOf("Dolby Vision → SDR", "Dolby Vision -> HDR10", "Original").chunked(VS10_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP)) {
                        row.forEach { label ->
                            FilledTonalButton(
                                onClick = {},
                                shape = TRANSPORT_SHAPE,
                                colors = neutralTonalButtonColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(TRANSPORT_BUTTON),
                            ) {
                                Vs10Label(label)
                            }
                        }
                    }
                }
            }
        }
    }
}
