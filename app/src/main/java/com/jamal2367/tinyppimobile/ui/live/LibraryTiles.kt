@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.theme.PosterShape
import com.jamal2367.tinyppimobile.util.Formatters

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
internal fun ArtFrame(
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
internal fun runtimeLabel(seconds: Int): String? {
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

/** The star on a rating pill, and how big it is drawn. */
private val RATING_STAR = Color(0xFFF4C04F)
private val RATING_STAR_SIZE = 13.dp

/** What a badge calls the house whose rating it draws. Brand names, untranslated. */
private val RATING_NAMES = mapOf("imdb" to "IMDb", "tmdb" to "TMDb")

/** What stands between a year and a length, or a number and a length. */
internal const val META_GAP = " \u00b7 "
