package com.jamal2367.tinyppimobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Fully rounded ends - what Material's expressive style puts on anything worth
 * pressing.
 */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * The corners the whole app is cut with.
 *
 * Material's expressive scale rather than the one Android 12 shipped with:
 * every role a step rounder, and the one chips are drawn from a pill outright.
 * These are the tokens the components read, so a chip, a card, a text box and
 * a sheet all follow from here rather than from a radius typed at each call.
 */
val TinyPpiShapes = Shapes(
    // Text boxes, menus, snackbars, tooltips. A 56dp-tall box with a 4dp
    // corner is the single most Android-12 thing on a screen.
    extraSmall = RoundedCornerShape(18.dp),
    // Chips - the shape says "press me" more plainly than an 8dp corner does.
    small = PillShape,
    // Cards, which is every block of readings on every screen here.
    medium = RoundedCornerShape(20.dp),
    // The larger surfaces, and buttons that are not pills.
    large = RoundedCornerShape(24.dp),
    // Bottom sheets and dialogs.
    extraLarge = RoundedCornerShape(28.dp),
)
