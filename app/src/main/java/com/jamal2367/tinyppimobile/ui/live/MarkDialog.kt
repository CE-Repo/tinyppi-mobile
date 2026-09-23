package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget

/**
 * A tile that answers a press and a held finger.
 *
 * The press is what it always did - start the film, open the show - and the
 * hold asks whether the box should count the title as seen or as unseen (see
 * [MarkWatchedDialog]). The hold is felt as well as seen: the dialog opens
 * under a thumb that is covering the poster it was asked about.
 *
 * A tile that cannot be pressed - one waiting on another film - cannot be
 * held either: the one thing at a time the walls allow holds for both.
 */
internal fun Modifier.markable(
    enabled: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    combinedClickable(
        enabled = enabled,
        onLongClickLabel = stringResource(R.string.mark_title),
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onHold()
        },
        onClick = onClick,
    )
}

/**
 * The question a held finger asks: count this as seen, or as unseen.
 *
 * Both answers are always offered, whatever the title is now: somebody who
 * holds a film already ticked may be asking to take the tick off, and one who
 * holds a series half-watched may mean either. What is written is the box's
 * business - a series is every episode of it - and the walls are read again
 * afterwards, so the answer shows as the library now holds it.
 */
@Composable
internal fun MarkWatchedDialog(
    target: MarkTarget,
    onDismiss: () -> Unit,
    onMark: (watched: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = target.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MarkOption(
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(R.string.mark_watched),
                    onClick = { onMark(true) },
                )
                MarkOption(
                    icon = Icons.Rounded.VisibilityOff,
                    label = stringResource(R.string.mark_unwatched),
                    onClick = { onMark(false) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mark_cancel))
            }
        },
    )
}

@Composable
private fun MarkOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* What each kind of tile is called by when it is asked about. */

internal fun LibraryFilm.markTarget() = MarkTarget(MarkTarget.Kind.MOVIE, id, title)

internal fun LibraryShow.markTarget() = MarkTarget(MarkTarget.Kind.SHOW, id, title)

internal fun LibraryEpisode.markTarget() = MarkTarget(
    MarkTarget.Kind.EPISODE,
    id,
    listOf(code, title).filter { it.isNotBlank() }.joinToString(META_GAP),
)

internal fun ContinueItem.markTarget() = if (isEpisode) {
    MarkTarget(
        MarkTarget.Kind.EPISODE,
        id,
        listOf(show.ifEmpty { title }, code).filter { it.isNotBlank() }.joinToString(META_GAP),
    )
} else {
    MarkTarget(MarkTarget.Kind.MOVIE, id, title)
}
