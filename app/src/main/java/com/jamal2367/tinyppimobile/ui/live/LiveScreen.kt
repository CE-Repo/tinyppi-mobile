@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.ui.components.ConversionBadge
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.FormatBadge
import com.jamal2367.tinyppimobile.ui.components.FoldChevron
import com.jamal2367.tinyppimobile.ui.components.FormatLogo
import com.jamal2367.tinyppimobile.ui.components.HdrGrade
import com.jamal2367.tinyppimobile.ui.components.InfoRow
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.components.PosterImage
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatTile
import com.jamal2367.tinyppimobile.ui.components.StatusLine
import com.jamal2367.tinyppimobile.ui.theme.LocalArtworkAccent
import com.jamal2367.tinyppimobile.ui.theme.accentText
import com.jamal2367.tinyppimobile.ui.theme.artworkGradient
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
    val pendingVolume by viewModel.pendingVolume.collectAsStateWithLifecycle()
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val snapshot = state.snapshot

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatusLine(
                connection = state.live.connection,
                serverLabel = state.live.server?.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

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
                    server = state.live.server,
                    poster = poster,
                    showArtwork = state.settings.showArtwork,
                    canControl = state.canControlPlayback,
                    pendingVolume = pendingVolume,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun LiveContent(
    snapshot: Snapshot,
    server: ServerConfig?,
    poster: String?,
    showArtwork: Boolean,
    canControl: Boolean,
    pendingVolume: Int?,
    viewModel: LiveViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
            if (snapshot.last.isPresent) {
                item { LastPlayedCard(snapshot) }
            }
            return@LazyColumn
        }

        item {
            NowPlayingCard(
                snapshot = snapshot,
                server = server,
                poster = poster,
                showArtwork = showArtwork,
                canControl = canControl,
                pendingVolume = pendingVolume,
                viewModel = viewModel,
            )
        }

        if (snapshot.vs10.options.isNotEmpty()) {
            item { Vs10Card(snapshot.vs10, canControl = snapshot.control, viewModel = viewModel) }
        }

        item { MetricsCard(snapshot) }
    }
}

/**
 * The title, how it is graded, and everything that can be done to it.
 *
 * One card rather than three: the title, the transport and the track pickers
 * are read and used in the same breath, and a card boundary between them only
 * put scrolling between a button and the thing it moves.
 *
 * Alone among the cards, this one folds a part of itself rather than the
 * whole: what is playing is what the screen is opened for, and a card that can
 * hide it is a card that can hide the answer. The arrow takes the transport and
 * the track pickers away instead - the part that is wanted a good deal less
 * often than it takes up room - and they start away, as they were asked to.
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
    server: ServerConfig?,
    poster: String?,
    showArtwork: Boolean,
    canControl: Boolean,
    pendingVolume: Int?,
    viewModel: LiveViewModel,
) {
    val accent = LocalArtworkAccent.current
    val container = MaterialTheme.colorScheme.surfaceContainerLow

    val folds = LocalCardFolds.current
    val expanded = folds.isExpanded(FOLD_CONTROLS, openByDefault = false)

    SectionCard(
        title = stringResource(R.string.live_now_playing),
        containerBrush = accent?.let { artworkGradient(it, container) },
        trailing = {
            if (canControl) {
                FoldChevron(
                    expanded = expanded,
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.live_controls_hide
                        } else {
                            R.string.live_controls_show
                        }
                    ),
                ) {
                    folds.setExpanded(FOLD_CONTROLS, !expanded, openByDefault = false)
                }
            }
        },
    ) {
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
                    // In the accent, like every heading on the screen - and on
                    // a card washed in the poster's own colour, the title is
                    // the one line that ought to be wearing it.
                    color = MaterialTheme.colorScheme.accentText,
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

                // A gap of its own above and below, both of the same weight,
                // so the badges sit halfway between the line that names the
                // film and the logos at the foot of the poster rather than
                // hanging off one of the two.
                Spacer(Modifier.weight(1f))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FormatBadge(
                        text = sourceLabelOf(snapshot),
                        prefix = stringResource(R.string.live_source),
                    )
                    if (snapshot.isConverting) {
                        ConversionBadge(
                            text = "→ ${HdrGrade.of(snapshot.outputType).label}",
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Wider apart than a badge would be: the logos have lost the
                // chips that used to hold them apart, and two bare wordmarks
                // six points from each other read as one.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FormatLogo(
                        url = MediaUrls.logo(server, snapshot.logos.video),
                        contentDescription = null,
                    )
                    FormatLogo(
                        url = MediaUrls.logo(server, snapshot.logos.audio),
                        contentDescription = null,
                    )
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

        if (canControl) {
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionRule()
                    TransportSection(
                        snapshot = snapshot,
                        pendingVolume = pendingVolume,
                        viewModel = viewModel,
                    )
                    if (!snapshot.controls.isEmpty) {
                        SectionRule()
                        TrackSection(snapshot.controls, viewModel)
                    }
                }
            }
        }
    }
}

/**
 * The hairline between two blocks inside a card.
 *
 * All the heading a block needs here: a row of transport buttons and a pair of
 * track pickers say what they are by their own shape, and a word over each
 * only named what was already in front of the reader.
 */
