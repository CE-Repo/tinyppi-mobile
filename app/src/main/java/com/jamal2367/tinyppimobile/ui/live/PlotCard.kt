package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
import java.util.Locale

/**
 * What the title is about, what it has been given and who made it: its
 * slogan, its plot, its ratings and its credits, as the box knows them.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it folds, comes off the screen and goes back on (see
 * CardLayout). Drawn for a title with a plot, ratings or credits (see
 * [MediaInfo.showsPlotCard]), and left out for one with none of them - a
 * file the library does not know. The slogan goes only with a plot: on its
 * own it says too little, and above a plot it is left out where there is
 * none, which is most episodes.
 *
 * Everything in it centred. The slogan first, in italics and in the text's
 * full colour, as the line a poster carries, and the plot in the quieter
 * colour the year and genre are set in, since it is the one paragraph on the
 * screen and the readings below it should not have to shout over it. Under
 * them, past a thin rule, the ratings as a row of their own: short facts
 * about the film, taken in at a glance. And past another, the credits as
 * plain lines.
 */
@Composable
internal fun PlotCard(media: MediaInfo) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.hasPlot) {
            // Centred as a block and line by line, over the row of ratings
            // under them, so the card reads down its middle.
            if (media.tagline.isNotBlank()) {
                Text(
                    text = media.tagline.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Text(
                text = media.plot.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (media.hasRatings) {
            // A thin rule between the plot and them, where there is a plot:
            // the same hairline the rows of a reading card are ruled with.
            if (media.hasPlot) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            RatingRow(media)
        }
        if (media.hasCredits) {
            // The same rule over them, from the ratings or from the plot.
            if (media.hasPlot || media.hasRatings) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            Credits(media)
        }
    }
}

/**
 * Who made the title, a centred line each in the order a film's credits are
 * asked about: who directed it and who is in it - what kind of film it is and
 * the studio stand in the card above. The first names of each, as the add-on
 * cuts them, with an ellipsis after them where there are more. Each is what
 * it is in bold and the text's full colour, named for one or for more than
 * one ("Director", "Directors"), and then the names in the plot's quieter
 * one; a list Kodi has none of is left out.
 */
@Composable
private fun Credits(media: MediaInfo) {
    val credits = listOf(
        Credit(
            pluralStringResource(R.plurals.live_credit_directors, counted(media.directors, media.directorsMore)),
            media.directors,
            media.directorsMore,
        ),
        Credit(stringResource(R.string.live_credit_cast), media.cast, media.castMore),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        // More air between two of them than between the lines of one: a
        // long cast runs on over several.
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val named = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        val names = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
        credits.filter { it.names.isNotEmpty() }.forEach { credit ->
            Text(
                text = buildAnnotatedString {
                    withStyle(named) { append(credit.label) }
                    append(" ")
                    withStyle(names) { append(Formatters.names(credit.names, credit.more)) }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One line of the credits: what it is called, its names and whether the add-on cut it. */
private class Credit(val label: String, val names: List<String>, val more: Boolean)

/** How many a list holds, for what it is called: a cut one is more than it shows. */
private fun counted(names: List<String>, more: Boolean): Int = names.size + if (more) 1 else 0

/**
 * Every rating the library holds: the age rating first - who the film is for
 * is asked before how good it is - then a score for each source with how many
 * voted for it, in the order the add-on sends them, the reader's own and the
 * place in the IMDb Top 250.
 *
 * Each is a small shield wearing its house's mark on a ground of its house's
 * colour rather than its name - the same drawings the add-on's dashboard
 * serves (`tools/gen_rating_marks.py` in script.tinyppi writes both) - and
 * says in words what it shows, since a screen reader can say nothing of a
 * drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo) {
    val tall = badgeHeight()
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
                    BoardPlate(drawing)
                }
            } else {
                RatingShield(spoken = said, value = said, tall = tall)
            }
        }
        media.ratings.forEach { entry ->
            val name = sourceName(entry)
            val value = Formatters.rating(entry.rating)
            val votes = entry.votes.takeIf { it > 0 }?.let(Formatters::count)
            val spoken = listOfNotNull(
                "$name $value".trim(),
                votes?.let { pluralStringResource(R.plurals.live_rating_votes, entry.votes, it) },
            ).joinToString(", ")
            val mark = RATING_MARKS[entry.source]
            RatingShield(
                spoken = spoken,
                value = value,
                tall = tall,
                note = votes,
                ground = mark?.ground ?: MaterialTheme.colorScheme.surfaceContainerHigh,
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
                value = value,
                tall = tall,
                ground = MaterialTheme.colorScheme.primary,
            ) { HeartMark() }
        }
        if (media.top250 > 0) {
            RatingShield(
                spoken = stringResource(R.string.live_rating_top250, media.top250),
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
    "imdb" to HouseMark(R.drawable.rating_imdb, Color(0xFFF5C518)),
    "tmdb" to HouseMark(R.drawable.rating_tmdb, Color(0xFF0D253F)),
    "trakt" to HouseMark(R.drawable.rating_trakt, Color(0xFFED1C24)),
    "tvdb" to HouseMark(R.drawable.rating_tvdb, Color(0xFF1E2A33)),
    "metacritic" to HouseMark(R.drawable.rating_metacritic, Color.Black),
    "rottentomatoes" to HouseMark(R.drawable.rating_rottentomatoes, Color.White),
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
 * from its edges, and on the right the figure in bold with how many voted.
 * Square-cornered like a plaque rather than round like a button. Without a
 * figure - an age rating from an add-on too old to send its level - the
 * side stands alone.
 *
 * Heard as [spoken] and nothing else - "IMDb 8.5, 612,345 votes" rather than
 * "8.5 612,345" with nothing to say what either is.
 */
@Composable
private fun RatingShield(
    spoken: String,
    value: String,
    tall: Dp,
    note: String? = null,
    ground: Color = Color.Transparent,
    inset: Dp = 2.dp,
    side: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .heardAs(spoken)
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
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
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
            .size(13.dp),
    )
}

/** How tall a mark stands on its plate, and the corners of a shield. */
private val MARK = 20.dp
private val SHIELD = RoundedCornerShape(7.dp)

/**
 * How tall every badge stands - a shield, a heart, an age rating's - so a
 * row of them is one height whatever it holds. It grows with
 * the reader's font size, and all of them with it: a shield's figures are set
 * in text and must not be cut, and a mark beside it must not then stand short.
 */
@Composable
private fun badgeHeight(): Dp = BADGE * LocalDensity.current.fontScale.coerceAtLeast(1f)

private val BADGE = 26.dp

/** A film's slogan, plot, ratings and credits, as the card draws them. */
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
                )
            }
        }
    }
}
