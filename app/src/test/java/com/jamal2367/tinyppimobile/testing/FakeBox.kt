package com.jamal2367.tinyppimobile.testing

import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import java.util.concurrent.ConcurrentHashMap
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * A box answering over real HTTP with whatever the test puts in [answers],
 * by path. The event stream sends [stream] - one `state` frame per entry -
 * and then hangs up, the way a box restarting would.
 */
class FakeBox : AutoCloseable {
    private val server = MockWebServer()
    val answers = ConcurrentHashMap<String, String>()
    val codes = ConcurrentHashMap<String, Int>()
    @Volatile var stream: List<String> = emptyList()
    /** What `/api/art` answers with: a picture, or a 404 while null. */
    @Volatile var art: ByteArray? = null
    /** Safe to read while the server is still writing to it - a test polls it as requests arrive. */
    val asked: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                asked += "${request.method} $path"
                if (path == "/api/stream") {
                    return MockResponse.Builder()
                        .setHeader("Content-Type", "text/event-stream")
                        .body(stream.joinToString("") { "event: state\ndata: $it\n\n" })
                        .build()
                }
                if (path == "/api/art") {
                    val picture = art ?: return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().setHeader("Content-Type", "image/png").body(okio.Buffer().write(picture)).build()
                }
                val code = codes[path] ?: 200
                val body = answers[path] ?: if (request.method == "POST") """{"ok":true}""" else "{}"
                return MockResponse.Builder().code(code).body(body).build()
            }
        }
        server.start()
    }

    val config: ServerConfig get() = ServerConfig(enabled = true, host = server.hostName, port = server.port)

    override fun close() = server.close()
}
