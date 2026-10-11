package com.jamal2367.tinyppimobile.remote

import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.FailoverInterceptor
import com.jamal2367.tinyppimobile.data.remote.NoServerConfiguredException
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import com.jamal2367.tinyppimobile.data.remote.ServerUnreachableException
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Which box a request ends up at.
 *
 * The network is stood in for by an interceptor after the failover one: it
 * answers for the hosts that are "up" and fails for the rest, so every path is
 * taken without a socket.
 */
class FailoverInterceptorTest {

    private val local = ServerConfig(enabled = true, host = "local", port = 8099, token = "LOCAL")
    private val remote = ServerConfig(enabled = true, host = "remote", port = 443, useHttps = true)

    private val seen = mutableListOf<Request>()

    private fun client(router: ServerRouter, network: Interceptor.Chain.() -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(FailoverInterceptor(router))
            .addInterceptor { chain -> seen += chain.request(); chain.network() }
            .build()

    private fun Interceptor.Chain.answer(): Response = Response.Builder()
        .request(request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("ok".toResponseBody()).build()

    private fun router(settings: AppSettings) = ServerRouter().apply { update(settings) }

    private fun get(client: OkHttpClient) =
        client.newCall(Request.Builder().url("http://localhost/api/state").build()).execute()

    @Test
    fun `nothing configured is its own failure`() {
        val client = client(router(AppSettings(primary = ServerConfig()))) { answer() }
        try {
            get(client)
            fail()
        } catch (_: NoServerConfiguredException) {
        }
    }

    @Test
    fun `the first box that answers takes the request, with its token`() {
        val router = router(AppSettings(primary = local, secondary = remote))
        get(client(router) { answer() }).close()
        assertEquals("http://local:8099/api/state", seen.single().url.toString())
        assertEquals("LOCAL", seen.single().header("X-TinyPPI-Token"))
        assertEquals(local, router.activeServer.value?.config)
    }

    @Test
    fun `a box that does not answer hands over to the next one, without a token`() {
        val router = router(AppSettings(primary = local, secondary = remote))
        val timeouts = mutableListOf<Int>()
        val client = client(router) {
            timeouts += connectTimeoutMillis()
            if (request().url.host == "local") throw IOException("no route")
            answer()
        }
        get(client).close()
        // A short leash for the box with another behind it, the whole timeout for the last.
        assertEquals(listOf(2_000, 10_000), timeouts)
        assertEquals(listOf("local", "remote"), seen.map { it.url.host })
        assertNull(seen.last().header("X-TinyPPI-Token"))
        assertEquals(remote, router.activeServer.value?.config)
    }

    @Test
    fun `when nothing answers every box is named`() {
        val router = router(AppSettings(primary = local, secondary = remote))
        router.markReachable(local)
        try {
            get(client(router) { throw IOException("down") })
            fail()
        } catch (e: ServerUnreachableException) {
            assertEquals(listOf(local, remote), e.attempted)
            assertEquals("down", e.message)
        }
        assertNull(router.activeServer.value)
    }

    @Test
    fun `a cancelled call stops at once`() {
        val router = router(AppSettings(primary = local, secondary = remote))
        val failure = IOException("cancelled")
        val client = client(router) {
            call().cancel()
            throw failure
        }
        try {
            get(client)
            fail()
        } catch (e: IOException) {
            assertTrue(e === failure || e.cause === failure || e.message == "Canceled")
        }
        assertEquals(1, seen.size)
    }
}
