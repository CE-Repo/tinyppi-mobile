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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
 * the plot, the director and the cast as plain lines - hidden with the rest
 * of a cut plot and let out with it - and last, past a thin rule, the ratings as a row of their
 * own: short facts about the film, taken in at a glance.
 */
@Composable
internal fun PlotCard(media: MediaInfo, colourfulRatings: Boolean) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.hasPlot) {
            // A long plot is cut to a few lines, and a tap on it lets the
            // rest out and takes it back. It folds only where there is more
            // to show, and a new plot starts cut again. The credits hang on
            // the plot's end: they come out with the rest of a cut plot, and
            // stand under one that fits. To a screen reader it is a button
            // that says what a tap does.
            var open by rememberSaveable(media.plot) { mutableStateOf(false) }
            var cut by remember(media.plot) { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Only one with more to show is a button at all: one
                        // that fits reads as its plot and its credits.
                        if (open || cut) {
                            Modifier
                                .clip(FOLD_SHAPE)
                                .clickable(
                                    onClickLabel = stringResource(
                                        if (open) R.string.live_plot_less else R.string.live_plot_more,
                                    ),
                                    role = Role.Button,
                                ) { open = !open }
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
                    onTextLayout = { cut = it.hasVisualOverflow },
                )
                if (media.hasCredits && (open || !cut)) {
                    Credits(media, Modifier.padding(top = 12.dp))
                }
            }
        } else if (media.hasCredits) {
            // Without a plot, the credits on their own.
            Credits(media, Modifier.align(Alignment.CenterHorizontally))
        }
        if (media.hasRatings) {
            // Last, past a thin rule, from the plot or the cast: the same
            // hairline the rows of a reading card are ruled with.
            if (media.hasPlot || media.hasCredits) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            RatingRow(media, colourfulRatings)
        }
    }
}

/**
 * Who directed it and who is in it - the directors over the cast, every name
 * the add-on sends - centred like everything over them. What kind of film
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
            CreditText(stringResource(R.string.live_credit_cast), Formatters.names(media.cast, media.castMore))
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
 * colour rather than its name - the same drawings the add-on's dashboard
 * serves (`tools/gen_rating_marks.py` in script.tinyppi writes both) - and
 * says in words what it shows, since a screen reader can say nothing of a
 * drawing. Not [colourful] - the reader's choice in the settings - every one
 * of them is drawn in greys instead: its mark without colour, on the plain
 * ground a source without a drawing has.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo, colourful: Boolean) {
    val tall = badgeHeight()
    val plain = MaterialTheme.colorScheme.surfaceContainerHigh
    fun ground(colour: Color) = if (colourful) colour else plain
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
                RatingShield(spoken = said, value = media.ageLevel.trim(), tall = tall, inset = 0.dp) {
                    BoardPlate(drawing, colourful)
                }
            } else {
                RatingShield(spoken = said, value = said, tall = tall)
            }
        }
        media.ratings.forEach { entry ->
            val name = sourceName(entry)
            val value = Formatters.rating(entry.rating)
            val mark = RATING_MARKS[entry.source]
            RatingShield(
                spoken = "$name $value".trim(),
                value = value,
                tall = tall,
                ground = mark?.ground?.let(::ground) ?: plain,
            ) {
                if (mark != null) DrawnMark(mark.drawing, colourful) else LetterMark(name.take(1))
            }
        }
        if (media.userRating > 0) {
            val value = media.userRating.toString()
            // The reader's own wears a heart on the accent, which is what
            // says it is theirs and not a house's.
            RatingShield(
                spoken = "${stringResource(R.string.live_rating_mine)} $value",
                value = value,
                tall = tall,
                ground = ground(MaterialTheme.colorScheme.primary),
            ) {
                HeartMark(if (colourful) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (media.top250 > 0) {
            RatingShield(
                spoken = stringResource(R.string.live_rating_top250, media.top250),
                value = "#${media.top250}",
                tall = tall,
                ground = ground(TOP250.ground),
            ) { DrawnMark(TOP250.drawing, colourful) }
        }
    }
}

/** A house's mark, and the colour of the ground it is drawn on. */
private class HouseMark(val drawing: Int, val ground: Color)

