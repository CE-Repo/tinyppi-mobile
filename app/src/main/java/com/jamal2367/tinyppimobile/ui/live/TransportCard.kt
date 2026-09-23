@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.withKeyTick
import com.jamal2367.tinyppimobile.ui.theme.neutralTonalButtonColors
import com.jamal2367.tinyppimobile.ui.theme.neutralTonalIconButtonColors

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
internal fun ControlsCard(
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
        onClick = withKeyTick(onClick),
        shape = TRANSPORT_SHAPE,
        colors = neutralTonalIconButtonColors(),
        modifier = modifier
            .height(TRANSPORT_BUTTON)
            .semantics { contentDescription = description },
    ) {
        // Said in words by the key itself; left to be read as well, "-10m"
        // came out after it as "minus ten m".
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics {},
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
    var volumeMenuOpen by remember { mutableStateOf(false) }
    val volumeDownLabel = stringResource(R.string.live_volume_down)
    val volumeUpLabel = stringResource(R.string.live_volume_up)
    val volumeMenuGap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val volumeState = when {
        controls.muted -> stringResource(R.string.live_volume_muted)
        controls.volume != null ->
            pluralStringResource(R.plurals.live_volume_percent, controls.volume, controls.volume)
        else -> null
    }

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
            onClick = withKeyTick(viewModel::playPause),
            shape = TRANSPORT_SHAPE,
            colors = neutralTonalIconButtonColors(),
            modifier = Modifier
                .weight(1f)
                .height(TRANSPORT_BUTTON),
        ) {
            Icon(
                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                // What pressing it will do, the way the icon says it - not the
                // pair of them, which left a screen reader to guess which.
                contentDescription = stringResource(
                    if (paused) R.string.live_play else R.string.live_pause,
                ),
            )
        }

        // Keep the transport row compact. The three volume actions live in
        // the menu anchored to this one button and therefore no longer take
        // three permanent places in the row.
        Box(modifier = Modifier.weight(1f)) {
            FilledTonalIconButton(
                onClick = withKeyTick { volumeMenuOpen = true },
                shape = TRANSPORT_SHAPE,
                colors = neutralTonalIconButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRANSPORT_BUTTON)
                    // The reading the speaker on the key stands for, in words:
                    // muted, or how loud Kodi's own mixer is.
                    .semantics { volumeState?.let { stateDescription = it } },
            ) {
                Icon(
                    imageVector = if (controls.muted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(R.string.live_volume),
                )
            }

            if (volumeMenuOpen) Popup(
                popupPositionProvider = VolumeMenuPositionProvider(volumeMenuGap),
                onDismissRequest = { volumeMenuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = withKeyTick(viewModel::volumeDown),
                            shape = TRANSPORT_SHAPE,
                            colors = neutralTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .width(VOLUME_MENU_BUTTON)
                                .height(TRANSPORT_BUTTON)
                                .semantics { contentDescription = volumeDownLabel },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(STEP_ICON),
                            )
                            Text(
                                text = "−",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clearAndSetSemantics {},
                            )
                        }
                        FilledTonalIconButton(
                            onClick = withKeyTick(viewModel::toggleMute),
                            shape = TRANSPORT_SHAPE,
                            colors = neutralTonalIconButtonColors(),
                            modifier = Modifier
                                .width(VOLUME_MENU_BUTTON)
                                .height(TRANSPORT_BUTTON),
                        ) {
                            Icon(
                                imageVector = if (controls.muted) {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                contentDescription = stringResource(
                                    if (controls.muted) R.string.live_unmute else R.string.live_mute,
                                ),
                            )
                        }
                        FilledTonalButton(
                            onClick = withKeyTick(viewModel::volumeUp),
                            shape = TRANSPORT_SHAPE,
                            colors = neutralTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .width(VOLUME_MENU_BUTTON)
                                .height(TRANSPORT_BUTTON)
                                .semantics { contentDescription = volumeUpLabel },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(STEP_ICON),
                            )
                            Text(
                                text = "+",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }

        FilledTonalIconButton(
            onClick = withKeyTick(viewModel::stop),
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

/** Keeps the volume popup centered on its anchor, regardless of screen width. */
private class VolumeMenuPositionProvider(
    private val verticalGap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
        y = anchorBounds.bottom + verticalGap,
    )
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
        onClick = withKeyTick(onClick),
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
internal val TRANSPORT_BUTTON = 40.dp

/** Shared width for the three controls inside the volume popup. */
private val VOLUME_MENU_BUTTON = 52.dp

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
internal val TRANSPORT_SHAPE = RoundedCornerShape(12.dp)

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
internal val TRANSPORT_GAP = 8.dp

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
    val pick = withKeyTick(onSelect)
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
        // Read as part of the button below rather than on its own, where it
        // was a stop of its own with nothing to press.
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = labelTextAlign,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        Box {
            FilledTonalButton(
                onClick = withKeyTick { open = true },
                shape = TRANSPORT_SHAPE,
                colors = neutralTonalButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRANSPORT_BUTTON)
                    // Which picker this is, then what it is set to: "Audio
                    // track, English DTS-HD" rather than the track alone.
                    .semantics {
                        contentDescription = label
                        stateDescription = current
                    },
            ) {
                Text(
                    text = current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {},
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (offLabel != null) {
                    DropdownMenuItem(
                        text = { Text(offLabel) },
                        onClick = {
                            open = false
                            pick(null)
                        },
                    )
                }
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(track.label) },
                        onClick = {
                            open = false
                            pick(track.index)
                        },
                    )
                }
            }
        }
    }
}
