package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.ui.theme.accentText

/**
 * One of the panels the overlay prints its readings in, as a card.
 *
 * Nothing here is composed by this app: the add-on renders each row the way
 * the on-screen overlay draws it - same formatting, same units, same labels,
 * translated through Kodi's own string table - and groups them the way the
 * overlay's panels are grouped. So a row that gains a unit on the television
 * gains it here, and neither can drift from the other.
 *
 * A row the stream does not carry is left out by the add-on rather than sent
 * blank, and a whole card whose source cannot carry it goes the same way,
 * which is what keeps this readable on a phone.
 *
 * These used to be a screen of their own, reached by a tab. They are the same
 * readings about the same film as the card at the top of the live screen, and
 * a tab between the two made a reader cross the app to answer a question the
 * poster had raised - so they are the foot of that screen now, under the
 * transport and the conversions.
 */
@Composable
fun GroupCard(group: InfoGroup) {
    // A row the box sent with nothing in it is dropped here rather than inside
    // the row: a rule is drawn between one row and the next, and a row that
    // draws nothing would leave its rule behind.
    val rows = group.rows.filter { it.value.isNotBlank() }

    // The box names its own groups, and the name is what the fold is filed
    // under - a group that comes back next week comes back folded as it was.
    SectionCard(title = group.title, foldId = "details.${group.id}") {
        rows.forEachIndexed { index, row ->
            // Ruled between the rows and not around them: what makes a list of
            // name-and-value pairs read as a table is the line between one pair
            // and the next, and a card already draws the outside edge.
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }

            ReadingRow(
                label = row.label,
                value = row.value,
                detail = row.detail,
            )
        }
    }
}

/**
 * One reading: its name on the left, its value on the right, and the
 * parenthesised extra beside the value rather than under it.
 *
 * The extra is what the overlay draws in its accent colour - a decoder behind
 * a codec, an average behind a live bitrate. On one line with the reading it
 * qualifies, because that is what it does: read down a column of two-line rows
 * it looked like a second reading of its own.
 *
 * The two sit on a common baseline, which is what keeps a small word beside a
 * larger one from looking dropped.
 *
 * The reading itself lights up as it moves, where it is one of the two that
 * move at all - see [movesWithThePicture].
 */
@Composable
private fun ReadingRow(label: String, value: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        Row(
            modifier = Modifier.weight(1.1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .alignByBaseline()
                    // Yields to the extra beside it rather than pushing it off
                    // the card: a long value wraps in the room that is left.
                    .weight(1f, fill = false)
                    .then(
                        if (label.movesWithThePicture()) {
                            Modifier.flashOnChange(value)
                        } else {
                            Modifier
                        }
                    ),
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.accentText,
                    textAlign = TextAlign.End,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/**
 * Whether a reading changes with the frame on screen rather than with the file.
 *
 * The two L1 pairs are read out of the picture itself and move several times a
 * second. Everything else here is a property of the file, of the output or of
 * the box, and changes once a film if at all - a reading that lights up twice
 * an hour is not being watched for it, and one that lights up on every scene
 * is.
 *
 * Matched on the name rather than on the row's id, because the ids are the
 * numbers of Kodi's own strings and these two rows are known by what they are
 * called. A box that renames them stops lighting them up, which is a screen
 * gone quiet rather than one gone wrong.
 */
private fun String.movesWithThePicture(): Boolean =
    startsWith("L1 Lum", ignoreCase = true) || startsWith("L1 PQ", ignoreCase = true)
