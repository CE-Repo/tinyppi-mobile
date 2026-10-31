@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DontMemoize
import androidx.compose.foundation.border
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
import com.jamal2367.tinyppimobile.data.model.Airing
import com.jamal2367.tinyppimobile.data.model.RelatedTitle
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.util.MediaUrls
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.icu.text.CompactDecimalFormat
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

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
 *
 * [onRelated] opens a title of the library the page leads on to - a film of
 * its series, one TMDb recommends - in place of this one; without it, those
 * cards lead nowhere but to TMDb.
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
    onRelated: ((RelatedTitle) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val pictures = if (showArtwork) server else null
    val details = state.details
    val poster = details?.let {
        MediaUrls.details(pictures, state.target, if (it.poster.isNotEmpty()) "poster" else "thumb", it.poster.ifEmpty { it.thumb })
    }
    // The dialog's window puts the platform's own link handler back - the
    // browser - so the app's (a Custom Tab, see InAppBrowser) is handed in.
    val links = LocalUriHandler.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DisableDialogWindowAnimations()
        CompositionLocalProvider(LocalUriHandler provides links) {
            MaterialTheme(colorScheme = LocalBaseColorScheme.current ?: MaterialTheme.colorScheme) {
                ArtworkAccentTheme(rememberArtworkAccent(poster.takeIf { adaptive }), intensity) {
                    DetailsPages(state, pictures, actions, over, onRelated)
                }
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
    onRelated: ((RelatedTitle) -> Unit)?,
) {
    val scroll = rememberScrollState()
    // A new title in the same window - one the page led on to - starts at its top.
    LaunchedEffect(state.target) { scroll.scrollTo(0) }
    if (over != null) over() else DetailsPage(state, pictures, actions, scroll, onRelated)
}

@Composable
private fun DetailsPage(
    state: DetailsUiState,
    server: ServerConfig?,
    actions: DetailsActions?,
    scroll: ScrollState,
    onRelated: ((RelatedTitle) -> Unit)?,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val details = state.details
        Column(modifier = Modifier.verticalScroll(scroll)) {
            Hero(state.target, details ?: TitleDetails(), server)
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
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
                    DetailsBody(details, server, onRelated)
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
internal fun DetailsBody(
    details: TitleDetails,
    server: ServerConfig? = null,
    onRelated: ((RelatedTitle) -> Unit)? = null,
) {
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
        // The slogan's compact italic line needs more room than the body-text facts.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.details_description),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    }
    val locale = LocalConfiguration.current.locales[0]
    // What it cost and what it took share a line - "Budget/Box office" over
    // "$165M/$402M" - or whichever of the two is known.
    val money = listOf(
        stringResource(R.string.details_budget) to dollars(details.budget, locale),
        stringResource(R.string.details_revenue) to dollars(details.revenue, locale),
    ).mapNotNull { (heading, amount) -> amount?.let { heading to it } }
    val made = details.revenue - details.budget
    val profit = dollars(kotlin.math.abs(made), locale)
    val facts = listOf(
        stringResource(R.string.details_genre) to details.genres,
        stringResource(R.string.details_studio) to details.studios,
        stringResource(R.string.details_country) to details.countries,
        stringResource(R.string.details_cinema) to listOfNotNull(
            details.cinema.ifEmpty { null }?.let { cinemaDay(it, locale) + details.cinemaCountry.ifEmpty { null }?.let { " ($it)" }.orEmpty() },
        ),
        money.joinToString("/") { it.first } to listOfNotNull(money.joinToString("/") { it.second }.ifEmpty { null }),
        // What it took less what it cost, where both are known: its profit,
        // or what it lost under that name.
        stringResource(if (made < 0) R.string.details_loss else R.string.details_profit) to
            listOfNotNull(profit.takeIf { money.size == 2 }),
    ).plus(showFacts(details, locale)).filter { it.second.isNotEmpty() }
    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            for ((heading, names) in facts) Fact(heading, names.joinToString(", "))
        }
    }
    val directorRole = stringResource(R.string.live_credit_director)
    val writerRole = stringResource(R.string.details_writer)
    // Kodi's directors and writers by their codes, everyone else the box's
    // TMDb credits name under their jobs as the box words them.
    val crew = if (details.crew.isNotEmpty()) details.crew.map {
        it.copy(role = when (it.role) {
            "director" -> directorRole
            "writer" -> writerRole
            else -> it.role
        })
    } else details.directors.map { CastMember(it, directorRole) } +
        details.writers.map { CastMember(it, writerRole) }
    if (details.cast.isNotEmpty()) Cast(details.cast, details.castMore, stringResource(R.string.live_credit_cast), server)
    if (crew.isNotEmpty()) Cast(crew, false, stringResource(R.string.details_crew), server)
    details.collection?.let { Related(it.name, it.parts, server, onRelated) }
    Related(stringResource(R.string.details_recommended), details.recommended, server, onRelated)
}

/**
 * A series' run on TMDb: its state, who airs it, how many seasons and
 * episodes there are in all - "Seasons/Episodes" over "5/62", where TMDb
 * knows both - and the next episode to air and the last aired,
 * "S03E01 · Its title · 16 Jan 2027". Nothing for a film.
 */
@Composable
private fun showFacts(details: TitleDetails, locale: Locale): List<Pair<String, List<String>>> {
    val status = when (details.status) {
        "returning" -> stringResource(R.string.details_status_returning)
        "ended" -> stringResource(R.string.details_status_ended)
        "canceled" -> stringResource(R.string.details_status_canceled)
        "production" -> stringResource(R.string.details_status_production)
        "planned" -> stringResource(R.string.details_status_planned)
        "pilot" -> stringResource(R.string.details_status_pilot)
        else -> details.status
    }
    val sized = details.tmdbSeasons > 0 && details.tmdbEpisodes > 0
    return listOf(
        stringResource(R.string.details_status) to listOfNotNull(status.ifEmpty { null }),
        stringResource(R.string.details_network) to details.networks,
        stringResource(R.string.details_seasons_episodes) to
            listOfNotNull("${details.tmdbSeasons}/${details.tmdbEpisodes}".takeIf { sized }),
        stringResource(R.string.details_next_episode) to listOfNotNull(details.nextEpisode?.let { airing(it, locale) }),
        stringResource(R.string.details_last_episode) to listOfNotNull(details.lastEpisode?.let { airing(it, locale) }),
    )
}

/** "S03E01 · Its title · 16 Jan 2027", each part where it is known. */
internal fun airing(episode: Airing, locale: Locale): String =
    listOf(episode.code, episode.title, episode.date.ifEmpty { null }?.let { cinemaDay(it, locale) }.orEmpty())
        .filter { it.isNotEmpty() }
        .joinToString(" \u00b7 ")

/**
 * Titles the page leads on to, under [heading] with how many there are: a
 * row of poster cards, as the cast's portraits are, each with its name and
 * year. One the library holds opens its own page ([onRelated]); one it does
 * not, marked TMDb, its page on TMDb; the page's own title, marked, neither.
 * Nothing where there are none.
 */
@Composable
private fun Related(heading: String, titles: List<RelatedTitle>, server: ServerConfig?, onRelated: ((RelatedTitle) -> Unit)?) {
    if (titles.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val yearStyle = MaterialTheme.typography.labelMedium
    val width = with(density) { CAST_WIDTH.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    // As the cast's: the row is as tall as its tallest name and year.
    val textHeight = remember(titles, measurer, nameStyle, yearStyle, width, gap) {
        val constraints = Constraints(maxWidth = width)
        val year = gap + measurer.measure("0000", yearStyle, constraints = constraints).size.height
        titles.fold(0) { height, title ->
            maxOf(height, measurer.measure(title.title, nameStyle, constraints = constraints).size.height)
        } + year
    }.let { with(density) { it.toDp() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("related-row")) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                append(heading)
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = muted)) {
                    append(" (${titles.size})")
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = PAGE_EDGE),
            modifier = Modifier.bleed(PAGE_EDGE),
        ) {
            items(titles) { title -> RelatedCard(title, textHeight, server, onRelated) }
        }
    }
}

@Composable
private fun RelatedCard(title: RelatedTitle, textHeight: Dp, server: ServerConfig?, onRelated: ((RelatedTitle) -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    val press: (() -> Unit)? = when {
        title.current -> null
        // Built afresh each time: a cached callback only adds compiler-generated branches.
        title.held -> onRelated?.let { open ->
            val toPage: () -> Unit = @DontMemoize { open(title) }
            toPage
        }
        else -> {
            val toTmdb: () -> Unit = @DontMemoize { runCatching { uriHandler.openUri(title.page) } }
            toTmdb
        }
    }
    val label = stringResource(if (title.held) R.string.details_open_related else R.string.details_on_tmdb)
    Column(
        modifier = Modifier
            .width(CAST_WIDTH)
            .clip(RoundedCornerShape(8.dp))
            .then(if (press != null) Modifier.clickable(onClickLabel = label, onClick = press) else Modifier)
            .height(RELATED_HEIGHT + 12.dp + textHeight)
            .testTag("related-card"),
    ) {
        Box(
            modifier = Modifier
                .width(CAST_WIDTH)
                .height(RELATED_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderBrush(title.title))
                .then(
                    if (title.current) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            MediaUrls.related(server, title)?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            val mark = when {
                title.current -> stringResource(R.string.details_this_film)
                title.held -> ""
                else -> "TMDb"
            }
            if (mark.isNotEmpty()) {
                Text(
                    text = mark,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("related-mark"),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 12.dp)) {
            Text(text = title.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (title.year > 0) {
                Text(
                    text = title.year.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "2021-09-16" as the reader writes a day; the day itself where it is none. */
internal fun cinemaDay(day: String, locale: Locale): String = try {
    LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
} catch (_: DateTimeParseException) {
    day
}

/** Whole US dollars, short - "165 Mio. $", "$165M" - or null for none. */
@Composable
private fun dollars(amount: Long, locale: Locale): String? {
    if (amount <= 0) return null
    val short = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        .apply { maximumSignificantDigits = 3 }
        .format(amount)
    return stringResource(R.string.details_dollars, short)
}

/**
 * A person's page on TMDb, with everything they have made: by their id where
 * the box found it in the title's credits, otherwise TMDb's search for their name.
 */
internal fun personPage(person: CastMember): String =
    if (person.tmdb > 0) "https://www.themoviedb.org/person/${person.tmdb}"
    else "https://www.themoviedb.org/search/person?query=" + Uri.encode(person.name)

/** One fact: the same bold heading as the other details sections. */
@Composable
private fun Fact(heading: String, names: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
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
private fun Cast(cast: List<CastMember>, more: Boolean, heading: String, server: ServerConfig?) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val roleStyle = MaterialTheme.typography.labelMedium
    val textWidth = with(density) { CAST_WIDTH.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    // Measure the whole row so scrolling to a longer name never changes its
    // height: the tallest name and role together, the role right under its name.
    val textHeight = remember(cast, measurer, nameStyle, roleStyle, textWidth, gap) {
        val constraints = Constraints(maxWidth = textWidth)
        val nameMinimum = measurer.measure("A", nameStyle, constraints = constraints).size.height
        cast.fold(nameMinimum) { height, person ->
            val name = measurer.measure(person.name, nameStyle, constraints = constraints).size.height
            val role = if (person.role.isEmpty()) 0
                else gap + measurer.measure(person.role, roleStyle, constraints = constraints).size.height
            maxOf(height, name + role)
        }
    }.let { with(density) { it.toDp() } }
    // Portraits have no text line-box inset; match the facts' visible breathing room.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // How many there are, beside the heading: "Cast (24)", "(20+)" where
        // the library holds more than the row shows.
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                append(heading)
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = muted)) {
                    append(" (${cast.size}${if (more) "+" else ""})")
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
            fontWeight = FontWeight.Bold,
        )
        // Scroll to the page edges, keeping portraits and text fully opaque.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = PAGE_EDGE),
            modifier = Modifier.bleed(PAGE_EDGE),
        ) {
            items(cast) { person -> CastCard(person, textHeight, server) }
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
private fun CastCard(person: CastMember, textHeight: Dp, server: ServerConfig?) {
    // The card is the way to the person's page on TMDb.
    val uriHandler = LocalUriHandler.current
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .width(CAST_WIDTH)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.details_person_page)) {
                runCatching { uriHandler.openUri(personPage(person)) }
            }
            .height(CAST_WIDTH + 12.dp + textHeight)
            .testTag("credit-card"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CAST_WIDTH)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderBrush(person.name))
                .testTag("credit-portrait"),
        ) {
            // Under the portrait, and in its place where there is none or it
            // cannot be read: a figure on a colour of the person's own.
            PersonFigure(Modifier.matchParentSize().testTag("credit-placeholder"))
            val portrait = MediaUrls.portrait(server, person.thumbnail)
            if (portrait != null) {
                AsyncImage(
                    model = portrait,
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
                modifier = Modifier.fillMaxWidth(),
            )
            if (person.role.isNotEmpty()) Text(
                text = person.role,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The colours a person without a portrait stands on, two to a gradient; each
 * person always gets the same pair, picked by their name.
 */
private val PLACEHOLDER_COLOURS = listOf(
    0xFF1D3557 to 0xFFE63946, 0xFF0B3D2E to 0xFF3CA87A, 0xFF2B1055 to 0xFFD53369,
    0xFF3A1C08 to 0xFFF4A259, 0xFF0F2027 to 0xFF2C7A99, 0xFF42275A to 0xFFB06AB3,
    0xFF141E30 to 0xFF4A7AB0, 0xFF5C2A09 to 0xFFE0B050, 0xFF1F1C2C to 0xFF928DAB,
    0xFF093028 to 0xFF3CA87A, 0xFF3E0E12 to 0xFFC0392B, 0xFF16222A to 0xFF4A8BA3,
)

internal fun placeholderBrush(name: String): Brush {
    val (from, to) = PLACEHOLDER_COLOURS[Math.floorMod(name.hashCode(), PLACEHOLDER_COLOURS.size)]
    return Brush.linearGradient(listOf(Color(from), Color(to)))
}

/** A head and shoulders in pale outline, the same shape at any size. */
@Composable
private fun PersonFigure(modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 300f
        val figure = Color.White.copy(alpha = 0.55f)
        drawCircle(figure, radius = 58f * unit, center = Offset(150f * unit, 118f * unit))
        drawOval(figure, topLeft = Offset(45f * unit, 198f * unit), size = Size(210f * unit, 184f * unit))
    }
}

/** The page's side margin, the backdrop's height, a poster's and a cast card's width. */
internal val PAGE_EDGE = 20.dp
private val HERO_HEIGHT = 300.dp
private val POSTER_WIDTH = 104.dp
private val CAST_WIDTH = 144.dp
/** A related title's poster, two by three as a poster stands. */
private val RELATED_HEIGHT = 216.dp

private val ACTION_HEIGHT = 48.dp

/**
 * The details of a title asked about, while they are open - on the page in
 * front only: the shelves and the live screen are neighbouring pages,
 * composed side by side, and one question is one dialog. [actions] are the
 * question's first answers, where the page was opened from one.
 */
@Composable
internal fun DetailsShown(
    viewModel: LiveViewModel,
    actions: DetailsActions?,
    except: MarkTarget.Kind? = null,
    onRelated: ((RelatedTitle) -> Unit)? = null,
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    // A kind the screen shows in a place of its own (see SeriesScreen).
    DetailsPage(
        viewModel,
        details?.takeIf { it.target.kind != except },
        actions,
        over = null,
        onRelated = onRelated,
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
    onRelated: ((RelatedTitle) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The page gone back to, with the actions it had: the screen's own are
    // the last title's it opened.
    var back by remember { mutableStateOf<LiveViewModel.DetailsBack?>(null) }
    val page = open?.takeIf { LocalIsCurrentPage.current } ?: return
    val restored = back?.takeIf { it.page.target == page.target }
    val shown = if (restored != null) restored.actions as DetailsActions? else actions
    // Where the screen has no way of its own, a title the page leads on to
    // opens as the overview's does: its details, with nothing to press.
    val related = onRelated ?: @DontMemoize { title: RelatedTitle -> viewModel.openDetails(title.markTarget()) }
    DetailsDialog(
        state = page,
        server = state.live.server,
        showArtwork = state.settings.showArtwork,
        actions = shown,
        adaptive = state.settings.adaptiveColor,
        intensity = state.settings.adaptiveColorIntensity,
        over = over,
        // The page stays behind the one it leads on to ...
        onRelated = { title ->
            viewModel.keepDetails(shown)
            related(title)
        },
        // ... and back, from there, goes back to it; from the first page,
        // or from the seasons a show's page gave way to, as before.
        onDismiss = {
            val previous = if (over == null) viewModel.backDetails() else null
            back = previous
            if (previous == null) onDismiss()
        },
    )
}
