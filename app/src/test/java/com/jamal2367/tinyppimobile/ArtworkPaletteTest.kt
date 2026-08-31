package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.util.ArtworkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour a poster is remembered by.
 *
 * Written against pixels made here rather than against a real poster: what is
 * being tested is which colour wins and why, and a fixture that can be read as
 * "mostly black, some red" says that where a JPEG would not.
 */
class ArtworkPaletteTest {

    @Test
    fun `the loud colour wins over the one that merely covers the most ground`() {
        // A poster the way most posters are: a black ground, a wash of muddy
        // grey-blue over most of it, and the title in red.
        val pixels = fill(BLACK, 800) + fill(MUDDY, 150) + fill(RED, 50)

        val hue = hueOf(ArtworkPalette.accent(pixels))
        assertTrue("expected a red accent, got $hue degrees", hue < 20f || hue > 340f)
    }

    @Test
    fun `red is not split in half by sitting on the seam of the wheel`() {
        // Two reds either side of zero degrees. Bucketed naively they land in
        // opposite ends of the wheel and each loses to the green.
        val pixels = fill(0xFFFF0011.toInt(), 60) + fill(0xFFFF2200.toInt(), 60) +
            fill(0xFF22AA22.toInt(), 90)

        val hue = hueOf(ArtworkPalette.accent(pixels))
        assertTrue("expected a red accent, got $hue degrees", hue < 20f || hue > 340f)
    }

    @Test
    fun `a picture with no colour in it yields no colour`() {
        assertNull(ArtworkPalette.accent(IntArray(0)))
        assertNull(ArtworkPalette.accent(fill(BLACK, 100)))
        assertNull(ArtworkPalette.accent(fill(0xFFEFEFEF.toInt(), 100)))
        // Transparent pixels are part of no picture, however red they are.
        assertNull(ArtworkPalette.accent(fill(0x20FF0000, 100)))
    }

    @Test
    fun `an accent is moved into the band its theme can show, hue intact`() {
        val deep = 0xFF2B0000.toInt()

        val onDark = ArtworkPalette.toHsv(ArtworkPalette.forTheme(deep, dark = true))
        val onLight = ArtworkPalette.toHsv(ArtworkPalette.forTheme(deep, dark = false))

        assertEquals(0f, onDark[0], 1f)
        assertEquals(0f, onLight[0], 1f)
        assertTrue("a dark screen wants a bright accent", onDark[2] > 0.6f)
        assertTrue("a light screen wants a deeper one", onLight[2] < 0.63f)
    }

    @Test
    fun `a colour survives the trip through hue, saturation and value`() {
        for (color in listOf(0xFFCC2222, 0xFF2244EE, 0xFF33AA55, 0xFF808080)) {
            val argb = color.toInt()
            val hsv = ArtworkPalette.toHsv(argb)
            assertEquals(argb, ArtworkPalette.fromHsv(hsv[0], hsv[1], hsv[2]))
        }
    }

    private fun fill(color: Int, count: Int) = IntArray(count) { color }

    private fun hueOf(color: Int?): Float {
        requireNotNull(color) { "expected an accent" }
        return ArtworkPalette.toHsv(color)[0]
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val MUDDY = 0xFF3A4048.toInt()
        const val RED = 0xFFD01020.toInt()
    }
}
