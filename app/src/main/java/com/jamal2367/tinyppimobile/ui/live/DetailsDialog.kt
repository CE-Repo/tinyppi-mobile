@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import com.jamal2367.tinyppimobile.ui.navigation.LocalIsCurrentPage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.tinyppimobile.ui.theme.rememberArtworkAccent
import com.jamal2367.tinyppimobile.ui.theme.LocalBaseColorScheme
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
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
    val busy: Boolean = false,
    val actionError: String? = null,
)

/**
 * What the details page offers to do with the title, as the question it was
 * opened from does: [play] names the first answer - Play, Resume, or Open for
 * a series - and [resumable] adds starting over; [onPlay] is told which (from [onFirst] or [onRestart]).
 */
internal class DetailsActions(
    @param:StringRes val play: Int,
    val resumable: Boolean,
    val watched: Boolean = false,
    val onMark: ((Boolean) -> Unit)? = null,
    val onClearResume: (() -> Unit)? = null,
    /** The way to the show an episode belongs to, where it can be gone to. */
    val onShow: (() -> Unit)? = null,
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
 *
 * Where the app takes its colour from what is playing ([adaptive]), the page
 * takes its own from the title's poster instead - its keys are this title's,
 * not the film on the television - at the reader's [intensity]; a title
 * without a colour of its own wears the app's.
 *
 * The page runs under the system's bars at both ends, edge to edge as the
 * screens under it do, and keeps its last line clear of the bar at the foot.
 *
 * [over] is a page the details give way to in the same window - a show's
 * seasons - rather than another window opening on top of the shelf.
 */
@Composable
internal fun DetailsDialog(
    state: DetailsUiState,
    server: ServerConfig?,
    showArtwork: Boolean,
    actions: DetailsActions?,
    adaptive: Boolean = false,
    intensity: Float = 1f,
    over: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val pictures = if (showArtwork) server else null
    val details = state.details
    val poster = details?.let {
        MediaUrls.details(pictures, state.target, if (it.poster.isNotEmpty()) "poster" else "thumb", it.poster.ifEmpty { it.thumb })
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DisableDialogWindowAnimations()
        MaterialTheme(colorScheme = LocalBaseColorScheme.current ?: MaterialTheme.colorScheme) {
            ArtworkAccentTheme(rememberArtworkAccent(poster.takeIf { adaptive }), intensity) {
                DetailsPages(state, pictures, actions, over)
            }
        }
    }
}

/** Keep every full-screen details window free of platform enter/exit animations. */
@Composable
internal fun DisableDialogWindowAnimations() {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    SideEffect { window.setWindowAnimations(0) }
}

/**
 * The details, or the page they have given way to ([over]). The switch is
 * immediate so moving between details and seasons never waits for an
 * animation to finish.
 */
@Composable
private fun DetailsPages(
    state: DetailsUiState,
    pictures: ServerConfig?,
    actions: DetailsActions?,
    over: (@Composable () -> Unit)?,
) {
    val scroll = rememberScrollState()
    if (over != null) over() else DetailsPage(state, pictures, actions, scroll)
}

@Composable
private fun DetailsPage(state: DetailsUiState, server: ServerConfig?, actions: DetailsActions?, scroll: ScrollState) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val details = state.details
        Column(modifier = Modifier.verticalScroll(scroll)) {
            Hero(state.target, details ?: TitleDetails(), server)
            Column(
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .padding(start = PAGE_EDGE, end = PAGE_EDGE, top = 18.dp, bottom = 32.dp)
                    .navigationBarsPadding(),
            ) {
                if (actions != null) Actions(
                    actions,
                    watched = details?.watched ?: actions.watched,
                    resumable = details?.resume?.let { it > 0 } ?: actions.resumable,
                    busy = state.busy || (details == null && !state.failed),
                )
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (details != null) {
                    DetailsBody(details, server)
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

/**
 * A pull down past the top of a page that closes it, the way a sheet is
 * flicked away: what the page's own scrolling leaves over at its top is
 * added up, and a fling that comes after enough of it closes the page.
 * Used by a show's seasons (see SeasonsDialog); details only scroll.
 */
@Composable
internal fun rememberDismissSwipe(onDismiss: () -> Unit): NestedScrollConnection {
    val threshold by rememberUpdatedState(with(LocalDensity.current) { 100.dp.toPx() })
    val dismiss by rememberUpdatedState(onDismiss)
    return remember {
        object : NestedScrollConnection {
            private var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    pulled = (pulled + available.y).coerceAtLeast(0f)
                    if (consumed.y < 0f) pulled = 0f
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled >= threshold) dismiss()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

/** The head of a title's details: its backdrop, poster, name, year, length and formats. */
@Composable
private fun Hero(target: MarkTarget, details: TitleDetails, server: ServerConfig?) {
    val backdrop = MediaUrls.details(
        server,
        target,
        if (details.fanart.isNotEmpty()) "fanart" else "thumb",
        details.fanart.ifEmpty { details.thumb },
    )
    val meta = listOf(
        if (details.year > 0) details.year.toString() else "",
        runtimeLabel(details.duration).orEmpty(),
    ).filter { it.isNotEmpty() }.joinToString(META_GAP)
    PageHero(
        backdrop = backdrop,
        poster = MediaUrls.details(server, target, "poster", details.poster),
        title = target.title,
        meta = meta,
    ) {
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

/**
 * The head of a page over the shelf - a title's details, a show's seasons:
 * the backdrop, darkened towards the foot so the heading on it reads, and
 * standing on its lower edge the poster beside the name, a line under it and
 * whatever else the page puts there ([extra]). Without a backdrop the heading
 * stands on the plain page, [bareTop] under its top.
 */
@Composable
internal fun PageHero(
    backdrop: String?,
    poster: String?,
    title: String,
    meta: String,
    bareTop: Dp = 20.dp,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
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
        Heading(
            title = title,
            meta = meta,
            poster = poster,
            extra = extra,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .statusBarsPadding()
                .padding(start = PAGE_EDGE, end = PAGE_EDGE, top = if (backdrop != null) 0.dp else bareTop),
        )
    }
}

/**
 * The keys under the heading: the first answer in the accent, filling the
 * row, and beside it starting over, for a title with a point to resume from.
 */
@Composable
private fun Actions(actions: DetailsActions, watched: Boolean, resumable: Boolean, busy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = withKeyTick(actions.onFirst),
            enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = ACTION_HEIGHT),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = if (actions.play == R.string.title_open) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(if (actions.play == R.string.title_resume && !resumable) R.string.title_play else actions.play), textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (resumable) {
                DetailAction(Icons.Rounded.Replay, R.string.title_restart, actions.onRestart, !busy)
                actions.onClearResume?.let { clear ->
                    DetailAction(Icons.Rounded.BookmarkRemove, R.string.resume_clear, clear, !busy)
                }
            }
            actions.onShow?.let { show ->
                DetailAction(Icons.Rounded.Tv, R.string.title_show, show, !busy)
            }
            actions.onMark?.let { mark ->
                DetailAction(
                    if (watched) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (watched) R.string.mark_unwatched else R.string.mark_watched,
                    { mark(!watched) },
                    !busy,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, enabled: Boolean) {
    val name = stringResource(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = withKeyTick(onClick),
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(icon, contentDescription = name, modifier = Modifier.size(22.dp))
        }
    }
}

/** The poster, where the title has one, beside the title, the line under it and the formats. */
@Composable
private fun Heading(
    title: String,
    meta: String,
    poster: String?,
    extra: @Composable ColumnScope.() -> Unit,
    modifier: Modifier,
) {
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra()
        }
    }
}

/**
 * What a details page says under its heading - the ratings, the slogan and
 * the plot, the facts and the cast - and what the overview card of the
 * playing title says, in the same words and the same shapes.
 */
@Composable
internal fun DetailsBody(details: TitleDetails, server: ServerConfig? = null) {
    val rated = MediaInfo(
        ratings = details.ratings,
        mpaa = details.mpaa,
        age = details.age,
        ageLevel = details.ageLevel,
        userRating = details.userrating,
        top250 = details.top250,
        awards = details.awards,
    )
    RatingGroups(rated)
    if (details.tagline.isNotEmpty() || details.plot.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.details_description),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (details.tagline.isNotEmpty()) {
                Text(
                    text = details.tagline,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (details.plot.isNotEmpty()) {
                Text(text = details.plot, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    val facts = listOf(
        stringResource(R.string.details_genre) to details.genres,
        stringResource(R.string.details_studio) to details.studios,
    ).filter { it.second.isNotEmpty() }
    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((heading, names) in facts) Fact(heading, names.joinToString(", "))
        }
    }
    val directorRole = stringResource(R.string.live_credit_director)
    val writerRole = stringResource(R.string.details_writer)
    val crew = details.directors.map { CastMember(it, directorRole) } +
        details.writers.map { CastMember(it, writerRole) }
    val people = crew + details.cast
    if (details.cast.isNotEmpty()) Cast(details.cast, details.castMore, stringResource(R.string.live_credit_cast), server, people)
    if (crew.isNotEmpty()) Cast(crew, false, stringResource(R.string.details_crew), sizing = people)
}

/** One fact: the same bold heading as the other details sections. */
@Composable
private fun Fact(heading: String, names: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = names, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The cast as a row of cards that scrolls sideways: a portrait with an
 * initial as its fallback, the name and who they played - in billing order, with an
 * ellipsis at the end where the library holds more.
 */
@Composable
private fun Cast(cast: List<CastMember>, more: Boolean, heading: String, server: ServerConfig? = null, sizing: List<CastMember>) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val roleStyle = MaterialTheme.typography.labelMedium
    val textWidth = with(density) { CAST_WIDTH.roundToPx() }
    // Measure the whole row so scrolling to a longer name never changes its
    // height. The same slots keep names and roles aligned without truncation.
    val heights = remember(sizing, measurer, nameStyle, roleStyle, textWidth) {
        val constraints = Constraints(maxWidth = textWidth)
        val nameMinimum = measurer.measure("A", nameStyle, constraints = constraints).size.height
        val nameHeight = sizing.fold(0) { height, person ->
            maxOf(height, measurer.measure(person.name, nameStyle, constraints = constraints).size.height)
        }.coerceAtLeast(nameMinimum)
        val roles = sizing.map { it.role }.filter { it.isNotEmpty() }
        val roleHeight = if (roles.isEmpty()) 0 else {
            val roleMinimum = measurer.measure("A", roleStyle, constraints = constraints).size.height
            roles.fold(0) { height, role ->
                maxOf(height, measurer.measure(role, roleStyle, constraints = constraints).size.height)
            }.coerceAtLeast(roleMinimum)
        }
        nameHeight to roleHeight
    }
    val nameHeight = with(density) { heights.first.toDp() }
    val roleHeight = with(density) { heights.second.toDp() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        // Scroll to the page edges, keeping portraits and text fully opaque.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = PAGE_EDGE),
            modifier = Modifier.bleed(PAGE_EDGE),
        ) {
            items(cast) { person -> CastCard(person, nameHeight, roleHeight, server) }
            if (more) {
                item { Text("…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp)) }
            }
        }
    }
}

/**
 * [edge] wider on either side than the column it stands in, out over the
 * column's own margin. Left alone where the column has no width to go by.
 */
internal fun Modifier.bleed(edge: Dp) = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = (edge * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun CastCard(person: CastMember, nameHeight: Dp, roleHeight: Dp, server: ServerConfig?) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .width(CAST_WIDTH)
            .height(CAST_WIDTH + 12.dp + nameHeight + if (roleHeight > 0.dp) 4.dp + roleHeight else 0.dp)
            .testTag("credit-card"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CAST_WIDTH)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (server != null && person.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = "${server.baseUrl}/api/art?kind=thumb&portrait=${person.thumbnail}&v=${person.thumbnail}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().height(nameHeight),
            )
            if (person.role.isNotEmpty()) Text(
                text = person.role,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().height(roleHeight),
            )
        }
    }
}

/** The page's side margin, the backdrop's height, a poster's and a cast card's width. */
internal val PAGE_EDGE = 20.dp
private val HERO_HEIGHT = 300.dp
private val POSTER_WIDTH = 104.dp
private val CAST_WIDTH = 160.dp

private val ACTION_HEIGHT = 48.dp

/**
 * The details of a title asked about, while they are open - on the page in
 * front only: the shelves and the live screen are neighbouring pages,
 * composed side by side, and one question is one dialog. [actions] are the
 * question's first answers, where the page was opened from one.
 */
@Composable
internal fun DetailsShown(viewModel: LiveViewModel, actions: DetailsActions?, except: MarkTarget.Kind? = null) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    // A kind the screen shows in a place of its own (see SeriesScreen).
    DetailsPage(
        viewModel,
        details?.takeIf { it.target.kind != except },
        actions,
        over = null,
        onDismiss = viewModel::closeDetails,
    )
}

/**
 * One details page, [open], on the page in front only. Where it is shown from
 * is the caller's: the series screen keeps a show's page in a place of its
 * own, under the show's seasons, so the same window stays as they come and go.
 */
@Composable
internal fun DetailsPage(
    viewModel: LiveViewModel,
    open: DetailsUiState?,
    actions: DetailsActions?,
    over: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = open?.takeIf { LocalIsCurrentPage.current } ?: return
    DetailsDialog(
        state = page,
        server = state.live.server,
        showArtwork = state.settings.showArtwork,
        actions = actions,
        adaptive = state.settings.adaptiveColor,
        intensity = state.settings.adaptiveColorIntensity,
        over = over,
        onDismiss = onDismiss,
    )
}
