package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingRow(media: MediaInfo) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        media.ratings.forEach { entry ->
            RatingBadge(
                value = Formatters.rating(entry.rating),
                // A source the library names nothing is drawn with no name
                // rather than an empty one beside the star.
                name = sourceName(entry).ifBlank { null },
                note = if (entry.votes > 0) {
                    pluralStringResource(R.plurals.live_rating_votes, entry.votes, Formatters.count(entry.votes))
                } else {
                    null
                },
                star = RATING_STAR,
            )
        }
        if (media.userRating > 0) {
            // The reader's own wears the star in the accent, which is what
            // says it is theirs and not a house's.
            RatingBadge(
                value = media.userRating.toString(),
                name = stringResource(R.string.live_rating_mine),
                star = MaterialTheme.colorScheme.primary,
            )
        }
        if (media.top250 > 0) {
            RatingBadge(value = stringResource(R.string.live_rating_top250, media.top250))
        }
        if (media.mpaa.isNotBlank()) {
            RatingBadge(value = media.mpaa.trim())
        }
    }
}

/**
 * The house a score is from: its brand name, or - for a source no scraper
 * names, which an NFO may carry - the name the library has, capitalised.
 */
private fun sourceName(entry: SourceRating): String =
    RATING_NAMES[entry.source] ?: entry.source.replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * One rating as a pill: the star where it is a score, who gave it, the figure
 * in bold, and how many voted. Read out as one, so a screen reader says
 * "IMDb 8.5 612,345 votes" rather than three things.
 *
 * Every word in the colour made for the pill's ground rather than the muted
 * grey a format badge is written in: a format badge is one short word, and a
 * pill of three parts set in grey on a tinted ground - a poster's, or the
 * phone's own palette - came out too faint to read the count in.
 */
@Composable
private fun RatingBadge(value: String, name: String? = null, note: String? = null, star: Color? = null) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (star != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = star,
                modifier = Modifier.size(RATING_STAR_SIZE),
            )
        }
        if (name != null) {
            Text(text = name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

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
                        userRating = 9,
                        top250 = 27,
                    ),
                )
            }
        }
    }
}
