package com.jamal2367.tinyppimobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * How far apart the cards sit, on every screen that stacks them.
 *
 * Wider than the gap a list of rows would take. These are not rows: each card
 * is a different question - what is playing, what can be done to it, what it
 * can be converted into - and the space between them is a good deal of what
 * says so, now that they are no longer told apart by a wall of colour.
 *
 * One figure for all five screens rather than a number typed into each list.
 * The tabs are read one after the other in the same sitting, and a rhythm that
 * changed as the reader moved along the bar would read as five apps.
 */
val CardGap = 17.dp

/**
 * How far the cards stay off the edges of the screen.
 *
 * Two points wider than the twelve a list of rows takes, and two inside the
 * sixteen Material would set a margin at. A card is a shape with its own edge,
 * and it wants a little more air than a row does - but the cards here are wide
 * ones, with a poster and a column of type to fit across them, and every point
 * taken off the sides is a point off the longest line on the screen.
 */
val ScreenEdge = 14.dp
