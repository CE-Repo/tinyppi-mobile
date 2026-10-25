@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.CastMember
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.util.MediaUrls

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
 * What the details page offers to do with the title, as the question it was
 * opened from does: [play] names the first answer - Play, Resume, or Open for
 * a series - and [resumable] adds starting over; [onPlay] is told which (from [onFirst] or [onRestart]).
 */
internal class DetailsActions(
    @param:StringRes val play: Int,
    val resumable: Boolean,
    onPlay: (fromStart: Boolean) -> Unit,
) {
    val onFirst: () -> Unit = { onPlay(false) }
    val onRestart: () -> Unit = { onPlay(true) }
}

/**
 * Everything the library holds about one title, as a page of its own over
 * the shelf: the title's backdrop across the top - a film's or a series'
 * fanart, an episode's still - fading into the page, the poster standing on
 * its lower edge beside the title, how long it runs and the file's formats;
 * then the ratings as the overview card draws the playing title's, the
 * slogan and the plot, who made it, and the cast as a row of cards.
 *
 * The read is the view model's (see [LiveViewModel.openDetails]), so a page
 * closed before it answers asks nothing more of the box, and one that fails
 * says so where it was asked for rather than along the bottom of the screen.
 * Pictures only where the reader has them on ([showArtwork]).
 */
@Composable
internal fun DetailsDialog(
    state: DetailsUiState,
    server: ServerConfig?,
    showArtwork: Boolean,
    actions: DetailsActions?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val details = state.details
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Hero(state.target, details ?: TitleDetails(), if (showArtwork) server else null, onDismiss)
                Column(
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.padding(start = PAGE_EDGE, end = PAGE_EDGE, top = 18.dp, bottom = 32.dp),
                ) {
                    if (actions != null) Actions(actions)
                    if (details != null) {
                        DetailsBody(details)
                    } else if (state.failed) {
                        Text(
                            text = stringResource(R.string.details_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

/**
 * The head of the page: the backdrop, darkened towards the foot so the
 * heading on it reads, and the close key on a scrim of its own. Without a
 * backdrop the heading stands on the plain page under the key.
 */
@Composable
private fun Hero(target: MarkTarget, details: TitleDetails, server: ServerConfig?, onDismiss: () -> Unit) {
    val backdrop = MediaUrls.details(
        server,
        target,
        if (details.fanart.isNotEmpty()) "fanart" else "thumb",
        details.fanart.ifEmpty { details.thumb },
    )
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = if (backdrop != null) HERO_HEIGHT else 0.dp)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.15f),
                            0.55f to surface.copy(alpha = 0.6f),
                            1f to surface,
                        ),
                    ),
            )
        }
        FilledTonalIconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(10.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.details_close))
        }
        Heading(
            target = target,
            details = details,
            poster = MediaUrls.details(server, target, "poster", details.poster),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = PAGE_EDGE, end = PAGE_EDGE, top = if (backdrop != null) 0.dp else 64.dp),
        )
    }
}

/**
 * The keys under the heading: the first answer in the accent, filling the
 * row, and beside it starting over, for a title with a point to resume from.
 */
@Composable
private fun Actions(actions: DetailsActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = withKeyTick(actions.onFirst), modifier = Modifier.weight(1f).height(ACTION_HEIGHT)) {
            Icon(
                imageVector = if (actions.play == R.string.title_open) {
                    Icons.AutoMirrored.Rounded.OpenInNew
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(actions.play), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
        if (actions.resumable) {
            OutlinedButton(onClick = withKeyTick(actions.onRestart), modifier = Modifier.weight(1f).height(ACTION_HEIGHT)) {
                Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.title_restart),
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The poster, where the title has one, beside the title, the line under it and the formats. */
@Composable
private fun Heading(target: MarkTarget, details: TitleDetails, poster: String?, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (poster != null) {
            PosterImage(url = poster, contentDescription = null, modifier = Modifier.width(POSTER_WIDTH).height(POSTER_WIDTH * 1.5f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(
                if (details.year > 0) details.year.toString() else "",
                runtimeLabel(details.duration).orEmpty(),
            ).filter { it.isNotEmpty() }.joinToString(META_GAP)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
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
                ) {
                    for (format in formats) FormatBadge(text = format)
                }
            }
        }
    }
}

@Composable
private fun DetailsBody(details: TitleDetails) {
    val rated = MediaInfo(
        ratings = details.ratings,
        mpaa = details.mpaa,
        age = details.age,
        ageLevel = details.ageLevel,
        userRating = details.userrating,
        top250 = details.top250,
    )
    if (rated.ratings.isNotEmpty() || rated.mpaa.isNotEmpty() || rated.userRating > 0 || rated.top250 > 0) {
        RatingRow(rated)
    }
    if (details.tagline.isNotEmpty() || details.plot.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (details.tagline.isNotEmpty()) {
                Text(
                    text = details.tagline,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (details.plot.isNotEmpty()) {
                Text(text = details.plot, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    val facts = listOf(
        stringResource(R.string.details_genre) to details.genres,
        stringResource(R.string.live_credit_director) to details.directors,
        stringResource(R.string.details_writer) to details.writers,
        stringResource(R.string.details_studio) to details.studios,
    ).filter { it.second.isNotEmpty() }
    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((heading, names) in facts) Fact(heading, names.joinToString(", "))
        }
    }
    if (details.cast.isNotEmpty()) Cast(details.cast, details.castMore)
}

/** One fact: its name in the quieter colour over the names. */
@Composable
private fun Fact(heading: String, names: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = names, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The cast as a row of cards that scrolls sideways: a circle with the
 * initial, the name and who they played - in billing order, with an
 * ellipsis at the end where the library holds more.
 */
@Composable
private fun Cast(cast: List<CastMember>, more: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.live_credit_cast),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cast) { person -> CastCard(person) }
            if (more) {
                item { Text("…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp)) }
            }
        }
    }
}

@Composable
private fun CastCard(person: CastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(CAST_WIDTH)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (person.role.isNotEmpty()) {
            Text(
                text = person.role,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The page's side margin, the backdrop's height, a poster's and a cast card's width. */
private val PAGE_EDGE = 20.dp
private val HERO_HEIGHT = 300.dp
private val POSTER_WIDTH = 104.dp
private val CAST_WIDTH = 112.dp
private val ACTION_HEIGHT = 52.dp
