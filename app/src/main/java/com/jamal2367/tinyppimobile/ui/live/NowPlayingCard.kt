@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.ui.components.HdrGrade
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatusRow
import com.jamal2367.tinyppimobile.ui.theme.LocalArtworkAccent
import com.jamal2367.tinyppimobile.ui.theme.SlimSliderThumb
import com.jamal2367.tinyppimobile.ui.theme.artworkGradient
import com.jamal2367.tinyppimobile.util.Formatters
import com.jamal2367.tinyppimobile.util.SourceLabel

/**
 * The title, how it is graded, and everything that can be done to it.
 *
 * One card rather than three: the title, the transport and the track pickers
 * are read and used in the same breath, and a card boundary between them only
 * put scrolling between a button and the thing it moves.
 *
 * It has no heading and no fold of its own: what is playing is what the screen
 * is opened for, and a card that can hide it is a card that can hide the
 * answer. The poster, the title of the film and the clock under it say what the
 * card is more plainly than a word over them could.
 *
 * The card is washed in the colour of the poster beside it, strongest at the
 * top and gone by the bottom - the same thing the add-on's dashboard does, and
 * the reason a card about Blade comes out looking like Blade. The wash is all
 * of it: an outline around the card as well would fence the colour in, and the
 * point of a wash is that it has no edge.
 *
 * The two badges are the point of the card and of the add-on itself: what the
 * file is, and what the box is turning it into on the way out. They sit side by
 * side so the answer to "is this being converted" is a glance rather than a
 * comparison.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowPlayingCard(
    snapshot: Snapshot,
    connection: LiveState.Connection,
    serverLabel: String?,
    poster: String?,
    showArtwork: Boolean,
    canControl: Boolean,
    viewModel: LiveViewModel,
) {
    val accent = LocalArtworkAccent.current
    val container = MaterialTheme.colorScheme.surfaceContainerLow

    SectionCard(
        // No heading: the poster, the title of the film and the clock under it
        // say what this card is more plainly than a word over them could.
        title = null,
        containerBrush = accent?.let { artworkGradient(it, container) },
    ) {
        // The first row of the card rather than a pill above it. It was always
        // about the same thing the card is - this box, this title - and it
        // took the card's own tint so the two would read as one piece; drawn
        // inside, it is one piece without having to be made to look like it.
        StatusRow(
            connection = connection,
            serverLabel = serverLabel,
            onReconnect = viewModel::reconnect,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.offset(y = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showArtwork) {
                PosterImage(
                    url = poster,
                    contentDescription = snapshot.title,
                    modifier = Modifier
                        .width(POSTER_WIDTH)
                        .aspectRatio(POSTER_RATIO),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    // As tall as the poster beside it, so the logos at its foot
                    // land on the poster's bottom edge. A minimum rather than a
                    // height: a long title and a long subtitle are allowed to
                    // push past the poster rather than be cut off by it.
                    .heightIn(min = if (showArtwork) POSTER_HEIGHT else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = snapshot.title.ifBlank { stringResource(R.string.live_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    // Plain, on a card already washed in the poster's colour.
                    // The wash is the colour of the film; the title set in it
                    // as well only said the same thing twice, and cost the
                    // longest line on the screen its contrast to say it.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitleOf(snapshot)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // All of the slack above the badges rather than half of it
                // either side, which lands the block on the bottom edge of the
                // poster beside it: the column is held to the poster's height,
                // so the last row of badges and the last row of pixels of the
                // picture finish on the same line. Centred, they floated in
                // the middle of the card against nothing.
                Spacer(Modifier.weight(1f))

                // The two rows travel together, a badge's own gap apart. They
                // used to have one of those weighted spacers between them,
                // which spread the picture and the sound to opposite ends of
                // whatever the poster left over - two rows of the same kind of
                // thing, reading as two unrelated groups.
                Column(verticalArrangement = Arrangement.spacedBy(BADGE_ROW_GAP)) {
                    // The picture: what it is graded as, what it is leaving
                    // as, and what else the box said about this release.
                    // `IMAX` is a cut of a film rather than a property of its
                    // sound, so it belongs beside the grade, not with the audio.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
                        verticalArrangement = Arrangement.spacedBy(BADGE_GAP),
                    ) {
                        SourceLabel.resolution(snapshot.metrics.frame)?.let {
                            FormatBadge(text = it)
                        }
                        FormatBadge(
                            text = sourceLabelOf(snapshot),
                            arrowSuffix = conversionTargetOf(snapshot),
                        )
                        SourceLabel.pictureMarks(snapshot).forEach { FormatBadge(text = it) }
                    }

                    // The sound, out of the Audio card at the foot of this
                    // screen: the codec, what rides on it, how wide it is.
                    // These were the add-on's own wordmarks once - brand
                    // graphics with their own weight and their own idea of how
                    // tall a logo should be, sitting under a line of type they
                    // had nothing in common with.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
                        verticalArrangement = Arrangement.spacedBy(BADGE_GAP),
                    ) {
                        SourceLabel.soundBadges(snapshot).forEach { FormatBadge(text = it) }
                    }
                }
            }
        }

        if (snapshot.filename.isNotBlank()) {
            Text(
                text = snapshot.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ProgressRow(snapshot, canControl, viewModel)
    }
}

/**
 * Where the title has got to, and the handle that moves it.
 *
 * The bar is both the reading and the control. While a finger is on it the
 * slider is the truth, and the moment it comes off the box is again - held as
 * "dragged, or nothing" rather than as a position kept in step with the
 * reading, because the reading arrives five times a second and a thumb
 * followed by it is a thumb dragged out from under the finger holding it.
 *
 * The left-hand figure says where the drag has reached rather than where the
 * player still is: a bar dragged half an hour on while the clock under it
 * insists on the old position is a bar nobody can aim.
 *
 * On a box with control switched off the same bar is drawn as a plain reading,
 * which is all it can be there.
 */
