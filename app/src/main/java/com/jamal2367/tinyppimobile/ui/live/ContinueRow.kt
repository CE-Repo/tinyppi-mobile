@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.util.MediaUrls

/**
 * The titles the box was stopped in the middle of, the last one seen first,
 * as a card of their own at the very top of a shelf: the quickest way back
 * into whatever was on.
 *
 * A row that scrolls sideways rather than more of the wall: it is a handful of
 * posters, the one somebody is after is nearly always the first, and a row
 * costs the screen one line of posters rather than several. The tiles are the
 * wall's own at the wall's own width, so the two cards read as one shelf.
 *
 * It folds like every other card, and is not there at all where there is
 * nothing to resume - an empty card with a heading over it would be a card
 * saying nothing.
 */
internal fun LazyListScope.continueCard(
    items: List<ContinueItem>,
    columns: Int,
    server: ServerConfig?,
    showArtwork: Boolean,
    starting: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (ContinueItem) -> Unit,
) {
    if (items.isEmpty()) return

    cardTop(
        key = "continue",
        gapAbove = false,
        expanded = expanded,
        onToggle = onToggle,
        title = { stringResource(R.string.continue_title) },
    )
    if (!expanded) return

    item(key = "continue-row") {
        val width = filmTileWidth(columns)
        CardSegment(CardPart.MIDDLE) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(FILM_GAP),
                modifier = Modifier.padding(top = CARD_INNER_GAP),
            ) {
                items(items = items, key = { it.key }) { item ->
                    ContinueTile(
                        item = item,
                        poster = if (showArtwork) MediaUrls.continuePoster(server, item) else null,
                        starting = starting == item.key,
                        // One press at a time, for the reason the wall gives.
                        enabled = starting == null,
                        onPlay = { onPlay(item) },
                        modifier = Modifier.width(width),
                    )
                }
            }
        }
    }
    cardBottom("continue")
}

/**
 * One title on the row: its poster with how far the box got along the bottom,
 * and under it the name.
 *
 * An episode stands as its show - the show's poster, the show's name and the
 * show's rating, which is what somebody scanning the row is looking for - with
 * which episode it is on the line beneath.
 */
@Composable
private fun ContinueTile(
    item: ContinueItem,
    poster: String?,
    starting: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(enabled = enabled, onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtFrame(
            url = poster,
            ratio = POSTER_RATIO,
            progress = item.progress,
            busy = starting,
            modifier = Modifier.fillMaxWidth(),
            rating = item.rating,
            ratingFrom = item.ratingFrom,
        )

        Text(
            text = if (item.isEpisode) item.show.ifEmpty { item.title } else item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = if (item.isEpisode) {
            listOf(item.code, item.title.takeIf { item.show.isNotEmpty() }.orEmpty())
                .filter { it.isNotEmpty() }
                .joinToString(META_GAP)
        } else {
            listOfNotNull(
                item.year.takeIf { it > 0 }?.toString(),
                runtimeLabel(item.duration),
            ).joinToString(META_GAP)
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
