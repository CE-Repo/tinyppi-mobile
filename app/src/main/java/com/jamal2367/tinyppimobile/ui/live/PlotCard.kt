package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.util.Formatters
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * What the title is about, what it has been given and who is in it: its
 * slogan, its plot, its ratings and its cast, as the box knows them.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it folds, comes off the screen and goes back on (see
 * CardLayout). Drawn for a title with a plot, ratings or a cast (see
 * [MediaInfo.showsPlotCard]), and left out for one with none of them - a
 * file the library does not know. The slogan goes only with a plot: on its
 * own it says too little, and above a plot it is left out where there is
 * none, which is most episodes.
 *
 * Everything in it centred. The slogan first, right over the plot so the two
 * read as one text, set like a credit's heading, and the plot in the quieter
 * colour the year and genre are set in, since it is the one paragraph on the
 * screen and the readings below it should not have to shout over it. Under
 * the plot, the director and the cast as plain lines, and last, under a
 * heading of their own, the ratings as a row of their own: short facts about
 * the film, taken in at a glance. All of them under the plot are folded away
 * until a tap on it lets them out.
 */
@Composable
internal fun PlotCard(media: MediaInfo, title: String) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.hasPlot) {
            // The card starts folded: the plot cut to a few lines, and the
            // credits and the ratings hanging on its end hidden under it. A
            // tap on it lets all of it out, and another folds it again. It
            // folds wherever there is more than those lines to show - a plot
            // that runs past them, or credits or ratings under it - and a new
            // plot starts folded again. To a screen reader it is a button
            // that says what a tap does; a tap on a rating's shield is the
            // shield's.
            //
            // Folded again for every new title - [title] says which this is -
            // and every new plot. What was let out is kept with them: a card
            // that comes back after the playback ended is handed its last
            // state whatever plays now, and only the same title may find it
            // open. The plot goes in as its hash, not as the whole text.
            val shown = "$title\n${media.plot.hashCode()}"
            var openFor by rememberSaveable { mutableStateOf<String?>(null) }
            val open = openFor == shown
            var cut by remember(title, media.plot) { mutableStateOf(false) }
            val folds = cut || media.hasCredits || media.hasRatings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Only one with more to show is a button at all: a
                        // plot that fits with nothing under it reads as text.
                        if (folds) {
                            Modifier
                                .clip(FOLD_SHAPE)
                                .clickable(
                                    onClickLabel = stringResource(
                                        if (open) R.string.live_plot_less else R.string.live_plot_more,
                                    ),
                                    role = Role.Button,
                                ) { openFor = if (open) null else shown }
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The slogan right over the plot, so the two read as one
                // text: upright, set like a credit's heading, and as far
                // over it as a credit's heading is over its names.
                if (media.tagline.isNotBlank()) {
                    Text(
                        text = media.tagline.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = SLOGAN_GAP),
                    )
                }
                Text(
                    text = media.plot.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = if (open) Int.MAX_VALUE else PLOT_LINES,
                    overflow = TextOverflow.Ellipsis,
                    // Measured folded: an open plot no longer runs past its
                    // lines, but is still one that folds.
                    onTextLayout = { if (!open) cut = it.hasVisualOverflow },
                )
                if (open) {
                    if (media.hasCredits) Credits(media, Modifier.padding(top = 12.dp))
                    if (media.hasRatings) Ratings(media, Modifier.padding(top = 16.dp))
                }
            }
        } else {
            // Without a plot, the credits and the ratings on their own.
            if (media.hasCredits) Credits(media, Modifier.align(Alignment.CenterHorizontally))
            if (media.hasRatings) Ratings(media, Modifier.padding(top = RATINGS_GAP))
        }
    }
}

/**
 * The ratings, last, under a heading of their own like a credit's, as far
 * under the cast as the cast is under the director.
 */
@Composable
private fun Ratings(media: MediaInfo, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.live_ratings),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        RatingRow(media)
    }
}

/**
 * Who directed it and who is in it - every director over the cast's first
 * twenty - centred like everything over them. What kind of film
 * it is and who made it stand in the card above.
 */
