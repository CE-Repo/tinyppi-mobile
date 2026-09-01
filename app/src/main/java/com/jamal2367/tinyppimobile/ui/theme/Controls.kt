package com.jamal2367.tinyppimobile.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable

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
 * A slider that is a setting rather than a reading.
 *
 * The progress bar keeps the accent: where the film has got to is what the
 * card is about. The volume is a second full-strength bar sitting directly
 * under the first, saying the same colour about a smaller thing - drawn grey
 * it still reads at a glance, and stops repeating the point.
 *
 * The same grey as the buttons at either end of it, not the dimmer one. The
 * bar runs between the stop and the mute and is read across all three, and a
 * handle a shade under the icons it sits between looks like a handle that has
 * been switched off.
 */
@Composable
fun neutralSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurface,
    activeTrackColor = MaterialTheme.colorScheme.onSurface,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)