@Composable
private fun ProgressRow(snapshot: Snapshot, canControl: Boolean, viewModel: LiveViewModel) {
    val reported = (snapshot.metrics.progress ?: 0.0).toFloat().coerceIn(0f, 100f)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val seeking = remember { MutableInteractionSource() }

    val target = dragged?.let { percent ->
        Formatters.clockSeconds(snapshot.duration)?.let { total ->
            Formatters.positionLike(total * percent / 100.0, snapshot.duration)
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        if (canControl) {
            Slider(
                value = dragged ?: reported,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let(viewModel::seekTo)
                    // Handed back to the box: what it reports next is where the
                    // player actually landed, which is not always what was asked.
                    dragged = null
                },
                valueRange = 0f..100f,
                // Held here rather than left to the slider, because the thumb
                // is drawn by hand below and it has to light up for the same
                // presses and drags the slider is hearing.
                interactionSource = seeking,
                thumb = { SlimSliderThumb(seeking) },
                // The bar is the slider's track rather than a reading drawn
                // beside it: the handle still drags, the semantics are still
                // the slider's, and what changes is only what gets painted
                // under the thumb.
                track = { state ->
                    PlaybackWave(
                        progress = { state.coercedValueAsFraction },
                        paused = snapshot.paused,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PlaybackWave(
                progress = { reported / 100f },
                paused = snapshot.paused,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (canControl) 0.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted rather than left at its own width, and so is the
            // length at the far end: two equal slots either side are what put
            // the reading between them over the centre of the bar. Their
            // figures are not the same length - `0:12:03` against `2:35:00`,
            // and further apart the moment a drag puts the hour into one of
            // them - so a row that only spaced its three readings out would
            // put the middle one wherever those two left it.
            Text(
                text = target ?: snapshot.time.ifBlank { "–" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (target != null) FontWeight.SemiBold else null,
                modifier = Modifier.weight(1f),
            )
            // When the title will be over, by the clock. The same style and
            // the same colour as the two readings either side of it: it is
            // the third reading of the row, not an aside about them.
            //
            // Left out entirely where the box named no end - a live stream, a
            // channel or a recording off the tuner, a title it does not yet
            // know the length of, or an add-on older than the reading -
            // rather than standing in for one with a dash, which would read
            // as a clock that failed to arrive.
            if (snapshot.finish.isNotBlank()) {
                Text(
                    text = stringResource(R.string.live_ends_at, snapshot.finish),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    // Weighted but not filled: the two figures either side are
                    // the same width, so whatever this leaves over is split
                    // evenly between the gaps beside it and the reading lands
                    // on the centre of the bar however short it is. The weight
                    // is only there to put a ceiling on it - half the row,
                    // which a clock and a word are nowhere near until the
                    // phone is turned up to the largest type, and past that
                    // the label gives way rather than the two clocks wrapping.
                    modifier = Modifier
                        .weight(2f, fill = false)
                        .padding(horizontal = 8.dp),
                )
            }
            Text(
                text = snapshot.duration.ifBlank { "–" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * How far the film has got, drawn as a wave that travels with it.
 *
 * The part that has played is a moving squiggle and the part that has not is a
 * straight line, so the bar says whether the picture is running without anyone
 * having to watch the figures under it change. A frozen stream and a paused
 * film look identical in every reading on this card - the wave is the one
 * thing on the screen that stops when the picture does.
 *
 * Which is what [paused] is for. It flattens rather than stopping: a wave that
 * simply froze mid-crest would read as a drawing bug, and one that settles to
 * a straight line reads as a film that has been paused. The settling is
 * animated for the same reason - the flattening is the announcement.
 *
 * Amplitude is the only thing said here. The wavelength and the speed are
 * Material's, and a wave tuned by hand on top of a component that already
 * tunes it is two answers to one question.
 */
@Composable
private fun PlaybackWave(
    progress: () -> Float,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val amplitude by animateFloatAsState(
        targetValue = if (paused) 0f else 1f,
        label = "waveAmplitude",
    )

    val width = with(LocalDensity.current) { WAVE_STROKE.toPx() }
    val stroke = remember(width) { Stroke(width = width, cap = StrokeCap.Round) }

    LinearWavyProgressIndicator(
        progress = progress,
        color = MaterialTheme.colorScheme.primary,
        // The neutral ground the buttons on this card stand on, so the length
        // still to play reads as the card rather than as a second colour.
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        stroke = stroke,
        trackStroke = stroke,
        // The dot that ends the track, matched to the line it ends. Left at
        // Material's figure it stayed the width of the old thin line and read
        // as a chip off the end of the new one.
        stopSize = WAVE_STROKE,
        amplitude = { amplitude },
        waveSpeed = WAVE_SPEED,
        modifier = modifier.height(WAVE_HEIGHT),
    )
}

/**
 * How far the wave travels in a second.
 *
 * Material moves it one wavelength - forty points - which is a crest a second
 * passing any given spot. That is a rate for something being waited on, and
 * this bar is not: a film is two hours long and the wave is there to say the
 * picture is running, not to hurry it. At this it drifts.
 *
 * Distance rather than a multiplier, because that is what the component takes,
 * and the wavelength is left at Material's - the wave is the same shape, just
 * carried across more slowly.
 */
private val WAVE_SPEED = 24.dp

/**
 * How thick the playback wave is drawn.
 *
 * Two points over Material's four. A four-point line is what a progress bar
 * takes when it is a strip of information along the foot of something else;
 * this one is the length of the card and the thing the card is about.
 */
private val WAVE_STROKE = 5.dp

/**
 * How much room the wave has to move in.
 *
 * Read against the stroke, not independent of it. The component plots the wave
 * across the height it is given less the width of the line, so these two
 * figures together are what is left for the wave to move in - five points
 * here - and raising the stroke without raising this flattens the wave.
 */
private val WAVE_HEIGHT = 10.dp

/**
 * The line under the title: the show and episode, the year, the genre.
 *
 * One colour throughout. The year and the genre used to be set in the accent
 * to hold them apart from the show and episode in front of them, which put the
 * whole of a film's subtitle - a film has no show and no episode - in the
 * accent, and left the second line of the card shouting as loudly as the first.
 * The dots hold the parts apart on their own.
 */
private fun subtitleOf(snapshot: Snapshot): String? {
    val media = snapshot.media
    val parts = listOfNotNull(
        media.show.takeIf { it.isNotBlank() },
        media.episodeLabel,
        media.year.takeIf { it.isNotBlank() },
        media.genre.takeIf { it.isNotBlank() },
    )

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** How far apart two badges sit in a row, and a wrapped row from the next. */
private val BADGE_GAP = 6.dp

/**
 * How far apart the picture's badges and the sound's sit.
 *
 * Wider than the gap inside a row. The two rows are about different things,
 * and at the same figure as the badges beside them they read as one block that
 * happened to wrap - the extra points are what says the second row started on
 * purpose.
 */
private val BADGE_ROW_GAP = 10.dp

/**
 * How big the poster on the live card is drawn.
 *
 * Kept as three named figures rather than two numbers in a modifier, because
 * the column of text beside it is measured against the same height - the logos
 * at its foot are meant to land on the poster's bottom edge.
 */
private val POSTER_WIDTH = 104.dp
internal const val POSTER_RATIO = 2f / 3f
private val POSTER_HEIGHT = POSTER_WIDTH / POSTER_RATIO

/**
 * What the source badge says: the grade, and for Dolby Vision rather more.
 *
 * Dolby Vision is abbreviated where the profile is spelled out beside it - the
 * badge is a pill on a card, and `Dolby Vision P7.6 EL` written out in full is
 * a pill the width of the screen saying what `DV P7.6 EL` says.
 */
private fun sourceLabelOf(snapshot: Snapshot): String {
    val grade = HdrGrade.of(snapshot.sourceType)
    if (grade != HdrGrade.DOLBY_VISION) return grade.label

    val detail = SourceLabel.dolbyVisionSuffix(snapshot) ?: return "DV"
    return "DV $detail"
}

/** The source badge also shows the active output conversion, if any. */
private fun conversionTargetOf(snapshot: Snapshot): String? =
    snapshot.outputType.takeIf { snapshot.isConverting }
        ?.let { HdrGrade.of(it).label }
        ?: vs10ConversionTarget(snapshot.vs10.output)?.takeUnless {
            it.equals(HdrGrade.of(snapshot.sourceType).label, ignoreCase = true)
        }

/** Read the active conversion from the VS10 output string when output_type is stale. */
private fun vs10ConversionTarget(output: String): String? {
    val normalized = output.trim().lowercase()
    return when {
        normalized.contains("sdr") -> "SDR"
        normalized.contains("hdr10+") || normalized.contains("hdr10plus") -> "HDR10+"
        normalized.contains("hdr10") -> "HDR10"
        normalized.contains("hlg") -> "HLG"
        else -> null
    }
}