@Composable
private fun Credits(media: MediaInfo, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        // A clear gap between the two credits, so each reads as its own.
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (media.directors.isNotEmpty()) {
            CreditText(stringResource(R.string.live_credit_director), Formatters.names(media.directors, false))
        }
        if (media.cast.isNotEmpty()) {
            // The leads alone, however many an older add-on sends.
            CreditText(
                stringResource(R.string.live_credit_cast),
                Formatters.names(media.cast.take(CAST_LIMIT), media.castMore || media.cast.size > CAST_LIMIT),
            )
        }
    }
}

@Composable
private fun CreditText(label: String, names: String) {
    Text(
        text = creditLine(label, names, MaterialTheme.colorScheme),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/**
 * A credit: what it is in bold and the text's full colour, on a line of its
 * own over the names in the quieter one the plot is set in. Heard as it
 * reads, "Cast, Timothée Chalamet, Zendaya". Built outside the composition,
 * like [heardAs]: a few lines of text have nothing to remember.
 */
private fun creditLine(label: String, names: String, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) { append(label) }
    append("\n")
    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(names) }
}

/**
 * Every rating the library holds: the age rating first - who the film is for
 * is asked before how good it is - then a score for each source, in the
 * order the add-on sends them, the reader's own and the place in the IMDb
 * Top 250.
 *
 * Each is a small shield wearing its house's mark on a ground of its house's
 * colour rather than its name - the same marks the add-on's dashboard
 * serves: the houses' own logos where there is one, the rest drawn by
 * `tools/gen_rating_marks.py` in script.tinyppi - and
 * says in words what it shows, since a screen reader can say nothing of a
 * drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo) {
    val tall = badgeHeight()
    val colours = MaterialTheme.colorScheme
    FlowRow(
        // Every line of them centred, the last one too.
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        // A little more air over them than between the lines of the plot:
        // they are a row of their own, not its last line.
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        if (media.mpaa.isNotBlank()) {
            // One the add-on could place - FSK, MPA, TV Parental Guidelines,
            // BBFC - is a shield like the scores': the board's name on the
            // colour of the level, edge to edge on the left, and the level
            // beside it ("FSK" | "12"). Any other is written as the scraper
            // wrote it.
            val said = media.mpaa.trim()
            val drawing = AGE_MARKS[media.age]
            if (drawing != null) {
                RatingShield(
                    spoken = said,
                    named = boardOf(media.age),
                    value = media.ageLevel.trim(),
                    tall = tall,
                    inset = 0.dp,
                ) { BoardPlate(drawing) }
            } else {
                RatingShield(spoken = said, named = said, value = said, tall = tall)
            }
        }
        media.ratings.forEach { entry ->
            val name = sourceName(entry)
            val value = Formatters.rating(entry.rating)
            val mark = RATING_MARKS[entry.source]
            RatingShield(
                spoken = "$name $value".trim(),
                named = name,
                value = value,
                tall = tall,
                ground = mark?.ground ?: colours.surfaceContainerHigh,
            ) {
                if (mark != null) DrawnMark(mark.drawing) else LetterMark(name.take(1))
            }
        }
        if (media.userRating > 0) {
            val value = media.userRating.toString()
            // The reader's own wears a heart on the accent, which is what
            // says it is theirs and not a house's.
            RatingShield(
                spoken = "${stringResource(R.string.live_rating_mine)} $value",
                named = stringResource(R.string.live_rating_mine),
                value = value,
                tall = tall,
                ground = colours.primary,
            ) { HeartMark() }
        }
        if (media.top250 > 0) {
            RatingShield(
                spoken = stringResource(R.string.live_rating_top250, media.top250),
                named = "IMDb Top 250",
                value = "#${media.top250}",
                tall = tall,
                ground = TOP250.ground,
            ) { DrawnMark(TOP250.drawing) }
        }
    }
}

/** A house's mark, and the colour of the ground it is drawn on. */
private class HouseMark(val drawing: Int, val ground: Color)

