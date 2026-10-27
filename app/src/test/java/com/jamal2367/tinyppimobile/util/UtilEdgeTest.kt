package com.jamal2367.tinyppimobile.util

import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.Frame
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.InfoRowData
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The corners of the small helpers the screens lean on. */
class UtilEdgeTest {

    @Test
    fun `a clock with a part that is not a count reads as nothing`() {
        assertNull(Formatters.clockSeconds("a:00"))
        assertNull(Formatters.clockSeconds("-1:00"))
        assertEquals(61.0, Formatters.clockSeconds("1:01")!!, 0.0)
    }

    @Test
    fun `a whole number is left as it is`() {
        assertEquals("5", Formatters.trimmed(5.0, 0))
        assertEquals("1500 nits", Formatters.expandThousands("1.5k nits"))
    }

    @Test
    fun `fanart is asked for by its own tag`() {
        val box = ServerConfig(enabled = true, host = "h", port = 1)
        assertEquals("f", MediaUrls.ArtKind.FANART.tagOf(ArtTags(poster = "p", fanart = "f")))
        assertEquals(
            "http://h:1/api/art?kind=fanart&v=f",
            MediaUrls.art(box, ArtTags(fanart = "f"), MediaUrls.ArtKind.FANART),
        )
    }

    @Test
    fun `a portrait is asked for by its tag alone`() {
        val box = ServerConfig(enabled = true, host = "h", port = 1)
        assertEquals(
            "http://h:1/api/art?kind=thumb&portrait=0123456789abcdef&v=0123456789abcdef",
            MediaUrls.portrait(box, "0123456789abcdef"),
        )
        assertNull(MediaUrls.portrait(box, ""))
        assertNull(MediaUrls.portrait(null, "0123456789abcdef"))
    }

    @Test
    fun `the playing title's details ask for its pictures without an id`() {
        val box = ServerConfig(enabled = true, host = "h", port = 1)
        val playing = MarkTarget(MarkTarget.Kind.MOVIE, 0, "Tagesschau")
        assertEquals("http://h:1/api/art?kind=poster&v=p1", MediaUrls.details(box, playing, "poster", "p1"))
        assertNull(MediaUrls.details(box, playing, "poster", ""))
        assertNull(MediaUrls.details(null, playing, "poster", "p1"))
        assertEquals(
            "http://h:1/api/art?kind=poster&movieid=3&v=p1",
            MediaUrls.details(box, MarkTarget(MarkTarget.Kind.MOVIE, 3, "Heat"), "poster", "p1"),
        )
    }

    @Test
    fun `the heaviest hue wins whichever bucket it sits in`() {
        val red = 0xFFE00000.toInt()
        val green = 0xFF00E000.toInt()
        val blue = 0xFF0000E0.toInt()
        val redFirst = ArtworkPalette.accent(IntArray(10) { red } + IntArray(2) { green })!!
        val greenLater = ArtworkPalette.accent(IntArray(2) { red } + IntArray(10) { blue })!!
        assertEquals(0f, ArtworkPalette.toHsv(redFirst)[0], 1f)
        assertEquals(240f, ArtworkPalette.toHsv(greenLater)[0], 1f)
    }

    @Test
    fun `every sixth of the wheel converts back`() {
        for (hue in listOf(30f, 90f, 150f, 210f, 270f, 330f)) {
            val colour = ArtworkPalette.fromHsv(hue, 1f, 1f)
            assertEquals(hue, ArtworkPalette.toHsv(colour)[0], 1f)
        }
    }

    private fun audio(vararg rows: InfoRowData) = Snapshot(groups = listOf(InfoGroup(id = "audio", rows = rows.toList())))

    @Test
    fun `sound badges read the codec row or the first one`() {
        assertEquals(emptyList<String>(), SourceLabel.soundBadges(Snapshot()))
        assertEquals(emptyList<String>(), SourceLabel.soundBadges(audio()))
        assertEquals(
            listOf("TrueHD", "Atmos", "7.1"),
            SourceLabel.soundBadges(
                audio(InfoRowData(id = "x", value = "N/A"), InfoRowData(id = "audio.32045", value = "TrueHD 7.1", detail = "(Atmos)")),
            ),
        )
        assertEquals(listOf("DTS"), SourceLabel.soundBadges(audio(InfoRowData(id = "x", value = " DTS "))))
        assertEquals(emptyList<String>(), SourceLabel.soundBadges(audio(InfoRowData(id = "audio.32045", value = "N/A"))))
        assertEquals(listOf("5.1"), SourceLabel.soundBadges(audio(InfoRowData(id = "audio.32045", value = "5.1"))))
    }

    @Test
    fun `a resolution is named by the width`() {
        assertNull(SourceLabel.resolution(null))
        assertNull(SourceLabel.resolution(Frame(0, 0)))
        assertEquals("UHD", SourceLabel.resolution(Frame(3840, 1608)))
        assertEquals("SD", SourceLabel.resolution(Frame(720, 576)))
    }

    @Test
    fun `a layer without a profile is still a badge`() {
        val snapshot = Snapshot(groups = listOf(InfoGroup(rows = listOf(InfoRowData(label = "Layers", value = "BL+EL")))))
        assertEquals("EL", SourceLabel.dolbyVisionSuffix(snapshot))
        val asked = Snapshot(groups = listOf(InfoGroup(rows = listOf(InfoRowData(label = "Has EL", value = "yes")))))
        assertEquals("EL", SourceLabel.dolbyVisionSuffix(asked))
        val other = Snapshot(groups = listOf(InfoGroup(rows = listOf(InfoRowData(label = "Has nothing", value = "yes")))))
        assertNull(SourceLabel.dolbyVisionSuffix(other))
    }
}
