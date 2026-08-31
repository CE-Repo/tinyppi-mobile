package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The handful of figures this app formats itself.
 *
 * Everything the overlay prints arrives already formatted - the add-on renders
 * a row the way the television draws it - so what is left here is the numeric
 * half of a snapshot, the readings that are charted rather than printed.
 */
class FormattersTest {

    @Test
    fun `a nit reading keeps its detail where the detail is the reading`() {
        // Below ten, a whole number is most of what is being said: 0 and 4 are
        // very different pictures.
        assertEquals("4.2 nits", Formatters.nits(4.2))
        assertEquals("120 nits", Formatters.nits(120.0))
        // Written out rather than abbreviated: a peak is compared against
        // figures people know, and `1.9 k` is no shorter than `1900`.
        assertEquals("1000 nits", Formatters.nits(1000.0))
        assertEquals("1900 nits", Formatters.nits(1900.0))
        assertEquals("4000 nits", Formatters.nits(4000.0))
    }

    @Test
    fun `a reading nobody took prints nothing at all`() {
        assertNull(Formatters.nits(null))
        assertNull(Formatters.percent(null))
        assertNull(Formatters.celsius(null))
        assertNull(Formatters.fps(null))
        assertNull(Formatters.aspect(null))
    }

    @Test
    fun `a frame rate keeps the decimals it has and drops the ones it does not`() {
        assertEquals("23.976 fps", Formatters.fps(23.976))
        assertEquals("24 fps", Formatters.fps(24.0))
        assertEquals("59.94 fps", Formatters.fps(59.94))
    }

    @Test
    fun `an aspect ratio reads the way the overlay writes one`() {
        assertEquals("2.39:1", Formatters.aspect(2.39))
        assertEquals("1.78:1", Formatters.aspect(1.78))
        assertEquals("2:1", Formatters.aspect(2.0))
    }

    @Test
    fun `percentages and temperatures are whole`() {
        assertEquals("31 %", Formatters.percent(31.4))
        assertEquals("100 %", Formatters.percent(99.6))
        assertEquals("58 °C", Formatters.celsius(57.8))
    }

    @Test
    fun `an elapsed time gains its hour only once there is one`() {
        assertEquals("0:00", Formatters.elapsed(0.0))
        assertEquals("1:07", Formatters.elapsed(67.4))
        assertEquals("1:12:30", Formatters.elapsed(4350.0))
    }

    @Test
    fun `a clock the box sent reads back as the seconds it stands for`() {
        assertEquals(389.0, Formatters.clockSeconds("00:06:29"))
        assertEquals(7214.0, Formatters.clockSeconds("02:00:14"))
        assertEquals(389.0, Formatters.clockSeconds("6:29"))
    }

    @Test
    fun `a clock that is not one reads back as nothing`() {
        assertNull(Formatters.clockSeconds(""))
        assertNull(Formatters.clockSeconds("live"))
        assertNull(Formatters.clockSeconds("1:2:3:4"))
    }

    @Test
    fun `a dragged position takes the shape of the reading it replaces`() {
        // The hours are the box's own: a title counted in them keeps them, and
        // one counted without them does not grow a leading 00 mid-gesture.
        assertEquals("00:06:29", Formatters.positionLike(389.0, "02:00:14"))
        assertEquals("06:29", Formatters.positionLike(389.0, "44:10"))
        assertEquals("01:00:00", Formatters.positionLike(3600.0, "44:10"))
    }

    @Test
    fun `a frame size uses the multiplication sign, not an x`() {
        assertEquals("3840 × 2160", Formatters.frameSize(3840, 2160))
    }

    @Test
    fun `trimming leaves a whole number whole`() {
        assertEquals("24", Formatters.trimmed(24.0, 3))
        assertEquals("23.976", Formatters.trimmed(23.976, 3))
        assertEquals("2.4", Formatters.trimmed(2.4, 2))
    }
}
