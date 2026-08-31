package com.jamal2367.tinyppimobile.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one colour a poster is remembered by.
 *
 * The add-on's own dashboard paints itself in the colour of what is playing,
 * and this is that reading: a poster reduced to the single hue a reader would
 * name it by. Blade is red, Matrix is green, and a screen that agrees with the
 * picture on the television is one nobody has to look twice at.
 *
 * Done here rather than with a palette library because the whole of it is the
 * hundred lines below, and because a poster is not a photograph: it is a
 * printed thing with two or three colours in it on purpose, and the loudest of
 * those is the one being looked for.
 *
 * Pure arithmetic over pixels, so it can be read and tested without a device.
 */
object ArtworkPalette {

    /**
     * Turn a picture's pixels into the colour it is remembered by, or null.
     *
     * Null for a poster with no colour in it at all - a black-and-white still,
     * a title card, a grey placeholder. There is no accent to be had there, and
     * inventing one out of grey would paint the screen a colour the picture
     * never had.
     */
    fun accent(pixels: IntArray): Int? {
        val weights = FloatArray(HUE_BUCKETS)
        val hues = FloatArray(HUE_BUCKETS)
        val saturations = FloatArray(HUE_BUCKETS)
        val values = FloatArray(HUE_BUCKETS)

        for (pixel in pixels) {
            if ((pixel ushr 24 and 0xFF) < MIN_ALPHA) continue

            val hsv = toHsv(pixel)
            val saturation = hsv[1]
            val value = hsv[2]

            // Ink and paper carry no hue worth having: near-black is the
            // ground most posters are printed on, and white is the text. Only
            // the black half is a question of brightness - white, grey and a
            // washed-out sky are all caught by having no colour in them, and
            // testing brightness for those would throw away a poster whose red
            // happens to be the brightest thing on it.
            if (value < MIN_VALUE) continue
            if (saturation < MIN_SATURATION) continue

            // Shifted half a bucket before sorting, so red - which sits on the
            // seam at zero and is the commonest poster colour there is - lands
            // whole in one bucket instead of split across the two ends.
            val shifted = (hsv[0] + HALF_BUCKET) % FULL_CIRCLE
            val bucket = (shifted / FULL_CIRCLE * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)

            // Squared, so a wall of nearly-grey does not outvote the one lit
            // thing in the frame by sheer acreage.
            val weight = saturation * saturation
            weights[bucket] += weight
            hues[bucket] += shifted * weight
            saturations[bucket] += saturation * weight
            values[bucket] += value * weight
        }

        var best = 0
        for (bucket in 1 until HUE_BUCKETS) {
            if (weights[bucket] > weights[best]) best = bucket
        }

        val total = weights[best]
        if (total <= 0f) return null

        val hue = (hues[best] / total - HALF_BUCKET + FULL_CIRCLE) % FULL_CIRCLE
        return fromHsv(hue, saturations[best] / total, values[best] / total)
    }

    /**
     * The same colour, made fit to read against.
     *
     * A poster's own red is often either too dark to see on a dark screen or
     * too pale to see on a light one, and it is being asked to serve as text
     * and as a button here rather than as paint. The hue is what carries the
     * likeness, so the hue is what is kept: only how deep and how bright it is
     * gets moved into the band the theme can show.
     */
    fun forTheme(color: Int, dark: Boolean): Int {
        val hsv = toHsv(color)
        val saturation = hsv[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)
        val value = if (dark) {
            hsv[2].coerceIn(MIN_DARK_VALUE, MAX_DARK_VALUE)
        } else {
            hsv[2].coerceIn(MIN_LIGHT_VALUE, MAX_LIGHT_VALUE)
        }
        return fromHsv(hsv[0], saturation, value)
    }

    /** A colour as hue in degrees, saturation and value, each 0 to 1. */
    fun toHsv(color: Int): FloatArray {
        val red = (color shr 16 and 0xFF) / 255f
        val green = (color shr 8 and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f

        val high = maxOf(red, green, blue)
        val low = minOf(red, green, blue)
        val range = high - low

        val hue = when {
            range == 0f -> 0f
            high == red -> 60f * (((green - blue) / range) % 6f)
            high == green -> 60f * ((blue - red) / range + 2f)
            else -> 60f * ((red - green) / range + 4f)
        }

        return floatArrayOf(
            if (hue < 0f) hue + FULL_CIRCLE else hue,
            if (high == 0f) 0f else range / high,
            high,
        )
    }

    /** The way back: hue, saturation and value as an opaque colour. */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val degrees = ((hue % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
        val level = saturation.coerceIn(0f, 1f)
        val brightness = value.coerceIn(0f, 1f)

        val chroma = brightness * level
        val second = chroma * (1f - abs((degrees / 60f) % 2f - 1f))
        val base = brightness - chroma

        val (red, green, blue) = when ((degrees / 60f).toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }

        return (0xFF shl 24) or
            (((red + base) * 255f).roundToInt() shl 16) or
            (((green + base) * 255f).roundToInt() shl 8) or
            ((blue + base) * 255f).roundToInt()
    }

    private const val FULL_CIRCLE = 360f

    /**
     * How finely the wheel is cut.
     *
     * Eighteen buckets is twenty degrees each - wide enough that the shades of
     * one poster's red count as the same colour, narrow enough that red and
     * orange are not asked to average into something neither of them is.
     */
    private const val HUE_BUCKETS = 18
    private const val HALF_BUCKET = FULL_CIRCLE / HUE_BUCKETS / 2f

    /** Below this a pixel is see-through, and part of no picture. */
    private const val MIN_ALPHA = 128

    private const val MIN_VALUE = 0.12f
    private const val MIN_SATURATION = 0.18f

    private const val MIN_ACCENT_SATURATION = 0.40f
    private const val MAX_ACCENT_SATURATION = 0.85f
    private const val MIN_DARK_VALUE = 0.62f
    private const val MAX_DARK_VALUE = 0.92f
    private const val MIN_LIGHT_VALUE = 0.38f
    private const val MAX_LIGHT_VALUE = 0.62f
}
