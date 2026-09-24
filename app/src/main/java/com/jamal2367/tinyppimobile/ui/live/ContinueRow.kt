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
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (ContinueItem) -> Unit,
) {
    posterRowCard(
        key = "continue",
        title = { stringResource(R.string.continue_title) },
        items = items,
        itemKey = { it.key },
        columns = columns,
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
    ) { item, modifier ->
        ContinueTile(
            item = item,
            poster = if (showArtwork) MediaUrls.continuePoster(server, item) else null,
            starting = starting == item.key,
            // One press at a time, for the reason the wall gives.
            enabled = starting == null,
            onPlay = { onPlay(item) },
            modifier = modifier,
        )
    }
}

/**
 * A card holding one row of posters that scrolls sideways: the
 * continue-watching row, and the row of what arrived last.
 *
 * The tiles are given the wall's own width, so a row reads as part of the
 * same shelf as the wall under it. Not drawn at all with nothing to put on it.
 */
internal fun <T> LazyListScope.posterRowCard(
    key: String,
    title: @Composable () -> String,
    items: List<T>,
    itemKey: (T) -> Any,
    columns: Int,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    tile: @Composable (T, Modifier) -> Unit,
) {
    if (items.isEmpty()) return

    cardTop(
        key = key,
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
        title = title,
    )
    if (!expanded) return

    item(key = "$key-row") {
        val width = filmTileWidth(columns)
        CardSegment(CardPart.MIDDLE) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(FILM_GAP),
                modifier = Modifier.padding(top = CARD_INNER_GAP),
            ) {
                items(items = items, key = itemKey) { item ->
                    tile(item, Modifier.width(width))
                }
            }
        }
    }
    cardBottom(key)
}

/**
 * What arrived in the library last, newest first, as a row under the
 * continue-watching one: the films on the films screen, and on the series
 * screen the shows that gained an episode last (Kodi dates a show by its
 * newest episode).
 *
 * Taken from the wall's own list rather than asked for, so the row moves
 * whenever the wall does and costs the box nothing. An add-on older than the
 * date sends none, and there is no row.
 */
internal fun <T> newest(
    titles: List<T>,
    added: (T) -> String,
    id: (T) -> Int,
): List<T> = titles
    .filter { added(it).isNotEmpty() }
    // The date as Kodi writes it sorts as text in the order it happened; a
    // batch scanned in the same second falls back on the id, which Kodi hands
    // out in the order it added them.
    .sortedWith(compareByDescending(added).thenByDescending(id))
    .take(RECENT_LIMIT)

/**
 * How many titles the row of what arrived last holds. A phone shows three or
 * four at once, so ten is two or three flicks along: enough for last week's
 * films, and not so many that the row turns into a second wall.
 */
internal const val RECENT_LIMIT = 10

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
