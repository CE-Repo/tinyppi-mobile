package com.jamal2367.tinyppimobile.ui.components

import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.ui.live.finishDrag
import com.jamal2367.tinyppimobile.ui.live.markTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Small rules under the cards. */
class PureBitsTest {

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
