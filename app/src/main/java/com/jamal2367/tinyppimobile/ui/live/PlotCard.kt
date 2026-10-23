package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.util.Formatters
import java.util.Locale

/**
 * What the title is about and what it has been given: its ratings, its slogan
 * and its plot, as the box's library has them.
 *
 * Under the card of what is playing until the reader moves it, and like every
 * other card there it folds, comes off the screen and goes back on (see
 * CardLayout). Drawn for a title with a plot, ratings or both (see
 * [MediaInfo.showsPlotCard]), and left out for one with neither - a file the
 * library does not know. The slogan goes only with a plot: on its own it says
 * too little, and above a plot it is left out where there is none, which is
 * most episodes.
 *
 * The ratings come first, as a row of pills of the kind the format badges
 * are: short facts about the film, taken in at a glance before the paragraph
 * under them is read. Then the slogan in italics and in the text's full
 * colour, as the line a poster carries, and the plot in the quieter colour
 * the year and genre are set in, since it is the one paragraph on the screen
 * and the readings below it should not have to shout over it.
 */
@Composable
internal fun PlotCard(media: MediaInfo) {
    SectionCard(title = stringResource(R.string.live_plot), foldId = FOLD_PLOT) {
        if (media.hasRatings) {
            RatingRow(media)
        }
        if (media.hasPlot) {
            if (media.tagline.isNotBlank()) {
                Text(
                    text = media.tagline.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = media.plot.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Every rating the library holds, in the order the add-on sends them: a score
 * for each source with how many voted for it, the reader's own, the place in
 * the IMDb Top 250 and the age rating.
 *
 * Each wears its house's mark in its house's colours rather than its name -
 * the same drawings the add-on's dashboard serves (`tools/gen_rating_marks.py`
 * in script.tinyppi writes both) - and says in words what it shows, since a
 * screen reader can say nothing of a drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        media.ratings.forEach { entry ->
            val name = sourceName(entry)
            val value = Formatters.rating(entry.rating)
            val votes = entry.votes.takeIf { it > 0 }?.let(Formatters::count)
            val spoken = listOfNotNull(
                "$name $value".trim(),
                votes?.let { pluralStringResource(R.plurals.live_rating_votes, entry.votes, it) },
            ).joinToString(", ")
            val drawing = RATING_MARKS[entry.source]
            RatingBadge(spoken = spoken, value = value, note = votes) {
                if (drawing != null) DrawnMark(drawing) else LetterMark(name.take(1))
            }
        }
        if (media.userRating > 0) {
            val value = media.userRating.toString()
            RatingBadge(spoken = "${stringResource(R.string.live_rating_mine)} $value", value = value) { HeartMark() }
        }
        if (media.top250 > 0) {
            RatingBadge(
                spoken = stringResource(R.string.live_rating_top250, media.top250),
                value = "#${media.top250}",
            ) { DrawnMark(R.drawable.rating_top250) }
        }
        if (media.mpaa.isNotBlank()) {
            // A German age rating is the FSK's coloured mark and nothing else;
            // any other is written as the scraper wrote it.
            val said = media.mpaa.trim()
            val fsk = FSK_MARKS[media.fsk]
            if (fsk != null) {
                RatingBadge(spoken = said) { DrawnMark(fsk) }
            } else {
                RatingBadge(spoken = said, value = said)
            }
        }
    }
}

/** The houses there is a drawing of, by the name the add-on gives them. */
private val RATING_MARKS = mapOf(
    "imdb" to R.drawable.rating_imdb,
    "tmdb" to R.drawable.rating_tmdb,
    "trakt" to R.drawable.rating_trakt,
    "tvdb" to R.drawable.rating_tvdb,
    "metacritic" to R.drawable.rating_metacritic,
    "rottentomatoes" to R.drawable.rating_rottentomatoes,
)

/** The FSK's five levels, by the level the add-on reads out of an age rating. */
private val FSK_MARKS = mapOf(
    "0" to R.drawable.rating_fsk0,
    "6" to R.drawable.rating_fsk6,
    "12" to R.drawable.rating_fsk12,
    "16" to R.drawable.rating_fsk16,
    "18" to R.drawable.rating_fsk18,
)

/**
 * The house a score is from, for the sentence a screen reader hears: its
 * brand name, or - for a source no scraper names, which an NFO may carry -
 * the name the library has, capitalised.
 */
private fun sourceName(entry: SourceRating): String =
    RATING_NAMES[entry.source] ?: entry.source.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * One rating as a pill: its mark hugging the left end, the figure in bold
 * and how many voted. Heard as [spoken] and nothing else - "IMDb 8.5,
 * 612,345 votes" rather than "8.5 612,345" with nothing to say what either is.
 *
 * Every word in the colour made for the pill's ground rather than the muted
 * grey a format badge is written in: a pill of three parts set in grey on a
 * tinted ground - a poster's, or the phone's own palette - came out too faint
 * to read the count in.
 */
@Composable
private fun RatingBadge(
    spoken: String,
    value: String? = null,
    note: String? = null,
    mark: (@Composable () -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier = Modifier
            .heardAs(spoken)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            // Tight to a mark, and a pill's own air beside words.
            .padding(
                start = if (mark != null) 4.dp else 10.dp,
                end = if (value != null) 10.dp else 4.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        mark?.invoke()
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink)
        }
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

/**
 * A pill heard as [spoken] and as nothing it holds. Built outside the
 * composition, where a lambda is just a lambda and not one more thing for the
 * Compose compiler to remember against its argument.
 */
private fun Modifier.heardAs(spoken: String): Modifier = clearAndSetSemantics { contentDescription = spoken }

/** A house's drawing, as tall as a line of the pill and as wide as it is drawn. */
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

/** A source there is no drawing of: its first letter in a plain circle. */
@Composable
private fun LetterMark(letter: String) {
    Box(
        modifier = Modifier
            .size(MARK)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The reader's own rating: a heart on the accent, which says it is theirs and not a house's. */
@Composable
private fun HeartMark() {
    Box(
        modifier = Modifier
            .size(MARK)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** How tall a mark stands in its pill. */
private val MARK = 20.dp

/** A film's ratings, slogan and plot, as the card draws them. */
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
                        fsk = "12",
                        userRating = 9,
                        top250 = 27,
                    ),
                )
            }
        }
    }
}