/** The houses there is a drawing of, by the name the add-on gives them. */
private val RATING_MARKS = mapOf(
    "imdb" to HouseMark(R.drawable.rating_imdb, Color(0xFFF5C518)),
    "tmdb" to HouseMark(R.drawable.rating_tmdb, Color(0xFF0D253F)),
    "trakt" to HouseMark(R.drawable.rating_trakt, Color(0xFFED1C24)),
    "tvdb" to HouseMark(R.drawable.rating_tvdb, Color(0xFF1E2A33)),
    "letterboxd" to HouseMark(R.drawable.rating_letterboxd, Color(0xFF14181C)),
    "metacritic" to HouseMark(R.drawable.rating_metacritic, Color(0xFF66CC33)),
    "metacriticuser" to HouseMark(R.drawable.rating_metacriticuser, Color(0xFF66CC33)),
    "rottentomatoes" to HouseMark(R.drawable.rating_rottentomatoes, Color(0xFFF9D320)),
    "popcorn" to HouseMark(R.drawable.rating_popcorn, Color(0xFF1E88E5)),
    "rogerebert" to HouseMark(R.drawable.rating_rogerebert, Color(0xFFC8102E)),
    "myanimelist" to HouseMark(R.drawable.rating_myanimelist, Color(0xFF2E51A2)),
)

private val TOP250 = HouseMark(R.drawable.rating_top250, Color(0xFFF5C518))

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
 * with nothing to say what it is. A tap - or a long press - shows the same
 * words over it for a moment, for a mark the reader does not know.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingShield(
    spoken: String,
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
        tooltip = { PlainTooltip { Text(spoken) } },
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
 * A badge heard as [spoken] and as nothing it holds. Built outside the
 * composition, where a lambda is just a lambda and not one more thing for the
 * Compose compiler to remember against its argument.
 */
private fun Modifier.heardAs(spoken: String): Modifier = clearAndSetSemantics { contentDescription = spoken }

/** A house's drawing, as tall as a line of the shield and as wide as it is drawn. */
@Composable
private fun DrawnMark(drawing: Int, colourful: Boolean) {
    val painter = painterResource(drawing)
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = greys(colourful),
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
private fun BoardPlate(drawing: Int, colourful: Boolean) {
    val painter = painterResource(drawing)
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = greys(colourful),
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

/** The heart the reader's own rating wears, in [tint] on the ground it is drawn on. */
@Composable
private fun HeartMark(tint: Color) {
    Icon(
        imageVector = Icons.Rounded.Favorite,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(11.dp),
    )
}

/** A mark drawn in greys, where the reader asked for no colours; null keeps its own. */
private fun greys(colourful: Boolean): ColorFilter? =
    if (colourful) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The air between the slogan and the plot under it. */
private val SLOGAN_GAP = 4.dp

/** The corners of the ripple a tap on the plot draws. */
private val FOLD_SHAPE = RoundedCornerShape(8.dp)

/** How many lines of a plot are shown until the reader asks for the rest. */
private const val PLOT_LINES = 3

/** How tall a mark stands on its plate, and the corners of a shield. */
private val MARK = 18.dp
private val SHIELD = RoundedCornerShape(6.dp)

/**
 * How tall every badge stands - a shield, a heart, an age rating's - so a
 * row of them is one height whatever it holds. It grows with
 * the reader's font size, and all of them with it: a shield's figures are set
 * in text and must not be cut, and a mark beside it must not then stand short.
 */
@Composable
private fun badgeHeight(): Dp = BADGE * LocalDensity.current.fontScale.coerceAtLeast(1f)

private val BADGE = 24.dp

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
                    colourfulRatings = true,
                )
            }
        }
    }
}
