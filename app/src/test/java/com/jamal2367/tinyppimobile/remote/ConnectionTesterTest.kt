package com.jamal2367.tinyppimobile.remote

import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.remote.ConnectionTestResult
import com.jamal2367.tinyppimobile.data.remote.ConnectionTester
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The button in the settings, against a box that answers each way it can. */
class ConnectionTesterTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val tester = ConnectionTester(OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build(), json)

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun box(token: String = "") =
        ServerConfig(enabled = true, host = server.hostName, port = server.port, token = token)

    private fun reply(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun test(config: ServerConfig = box()) = runBlocking { tester.test(config) }

    @Test
    fun `an open box is reachable without asking for a reading`() {
        reply(200, """{"name":"TinyPPI","auth_read":false}""")
        val result = test(box(" AB ")) as ConnectionTestResult.Reachable
        assertTrue(result.tokenAccepted)
        assertEquals("TinyPPI", result.hello.name)
        assertEquals("AB", server.takeRequest().headers["X-TinyPPI-Token"])
    }

    @Test
    fun `a closed box is asked for a reading with the token`() {
        reply(200, """{"auth_read":true}""")
        reply(200, "{}")
        assertTrue((test() as ConnectionTestResult.Reachable).tokenAccepted)
        assertNull(server.takeRequest().headers["X-TinyPPI-Token"])

        reply(200, """{"auth_read":true}""")
        reply(401, "{}")
        assertFalse((test() as ConnectionTestResult.Reachable).tokenAccepted)
    }

    @Test
    fun `a refusal carries the box's own words, or the status line`() {
        reply(500, """{"error":"broken"}""")
        assertEquals(ApiFailure.Api(500, "broken"), (test() as ConnectionTestResult.Refused).failure)
        for (body in listOf("", "not json", """{"error":""}""")) {
            reply(503, body)
            assertEquals(ApiFailure.Api(503, "Server Error"), (test() as ConnectionTestResult.Refused).failure)
        }
    }

    @Test
    fun `an answer that is not a hello is malformed`() {
        reply(200, "[1]")
        assertEquals(ApiFailure.Malformed(null), (test() as ConnectionTestResult.Refused).failure)
    }

    @Test
    fun `an address that answers nothing is unreachable`() {
        val port = ServerSocket(0).use { it.localPort }
        val result = test(ServerConfig(enabled = true, host = "127.0.0.1", port = port)) as ConnectionTestResult.Refused
        val failure = result.failure as ApiFailure.Unreachable
        assertEquals("127.0.0.1:$port", failure.serverLabel)
        assertFalse(failure.timedOut)
    }

    @Test
    fun `a box that stalls has timed out`() {
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("{}").build())
        val failure = (test() as ConnectionTestResult.Refused).failure as ApiFailure.Unreachable
        assertTrue(failure.timedOut)
    }

    @Test
    fun `a box that stalls on the reading is unreachable`() {
        reply(200, """{"auth_read":true}""")
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("{}").build())
        assertTrue((test() as ConnectionTestResult.Refused).failure is ApiFailure.Unreachable)
    }
}
