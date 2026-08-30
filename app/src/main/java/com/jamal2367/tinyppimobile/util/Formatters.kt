package com.jamal2367.tinyppimobile.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How numbers are printed.
 *
 * Most of what this app shows is already text: the add-on formats a reading
 * the way the overlay draws it, units and all, and sending that through a
 * second formatter here would only ever disagree with the television. What is
 * left is the handful of figures that arrive as numbers because they are
 * charted rather than printed - and those are formatted here.
 */
object Formatters {

    /** A luminance in nits, as the chart's axis and its tiles print one. */
    fun nits(value: Double?): String? {
        val number = value ?: return null
        val figure = when {
            number >= 1000 -> "${trimmed(number / 1000, 1)} k"
            number >= 10 -> number.roundToInt().toString()
            // Below ten a whole number is most of the reading: 0 and 4 are
            // very different pictures, and rounding them together hides that.
            else -> trimmed(number, 1)
        }
        return "$figure nits"
    }

    /** A percentage, whole. */
    fun percent(value: Double?): String? = value?.let { "${it.roundToInt()} %" }

    /** A temperature in degrees. */
    fun celsius(value: Double?): String? = value?.let { "${it.roundToInt()} °C" }

    /** A frame rate, with the decimal only where there is one worth printing. */
    fun fps(value: Double?): String? {
        val number = value ?: return null
        return "${trimmed(number, 3)} fps"
    }

    /** An aspect ratio, as the overlay writes it. */
    fun aspect(value: Double?): String? {
        val number = value ?: return null
        return "${trimmed(number, 2)}:1"
    }

    /** `1920 × 1080`, with the multiplication sign rather than an x. */
    fun frameSize(width: Int, height: Int): String = "$width × $height"

    /**
     * A number of seconds as a clock: `1:07`, `1:12:30`.
     *
     * Used for the chart's own axis, which counts from the start of the title
     * rather than through it - the player's own position arrives already
     * formatted and is printed as it came.
     */
    fun elapsed(seconds: Double): String {
        val whole = abs(seconds).roundToInt()
        val hours = whole / 3600
        val minutes = (whole % 3600) / 60
        val secondsPart = whole % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secondsPart)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, secondsPart)
        }
    }

    /**
     * A number with up to [decimals] places, and none where they are zeroes.
     *
     * `23.976` keeps all three, `24.0` prints as `24`. A trailing `.0` on a
     * frame rate reads as a precision that is not being claimed.
     *
     * Formatted against the root locale rather than the phone's, so the
     * separator is the one the trimming below looks for. Every figure this
     * touches is a measurement beside a unit - nits, fps, a ratio - and the
     * rows around them come already formatted from the add-on, which is where
     * a locale-aware number in this app would have to agree with something.
     */
    fun trimmed(value: Double, decimals: Int): String {
        val text = String.format(Locale.ROOT, "%.${decimals}f", value)
        if (!text.contains('.')) return text
        return text.trimEnd('0').trimEnd('.')
    }
}
