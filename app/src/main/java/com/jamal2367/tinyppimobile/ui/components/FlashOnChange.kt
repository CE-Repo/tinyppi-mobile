package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.ui.theme.accentText
import kotlinx.coroutines.delay

/**
 * Light this row up for a moment whenever [value] changes.
 *
 * The printed readings arrive five times a second and most of them stand still
 * for minutes at a time, so a screenful of them says nothing about which one
 * just moved. This does: the row that changed is washed in the accent and
 * fades back, and a glance at a still screen is enough to find it.
 *
 * Nothing is lit the first time a row is drawn. Everything on screen is new
 * when a screen opens, and a card that lights up whole says only that it has
 * been opened.
 *
 * A reading that changes faster than the wash fades stays lit, which is the
 * honest answer for one - a live bitrate is not standing still and should not
 * look as though it is.
 *
 * Drawn behind rather than clipped around, so it costs no layer and can bleed a
 * little past the text on every side: a wash that stops at the glyphs reads as
 * a highlighter mark rather than as the row lighting up.
 */
@Composable
fun Modifier.flashOnChange(value: Any?): Modifier {
    var lit by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        lit = true
        delay(HOLD_MS)
        lit = false
    }

    val accent = MaterialTheme.colorScheme.accentText
    val wash by animateColorAsState(
        targetValue = if (lit) accent.copy(alpha = LIT_ALPHA) else Color.Transparent,
        animationSpec = tween(durationMillis = if (lit) RISE_MS else FADE_MS),
        label = "flash",
    )

    val density = LocalDensity.current
    val bleedX = with(density) { BLEED_X.toPx() }
    val bleedY = with(density) { BLEED_Y.toPx() }
    val radius = with(density) { RADIUS.toPx() }

    return drawBehind {
        if (wash.alpha <= 0f) return@drawBehind

        drawRoundRect(
            color = wash,
            topLeft = Offset(-bleedX, -bleedY),
            size = Size(size.width + bleedX * 2, size.height + bleedY * 2),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

/** How long the wash is held at full before it starts to go. */
private const val HOLD_MS = 260L

private const val RISE_MS = 90
private const val FADE_MS = 420
private const val LIT_ALPHA = 0.22f

private val BLEED_X = 6.dp
private val BLEED_Y = 3.dp
private val RADIUS = 6.dp
