package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A press on a key that acts on the television, with a tick under the thumb.
 *
 * The keys that drive the box are pressed with the eyes on the picture rather
 * than on the phone, and what they do happens a room away and a moment later.
 * The tick is the part of the answer that arrives in the hand, at once: the key
 * was hit, and hit once.
 *
 * The system's own touch-feedback switch still decides - a phone set to no
 * vibration on touch gets none from here either.
 */
@Composable
fun withKeyTick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }
}

/**
 * The same tick for a choice made in a picker, which takes the choice with it.
 *
 * Kept apart from [withKeyTick] only because the callback carries a value.
 */
@Composable
fun <T> withKeyTick(onSelect: (T) -> Unit): (T) -> Unit {
    val haptics = LocalHapticFeedback.current
    return { value ->
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onSelect(value)
    }
}
