package com.jamal2367.tinyppimobile.remote

import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.SnapshotStream
import com.jamal2367.tinyppimobile.data.remote.StreamEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** One connection to `/api/stream`, from the frames the box sends to the events the app sees. */
class SnapshotStreamTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val stream = SnapshotStream(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), json)

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun box(token: String = "") = ServerConfig(enabled = true, host = server.hostName, port = server.port, token = token)

    private fun events(token: String = ""): List<StreamEvent> = runBlocking {
        withTimeout(10_000) { stream.connect(box(token)).toList() }
    }

    private fun sse(vararg frames: Pair<String, String>) = server.enqueue(
        MockResponse.Builder()
            .setHeader("Content-Type", "text/event-stream")
            .body(frames.joinToString("") { (type, data) -> "event: $type\ndata: $data\n\n" })
            .build(),
    )

    @Test
    fun `a whole frame, its deltas and a parting word`() {
        sse(
            "delta" to """{"set":{"title":"early"}}""",
            "state" to """{"seq":1,"playing":true,"title":"Dune"}""",
            "delta" to """{"seq":2,"set":{"time":"0:00:01"}}""",
            "delta" to "not json",
            "state" to "not json",
            "state" to """{"playing":[1]}""",
            "other" to "{}",
            "bye" to """{"retry_ms":5000}""",
            "bye" to """{"retry_ms":"soon"}""",
            "bye" to """{"retry_ms":{}}""",
            "bye" to "{}",
            "bye" to "not json",
        )
        val events = events(" AB ")
        assertEquals(StreamEvent.Connected, events[0])
        val states = events.filterIsInstance<StreamEvent.State>()
        assertEquals(listOf("Dune", "Dune"), states.map { it.snapshot.title })
        assertEquals("0:00:01", states[1].snapshot.time)
        assertEquals(
            listOf(5000L, 20_000L, 20_000L, 20_000L, 20_000L),
            events.filterIsInstance<StreamEvent.GoingAway>().map { it.retryMillis },
        )
        val request = server.takeRequest()
        assertEquals("/api/stream", request.url.encodedPath)
        assertEquals("AB", request.headers["X-TinyPPI-Token"])
        assertEquals("text/event-stream", request.headers["Accept"])
    }

    @Test
    fun `a box without a token is asked without one`() {
        sse()
        events()
        assertNull(server.takeRequest().headers["X-TinyPPI-Token"])
    }

    @Test
    fun `refusals say why, and anything else just ends`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        assertEquals(listOf(StreamEvent.Unauthorized), events())
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(listOf(StreamEvent.Busy), events())
        server.enqueue(MockResponse.Builder().code(500).build())
        assertEquals(emptyList<StreamEvent>(), events())
        server.close()
        assertEquals(emptyList<StreamEvent>(), events())
    }
}