/** The houses there is a drawing of, by the name the add-on gives them. */
private val RATING_MARKS = mapOf(
    "imdb" to HouseMark(R.drawable.rating_imdb, Color(0xFF121212)),
    "tmdb" to HouseMark(R.drawable.rating_tmdb, Color(0xFF0D253F)),
    "trakt" to HouseMark(R.drawable.rating_trakt, Color(0xFF9F42C6)),
    "tvdb" to HouseMark(R.drawable.rating_tvdb, Color(0xFF555555)),
    "letterboxd" to HouseMark(R.drawable.rating_letterboxd, Color(0xFF202830)),
    "metacritic" to HouseMark(R.drawable.rating_metacritic, Color(0xFF66CC33)),
    "metacriticuser" to HouseMark(R.drawable.rating_metacriticuser, Color(0xFF66CC33)),
    "rottentomatoes" to HouseMark(R.drawable.rating_rottentomatoes, Color(0xFFF9D320)),
    "popcorn" to HouseMark(R.drawable.rating_popcorn, Color(0xFF1E88E5)),
    "rogerebert" to HouseMark(R.drawable.rating_rogerebert, Color(0xFFC8102E)),
    "myanimelist" to HouseMark(R.drawable.rating_myanimelist, Color(0xFF2E51A2)),
)

private val TOP250 = HouseMark(R.drawable.rating_top250, Color(0xFFF5C518))

/** The board an age rating's mark is from, by its name ("fsk12" is the FSK's). */
private fun boardOf(age: String): String = when {
    age.startsWith("fsk") -> "FSK"
    age.startsWith("mpa_") -> "MPA"
    age.startsWith("tv_") -> "TV Parental Guidelines"
    else -> "BBFC"
}

/**
 * The age ratings there is a drawing of, by the name the add-on gives the
 * mark (see `age_rating` in its `web/ratings.py`): the plate of the FSK's,
 * the MPA's and the TV Parental Guidelines' - both in the FSK's colours - and
 * the BBFC's levels, each the board's name on the level's colour.
 */
private val AGE_MARKS = mapOf(
    "fsk0" to R.drawable.rating_fsk0,
    "fsk6" to R.drawable.rating_fsk6,
    "fsk12" to R.drawable.rating_fsk12,
    "fsk16" to R.drawable.rating_fsk16,
    "fsk18" to R.drawable.rating_fsk18,
    "mpa_g" to R.drawable.rating_mpa_g,
    "mpa_pg" to R.drawable.rating_mpa_pg,
    "mpa_pg13" to R.drawable.rating_mpa_pg13,
    "mpa_r" to R.drawable.rating_mpa_r,
    "mpa_nc17" to R.drawable.rating_mpa_nc17,
    "tv_y" to R.drawable.rating_tv_y,
    "tv_y7" to R.drawable.rating_tv_y7,
    "tv_g" to R.drawable.rating_tv_g,
    "tv_pg" to R.drawable.rating_tv_pg,
    "tv_14" to R.drawable.rating_tv_14,
    "tv_ma" to R.drawable.rating_tv_ma,
    "bbfc_u" to R.drawable.rating_bbfc_u,
    "bbfc_pg" to R.drawable.rating_bbfc_pg,
    "bbfc_12a" to R.drawable.rating_bbfc_12a,
    "bbfc_12" to R.drawable.rating_bbfc_12,
    "bbfc_15" to R.drawable.rating_bbfc_15,
    "bbfc_18" to R.drawable.rating_bbfc_18,
    "bbfc_r18" to R.drawable.rating_bbfc_r18,
)

/**
 * The house a score is from, for the sentence a screen reader hears: its
 * brand name, or - for a source no scraper names, which an NFO may carry -
 * the name the library has, capitalised.
 */
private fun sourceName(entry: SourceRating): String =
    RATING_NAMES[entry.source] ?: entry.source.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * One rating as a shield: [side] on the left, on [ground] - a house's own
 * colour, so its mark runs into it and the left reads as one plate - [inset]
 * from its edges, and on the right the figure in bold.
 * Square-cornered like a plaque rather than round like a button. Without a
 * figure - an age rating from an add-on too old to send its level - the
 * side stands alone.
 *
 * Heard as [spoken] and nothing else - "IMDb 8.5" rather than a bare "8.5"
 * with nothing to say what it is. A tap - or a long press - shows over it
 * for a moment whose it is, [named], for a mark the reader does not know.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingShield(
    spoken: String,
    named: String,
    value: String,
    tall: Dp,
    ground: Color = Color.Transparent,
    inset: Dp = 2.dp,
    side: (@Composable () -> Unit)? = null,
) {
    val told = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(named) } },
        state = told,
    ) {
        Shield(spoken, value, tall, ground, inset, side) { scope.launch { told.show() } }
    }
}

