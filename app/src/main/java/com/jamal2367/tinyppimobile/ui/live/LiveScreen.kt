@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.ui.components.GroupCard
import com.jamal2367.tinyppimobile.ui.components.HdrGrade
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatusLine
import com.jamal2367.tinyppimobile.ui.components.StatusRow
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.LocalArtworkAccent
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import com.jamal2367.tinyppimobile.ui.theme.SlimSliderThumb
import com.jamal2367.tinyppimobile.ui.theme.artworkGradient
import com.jamal2367.tinyppimobile.ui.theme.neutralTonalButtonColors
import com.jamal2367.tinyppimobile.ui.theme.neutralTonalIconButtonColors
import com.jamal2367.tinyppimobile.util.Formatters
import com.jamal2367.tinyppimobile.util.MediaUrls
import com.jamal2367.tinyppimobile.util.SourceLabel

/**
 * What the box is playing, and what can be done to it.
 *
 * The screen anyone opens this app for: the title with its poster, how it is
 * graded, what is leaving the box, and - on a box that allows it - the
 * transport row and the VS10 conversions.
 */
@Composable
fun LiveScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    val poster = state.snapshot
        ?.takeIf { state.settings.showArtwork }
        ?.let { MediaUrls.art(state.live.server, it.art, MediaUrls.ArtKind.POSTER) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val snapshot = state.snapshot

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Only where there is no film: while one runs the line goes into
            // the list instead, directly above the card it belongs to, and
            // scrolls away with it. With nothing playing there is no card for
            // it to sit over, and whether the box is answering at all is the
            // one thing the screen still has to say - so it stays at the top.
            if (snapshot?.playing != true) {
                StatusLine(
                    connection = state.live.connection,
                    serverLabel = state.live.server?.label,
                    onReconnect = viewModel::reconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenEdge, vertical = 8.dp),
                )
            }

            when {
                !state.isConfigured -> EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_not_configured_title),
                    message = stringResource(R.string.live_not_configured_text),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                snapshot == null -> EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_waiting_title),
                    message = stringResource(R.string.live_waiting_text),
                )

                else -> LiveContent(
                    snapshot = snapshot,
                    connection = state.live.connection,
                    serverLabel = state.live.server?.label,
                    poster = poster,
                    showArtwork = state.settings.showArtwork,
                    canControl = state.canControlPlayback,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun LiveContent(
    snapshot: Snapshot,
    connection: LiveState.Connection,
    serverLabel: String?,
    poster: String?,
    showArtwork: Boolean,
    canControl: Boolean,
    viewModel: LiveViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = ScreenEdge, end = ScreenEdge, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!snapshot.playing) {
            item {
                EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_idle_title),
                    message = stringResource(R.string.live_idle_text),
                    modifier = Modifier.height(320.dp),
                )
            }
            return@LazyColumn
        }

        item {
            NowPlayingCard(
                snapshot = snapshot,
                connection = connection,
                serverLabel = serverLabel,
                poster = poster,
                showArtwork = showArtwork,
                canControl = canControl,
                viewModel = viewModel,
            )
        }

        if (canControl) {
            item {
                ControlsCard(
                    snapshot = snapshot,
                    viewModel = viewModel,
                )
            }
        }

        if (snapshot.vs10.options.isNotEmpty()) {
            item {
                Vs10Card(
                    snapshot.vs10,
                    canControl = snapshot.control,
                    viewModel = viewModel,
                )
            }
        }

        // The readings the overlay prints, at the foot of the same screen
        // rather than behind a tab of their own. They answer questions the card
        // at the top raises - what this file actually is, what the box is doing
        // with it - and an answer a tab away is an answer nobody goes and gets.
        //
        // Keyed by the group's own id, so a card keeps its place in the list
        // and its fold as the box adds and drops panels mid-film.
        items(items = snapshot.groups, key = InfoGroup::id) { group ->
            GroupCard(group)
        }
    }
}

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
private fun NowPlayingCard(
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

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
 * The transport and the track pickers, in a card of their own.
 *
 * Folded shut until the reader opens it, and found that way again on the next
 * launch: what is playing is what this screen is opened for, and the buttons
 * are wanted a good deal less often than they take up room.
 *
 * No rule between the transport and the pickers: the pickers carry their own
 * labels, and a line between the volume and the word "audio track" separates
 * two things nobody was confusing.
 */
@Composable
private fun ControlsCard(
    snapshot: Snapshot,
    viewModel: LiveViewModel,
) {
    SectionCard(
        title = stringResource(R.string.live_transport),
        foldId = FOLD_CONTROLS,
        foldOpenByDefault = false,
    ) {
        TransportSection(
            snapshot = snapshot,
            viewModel = viewModel,
        )

        if (!snapshot.controls.isEmpty) {
            TrackSection(snapshot.controls, viewModel)
        }
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
        ) {
            Text(
                text = target ?: snapshot.time.ifBlank { "–" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (target != null) FontWeight.SemiBold else null,
            )
            Text(
                text = snapshot.duration.ifBlank { "–" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private val WAVE_STROKE = 7.dp

/**
 * How much room the wave has to move in.
 *
 * Read against the stroke, not independent of it. The component plots the wave
 * across the height it is given less the width of the line, so these two
 * figures together are what is left for the wave to move in - five points
 * here - and raising the stroke without raising this flattens the wave.
 */
private val WAVE_HEIGHT = 12.dp

/**
 * The six jumps, and under them the row that stops, sets and plays.
 *
 * Drawn only where the box allows control: the add-on gathers the track lists
 * and the volume for these rows alone, and a box that has control switched off
 * sends none of it, so there would be nothing under the buttons anyway.
 *
 * Every jump is written rather than drawn. The icon set counts in seconds and
 * stops at thirty, so half of these six would have had to be labelled anyway,
 * and a row of three icons beside three labels reads as two kinds of button
 * doing one kind of thing.
 *
 * Six of one kind and nothing else, now that play has gone below: three back
 * and three forward, spread evenly, and the row reads as one scale rather than
 * as two halves either side of something bigger.
 *
 * Jumping to a point rather than by one is the bar at the top of the card.
 */
@Composable
private fun TransportSection(
    snapshot: Snapshot,
    viewModel: LiveViewModel,
) {
    // The two rows are held at the same distance apart as the keys within
    // them, which the card would otherwise not do: it sets ten points between
    // whatever it is given, and a keypad with more air across it than along it
    // reads as two rows rather than as one block. What the card still puts in
    // is the space under the block, before the track pickers - and that one is
    // a division worth drawing.
    Column(verticalArrangement = Arrangement.spacedBy(TRANSPORT_GAP)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JumpButton(
                label = "-10m",
                description = pluralStringResource(R.plurals.live_seek_back_minutes, 10, 10),
                onClick = { viewModel.seekBy(-600) },
                modifier = Modifier.weight(1f),
            )
            JumpButton(
                label = "-1m",
                description = pluralStringResource(R.plurals.live_seek_back_minutes, 1, 1),
                onClick = { viewModel.seekBy(-60) },
                modifier = Modifier.weight(1f),
            )
            JumpButton(
                label = "-10s",
                description = pluralStringResource(R.plurals.live_seek_back, 10, 10),
                onClick = { viewModel.seekBy(-10) },
                modifier = Modifier.weight(1f),
            )
            JumpButton(
                label = "+10s",
                description = pluralStringResource(R.plurals.live_seek_forward, 10, 10),
                onClick = { viewModel.seekBy(10) },
                modifier = Modifier.weight(1f),
            )
            JumpButton(
                label = "+1m",
                description = pluralStringResource(R.plurals.live_seek_forward_minutes, 1, 1),
                onClick = { viewModel.seekBy(60) },
                modifier = Modifier.weight(1f),
            )
            JumpButton(
                label = "+10m",
                description = pluralStringResource(R.plurals.live_seek_forward_minutes, 10, 10),
                onClick = { viewModel.seekBy(600) },
                modifier = Modifier.weight(1f),
            )
        }

        VolumeRow(snapshot.controls, snapshot.paused, viewModel)
    }
}

/**
 * One jump, as far as it goes.
 *
 * The label carries the distance and the sign carries the direction, so the
 * row reads left to right without counting buttons out from the middle. The
 * screen reader is told the same thing in words.
 *
 * Drawn on the neutral ground: six of these in the accent, three to each side
 * of the play button, is a row where the one button that matters is the same
 * colour as the six that surround it.
 *
 * Each takes an equal share of the row rather than a width of its own. Spread
 * across the card at their own size they stood a finger's width apart, which
 * read as six separate things that happened to be in a line; shoulder to
 * shoulder with a hairline between them they read as one scale, which is what
 * they are.
 */
@Composable
private fun JumpButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = TRANSPORT_SHAPE,
        colors = neutralTonalIconButtonColors(),
        modifier = modifier
            .height(TRANSPORT_BUTTON)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The chapter keys, play, the volume, and stop.
 *
 * The row the card is for: step between the film's own marks, start or hold
 * what is playing, set how loud, end it. All of them the same height, so the
 * row has one line to it rather than a tall thing at one end and short ones at
 * the other.
 *
 * The volume steps rather than slides, and there is a reason it has to. A
 * slider could only ever have set Kodi's own mixer: a box that passes volume
 * on over CEC leaves that number alone and sends the amplifier a command
 * instead, and it does that from the input path - which an absolute level
 * never reaches and a step always does. So a soundbar answers these three
 * keys where it could not answer the slider, at the cost of the level itself,
 * which CEC has no command for.
 *
 * Mute is the key between them, and it carries the reading. That is one key
 * doing two jobs, but the two belong together: it says how loud and whether
 * at all, and pressing the thing that says "on" to turn it off is the shortest
 * sentence the row can be written in. It also keeps the row at seven, which
 * is as many as a phone's width divides into.
 *
 * The reading is Kodi's own level, which on a box passing volume over CEC is
 * not the amplifier's - so it can sit still while the room gets louder. A box
 * that sends no volume at all shows the speaker without a figure.
 */
@Composable
private fun VolumeRow(
    controls: PlayerControls,
    paused: Boolean,
    viewModel: LiveViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
    ) {
        // The two chapter keys take the two ends of the row rather than
        // standing either side of play. Back at the near corner and forward at
        // the far one, they bracket everything between them: the pair is still
        // read as one pair, and neither of them is close enough to play or to
        // stop to be pressed in mistake for either.
        ChapterButton(
            forward = false,
            enabled = controls.hasChapters,
            onClick = viewModel::previousChapter,
            modifier = Modifier.weight(1f),
        )

        // Play near one end, stop near the other. Every button here is the
        // same to look at - same fill, same size, same shape - so what tells
        // them apart is where they sit and what is drawn on them. That is on
        // purpose: stop ends playback outright, and the far side of the
        // volume from the thumb that reaches for play is as hard a place to
        // hit by accident as the row has.
        FilledTonalIconButton(
            onClick = viewModel::playPause,
            shape = TRANSPORT_SHAPE,
            colors = neutralTonalIconButtonColors(),
            modifier = Modifier
                .weight(1f)
                .height(TRANSPORT_BUTTON),
        ) {
            Icon(
                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = stringResource(R.string.live_playpause),
            )
        }

        // The three volume keys take the middle of the row, one share each
        // like everything either side of them.
        StepButton(
            up = false,
            onClick = viewModel::volumeDown,
            modifier = Modifier.weight(1f),
        )

        MuteButton(
            muted = controls.muted,
            onClick = viewModel::toggleMute,
            modifier = Modifier.weight(1f),
        )

        StepButton(
            up = true,
            onClick = viewModel::volumeUp,
            modifier = Modifier.weight(1f),
        )

        FilledTonalIconButton(
            onClick = viewModel::stop,
            shape = TRANSPORT_SHAPE,
            colors = neutralTonalIconButtonColors(),
            modifier = Modifier
                .weight(1f)
                .height(TRANSPORT_BUTTON),
        ) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.live_stop),
            )
        }

        ChapterButton(
            forward = true,
            enabled = controls.hasChapters,
            onClick = viewModel::nextChapter,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One chapter back, or one on.
 *
 * Off on a file that has none, which is most of them: the add-on refuses the
 * command outright rather than seeking a long way instead, so a key that is
 * still pressable here would only ever produce the failure line under the
 * card. Dimmed and left in place rather than taken out, because a row that
 * changes width when the film changes is a row whose buttons move out from
 * under the thumb that was aiming at one.
 *
 * On the neutral ground with the rest of the row, and cut to the same corner:
 * every key on this card is one kind of thing, and where it sits is what says
 * which.
 */
@Composable
private fun ChapterButton(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = TRANSPORT_SHAPE,
        colors = neutralTonalIconButtonColors(),
        modifier = modifier.height(TRANSPORT_BUTTON),
    ) {
        Icon(
            imageVector = if (forward) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(
                if (forward) R.string.live_chapter_next else R.string.live_chapter_previous,
            ),
        )
    }
}

/**
 * One notch louder, or one quieter.
 *
 * A speaker and a sign, in that order. The sign alone said the right thing in
 * the wrong row: a bare plus among keys marked "+10s" is a key that has to be
 * worked out, and the speaker says which kind of louder it means before it is
 * read. The pair is what the mute key between them is drawn from, so the three
 * read as one group without being drawn a box.
 *
 * Never switched off, however little the box has told us. The step does not
 * depend on a level having arrived - and on a box that passes volume over CEC
 * there is no level to know, only a soundbar that gets louder.
 */
@Composable
private fun StepButton(up: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(
        if (up) R.string.live_volume_up else R.string.live_volume_down,
    )

    FilledTonalButton(
        onClick = onClick,
        shape = TRANSPORT_SHAPE,
        colors = neutralTonalButtonColors(),
        // Nothing of its own: the key is as wide as its share of the row, and
        // what is in it is two small things that have to sit together.
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(TRANSPORT_BUTTON)
            .semantics { contentDescription = name },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(STEP_ICON),
        )
        Text(
            text = if (up) "+" else "−",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * The key between the two steps, which turns the sound off.
 *
 * A speaker on its own, between two speakers that carry a sign: the middle of
 * the three is the one that does the plain thing to the volume, and the group
 * says so by what is missing from it rather than by a word.
 *
 * No figure beside it. The level the box reports is Kodi's own mixer, and a
 * box that passes volume over CEC leaves that number where it is while the
 * amplifier does the moving - so the reading would have been a number that sat
 * still through everything these keys did, on the very boxes they were added
 * for.
 */
@Composable
private fun MuteButton(muted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = TRANSPORT_SHAPE,
        colors = neutralTonalIconButtonColors(),
        modifier = modifier.height(TRANSPORT_BUTTON),
    ) {
        Icon(
            imageVector = if (muted) {
                Icons.AutoMirrored.Rounded.VolumeOff
            } else {
                Icons.AutoMirrored.Rounded.VolumeUp
            },
            contentDescription = stringResource(
                if (muted) R.string.live_unmute else R.string.live_mute
            ),
        )
    }
}

/**
 * How tall a key on the Player and VS10 cards is drawn.
 *
 * Material's own figure for a button, which is what the volume and the two
 * track pickers were taking anyway - they are written buttons and set their
 * height from their text and its padding, while an icon button takes whatever
 * it is given. At thirty-eight the icon keys sat two points shy of the pill
 * they share a row with, which is the kind of difference that is not seen so
 * much as felt.
 *
 * So it is put on all of them by hand rather than left to two components to
 * agree on, and every key on both cards is the same height as every other.
 *
 * Height only. Neither row sets a width: both divide what the card gives them
 * between the keys in them, so a key is as wide as its share and no wider, and
 * the two rows line up at the edges of the card without being told to.
 */
private val TRANSPORT_BUTTON = 40.dp

/**
 * The corner every control on the card is cut with.
 *
 * Not the circle Material puts on an icon button, and not the app's own
 * twenty-four either: a round button says one thing sitting on its own, and a
 * dozen of them stacked in rows say a handful of loose coins. Cut square
 * enough to have sides, they line up along theirs - which is what a keypad is,
 * and what these two cards have been all along.
 *
 * Still cut, though, and cut deep enough to be seen against the card it sits
 * on. The corner is what keeps a row of keys from reading as one bar with
 * lines drawn on it.
 */
private val TRANSPORT_SHAPE = RoundedCornerShape(12.dp)

/**
 * How big the speaker on a step key is drawn.
 *
 * Well under Material's twenty-four, which every other key in the row takes.
 * These two are the only keys with two things to fit, and the speaker is the
 * half that can afford to give: what has to stay legible at arm's length is
 * the sign, and a speaker that has shrunk to make room for it still reads as a
 * speaker.
 */
private val STEP_ICON = 16.dp

/**
 * How far the things on the card stand apart.
 *
 * Air rather than space: enough that two keys are two keys, and no more.
 * These are a dozen controls across two cards that all do one job between
 * them, and a gap wide enough to read as a division between them was drawing
 * divisions that are not there.
 *
 * The seek keys take an equal share of what is left over, so the figure sets
 * their width as well as the distance between them - and the volume, which is
 * the one thing in the middle row with width to give, keeps what the gap does
 * not take.
 */
private val TRANSPORT_GAP = 8.dp

/**
 * The air around a track picker: over the pair of them, and under each name.
 *
 * The space above sits on top of the ten the card already puts between its own
 * children, so the pickers stand a good deal further from the volume row than
 * the volume row does from the seek keys. That is the point: it is what says
 * the keypad has ended and something else has started.
 *
 * The same figure again between a picker's name and the button under it. Both
 * are the gap that separates a caption from what it captions, so both are the
 * one number rather than two that happen to agree.
 */
private val TRACK_GAP = 6.dp

/** The audio and subtitle tracks, as two pickers. */
@Composable
private fun TrackSection(controls: PlayerControls, viewModel: LiveViewModel) {
    val hasAudio = controls.audio.isNotEmpty()
    val hasSubtitles = controls.subtitle.isNotEmpty()

    // Set down off the keypad rather than carried on under it. The rows above
    // are one block of keys held six points apart; these two are a different
    // kind of thing - each one a named picker rather than a key - and the
    // ten points the card puts between what it is given were not enough to
    // say so once the keys themselves had closed up.
    Row(
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TRACK_GAP),
    ) {
        if (hasAudio) {
            TrackPicker(
                label = stringResource(R.string.live_audio_track),
                tracks = controls.audio,
                selected = controls.audioCurrent,
                offLabel = null,
                onSelect = { index -> index?.let(viewModel::selectAudio) },
                modifier = if (hasSubtitles) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
        }
        if (hasSubtitles) {
            TrackPicker(
                label = stringResource(R.string.live_subtitles),
                tracks = controls.subtitle,
                // Kodi goes on naming the track that was switched off, so the
                // picker only follows the current index while they are on.
                selected = if (controls.subtitleOn) controls.subtitleCurrent else null,
                offLabel = stringResource(R.string.live_subtitles_off),
                onSelect = viewModel::selectSubtitle,
                modifier = if (hasAudio) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrackPicker(
    label: String,
    tracks: List<Track>,
    selected: Int?,
    offLabel: String?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    labelTextAlign: TextAlign = TextAlign.Center,
) {
    var open by remember { mutableStateOf(false) }
    val current = tracks.firstOrNull { it.index == selected }?.label
        ?: offLabel
        ?: stringResource(R.string.live_track_unknown)

    // The name stands off its picker rather than sitting on it. Set tight
    // against the button, a caption in the card's own quieter grey read as
    // part of the button's own furniture instead of as the thing that says
    // what the button is for.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TRACK_GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = labelTextAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Box {
            FilledTonalButton(
                onClick = { open = true },
                shape = TRANSPORT_SHAPE,
                colors = neutralTonalButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRANSPORT_BUTTON),
            ) {
                Text(
                    text = current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (offLabel != null) {
                    DropdownMenuItem(
                        text = { Text(offLabel) },
                        onClick = {
                            open = false
                            onSelect(null)
                        },
                    )
                }
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(track.label) },
                        onClick = {
                            open = false
                            onSelect(track.index)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The conversions this source can be put through.
 *
 * The buttons are the box's own: it sends the group that applies to the grade
 * that is playing, and the same set the on-screen dialog offers. HDR10+ and
 * HLG carry none, and then the whole card is left out rather than offering a
 * conversion nothing else offers either.
 *
 * Tonal and neutral: a filled button is the accent at full strength, and two of
 * those shouting from a card near the bottom of the screen outrank the play
 * button they are sitting under. Even in the tonal shade they were two more
 * coloured pills on a screen that had a dozen. These wear what the transport
 * wears throughout - the card's own ground, the same corner, the same height
 * and the same air between them - so the two cards read as one set of keys in
 * two groups rather than as two ideas of what a button is.
 *
 * Two to a line, sharing the width equally. They are alternatives to each
 * other, so one drawn wider than the next would be saying something about it
 * that is not true - and a box that offers four of them would otherwise put
 * four names in the width of one, each cut to nothing. An odd one at the end
 * takes the line to itself.
 */
@Composable
private fun Vs10Card(
    vs10: Vs10State,
    canControl: Boolean,
    viewModel: LiveViewModel,
) {
    SectionCard(title = stringResource(R.string.live_vs10), foldId = FOLD_VS10) {
        if (!canControl) {
            Text(
                text = stringResource(R.string.live_control_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            vs10.options.chunked(VS10_PER_ROW).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { option ->
                        FilledTonalButton(
                            onClick = { viewModel.setMode(option.mode) },
                            shape = TRANSPORT_SHAPE,
                            colors = neutralTonalButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .height(TRANSPORT_BUTTON),
                        ) {
                            Vs10Label(option.label)
                        }
                    }
                }
            }
        }
    }
}

/**
 * What each card on this screen is remembered by.
 *
 * Written out rather than taken from the heading: the heading is translated,
 * and a card folded away in German should still be folded in English.
 */
private const val FOLD_CONTROLS = "live.controls"
private const val FOLD_VS10 = "live.vs10"

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
private val POSTER_WIDTH = 84.dp
private const val POSTER_RATIO = 2f / 3f
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

/** How many conversions the VS10 card puts on one line. */
private const val VS10_PER_ROW = 2

/**
 * A conversion, as `DV → SDR`, with the arrow drawn rather than typed.
 *
 * The box writes the label with an arrow character in it, and a character is at
 * the mercy of the font that has to draw it: the one the system picks here sets
 * it thin, small, and off the line of the words either side. Drawn as an icon
 * it takes the weight and the colour of the text around it and sits where an
 * arrow between two words should sit.
 *
 * A label with no arrow in it - or one written some other way - is set as it
 * came. Nothing here needs the split to succeed.
 */
@Composable
private fun Vs10Label(label: String) {
    val sides = ARROW.split(shortened(label)).map { it.trim() }

    if (sides.size != 2) {
        Text(
            text = shortened(label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = sides[0], maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(text = sides[1], maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A conversion's name, short enough for half a card.
 *
 * The box spells Dolby Vision out, and two buttons side by side have room for
 * about eight characters each before a name has to be cut. `DV` is what the
 * badge on the card above calls it anyway.
 */
private fun shortened(label: String): String =
    label.replace("Dolby Vision", "DV", ignoreCase = true)

/** However the box wrote the arrow between the two halves of a conversion. */
private val ARROW = Regex("""\s*(?:->|=>|\u2192|\u27F6)\s*""")
