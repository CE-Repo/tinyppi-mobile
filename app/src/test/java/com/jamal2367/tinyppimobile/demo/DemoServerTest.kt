package com.jamal2367.tinyppimobile.demo

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The pretend box debug builds carry, asked over HTTP the way the app asks it.
 *
 * One test, because the box is one: what a command changes is what the next
 * answer says, and the order the commands go in is the story being checked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoServerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val base = "http://127.0.0.1:8099"

    private fun awaitBox() {
        DemoServer.start(context)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { Socket("127.0.0.1", 8099).close() }.isSuccess) return
            Thread.sleep(20)
        }
        error("demo box did not start")
    }

    private fun get(path: String): Pair<Int, String> =
        client.newCall(Request.Builder().url("$base$path").build()).execute().use { it.code to it.body.string() }

    private fun json(path: String): JsonObject = Json.parseToJsonElement(get(path).second).jsonObject

    private fun post(path: String, body: String = "{}"): JsonObject = client.newCall(
        Request.Builder().url("$base$path").post(body.toRequestBody("application/json".toMediaType())).build(),
    ).execute().use { Json.parseToJsonElement(it.body.string()).jsonObject }

    private fun command(action: String, value: Any? = null) =
        post("/api/command", if (value == null) """{"action":"$action"}""" else """{"action":"$action","value":$value}""")

    private fun state() = json("/api/state")

    private fun raw(request: String): String = Socket("127.0.0.1", 8099).use { socket ->
        socket.soTimeout = 5_000
        socket.getOutputStream().apply { write(request.toByteArray()); flush() }
        socket.shutdownOutput()
        socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `the pretend box answers everything the app asks`() {
        awaitBox()

        // A second start is a no-op, and one that finds the port taken says so.
        DemoServer.start(context)
        DemoServer::class.java.getDeclaredField("started").apply { isAccessible = true }.setBoolean(DemoServer, false)
        DemoServer.start(context)
        Thread.sleep(200)

        val hello = json("/api/hello")
        assertEquals("TinyPPI Demo", hello["name"]!!.jsonPrimitive.content)

        // Playing: a film, with every card.
        var snapshot = state()
        assertTrue(snapshot["playing"]!!.jsonPrimitive.boolean)
        assertEquals("DV-LL", snapshot["vs10"]!!.jsonObject["output"]!!.jsonPrimitive.content)

        // The library and its rows.
        assertEquals(24, json("/api/library")["count"]!!.jsonPrimitive.int)
        assertEquals(9, json("/api/series")["count"]!!.jsonPrimitive.int)
        assertEquals(18, json("/api/episodes?tvshowid=101")["count"]!!.jsonPrimitive.int)
        assertEquals(24, json("/api/episodes?tvshowid=102&x")["count"]!!.jsonPrimitive.int.let { it + 8 })
        assertEquals("unknown show", json("/api/episodes?tvshowid=1")["error"]!!.jsonPrimitive.content)
        assertEquals("unknown show", json("/api/episodes")["error"]!!.jsonPrimitive.content)
        val continuing = json("/api/continue")
        assertTrue(continuing["count"]!!.jsonPrimitive.int > 0)
        assertTrue(json("/api/history")["events"]!!.jsonArray.size == 5)
        assertEquals(404, get("/api/nothing").first)

        // The next-up row, the sorting fields, and a title's details of every kind.
        assertTrue(continuing["next"]!!.jsonArray.isNotEmpty())
        assertEquals("Science Fiction", json("/api/library")["movies"]!!.jsonArray.first().jsonObject["genres"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("Dune: Part Two", json("/api/details?movieid=1")["title"]!!.jsonPrimitive.content)
        assertEquals("fanart-1", json("/api/details?movieid=1")["fanart"]!!.jsonPrimitive.content)
        // Who each played, where the pretend library knows, and nobody's role where it does not.
        val role = { id: Int -> json("/api/details?movieid=$id")["cast"]!!.jsonArray.first().jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals("Paul Atreides", role(1))
        assertEquals("", role(2))
        assertEquals("tvshow", json("/api/details?tvshowid=101")["kind"]!!.jsonPrimitive.content)
        assertEquals("Severance", json("/api/details?episodeid=101102")["show"]!!.jsonPrimitive.content)
        assertEquals("1080p", json("/api/details?movieid=3")["resolution"]!!.jsonPrimitive.content)
        assertEquals("8.1", json("/api/details?movieid=8")["dv_profile"]!!.jsonPrimitive.content)
        for (unknown in listOf("movieid=999", "movieid=x", "tvshowid=1", "tvshowid=x", "episodeid=5", "episodeid=x", "")) {
            assertEquals(unknown, 404, get("/api/details?$unknown").first)
        }

        // Pictures of every kind, drawn once and then served again.
        for (query in listOf(
            "kind=poster&v=movie-1", "kind=poster&v=movie-1", "kind=poster&v=movie-21",
            "kind=fanart&v=fanart-101", "kind=thumb&v=thumb-101102", "kind=poster&v=show-103",
            "kind=poster&v=movie-999", "kind=poster&v=other-1", "kind=poster&v=show-999", "kind=fanart&v=fanart-999", "kind=poster&v=plain", "v=x", "",
        )) {
            assertEquals(query, 200, get("/api/art?$query").first)
        }

        // VS10 and the transport.
        for ((mode, output) in listOf("hdr10" to "HDR10", "sdr8" to "SDR", "off" to "DV-LL")) {
            post("/api/mode", """{"mode":"$mode"}""")
            assertEquals(output, state()["vs10"]!!.jsonObject["output"]!!.jsonPrimitive.content)
        }
        post("/api/mode")
        command("playpause")
        assertTrue(state()["paused"]!!.jsonPrimitive.boolean)
        command("playpause")
        command("seek", 30)
        command("seek")
        command("seek", "\"x\"")
        command("seek_percent", 50)
        command("seek_percent")
        // The chapters: twelve named ones for the film, stepped through and jumped to.
        var chapters = state()["chapters"]!!.jsonArray
        assertEquals(12, chapters.size)
        assertEquals("Arrakis", chapters[1].jsonObject["name"]!!.jsonPrimitive.content)
        command("chapter_goto", 12)
        assertEquals(12, state()["chapter"]!!.jsonPrimitive.int)
        command("chapter_next")
        assertEquals(12, state()["chapter"]!!.jsonPrimitive.int)
        command("chapter_goto", 99)
        command("chapter_goto", 1)
        assertEquals(1, state()["chapter"]!!.jsonPrimitive.int)
        command("chapter_previous")
        assertEquals(1, state()["chapter"]!!.jsonPrimitive.int)
        command("chapter_next")
        assertEquals(2, state()["chapter"]!!.jsonPrimitive.int)
        command("chapter_previous")
        command("chapter_goto")
        command("seek_percent", 100)
        assertEquals(12, state()["chapter"]!!.jsonPrimitive.int)
        repeat(7) { command("volume_up") }
        repeat(22) { command("volume_down") }
        command("mute")
        command("mute")
        command("audio", 1)
        command("audio")
        command("subtitle", 2)
        command("subtitle", -1)
        command("subtitle")
        command("audio_delay", 0.05)
        command("subtitle_delay", -0.2)
        var fine = state()["controls"]!!.jsonObject
        assertEquals(0.05, fine["audio_delay"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(-0.2, fine["subtitle_delay"]!!.jsonPrimitive.content.toDouble(), 0.0)
        command("audio_delay")
        command("subtitle_delay")
        command("playpause")
        fine = state()["controls"]!!.jsonObject
        assertEquals(0.0, fine["audio_delay"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(0.0, fine["subtitle_delay"]!!.jsonPrimitive.content.toDouble(), 0.0)
        command("playpause")
        command("unknown")
        post("/api/command", "not json")
        snapshot = state()
        val controls = snapshot["controls"]!!.jsonObject
        assertEquals(0, controls["volume"]!!.jsonPrimitive.int)
        assertFalse(controls["subtitle_on"]!!.jsonPrimitive.boolean)

        // Marks and resume points.
        post("/api/watched", """{"movieid":1,"watched":true}""")
        post("/api/watched", """{"episodeid":101101,"watched":false}""")
        post("/api/watched", """{"tvshowid":101,"watched":true}""")
        post("/api/watched", """{"tvshowid":9999,"watched":true}""")
        post("/api/watched", """{"movieid":5}""")
        post("/api/unknown")
        post("/api/resume", """{"movieid":2}""")
        post("/api/resume", """{"movieid":999}""")
        post("/api/resume")
        // What the marks did to the shelves and the row.
        json("/api/library")
        json("/api/series")
        json("/api/episodes?tvshowid=101")
        json("/api/continue")

        // Starting things off the shelves.
        post("/api/play", """{"movieid":1}""")
        assertEquals("Long live the fighters.", state()["media"]!!.jsonObject["tagline"]!!.jsonPrimitive.content)
        val rated = state()["media"]!!.jsonObject
        assertEquals("imdb", rated["ratings"]!!.jsonArray[0].jsonObject["source"]!!.jsonPrimitive.content)
        assertEquals(612_000, rated["ratings"]!!.jsonArray[0].jsonObject["votes"]!!.jsonPrimitive.int)
        // Rated as MDBList rates it, every house it gathers.
        assertEquals(
            listOf("imdb", "tmdb", "trakt", "letterboxd", "metacritic", "rottentomatoes", "popcorn"),
            rated["ratings"]!!.jsonArray.map { it.jsonObject["source"]!!.jsonPrimitive.content },
        )
        assertEquals("FSK 12", rated["mpaa"]!!.jsonPrimitive.content)
        assertEquals("fsk12", rated["age"]!!.jsonPrimitive.content)
        assertEquals("12", rated["age_level"]!!.jsonPrimitive.content)
        // And who made it, as the add-on sends it: nothing cut.
        assertEquals("Denis Villeneuve", rated["directors"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(null, rated["directors_more"])
        assertEquals(10, rated["cast"]!!.jsonArray.size)
        assertEquals(false, rated["cast_more"]!!.jsonPrimitive.boolean)
        assertEquals("Legendary Pictures", rated["studios"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(null, rated["studios_more"])
        assertEquals("Science Fiction", rated["genres"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Chani", rated["roles"]!!.jsonArray[1].jsonPrimitive.content)
        assertEquals("Jon Spaihts", rated["writers"]!!.jsonArray[1].jsonPrimitive.content)
        assertEquals(9, rated["userrating"]!!.jsonPrimitive.int)
        assertEquals(27, rated["top250"]!!.jsonPrimitive.int)
        post("/api/play", """{"movieid":3,"resume":false}""")
        // A film the pretend library says nothing about, nor rates for its age.
        assertEquals("", state()["media"]!!.jsonObject["plot"]!!.jsonPrimitive.content)
        assertEquals(0, state()["media"]!!.jsonObject["cast"]!!.jsonArray.size)
        // Rated as a scraper leaves a film.
        assertEquals(2, state()["media"]!!.jsonObject["ratings"]!!.jsonArray.size)
        assertEquals("", state()["media"]!!.jsonObject["age"]!!.jsonPrimitive.content)
        post("/api/play", """{"movieid":6}""")
        post("/api/play", """{"movieid":999}""")
        post("/api/play", """{"episodeid":102203}""")
        assertEquals("2", state()["media"]!!.jsonObject["season"]!!.jsonPrimitive.content)
        // An episode: a plot and no slogan, its own score and its show's age rating.
        assertEquals("", state()["media"]!!.jsonObject["tagline"]!!.jsonPrimitive.content)
        assertEquals("tvdb", state()["media"]!!.jsonObject["ratings"]!!.jsonArray.single().jsonObject["source"]!!.jsonPrimitive.content)
        assertEquals("TV-MA", state()["media"]!!.jsonObject["mpaa"]!!.jsonPrimitive.content)
        assertEquals("tv_ma", state()["media"]!!.jsonObject["age"]!!.jsonPrimitive.content)
        assertEquals("MA", state()["media"]!!.jsonObject["age_level"]!!.jsonPrimitive.content)
        assertEquals("Sam Guest", state()["media"]!!.jsonObject["cast"]!!.jsonArray.first().jsonPrimitive.content)
        assertTrue(state()["media"]!!.jsonObject["plot"]!!.jsonPrimitive.content.startsWith("Season 2, episode 3"))
        chapters = state()["chapters"]!!.jsonArray
        assertEquals(6, chapters.size)
        assertEquals("", chapters[0].jsonObject["name"]!!.jsonPrimitive.content)
        post("/api/play", """{"episodeid":5}""")
        post("/api/play")

        // Stopped: the last title, and no cards.
        command("stop")
        snapshot = state()
        assertFalse(snapshot["playing"]!!.jsonPrimitive.boolean)
        assertNull(snapshot["title"])
        assertEquals("Dune: Part Two", snapshot["last"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        post("/api/play", """{"movieid":2}""")
        DemoServer.reset()
        assertEquals("Dune: Part Two", state()["title"]!!.jsonPrimitive.content)

        // The stream sends frames until the app hangs up.
        Socket("127.0.0.1", 8099).use { socket ->
            socket.getOutputStream().write("GET /api/stream HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val input: InputStream = socket.getInputStream()
            val seen = StringBuilder()
            while (!seen.contains("event: state")) seen.append(input.read().toChar())
        }
        // Long enough for the box to wake, write into the closed socket and give up.
        Thread.sleep(2_500)

        // Requests the app would never send, answered or dropped without fuss.
        assertTrue(raw("GET /api/hello HTTP/1.1\r\nbroken header\r\n: empty name\r\n\r\n").contains("TinyPPI Demo"))
        assertTrue(raw("GET /api/hello HTTP/1.1\nHost: x\n\n").contains("200 OK"))
        assertTrue(raw("POST /api/command HTTP/1.1\r\nContent-Length: 50\r\n\r\n{}").contains("\"ok\":true"))
        assertTrue(raw("POST /api/command HTTP/1.1\r\nContent-Length: x\r\n\r\n").contains("\"ok\":true"))
        assertTrue(raw("GET").contains("404"))
        assertEquals("", raw(""))
        assertTrue(raw("GET /api/hello HTTP/1.1").contains("TinyPPI Demo"))
        // One that is torn down mid-request is dropped.
        Socket("127.0.0.1", 8099).use { socket ->
            socket.getOutputStream().apply { write("GET /api/state HTTP/1.1\r\n".toByteArray()); flush() }
            Thread.sleep(200)
            socket.setSoLinger(true, 0)
        }
        Thread.sleep(300)
        // A request that hangs up before its headers end is answered with what it had.
        assertTrue(raw("GET /api/state HTTP/1.1\r\nHost: x").contains("\"seq\""))
    }

    @Test
    fun `the provider starts the box and answers nothing itself`() {
        val starter = DemoServerStarter()
        assertTrue(starter.onCreate())
        starter.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "x.demo" })
        val uri = android.net.Uri.parse("content://x")
        assertNull(starter.query(uri, null, null, null, null))
        assertNull(starter.getType(uri))
        assertNull(starter.insert(uri, null))
        assertEquals(0, starter.delete(uri, null, null))
        assertEquals(0, starter.update(uri, null, null, null))
    }
}
