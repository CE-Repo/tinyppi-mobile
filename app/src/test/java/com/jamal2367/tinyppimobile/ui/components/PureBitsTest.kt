package com.jamal2367.tinyppimobile.ui.components

import com.jamal2367.tinyppimobile.data.model.Chapter
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.ui.live.chapterAt
import com.jamal2367.tinyppimobile.ui.live.chapterDots
import com.jamal2367.tinyppimobile.ui.live.finishDrag
import com.jamal2367.tinyppimobile.ui.live.percentOf
import com.jamal2367.tinyppimobile.ui.live.snapTarget
import com.jamal2367.tinyppimobile.ui.live.upcomingChapters
import com.jamal2367.tinyppimobile.ui.live.markTarget
import com.jamal2367.tinyppimobile.ui.theme.accentOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Small rules under the cards. */
class PureBitsTest {

    @Test
    fun `a coloured poster gives an accent and a grey one none`() {
        assertNotNull(accentOf(IntArray(64) { 0xFFC82828.toInt() }, dark = true))
        assertNotNull(accentOf(IntArray(64) { 0xFFC82828.toInt() }, dark = false))
        assertNull(accentOf(IntArray(64) { 0xFF808080.toInt() }, dark = true))
    }

    @Test
    fun `an episode on the continue row is named by its show and code, where it has them`() {
        assertEquals("Dark · S01E02", ContinueItem("episode", 1, "Lies", show = "Dark", season = 1, episode = 2).markTarget().title)
        assertEquals("Lies", ContinueItem("episode", 1, "Lies", show = "").markTarget().title)
        assertEquals("", ContinueItem("episode", 1, "", show = "").markTarget().title)
        assertEquals("Heat", ContinueItem("movie", 1, "Heat").markTarget().title)
    }

    @Test
    fun `letting go of a drag seeks only where it moved`() {
        val seeks = mutableListOf<Float>()
        finishDrag(null, seeks::add)
        finishDrag(40f, seeks::add)
        assertEquals(listOf(40f), seeks)
    }

    private val chapters = listOf(
        Chapter(1, 0, 0.0, "00:00:00", "Opening"),
        Chapter(2, 1800, 25.0, "00:30:00", "Arrival"),
        Chapter(3, 3636, 50.5, "01:00:36"),
        Chapter(4, 3650, 51.0, "01:00:50"),
        Chapter(5, 6840, 95.0, "01:54:00", "Credits"),
    )

    @Test
    fun `a drag that let go on a dot jumps to its chapter rather than the spot`() {
        val seeks = mutableListOf<Float>()
        val jumps = mutableListOf<Int>()
        finishDrag(25f, seeks::add, chapters[1], jumps::add)
        finishDrag(30f, seeks::add, null, jumps::add)
        assertEquals(listOf(2), jumps)
        assertEquals(listOf(30f), seeks)
    }

    @Test
    fun `a dot for every chapter but the first, close ones drawn as one`() {
        val wide = chapterDots(chapters, width = 2000f, minGap = 6f)
        assertEquals(listOf(2, 3, 4, 5), wide.map { it.chapter.index })
        // Half a percent of 400 pixels is two: chapter 4 is drawn by 3's dot.
        val narrow = chapterDots(chapters, width = 400f, minGap = 6f)
        assertEquals(listOf(2, 3, 5), narrow.map { it.chapter.index })
        assertEquals(setOf(3, 4), narrow[1].stands)
        // Not measured yet: nothing merged.
        assertEquals(4, chapterDots(chapters, width = 0f, minGap = 6f).size)
        assertEquals(emptyList<Any>(), chapterDots(emptyList(), 400f, 6f))
    }

    @Test
    fun `a chapter that has ended is not marked`() {
        assertEquals(listOf(3, 4, 5), upcomingChapters(chapters, current = 3).map { it.index })
        assertEquals(listOf(5), upcomingChapters(chapters, current = 5).map { it.index })
        // Before the first one has begun, or a number the list does not hold.
        assertEquals(chapters, upcomingChapters(chapters, current = 0))
        assertEquals(chapters, upcomingChapters(chapters, current = 9))
    }

    @Test
    fun `a drag snaps to the nearest dot within reach, never to the start`() {
        assertEquals(2, snapTarget(26f, chapters, reach = 2f)?.index)
        assertEquals(4, snapTarget(50.9f, chapters, reach = 2f)?.index)
        assertEquals(3, snapTarget(50.6f, chapters, reach = 2f)?.index)
        assertNull(snapTarget(30f, chapters, reach = 2f))
        assertNull(snapTarget(0.5f, chapters, reach = 2f))
        assertNull(snapTarget(25f, emptyList(), reach = 2f))
    }

    @Test
    fun `the chapter a position is in, and pixels as a share of the bar`() {
        assertEquals(1, chapterAt(10f, chapters)?.index)
        assertEquals(5, chapterAt(100f, chapters)?.index)
        assertNull(chapterAt(10f, chapters.drop(1)))
        assertEquals(2f, percentOf(8f, 400))
        assertEquals(0f, percentOf(8f, 0))
    }

    @Test
    fun `every grade is read off its token`() {
        assertEquals(HdrGrade.SDR, HdrGrade.of(""))
        assertEquals(HdrGrade.SDR, HdrGrade.of("sdr"))
        assertEquals(HdrGrade.DOLBY_VISION, HdrGrade.of("dolbyvision"))
        assertEquals(HdrGrade.DOLBY_VISION, HdrGrade.of("dvhe"))
        assertEquals(HdrGrade.HDR10_PLUS, HdrGrade.of("hdr10plus"))
        assertEquals(HdrGrade.HDR10_PLUS, HdrGrade.of("HDR10+"))
        assertEquals(HdrGrade.HLG, HdrGrade.of("hlg"))
        assertEquals(HdrGrade.HDR10, HdrGrade.of("hdr10"))
        assertEquals(HdrGrade.SDR, HdrGrade.of("mystery"))
    }

    @Test
    fun `a move against a stored order keeps it`() {
        val moved = mutableListOf<List<String>>()
        val layout = CardLayout(orders = mapOf("live" to listOf("b", "a")), onOrder = { _, order -> moved += order })
        layout.move("live", listOf("a", "b"), "a", -1)
        assertEquals(listOf(listOf("a", "b")), moved)
    }
}
