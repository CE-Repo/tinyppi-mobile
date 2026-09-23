@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.util.MediaUrls

/**
 * What the box could be playing, as a card of posters.
 *
 * A wall of posters rather than a list of titles: a film is recognised by its
 * cover long before its name has been read.
 *
 * Laid out as rows of tiles inside the screen's own list rather than as a grid
 * of its own, and the card around them drawn a row at a time (see
 * [CardSegment]). A grid inside a scrolling column is two things that scroll,
 * and a card holding five hundred posters in one piece is five hundred posters
 * laid out at once; chunked into rows it stays lazy, so a library of five
 * hundred films draws the six tiles on screen and asks the box for six posters.
 *
 */
internal fun LazyListScope.filmWall(
    films: List<LibraryFilm>,
    key: String = "film-wall",
    title: @Composable (Int) -> String = { stringResource(R.string.library_all, it) },
    columns: Int,
    server: ServerConfig?,
    showArtwork: Boolean,
    starting: Int?,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (LibraryFilm) -> Unit,
) {
    shelfCard(
        key = key,
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
        title = { title(films.size) },
        noMatch = { stringResource(R.string.library_no_match) },
        tiles = films,
        columns = columns,
        rowKey = { row -> "$key-row-${row.first().id}" },
    ) { film ->
        FilmTile(
            film = film,
            poster = if (showArtwork) MediaUrls.filmPoster(server, film) else null,
            starting = starting == film.id,
            // While one film is on its way nothing else may be pressed: two
            // Player.Opens a second apart leave the box playing whichever
            // won, which is not the one the second press was for.
            enabled = starting == null,
            onPlay = { onPlay(film) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One film: its poster, its name and its year, and a press that starts it.
 *
 * A film the box left half-watched carries a bar along the bottom of the
 * poster, and pressing it resumes it where it was - the box decides that from
 * its own library, the same as pressing the film in Kodi's own window. One
 * already seen wears a tick in the corner of the picture.
 *
 * A mark rather than a dimmed poster, which is what this was: dimming says
 * "not this one" about whatever it touches, and on a shelf where most of the
 * films have been watched that is most of the wall greyed out - which reads as
 * artwork that failed to load rather than as an answer. The mark costs one
 * corner of one poster and says the same thing about the smaller group.
 */
@Composable
private fun FilmTile(
    film: LibraryFilm,
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
            progress = film.progress,
            watched = film.watched,
            busy = starting,
            modifier = Modifier.fillMaxWidth(),
            rating = film.rating,
            ratingFrom = film.ratingFrom,
        )

        Text(
            text = film.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // When it came out and how long it runs, on one line: two lines of
        // small grey type under every poster would be a wall of them.
        val meta = listOfNotNull(
            film.year.takeIf { it > 0 }?.toString(),
            runtimeLabel(film.duration),
        ).joinToString(META_GAP)
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The tick a film the box counts as seen wears.
 *
 * Drawn as a disc with a tick on it rather than as Material's own filled
 * `CheckCircle`, whose tick is knocked out of the disc: over a poster that
 * hole is the poster, and a tick made of whatever picture happens to be behind
 * it is a tick nobody can read.
 *
 * The shadow is not decoration either. Posters are photographs, and a disc in
 * one flat colour has nothing to stand on where the picture under it happens
 * to be light.
 */
@Composable
internal fun WatchedMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(WATCHED_MARK)
            .shadow(3.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = stringResource(R.string.library_watched),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(WATCHED_TICK),
        )
    }
}

/** How long a pressed tile waits for a film that never starts. */
internal const val FILM_START_TIMEOUT_MS = 6_000L

/** The mark a film the box counts as seen wears, and how far off the corner. */
private val WATCHED_MARK = 21.dp
private val WATCHED_TICK = 14.dp
internal val WATCHED_INSET = 5.dp
