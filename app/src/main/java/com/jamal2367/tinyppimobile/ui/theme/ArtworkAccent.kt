@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.jamal2367.tinyppimobile.util.ArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour of what is playing, read off its own poster.
 *
 * Null until the picture is in hand, and null for a title with no poster or a
 * poster with no colour in it - the caller falls back to the app's own palette
 * there rather than being handed a grey pretending to be an accent.
 *
 * The picture is asked for at a size a thumbnail would be embarrassed by. It is
 * being averaged, not looked at, and forty-eight pixels square carries every
 * bit of the answer that a full poster does at a thousandth of the work.
 */
@Composable
fun rememberArtworkAccent(url: String?): Color? {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var accent by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(url, dark) {
        accent = url?.let { readAccent(context, it, dark) }
    }

    return accent
}

/**
 * Whether the colours around this point came off a poster, and which one.
 *
 * Null where nothing is playing, where the title has no poster, or where the
 * reader has switched the whole thing off. Read by anything that wants to do
 * more with the colour than a theme role can say - the wash behind the live
 * card - and ignored by everything else, which simply reads `primary` and gets
 * the right answer either way.
 */
val LocalArtworkAccent = staticCompositionLocalOf<Color?> { null }

/**
 * The app's theme again, in the colour of the poster on screen.
 *
 * Wrapped around the whole app rather than the one screen showing the poster:
 * the tabs, the settings and the charts are all part of the same sitting, and a
 * colour that stopped at the edge of one screen would look like a bug in the
 * other four.
 *
 * Every accent role is moved at once rather than a handful of call sites being
 * painted by hand: the buttons, the sliders, the headings and the chevron are
 * all reading `primary` already, and a screen where half of them followed the
 * poster and half did not would look like a bug rather than a theme.
 *
 * Surfaces are left where they are. The picture decides the accents; the reader
 * decided light or dark, and a poster is not entitled to overturn that.
 */
@Composable
fun ArtworkAccentTheme(accent: Color?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, accent) { if (accent == null) base else base.accented(accent) }

    CompositionLocalProvider(LocalArtworkAccent provides accent) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

/**
 * The wash a card is drawn on when the poster has lent it a colour.
 *
 * Strongest at the top where the poster itself is and gone a little below it,
 * so the card reads as the picture bleeding into the page rather than as a
 * block of paint sitting on it.
 *
 * It fades out over a fixed distance rather than over the card's own height:
 * the card grows by the length of a transport and two track pickers when it is
 * folded open, and a wash measured in fractions would stretch to cover all of
 * that - a tint spread down the whole of a long card is a tinted card.
 */
@Composable
fun artworkGradient(accent: Color, container: Color): Brush {
    val fade = with(LocalDensity.current) { WASH_HEIGHT.toPx() }

    return remember(accent, container, fade) {
        Brush.verticalGradient(
            colors = listOf(lerp(container, accent, TOP_TINT), container),
            startY = 0f,
            endY = fade,
        )
    }
}

private suspend fun readAccent(context: Context, url: String, dark: Boolean): Color? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(SAMPLE_SIZE)
        // A hardware bitmap has no pixels to read - it lives on the graphics
        // card - and reading pixels is the whole of what this request is for.
        .allowHardware(false)
        .build()

    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    val bitmap = (result?.image as? BitmapImage)?.bitmap ?: return null

    return withContext(Dispatchers.Default) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        ArtworkPalette.accent(pixels)
            ?.let { ArtworkPalette.forTheme(it, dark) }
            ?.let(::Color)
    }
}

/**
 * The scheme's accent roles, moved onto the poster's hue.
 *
 * One hue throughout, not three. An earlier turn of this turned the second and
 * third tones a little around the wheel to keep three stat tiles apart, and a
 * red poster came out with pink tabs along the bottom of the app - twenty-four
 * degrees off red is where pink lives. The three tones differ in how deep they
 * are drawn instead, which tells them apart without renaming the colour.
 *
 * The second is the workhorse: the tonal buttons, the stop button and the
 * selected tab all read `secondaryContainer`, and they should all come out the
 * same shade of the poster's colour.
 */
private fun ColorScheme.accented(accent: Color): ColorScheme {
    val dark = surface.luminance() < 0.5f
    val onAccent = accent.readableOn()

    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container(accent, dark, STRONG_TINT),
        onPrimaryContainer = accent.onContainer(dark),
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container(accent, dark, MEDIUM_TINT),
        onSecondaryContainer = accent.onContainer(dark),
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container(accent, dark, FAINT_TINT),
        onTertiaryContainer = accent.onContainer(dark),
        surfaceTint = accent,
        // Enough tint to belong to the same picture, not enough to draw a box
        // around everything on the screen.
        outline = lerp(outline, accent, OUTLINE_TINT),
        outlineVariant = lerp(outlineVariant, accent, OUTLINE_TINT),
    )
}

private fun ColorScheme.container(accent: Color, dark: Boolean, tint: Float): Color =
    lerp(surfaceContainerHigh, accent, if (dark) tint else tint + LIGHT_EXTRA_TINT)

/**
 * What can be read on one of those containers.
 *
 * Brightened rather than mixed towards white: white takes the colour out as it
 * goes, and a red carried most of the way to white is a pink. Keeping the hue
 * and nearly all of the saturation and simply turning the light up leaves a red
 * that is still red at the top of its range.
 */
private fun Color.onContainer(dark: Boolean): Color =
    if (dark) toned(ON_DARK_VALUE, ON_DARK_SATURATION) else toned(ON_LIGHT_VALUE)

/** Black or white, whichever can be read on this colour. */
private fun Color.readableOn(): Color = if (luminance() > 0.40f) Color.Black else Color.White

/** The same hue, taken to a given brightness. */
private fun Color.toned(value: Float, saturationScale: Float = 1f): Color {
    val hsv = ArtworkPalette.toHsv(toArgb())
    return Color(
        ArtworkPalette.fromHsv(hsv[0], (hsv[1] * saturationScale).coerceIn(0f, 1f), value)
    )
}

/** How wide the poster is sampled, in pixels. */
private const val SAMPLE_SIZE = 48

/** How far down the card the poster's colour has faded to nothing. */
private val WASH_HEIGHT = 190.dp

private const val TOP_TINT = 0.22f
private const val OUTLINE_TINT = 0.35f

private const val STRONG_TINT = 0.34f
private const val MEDIUM_TINT = 0.22f
private const val FAINT_TINT = 0.13f

/** A pale ground takes more of the colour before it shows at all. */
private const val LIGHT_EXTRA_TINT = 0.06f

private const val ON_DARK_VALUE = 0.95f
private const val ON_DARK_SATURATION = 0.75f
private const val ON_LIGHT_VALUE = 0.30f
