@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.util.Formatters

/**
 * A title whose details were asked for, and where reading them has got to:
 * [details] is null while the box is asked, and [failed] once it could not
 * say.
 */
data class DetailsUiState(
    val target: MarkTarget,
    val details: TitleDetails? = null,
    val failed: Boolean = false,
)

/**
 * Everything the library holds about one title, read when it is asked for
 * and shown as text: the plot, the people and the ratings, which a tile has
 * no room for.
 *
 * The read is the view model's (see [LiveViewModel.openDetails]), so a
 * details view closed before it answers asks nothing more of the box, and
 * one that fails says so where it was asked for rather than along the
 * bottom of the screen.
 */
@Composable
internal fun DetailsDialog(
    state: DetailsUiState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.target.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            val details = state.details
            if (details != null) {
                DetailsBody(details)
            } else if (state.failed) {
                Text(stringResource(R.string.details_failed))
            } else {
                LoadingIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.details_close))
            }
        },
    )
}

@Composable
private fun DetailsBody(details: TitleDetails) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        val meta = listOf(
            if (details.year > 0) details.year.toString() else "",
            runtimeLabel(details.duration).orEmpty(),
            details.mpaa,
        ).filter { it.isNotEmpty() }.joinToString(META_GAP)
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val formats = listOf(
            FormatNames.video(if (details.dvProfile.isEmpty()) details.video else FormatNames.PROFILE + details.dvProfile),
            FormatNames.of(FormatNames.RESOLUTION, details.resolution),
            FormatNames.of(FormatNames.AUDIO, details.audio),
        ).filter { it.isNotEmpty() }
        if (formats.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (format in formats) FormatBadge(text = format)
            }
        }
        if (details.tagline.isNotEmpty()) {
            Text(text = details.tagline, fontStyle = FontStyle.Italic)
        }
        if (details.plot.isNotEmpty()) {
            Text(text = details.plot, style = MaterialTheme.typography.bodyMedium)
        }
        for ((heading, names) in facts(details)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = names, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The rows under the plot, each a heading and its names; empty ones are left out. */
@Composable
private fun facts(details: TitleDetails): List<Pair<String, String>> {
    val cast = details.cast.map { if (it.role.isEmpty()) it.name else "${it.name} (${it.role})" } +
        listOfNotNull("…".takeIf { details.castMore })
    val ratings = details.ratings.map {
        "${RATING_NAMES[it.source] ?: it.source} ${Formatters.rating(it.rating)}"
    } + listOfNotNull(
        details.userrating.takeIf { it > 0 }?.let { "${stringResource(R.string.live_rating_mine)} $it" },
        details.top250.takeIf { it > 0 }?.let { stringResource(R.string.live_rating_top250, it) },
    )
    return listOf(
        stringResource(R.string.details_genre) to details.genres,
        stringResource(R.string.live_credit_director) to details.directors,
        stringResource(R.string.details_writer) to details.writers,
        stringResource(R.string.details_studio) to details.studios,
        stringResource(R.string.details_ratings) to ratings,
        stringResource(R.string.live_credit_cast) to cast,
    ).filter { it.second.isNotEmpty() }.map { (heading, names) -> heading to names.joinToString(", ") }
}