/** The shield itself: [side] and the figure, a tap away from its words. */
@Composable
private fun Shield(
    spoken: String,
    value: String,
    tall: Dp,
    ground: Color,
    inset: Dp,
    side: (@Composable () -> Unit)?,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heardAs(spoken)
            .clickable(onClick = onTap)
            .height(tall)
            .clip(SHIELD)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, SHIELD)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (side != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(ground)
                    .padding(horizontal = inset),
                contentAlignment = Alignment.Center,
            ) { side() }
        }
        // On one line, centred in the height the shield is given: a long age
        // rating written out is cut at the edge rather than growing a second.
        if (value.isNotEmpty()) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A badge heard as [spoken] and as nothing it holds - on its own, not as
 * part of the plot it folds with. Built outside the composition, where a
 * lambda is just a lambda and not one more thing for the Compose compiler to
 * remember against its argument.
 */
private fun Modifier.heardAs(spoken: String): Modifier =
    semantics(mergeDescendants = true) {}.clearAndSetSemantics { contentDescription = spoken }

/** A house's drawing, as tall as a line of the shield and as wide as it is drawn. */
@Composable
private fun DrawnMark(drawing: Int) {
    val painter = painterResource(drawing)
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .height(MARK)
            .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
    )
}

/**
 * An age rating board's plate: its name on the level's colour, the whole
 * height of the shield and as wide as it is drawn, so the colour runs to the
 * shield's edges as a house's ground does.
 */
@Composable
private fun BoardPlate(drawing: Int) {
    val painter = painterResource(drawing)
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
    )
}

/** A source there is no drawing of: its first letter on the plate. */
@Composable
private fun LetterMark(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .widthIn(min = 12.dp),
    )
}

/** The heart the reader's own rating wears, on the accent it is drawn on. */
@Composable
private fun HeartMark() {
    Icon(
        imageVector = Icons.Rounded.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(11.dp),
    )
}

/** How many of the cast are named, as the add-on cuts them; "…" says there are more. */
private const val CAST_LIMIT = 20

/** The air over the ratings' heading, besides the card's own between its rows. */
private val RATINGS_GAP = 6.dp

/** The air between the slogan and the plot under it. */
private val SLOGAN_GAP = 4.dp

/** The corners of the ripple a tap on the plot draws. */
private val FOLD_SHAPE = RoundedCornerShape(8.dp)

/** How many lines of a plot are shown until the reader asks for the rest. */
private const val PLOT_LINES = 5

/** How tall a mark stands on its plate, and the corners of a shield. */
private val MARK = 20.dp
private val SHIELD = RoundedCornerShape(6.dp)

/**
 * How tall every badge stands - a shield, a heart, an age rating's - so a
 * row of them is one height whatever it holds. It grows with
 * the reader's font size, and all of them with it: a shield's figures are set
 * in text and must not be cut, and a mark beside it must not then stand short.
 */
@Composable
private fun badgeHeight(): Dp = BADGE * LocalDensity.current.fontScale.coerceAtLeast(1f)

private val BADGE = 26.dp

/** A film's slogan, plot, ratings and cast, as the card draws them. */
@PreviewLightDark
@Composable
internal fun PlotCardPreview() {
    TinyPpiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                PlotCard(
                    MediaInfo(
                        tagline = "Long live the fighters.",
                        plot = "Paul joins the Fremen and walks the desert as one of them.",
                        ratings = listOf(SourceRating("imdb", 8.5, 612_345), SourceRating("tmdb", 8.2, 6_120)),
                        mpaa = "FSK 12",
                        age = "fsk12",
                        ageLevel = "12",
                        userRating = 9,
                        top250 = 27,
                        directors = listOf("Denis Villeneuve"),
                        cast = listOf("Timothée Chalamet", "Zendaya", "Rebecca Ferguson", "Javier Bardem", "Josh Brolin"),
                        castMore = true,
                    ),
                    title = "Dune: Part Two",
                )
            }
        }
    }
}
