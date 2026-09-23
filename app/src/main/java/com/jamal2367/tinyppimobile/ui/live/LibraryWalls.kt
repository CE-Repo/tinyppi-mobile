@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.FoldChevron
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.SectionHeading
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.PosterShape
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import com.jamal2367.tinyppimobile.util.Formatters
import com.jamal2367.tinyppimobile.util.MediaUrls
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle

/**
 * The walls of posters the box's library is offered as, and everything they
 * are drawn out of.
 *
 * Three screens draw these: the live screen while the box is playing nothing,
 * and the two shelves in the navigation bar, which are the same walls reached
 * on purpose rather than come across. Only the drawing of them is here - the
 * state they read is the live view model's, because a shelf read on one screen
 * is the same shelf on the next, and holding it twice would be asking the box
 * twice.
 */

/* --- Continue watching -------------------------------------------------- */

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

/* --- The film library ---------------------------------------------------- */

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
        key = "film-wall",
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
        title = { stringResource(R.string.library_all, films.size) },
        noMatch = { stringResource(R.string.library_no_match) },
        tiles = films,
        columns = columns,
        rowKey = { row -> "film-row-${row.first().id}" },
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
 * One shelf as a card: its heading and the wall a row at a time.
 *
 * One builder for both shelves. The films and the series are the same offer
 * made twice, and a second card drawn differently would read as a different
 * screen rather than a second shelf.
 *
 * The box that narrows it down is not in here: it sits at the very top of the
 * screen, above the continue-watching row as well (see [shelfTop]), because
 * it is where somebody who came to look for one title starts.
 */
