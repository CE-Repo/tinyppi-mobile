@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.tinyppimobile.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * What a button wears when it is a tool rather than an answer.
 *
 * The accent is a way of saying "this one". A screen that says it of the jump
 * buttons, the stop, the mute, the two track pickers and the two conversions
 * as well has said it of everything on the card and therefore of nothing: the
 * play button, which is the one thing on the screen worth pointing at, was
 * competing with twelve pills in its own colour.
 *
 * So the tools are drawn on the card's own ground, one step lighter than the
 * card - the same shade the hairline round the card is drawn in, which is what
 * a raised surface reads as here - and the accent is left to the handful of
 * places that mean something by it.
 */
@Composable
fun neutralTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** The same ground, for a tool that carries an icon instead of a word. */
@Composable
fun neutralTonalIconButtonColors(): IconButtonColors =
    IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

/**
 * How thick a slider's rail is drawn.
 *
 * Half of Material's sixteen. That figure is drawn for a slider that is the
 * subject of the screen it sits on - a brightness or a volume alone on a
 * settings page. Every slider here is one row among several on a card, sitting
 * under a wave six points thick and between two buttons, and at sixteen it was
 * the heaviest thing in the group by some way.
 */
val SliderTrackHeight = 8.dp

/**
 * That rail, for a slider that asks for it.
 *
 * The track is the only thing changed: the handle, the gap it holds either
 * side of itself and the dot at the end are Material's, measured against
 * whatever height the track is given.
 */
@Composable
fun SlimSliderTrack(
    state: SliderState,
    enabled: Boolean,
) {
    SliderDefaults.Track(
        sliderState = state,
        colors = SliderDefaults.colors(),
        enabled = enabled,
        modifier = Modifier.height(SliderTrackHeight),
    )
}

/**
 * How big a slider's handle is drawn.
 *
 * The playback bar's, and every other slider follows it: the transport is
 * where a handle on this app is learnt, and one shape of handle everywhere is
 * what stops the volume under it reading as a different kind of control.
 *
 * Wider than Material's four, because the wave it rides is drawn at the same
 * seven and a handle thinner than its own line looks like a crack in it.
 * Shorter than Material's forty-four, which is the height of a thumb - sized
 * to be grabbed rather than to be looked at - and against a twelve-point rail
 * read as a bar dropped across the card.
 *
 * Nothing is lost by taking the height down. A slider carries a full-sized
 * touch target of its own whatever the handle is painted at, so what came off
 * is paint and not reach.
 */
val SliderThumbWidth = 5.dp
val SliderThumbHeight = 28.dp

/**
 * That handle, for a slider that asks for it.
 *
 * The [interactionSource] has to be the one the slider itself was given: a
 * handle drawn by hand hears nothing on its own, and one that does not light
 * up under a finger is a handle that looks broken while it works.
 */
@Composable
fun SlimSliderThumb(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
) {
    SliderDefaults.Thumb(
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(),
        enabled = enabled,
        thumbSize = DpSize(SliderThumbWidth, SliderThumbHeight),
    )
}