@Composable
private fun SectionRule() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(
                progress = { reported / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
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
                color = if (target != null) {
                    MaterialTheme.colorScheme.accentText
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
 * Play, the six jumps, the volume and stop.
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
 * Jumping to a point rather than by one is the bar at the top of the card.
 */
@Composable
private fun TransportSection(
    snapshot: Snapshot,
    pendingVolume: Int?,
    viewModel: LiveViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JumpButton(
            label = "-10m",
            description = pluralStringResource(R.plurals.live_seek_back_minutes, 10, 10),
            onClick = { viewModel.seekBy(-600) },
        )
        JumpButton(
            label = "-1m",
            description = pluralStringResource(R.plurals.live_seek_back_minutes, 1, 1),
            onClick = { viewModel.seekBy(-60) },
        )
        JumpButton(
            label = "-10s",
            description = pluralStringResource(R.plurals.live_seek_back, 10, 10),
            onClick = { viewModel.seekBy(-10) },
        )
        FilledIconButton(
            onClick = viewModel::playPause,
            modifier = Modifier.size(54.dp),
        ) {
            Icon(
                imageVector = if (snapshot.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(R.string.live_playpause),
            )
        }
        JumpButton(
            label = "+10s",
            description = pluralStringResource(R.plurals.live_seek_forward, 10, 10),
            onClick = { viewModel.seekBy(10) },
        )
        JumpButton(
            label = "+1m",
            description = pluralStringResource(R.plurals.live_seek_forward_minutes, 1, 1),
            onClick = { viewModel.seekBy(60) },
        )
        JumpButton(
            label = "+10m",
            description = pluralStringResource(R.plurals.live_seek_forward_minutes, 10, 10),
            onClick = { viewModel.seekBy(600) },
        )
    }

    VolumeRow(snapshot.controls, pendingVolume, viewModel)
}

/**
 * One jump, as far as it goes.
 *
 * The label carries the distance and the sign carries the direction, so the
 * row reads left to right without counting buttons out from the middle. The
 * screen reader is told the same thing in words.
 */
@Composable
private fun JumpButton(label: String, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
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
 * The volume, with stop at the end of it.
 *
 * Stop takes the far right of the last row rather than a button of its own in
 * the middle of the transport: it ends playback outright, and the far corner is
 * the hardest place on the row to hit by accident. A box that sends no volume
 * still gets the button - what goes missing is the slider, not the transport.
 */
@Composable
private fun VolumeRow(
    controls: PlayerControls,
    pendingVolume: Int?,
    viewModel: LiveViewModel,
) {
    val level = pendingVolume ?: controls.volume

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (level == null) {
            Spacer(Modifier.weight(1f))
        } else {
            FilledTonalIconButton(onClick = viewModel::toggleMute) {
                Icon(
                    imageVector = if (controls.muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (controls.muted) R.string.live_unmute else R.string.live_mute
                    ),
                )
            }
            Slider(
                value = level.toFloat(),
                onValueChange = { viewModel.previewVolume(it.toInt()) },
                onValueChangeFinished = { viewModel.commitVolume(level) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$level",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(36.dp),
            )
        }
        FilledTonalIconButton(onClick = viewModel::stop) {
            Icon(
                Icons.Filled.Stop,
                contentDescription = stringResource(R.string.live_stop),
            )
        }
    }
}

/** The audio and subtitle tracks, as two pickers. */
@Composable
private fun TrackSection(controls: PlayerControls, viewModel: LiveViewModel) {
    if (controls.audio.isNotEmpty()) {
        TrackPicker(
            label = stringResource(R.string.live_audio_track),
            tracks = controls.audio,
            selected = controls.audioCurrent,
            offLabel = null,
            onSelect = { index -> index?.let(viewModel::selectAudio) },
        )
    }
    if (controls.subtitle.isNotEmpty()) {
        TrackPicker(
            label = stringResource(R.string.live_subtitles),
            tracks = controls.subtitle,
            // Kodi goes on naming the track that was switched off, so the
            // picker only follows the current index while they are on.
            selected = if (controls.subtitleOn) controls.subtitleCurrent else null,
            offLabel = stringResource(R.string.live_subtitles_off),
            onSelect = viewModel::selectSubtitle,
        )
    }
}

@Composable
private fun TrackPicker(
    label: String,
    tracks: List<Track>,
    selected: Int?,
    offLabel: String?,
    onSelect: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = tracks.firstOrNull { it.index == selected }?.label
        ?: offLabel
        ?: stringResource(R.string.live_track_unknown)

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
 * Tonal rather than filled: a filled button is the accent at full strength, and
 * two of those shouting from a card near the bottom of the screen outrank the
 * play button they are sitting under. These wear what the transport wears.
 *
 * Two to a line, sharing the width equally. They are alternatives to each
 * other, so one drawn wider than the next would be saying something about it
 * that is not true - and a box that offers four of them would otherwise put
 * four names in the width of one, each cut to nothing. An odd one at the end
 * takes the line to itself.
 */
@Composable
private fun Vs10Card(vs10: Vs10State, canControl: Boolean, viewModel: LiveViewModel) {
    SectionCard(title = stringResource(R.string.live_vs10), foldId = FOLD_VS10) {
        InfoRow(
            label = stringResource(R.string.live_vs10_output),
            value = vs10.output.ifBlank { null },
        )

        if (!canControl) {
            Text(
                text = stringResource(R.string.live_control_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            vs10.options.chunked(VS10_PER_ROW).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { option ->
                        FilledTonalButton(
                            onClick = { viewModel.setMode(option.mode) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = option.label,
                                // A button is one line tall whatever is written
                                // on it, so a name too long for its share is cut
                                // rather than wrapped out of sight.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The figures that arrive as numbers rather than as text.
 *
 * Everything the overlay prints is on the details screen, already formatted by
 * the box. What is here is the other half of a snapshot: the readings the
 * dashboard charts, which are worth a tile each because they move.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricsCard(snapshot: Snapshot) {
    val metrics = snapshot.metrics

    SectionCard(title = stringResource(R.string.live_metrics), foldId = FOLD_METRICS) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The luminance pair only exists inside a Dolby Vision RPU; the
            // add-on leaves it out for every other grade rather than send the
            // zeroes its own getter pads an absent block with.
            Formatters.nits(metrics.l1.max)?.let {
                StatTile(value = it, caption = stringResource(R.string.metric_peak))
            }
            Formatters.nits(metrics.l1.avg)?.let {
                StatTile(
                    value = it,
                    caption = stringResource(R.string.metric_average),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Formatters.fps(metrics.fpsOut)?.let {
                StatTile(
                    value = it,
                    caption = stringResource(R.string.metric_fps),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        InfoRow(
            label = stringResource(R.string.metric_aspect),
            value = Formatters.aspect(metrics.aspect),
        )
        metrics.frame?.takeIf { it.isUsable }?.let { frame ->
            InfoRow(
                label = stringResource(R.string.metric_frame),
                value = Formatters.frameSize(frame.w, frame.h),
            )
        }
        metrics.activeArea?.let { area ->
            InfoRow(
                label = stringResource(R.string.metric_active_area),
                value = stringResource(
                    R.string.metric_active_area_value,
                    area.left.toInt(),
                    area.right.toInt(),
                    area.top.toInt(),
                    area.bottom.toInt(),
                ),
            )
        }
        InfoRow(
            label = stringResource(R.string.metric_cpu),
            value = Formatters.percent(metrics.cpu),
        )
        InfoRow(
            label = stringResource(R.string.metric_temperature),
            value = Formatters.celsius(metrics.cpuTemp),
        )
        InfoRow(
            label = stringResource(R.string.metric_memory),
            value = Formatters.percent(metrics.memory),
        )
        InfoRow(
            label = stringResource(R.string.metric_cache),
            value = Formatters.percent(metrics.cache),
        )
    }
}

/**
 * What the title that has just ended came to.
 *
 * The figures are worth most in the minutes right after the credits, which is
 * exactly when a dashboard that threw them away at the end of playback had
 * already lost them. The box holds them for ten minutes and this is where they
 * are read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastPlayedCard(snapshot: Snapshot) {
    val last = snapshot.last

    SectionCard(title = stringResource(R.string.live_last_played), foldId = FOLD_LAST_PLAYED) {
        Text(
            text = last.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Formatters.nits(last.peak)?.let {
                StatTile(value = it, caption = stringResource(R.string.metric_peak))
            }
            StatTile(
                value = last.switches.toString(),
                caption = stringResource(R.string.live_switches),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            StatTile(
                value = last.warnings.toString(),
                caption = stringResource(R.string.live_warnings),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        InfoRow(
            label = stringResource(R.string.live_last_position),
            value = last.position.ifBlank { null },
        )
        InfoRow(
            label = stringResource(R.string.live_last_ago),
            value = Formatters.elapsed(last.ago.toDouble()),
        )
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
private const val FOLD_METRICS = "live.metrics"
private const val FOLD_LAST_PLAYED = "live.last_played"

/** The line under the title: the show and episode, the year, the genre. */
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

/** How many conversions the VS10 card puts on one line. */
private const val VS10_PER_ROW = 2