private fun <T> LazyListScope.shelfCard(
    key: String,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: @Composable () -> String,
    noMatch: @Composable () -> String,
    tiles: List<T>,
    columns: Int,
    rowKey: (List<T>) -> String,
    tile: @Composable RowScope.(T) -> Unit,
) {
    cardTop(key, gapAbove, expanded, onToggle, title)
    if (!expanded) return

    if (tiles.isEmpty()) {
        // A search nothing answers. The library itself being empty is handled
        // before the card is drawn at all, where the screen has room to say so
        // properly.
        item(key = "$key-empty") {
            CardSegment(CardPart.MIDDLE) {
                Text(
                    text = noMatch(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                )
            }
        }
    } else {
        items(items = tiles.chunked(columns), key = rowKey) { row ->
            CardSegment(CardPart.MIDDLE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FILM_GAP),
                    modifier = Modifier.padding(top = FILM_GAP),
                ) {
                    for (entry in row) tile(entry)
                    // The last row is rarely full. Without this its tiles
                    // would be spread across the width instead of standing
                    // under the ones above them.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    cardBottom(key)
}

/**
 * The top of a card on a shelf: the gap that parts it from the card above,
 * and the heading - the same heading every other card in the app wears, the
 * accent tick, the title and the arrow that folds it.
 *
 * Folded, this is the whole card, rounded at the bottom as well.
 */
private fun LazyListScope.cardTop(
    key: String,
    gapAbove: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: @Composable () -> String,
) {
    if (gapAbove) {
        item(key = "$key-gap") { Spacer(Modifier.height(CardGap)) }
    }
    item(key = "$key-heading") {
        CardSegment(if (expanded) CardPart.TOP else CardPart.WHOLE) {
            Box(
                modifier = Modifier.padding(
                    top = CARD_PADDING,
                    bottom = if (expanded) 0.dp else CARD_PADDING,
                ),
            ) {
                SectionHeading(
                    title = title(),
                    expanded = expanded,
                    onToggle = onToggle,
                )
            }
        }
    }
}

/** The foot of an open card: its padding, and its rounded bottom edge. */
private fun LazyListScope.cardBottom(key: String) {
    item(key = "$key-foot") {
        CardSegment(CardPart.BOTTOM) {
            Spacer(Modifier.height(CARD_PADDING))
        }
    }
}

/** Which part of a card a [CardSegment] is. */
private enum class CardPart { TOP, MIDDLE, BOTTOM, WHOLE }

/**
 * One slice of a card that is laid out a row at a time.
 *
 * A shelf cannot be one `SectionCard`: a card is laid out in one piece, and
 * one holding five hundred posters would lay out five hundred posters. So the
 * card is drawn in slices, each an item of the screen's own lazy list, and
 * each slice paints the part of the card that falls inside it - the rounded
 * top, the two sides, the rounded bottom - out of one card shape drawn
 * taller than the slice and cut to it. The slices meet with no gap between
 * them, so what the eye sees is one card in the same colour, with the same
 * hairline and the same corners as every card elsewhere in the app.
 */
@Composable
private fun CardSegment(
    part: CardPart,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val fill = MaterialTheme.colorScheme.surfaceContainerLow
    val line = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                // Well past any corner, so the corner of a slice that is not
                // meant to have one is always outside it.
                val beyond = 64.dp.toPx()
                val stroke = 1.dp.toPx()
                val (top, height) = when (part) {
                    CardPart.TOP -> 0f to size.height + beyond
                    CardPart.MIDDLE -> -beyond to size.height + beyond * 2
                    CardPart.BOTTOM -> -beyond to size.height + beyond
                    CardPart.WHOLE -> 0f to size.height
                }
                // The hairline inside the edge, the way a card's border is.
                val outline = shape.createOutline(
                    Size(size.width - stroke, height - stroke),
                    layoutDirection,
                    this,
                )
                translate(left = stroke / 2, top = top + stroke / 2) {
                    drawOutline(outline, fill)
                    drawOutline(outline, line, style = Stroke(stroke))
                }
            }
            .padding(horizontal = CARD_PADDING),
        content = content,
    )
}

/**
 * The head of a shelf screen: its name, and under it the box the shelf is
 * narrowed down with - above everything else on the screen, the
 * continue-watching row included, because it is where somebody who came to
 * look for one title starts.
 */
internal fun LazyListScope.shelfTop(
    title: @Composable () -> String,
    searchLabel: @Composable () -> String,
    search: String,
    onSearch: (String) -> Unit,
) {
    item(key = "shelf-title") {
        ScreenTitle(text = title(), modifier = Modifier.padding(bottom = 14.dp))
    }
    item(key = "shelf-search") {
        WallSearch(
            label = searchLabel(),
            search = search,
            onSearch = onSearch,
            modifier = Modifier.padding(bottom = CardGap),
        )
    }
}

/**
 * The box a shelf is narrowed down with: a pill with a magnifier in it, in
 * the colour of the cards under it.
 *
 * The cross empties it, and only while there is something to empty: over a
 * field nobody has typed in it is a control that does nothing. It puts the
 * whole shelf back and then gets out of the way - field and keyboard both.
 * Somebody who clears a search is done with it; one who meant to search for
 * something else can press the field again, which is one press against the
 * screenful of posters a keyboard would otherwise go on covering.
 */
@Composable
private fun WallSearch(
    label: String,
    search: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The cards' own ground and hairline, so the box reads as one more piece
    // of the same screen rather than a control laid on top of it.
    val fill = MaterialTheme.colorScheme.surfaceContainerLow
    val line = MaterialTheme.colorScheme.surfaceContainerHigh

    TextField(
        value = search,
        onValueChange = onSearch,
        singleLine = true,
        shape = PillShape,
        placeholder = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (search.isEmpty()) {
            null
        } else {
            {
                IconButton(
                    onClick = {
                        onSearch("")
                        dismissSearch(focus, keyboard)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.library_search_clear),
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        // The key the keyboard offers instead of a newline, and what it does:
        // nothing but close, because the wall narrowed itself as the letters
        // arrived.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { dismissSearch(focus, keyboard) },
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, line, PillShape),
    )
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
private fun WatchedMark(modifier: Modifier = Modifier) {
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

/* --- The series library -------------------------------------------------- */

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
) {
    shelfCard(
        key = "series-wall",
        gapAbove = gapAbove,
        expanded = expanded,
        onToggle = onToggle,
        title = { stringResource(R.string.series_all, shows.size) },
        noMatch = { stringResource(R.string.series_no_match) },
        tiles = shows,
        columns = columns,
        rowKey = { row -> "series-row-${row.first().id}" },
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
                .clickable(interactionSource = press, indication = null, onClick = onToggle)
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
 * The picture on a tile or a row, with whatever the library has to say on top
 * of it.
 *
 * One frame for all three shelves: a film's poster, a show's poster and an
 * episode's still carry the same marks in the same corners and hold their
 * space the same way when there is no picture at all, and three copies of that
 * would be three places to keep it right.
 */
@Composable
private fun ArtFrame(
    url: String?,
    ratio: Float,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    watched: Boolean = false,
    busy: Boolean = false,
    unseen: Int = 0,
    rating: Double = 0.0,
    ratingFrom: String = "",
) {
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .clip(PosterShape),
    ) {
        // A title with no picture - and everything on a phone told not to show
        // artwork - gets the stand-in the playing title gets, which holds the
        // same space rather than collapsing the row it is in.
        PosterImage(
            url = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

        progress?.let { far ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(RESUME_BAR)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(far)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        if (rating > 0) {
            RatingMark(
                rating = rating,
                from = ratingFrom,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(WATCHED_INSET),
            )
        }

        when {
            watched -> WatchedMark(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WATCHED_INSET),
            )
            unseen > 0 -> UnseenMark(
                count = unseen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WATCHED_INSET),
            )
        }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * The pill a poster wears in a corner.
 *
 * One shape for both of the remarks a poster carries - what the houses made of
 * it, and how many episodes of it are still waiting - because they are the
 * same kind of remark about the same picture. A shelf where one of them was a
 * coloured disc and the other a dark tablet read as two unrelated marks that
 * happened to land on the same posters.
 *
 * Black with white on it rather than themed. What it sits on is a photograph
 * and not the screen, so it has to read on a poster that is black at that
 * corner and on one that is white there - which is the argument the tick's
 * shadow makes, and this carries one for the same reason.
 */
@Composable
private fun CornerPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .shadow(3.dp, CircleShape)
            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * What IMDb or TMDb made of it, top left of the poster.
 *
 * Left, because the other corner is taken by whichever of the tick and the
 * count the picture is wearing.
 *
 * The star is what says the number beside it is a rating and not a count -
 * which is the whole of what tells this pill from the other one. Amber rather
 * than white: it is the colour a rating star is everywhere anybody has seen
 * one, and it carries that meaning before the number has been read.
 *
 * The number alone is drawn, because a poster has room for a number and not
 * for a sentence; which house said so is what it tells a screen reader,
 * because 8.3 means different things at the two of them.
 */
@Composable
private fun RatingMark(rating: Double, from: String, modifier: Modifier = Modifier) {
    val figure = Formatters.rating(rating)
    val said = listOfNotNull(RATING_NAMES[from], figure).joinToString(" ")
    CornerPill(modifier.semantics { contentDescription = said }) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = RATING_STAR,
            modifier = Modifier.size(RATING_STAR_SIZE),
        )
        Text(
            text = figure,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * How long something runs, as a tile writes it: `1 h 38 min`, or `45 min`
 * where there is no hour to write.
 *
 * The hours split out rather than a hundred and ninety-eight minutes, because
 * what is being asked of a film is how long an evening it is and an hour is
 * the unit an evening is measured in.
 *
 * Null for a library that does not know how long it is, so nothing is drawn
 * rather than a nought.
 */
@Composable
private fun runtimeLabel(seconds: Int): String? {
    val (hours, minutes) = Formatters.runtimeParts(seconds) ?: return null
    return when {
        hours <= 0 -> stringResource(R.string.library_runtime_m, minutes)
        // An hour with nothing left over says so and stops: "1 h 0 min" is a
        // length nobody writes, and a season adding up to a round number of
        // hours is not rare.
        minutes == 0 -> stringResource(R.string.library_runtime_h, hours)
        else -> stringResource(R.string.library_runtime_hm, hours, minutes)
    }
}

/**
 * How many episodes of a series are still waiting, top right of the poster.
 *
 * The rating's own pill without its star, because that is the difference
 * between the two: one of them is a rating and this is a count.
 */
@Composable
private fun UnseenMark(count: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.series_unseen, count, count)
    CornerPill(modifier.semantics { contentDescription = label }) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * Let the search field go, and the keyboard with it.
 *
 * Clearing the focus is usually enough to put the keyboard away, but only
 * usually: a field disposed from under it - which is what scrolling a wall
 * does to the heading it sits in - can leave the keyboard up over a screen
 * with nothing left to type into. Both, then, and neither costs anything when
 * there was nothing focused to begin with.
 */
internal fun dismissSearch(focus: FocusManager, keyboard: SoftwareKeyboardController?) {
    focus.clearFocus()
    keyboard?.hide()
}

/**
 * The films whose name - or year - answers what has been typed.
 *
 * Matched here rather than by the box: the whole library is already on the
 * phone, and a round trip per keystroke to narrow a list this app is holding
 * would be a slower answer to a question already in front of it.
 */
internal fun matching(films: List<LibraryFilm>, search: String): List<LibraryFilm> =
    matching(films, search, { it.title }, { it.year })

/**
 * The same, for any shelf whose tiles carry a name and a year.
 *
 * Written once rather than once per shelf: the films and the series are
 * narrowed by the same question, and two copies of it would be two answers to
 * keep the same.
 */
internal fun <T> matching(
    items: List<T>,
    search: String,
    title: (T) -> String,
    year: (T) -> Int,
): List<T> {
    val needle = search.trim()
    if (needle.isEmpty()) return items
    return items.filter { item ->
        title(item).contains(needle, ignoreCase = true) ||
            (year(item) > 0 && year(item).toString().contains(needle))
    }
}

/**
 * How many posters go across, from how wide the screen is.
 *
 * A count rather than a width, because the row has to divide the screen
 * exactly: tiles that each take a fixed width leave a ragged edge down the
 * right of every row. Three on a phone, more on a tablet or a phone held
 * sideways - worked out from the same poster width the card at the top of this
 * screen uses.
 */
@Composable
internal fun filmColumns(): Int {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    // Inside a card now, so its padding comes off both sides as well.
    val usable = width - ScreenEdge * 2 - CARD_PADDING * 2
    return (usable / (FILM_TILE_MIN + FILM_GAP)).toInt().coerceIn(3, 6)
}

/**
 * How wide one tile of a wall of [columns] is, for a row that has to match it
 * without being one of the wall's rows.
 */
@Composable
private fun filmTileWidth(columns: Int): Dp {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val usable = width - ScreenEdge * 2 - CARD_PADDING * 2
    return (usable - FILM_GAP * (columns - 1)) / columns
}

/** The star on a rating pill, and how big it is drawn. */
private val RATING_STAR = Color(0xFFF4C04F)
private val RATING_STAR_SIZE = 13.dp

/** What a badge calls the house whose rating it draws. Brand names, untranslated. */
private val RATING_NAMES = mapOf("imdb" to "IMDb", "tmdb" to "TMDb")

/** What stands between a year and a length, or a number and a length. */
private const val META_GAP = " \u00b7 "

/** How long a pressed tile waits for a film that never starts. */
internal const val FILM_START_TIMEOUT_MS = 6_000L

/** The mark a film the box counts as seen wears, and how far off the corner. */
private val WATCHED_MARK = 21.dp
private val WATCHED_TICK = 14.dp
private val WATCHED_INSET = 5.dp

/**
 * The air between two posters.
 *
 * The card gap, because the rows of the wall are items of the screen's own
 * list and are spaced by it from above and below: a narrower gap across than
 * down would have drawn a grid with two different rhythms in it.
 */
private val FILM_GAP = CardGap

/** A shelf card's padding, and the room between its heading and what it holds - a `SectionCard`'s. */
private val CARD_PADDING = 16.dp
private val CARD_INNER_GAP = 12.dp
private val FILM_TILE_MIN = 104.dp
private val RESUME_BAR = 3.dp

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
