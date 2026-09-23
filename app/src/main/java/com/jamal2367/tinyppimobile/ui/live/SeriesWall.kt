@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.FoldChevron
import com.jamal2367.tinyppimobile.util.MediaUrls

/**
 * The same card again for what Kodi knows as TV shows.
 *
 * A series is not something that can be put on - an episode is - so a press
 * here does not start anything: it opens the show, and the screen becomes that
 * show's episodes (see [episodeList]). What the poster carries instead of a
 * resume bar is how many episodes are still waiting, which is the one number a
 * shelf of series is scanned for.
 */
internal fun LazyListScope.seriesWall(
    shows: List<LibraryShow>,
    columns: Int,
    server: ServerConfig?,
    showArtwork: Boolean,
    opening: Int?,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (LibraryShow) -> Unit,
    key: String = "series-wall",
    title: @Composable (Int) -> String = { stringResource(R.string.series_all, it) },
) {
    shelfCard(
        key = key,
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
        title = { title(shows.size) },
        noMatch = { stringResource(R.string.series_no_match) },
        tiles = shows,
        columns = columns,
        rowKey = { row -> "$key-row-${row.first().id}" },
    ) { show ->
        ShowTile(
            show = show,
            poster = if (showArtwork) MediaUrls.showPoster(server, show) else null,
            opening = opening == show.id,
            // While one show's episodes are being read nothing else may be
            // pressed: two reads a second apart would leave whichever won on
            // the screen.
            enabled = opening == null,
            onOpen = { onOpen(show) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One series: its poster, its name and its year, and a press that opens it.
 *
 * A show seen right through wears the tick a watched film wears; one with
 * episodes still waiting wears how many instead, in the same corner. Either
 * way it is one mark on one corner of one picture, which is what a poster can
 * carry without stopping being a poster.
 */
@Composable
private fun ShowTile(
    show: LibraryShow,
    poster: String?,
    opening: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(enabled = enabled, onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtFrame(
            url = poster,
            ratio = POSTER_RATIO,
            progress = null,
            watched = show.watched,
            unseen = show.unseen,
            busy = opening,
            modifier = Modifier.fillMaxWidth(),
            rating = show.rating,
            ratingFrom = show.ratingFrom,
        )

        Text(
            text = show.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (show.year > 0) {
            Text(
                text = show.year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The episodes of the show somebody opened, and the way back out.
 *
 * Rows rather than a wall: an episode is chosen by its number and its name -
 * the still is a reminder of which one it was, not what it is recognised by -
 * and a row is where a number and a name fit. They arrive in the order they
 * were made, under the season they belong to.
 *
 * The way back sits at the top, where it is one press away however far down a
 * forty-episode show somebody has scrolled.
 */
internal fun LazyListScope.episodeList(
    open: OpenShow,
    server: ServerConfig?,
    showArtwork: Boolean,
    starting: Int?,
    openSeasons: Set<Int>,
    onBack: () -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (LibraryEpisode) -> Unit,
) {
    item(key = "episode-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.series_back))
            }
            // The show itself, before its seasons: a picture at the shape a
            // television is, which is what a series is recognised by once its
            // poster has been left behind on the wall.
            //
            // Only where the phone is showing artwork at all, and the frame
            // holds its place either way - a show the library scraped no
            // fanart for gets the stand-in rather than a heading that sits
            // higher than it does on every other show.
            if (showArtwork) {
                ArtFrame(
                    url = MediaUrls.showFanart(server, open.id, open.fanart),
                    ratio = WIDE_RATIO,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = open.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.series_episodes,
                        open.episodes.size,
                        open.episodes.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val rows: (List<LibraryEpisode>) -> Unit = { listing ->
        for (episode in listing) {
            item(key = "episode-${episode.id}") {
                EpisodeRow(
                    episode = episode,
                    still = if (showArtwork) MediaUrls.episodeStill(server, episode) else null,
                    starting = starting == episode.id,
                    enabled = starting == null,
                    onPlay = { onPlay(episode) },
                )
            }
        }
    }

    // Grouped rather than walked in order, so a library that files the same
    // season in two places still gets one fold for it - and one key for that
    // fold, where a second would be a crash rather than a heading out of
    // order.
    var ruled = false
    for ((number, episodes) in open.episodes.groupBy { it.season }) {
        if (number < 0) {
            // An episode the library files under no season at all goes under
            // no heading, and so into no fold either: there is nothing to call
            // it, and a fold with no name on it is a row that hides things.
            rows(episodes)
            ruled = true
            continue
        }

        val unfolded = number in openSeasons
        item(key = "season-$number") {
            SeasonHeading(
                label = if (number == 0) {
                    stringResource(R.string.series_specials)
                } else {
                    stringResource(R.string.series_season, number)
                },
                count = episodes.size,
                seconds = episodes.sumOf { it.duration },
                expanded = unfolded,
                ruled = ruled,
                onToggle = { onSeason(number) },
            )
        }
        ruled = true
        if (unfolded) rows(episodes)
    }
}

/**
 * One season's heading: what it is, how much of it there is, and the arrow
 * that unfolds it.
 *
 * The whole line answers to a finger and the arrow is what lights up for it -
 * one interaction source, held by the row and drawn by the arrow, the way the
 * cards on this screen fold (see FoldChevron). A season that only opened on
 * its arrow would feel broken along the rest of the line.
 */
@Composable
private fun SeasonHeading(
    label: String,
    count: Int,
    seconds: Int,
    expanded: Boolean,
    ruled: Boolean,
    onToggle: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }

    Column {
        // Ruled between one season and the next rather than around each: what
        // makes a stack of folds read as one list is the line between them.
        if (ruled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = press,
                    indication = null,
                    // Which way the press folds it, as the card headings say.
                    onClickLabel = stringResource(
                        if (expanded) R.string.card_collapse else R.string.card_expand,
                    ),
                    onClick = onToggle,
                )
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // How many, and how long that is altogether - which folded
                // away is the whole of what the season still has to say, and
                // the one thing somebody weighing an evening against a season
                // wants to know.
                Text(
                    text = listOfNotNull(
                        pluralStringResource(R.plurals.series_episodes, count, count),
                        runtimeLabel(seconds),
                    ).joinToString(META_GAP),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                FoldChevron(expanded, interactionSource = press, onClick = onToggle)
            }
        }
    }
}

/** One episode: its still, which one it is, its name, and a press that starts it. */
@Composable
private fun EpisodeRow(
    episode: LibraryEpisode,
    still: String?,
    starting: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPlay),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtFrame(
            url = still,
            ratio = WIDE_RATIO,
            progress = episode.progress,
            watched = episode.watched,
            busy = starting,
            modifier = Modifier.width(STILL_WIDTH),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Which episode it is and how long it runs, on the one line: both
            // are what somebody choosing between two of them is weighing.
            val numbered = listOfNotNull(
                episode.code.takeIf { it.isNotEmpty() },
                runtimeLabel(episode.duration),
            ).joinToString(META_GAP)
            if (numbered.isNotEmpty()) {
                Text(
                    text = numbered,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                // An episode the library has no name for is called by its
                // number, which is the only name it has ever had.
                text = episode.title.ifBlank { episode.code },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The shape a television picture is, which two things here are cut to: an
 * episode's still, and the cover a show's episodes stand under.
 */
private const val WIDE_RATIO = 16f / 9f

/**
 * How wide a column of stills is.
 *
 * A fixed width rather than a share of the row, so every still down the list
 * is the same size and the names beside them start on one line.
 */
private val STILL_WIDTH = 116.dp
