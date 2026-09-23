package com.jamal2367.tinyppimobile.ui.live

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * What a press on a title asks, and what the first of its answers does.
 *
 * [play] names that answer - Play, Resume, or Open for a series, which is not
 * something that can be put on - and [onPlay] is what the press used to do by
 * itself before it asked.
 */
internal class TitleQuestion(
    val target: MarkTarget,
    @StringRes val play: Int,
    val onPlay: () -> Unit,
)

/**
 * The question a press on a film, a series or an episode asks: play it (open
 * it, for a series), or count it as seen or as unseen.
 *
 * Both marks are always offered, whatever the title is now: somebody who
 * presses a film already ticked may be asking to take the tick off, and one
 * who presses a series half-watched may mean either. What is written is the
 * box's business - a series is every episode of it - and the walls are read
 * again afterwards, so the answer shows as the library now holds it.
 */
@Composable
internal fun TitleDialog(
    question: TitleQuestion,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMark: (watched: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = question.target.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TitleOption(
                    icon = if (question.play == R.string.title_open) {
                        Icons.AutoMirrored.Rounded.OpenInNew
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    label = stringResource(question.play),
                    onClick = onPlay,
                )
                TitleOption(
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(R.string.mark_watched),
                    onClick = { onMark(true) },
                )
                TitleOption(
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
private fun TitleOption(
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
