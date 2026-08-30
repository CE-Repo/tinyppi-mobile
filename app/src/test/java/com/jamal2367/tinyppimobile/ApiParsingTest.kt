package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.PlaybackEventKind
import com.jamal2367.tinyppimobile.data.model.Snapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the add-on's answers read as the models this app is built on.
 *
 * The two states the API answers with are not the same size - a stopped player
 * sends five keys, a playing one sends twenty - and the reason every field
 * carries a default is that a screen must not have to know which it is holding.
 * These are the shapes `SnapshotBuilder.build` actually produces.
 */
class ApiParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `a stopped player parses, five keys and all`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """
            {"seq":42,"playing":false,"groups":[],"metrics":{},
             "vs10":{"options":[],"output":""},
             "session":{"seq":0,"switches":0,"warnings":0},"last":{}}
            """.trimIndent(),
        )

        assertFalse(snapshot.playing)
        assertTrue(snapshot.groups.isEmpty())
        assertTrue(snapshot.metrics.l1.isEmpty)
        assertFalse(snapshot.last.isPresent)
        assertFalse(snapshot.isConverting)
    }

    @Test
    fun `a title that has just ended is read off the last block`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """
            {"seq":9,"playing":false,"last":{"title":"Dune","position":"2:35:12",
             "ago":41,"switches":3,"warnings":1,"peak":4000.0,"events":12}}
            """.trimIndent(),
        )

        assertTrue(snapshot.last.isPresent)
        assertEquals("Dune", snapshot.last.title)
        assertEquals(4000.0, snapshot.last.peak!!, 0.001)
        assertEquals(41, snapshot.last.ago)
    }

    @Test
    fun `a playing snapshot carries its cards, badges and controls`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """
            {"seq":7,"playing":true,"paused":false,"title":"Dune","filename":"/media/dune.mkv",
             "hdr_type":"dolbyvision","effective":"dolbyvision","output_type":"hdr10",
             "time":"0:12:03","duration":"2:35:00",
             "metrics":{"l1":{"min":0.0,"max":1200.5,"avg":98.0},"bars":[0.0,0.0,138.0,138.0],
                        "frame":{"w":3840,"h":2160},"aspect":2.39,"fps_in":23.976,"fps_drop":0.0,
                        "fps_out":23.976,"progress":7.8,"cpu":31.0,"cpu_temp":58.0,
                        "memory":42.0,"cache":100.0},
             "groups":[{"id":"video","title":"Video","rows":[
                {"id":"video.1","label":"Display mode","value":"3840x2160p23","detail":""}]}],
             "vs10":{"options":[{"mode":"sdr8","label":"Dolby Vision → SDR"}],"output":"DV-LL"},
             "logos":{"video":"codecs/Dolby_Vision.png","audio":"codecs/Dolby_TrueHD_Atmos.png"},
             "art":{"poster":"1a2b3c4d","fanart":""},
             "media":{"year":"2021","genre":"Science Fiction","show":"","season":"","episode":""},
             "controls":{"audio":[{"index":0,"label":"ENG · TrueHD 7.1"}],"subtitle":[],
                         "audio_current":0,"subtitle_current":-1,"subtitle_on":false,
                         "volume":72,"muted":false},
             "session":{"seq":4,"switches":2,"warnings":0},
             "control":true,"streams_full":false}
            """.trimIndent(),
        )

        assertTrue(snapshot.playing)
        assertEquals("dolbyvision", snapshot.sourceType)
        // Dolby Vision in, HDR10 out - which is the whole point of the badge.
        assertTrue(snapshot.isConverting)
        assertEquals(1, snapshot.groups.size)
        assertEquals("Display mode", snapshot.groups[0].rows[0].label)
        assertEquals(1200.5, snapshot.metrics.l1.max!!, 0.001)
        assertEquals(72, snapshot.controls.volume)
        assertTrue(snapshot.control)

        val area = snapshot.metrics.activeArea!!
        assertTrue(area.isLetterboxed)
        assertFalse(area.isPillarboxed)
    }

    @Test
    fun `an incomplete set of L5 offsets is no active area at all`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """{"playing":true,"metrics":{"bars":[0.0,0.0]}}""",
        )
        assertNull(snapshot.metrics.activeArea)
    }

    @Test
    fun `an episode names itself the way an episode is named`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """{"playing":true,"media":{"show":"Severance","season":"2","episode":"7"}}""",
        )
        assertEquals("S02E07", snapshot.media.episodeLabel)
    }

    @Test
    fun `a film is not an episode of anything`() {
        val snapshot = json.decodeFromString(
            Snapshot.serializer(),
            """{"playing":true,"media":{"year":"2021"}}""",
        )
        assertNull(snapshot.media.episodeLabel)
    }

    @Test
    fun `hello says what the box will and will not allow`() {
        val hello = json.decodeFromString(
            Hello.serializer(),
            """
            {"name":"TinyPPI","version":"3.1.0","auth_read":true,"control":false,
             "interval_ms":200,"strings":{"connected":"Verbunden"}}
            """.trimIndent(),
        )

        assertEquals("3.1.0", hello.version)
        assertTrue(hello.authRead)
        assertFalse(hello.control)
        assertEquals(200, hello.intervalMs)
        assertEquals("Verbunden", hello.strings["connected"])
    }

    @Test
    fun `history keeps its gaps as gaps rather than as zeroes`() {
        val history = json.decodeFromString(
            History.serializer(),
            """
            {"now":12.0,"step":1.0,"t":[0.0,1.0,2.0],"max":[100.0,null,300.0],
             "avg":[10.0,20.0,30.0],"events":[],"seq":0,"switches":0}
            """.trimIndent(),
        )

        // A sample nothing was read for is left out of the line rather than
        // drawn as a fall to black.
        val peak = history.series { it.max }
        assertEquals(2, peak.size)
        assertEquals(0.0, peak[0].first, 0.001)
        assertEquals(300.0, peak[1].second, 0.001)
        assertEquals(300.0, history.peak!!, 0.001)
    }

    @Test
    fun `a transition reads as text whichever type its two sides arrived as`() {
        val history = json.decodeFromString(
            History.serializer(),
            """
            {"t":[],"max":[],"avg":[],"events":[
              {"t":4.2,"pos":"0:00:04","kind":"vs10","from":"SDR BT.709","to":"DV-LL BT.2020nc"},
              {"t":9.9,"pos":"0:00:09","kind":"fps","from":24,"to":60},
              {"t":30.0,"pos":"0:00:30","kind":"temperature","value":78.0},
              {"t":44.0,"pos":"0:00:44","kind":"subtitle","from":"DEU","to":"__off__"}
            ]}
            """.trimIndent(),
        )

        val (vs10, fps, temperature, subtitle) = history.events

        assertEquals(PlaybackEventKind.VS10, vs10.eventKind)
        assertEquals("DV-LL BT.2020nc", vs10.to)
        assertTrue(vs10.isTransition)

        // The same two keys carry numbers for a frame rate; they are only ever
        // printed, so they arrive as their own text.
        assertEquals("24", fps.from)
        assertEquals("60", fps.to)

        assertEquals(78.0, temperature.value!!, 0.001)
        assertFalse(temperature.isTransition)
        assertTrue(temperature.eventKind!!.isWarning)

        assertEquals(PlaybackEventKind.SUBTITLES_OFF, subtitle.to)
        assertTrue(subtitle.eventKind!!.isSwitch)
    }

    @Test
    fun `an event kind this build has never heard of is still a row`() {
        val history = json.decodeFromString(
            History.serializer(),
            """{"t":[],"max":[],"avg":[],"events":[{"t":1.0,"pos":"0:00:01","kind":"something_new"}]}""",
        )
        assertNull(history.events.first().eventKind)
        assertEquals("something_new", history.events.first().kind)
    }
}
