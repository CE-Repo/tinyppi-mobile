package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Chapter

/*
 * The chapters on the progress bar: a dot where each chapter ahead of the
 * position starts, and the number and name of the one playing over the bar.
 * The bar behind the position is the part already seen and carries no dots -
 * until a finger is on the bar: then every chapter is marked, those behind as
 * well, so a drag back finds the ones already seen.
 *
 * The box places them (see `chapter_list` in the add-on's `web/player.py`):
 * [Chapter.at] is on the same 0 to 100 scale the bar is, so nothing here works
 * a percentage out of a clock. The add-on's dashboard draws the same dots by
 * the same rules (`js/live-chapters.js`), so the two read alike.
 */

/**
 * The chapters of [chapters] that get a dot on a bar [width] pixels wide.
 *
 * The first chapter of nearly every film starts at the very beginning, where
 * there is no boundary to mark, so a chapter at 0 gets no dot. Two closer than
 * [minGap] would read as one smudge - some discs carry a chapter every few
 * minutes, and a phone is narrow - so the later one is left out and the dot
 * before it stands for both. A bar not measured yet merges nothing.
 */
internal fun chapterDots(chapters: List<Chapter>, width: Float, minGap: Float): List<Chapter> {
    val dots = mutableListOf<Chapter>()
    for (chapter in chapters) {
        if (chapter.at <= 0.0) continue
        val last = dots.lastOrNull()
        if (last != null && width > 0f && (chapter.at - last.at) / 100.0 * width < minGap) continue
        dots += chapter
    }
    return dots
}

/**
 * The chapter a drag at [percent] would snap to: the one starting nearest it,
 * if that is within [reach] percent. None at 0, the start of the bar, which a
 * drag reaches on its own.
 */
internal fun snapTarget(percent: Float, chapters: List<Chapter>, reach: Float): Chapter? =
    chapters
        .filter { it.at > 0.0 && kotlin.math.abs(it.at - percent) <= reach }
        .minByOrNull { kotlin.math.abs(it.at - percent) }

/** The chapter a position at [percent] is in, or null before the first one starts. */
internal fun chapterAt(percent: Float, chapters: List<Chapter>): Chapter? =
    chapters.lastOrNull { it.at <= percent }

/** How many percent of a bar [width] pixels wide [pixels] come to; none for no bar. */
internal fun percentOf(pixels: Float, width: Int): Float =
    if (width > 0) pixels / width * 100f else 0f

/**
 * Where the drag that just let go lands: on the chapter it snapped to, by its
 * number, else on the spot it reached - and nowhere for a release that moved
 * nothing, since the slider can report the end of a press it never reported
 * a value for.
 */
internal fun finishDrag(
    dragged: Float?,
    seekTo: (Float) -> Unit,
    snapped: Chapter? = null,
    goTo: (Int) -> Unit = {},
) {
    when {
        snapped != null -> goTo(snapped.index)
        else -> dragged?.let(seekTo)
    }
}

/** The two colours a dot can be: ahead of the position, and behind it. */
@Immutable
internal data class ChapterDotColors(val ahead: Color, val behind: Color)

@Composable
internal fun chapterDotColors() = ChapterDotColors(
    // On the length still to play, in the colour of the part that has played.
    ahead = MaterialTheme.colorScheme.primary,
    // On the part that has played - only while a finger is on the bar - cut
    // into it in the colour of the card.
    behind = MaterialTheme.colorScheme.surface,
)

/**
 * The dots, drawn over whatever this modifier is put on - the playback wave.
 *
 * Those ahead of [progress], or every one while [all] - a finger on the bar.
 * Laid out again only when the bar moves or the screen does rather than once
 * per frame: the wave under them is animated, and the dots only change as the
 * position passes one or a drag moves the thumb.
 */
internal fun Modifier.chapterDots(
    chapters: List<Chapter>,
    all: Boolean,
    progress: () -> Float,
    colors: ChapterDotColors,
): Modifier = if (chapters.isEmpty()) this else drawWithCache {
    val at = progress()
    val shown = if (all) chapters else chapters.filter { it.at > at }
    val dots = chapterDots(shown, size.width, DOT_GAP.toPx())
    val radius = DOT_RADIUS.toPx()
    onDrawWithContent {
        drawContent()
        for (dot in dots) {
            // Held clear of the rounded ends, and never a range turned inside
            // out on a bar not yet wide enough to hold one dot.
            val x = (dot.at / 100.0 * size.width).toFloat()
                .coerceIn(radius, maxOf(radius, size.width - radius))
            drawCircle(
                if (dot.at <= at) colors.behind else colors.ahead,
                radius = radius,
                center = Offset(x, size.height / 2),
            )
        }
    }
}

/**
 * "Chapter 3 of 12 · Arrival" over the bar: the number in the accent, the name
 * in the colour of the clocks under it, one line however long the name.
 *
 * Nothing at all before the first chapter has begun or for a file without
 * chapters, rather than a line that says so.
 */
@Composable
internal fun ChapterLabel(chapter: Chapter?, count: Int, modifier: Modifier) {
    chapter ?: return
    val number = stringResource(R.string.live_chapter_of, chapter.index, count)
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(number) }
            if (chapter.name.isNotBlank()) append(" · ${chapter.name}")
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * How far a drag snaps to a dot from.
 *
 * About a fingertip's slack either side: near enough that a drag aimed at a
 * chapter finds it, far enough apart that the ten minutes between two dots are
 * still mostly free ground.
 */
internal val SNAP_REACH: Dp = 8.dp

/** How close two dots may come before they are drawn as one. */
private val DOT_GAP = 6.dp

/** A dot, small enough to sit inside the five-point line it is drawn on. */
private val DOT_RADIUS = 1.75.dp
