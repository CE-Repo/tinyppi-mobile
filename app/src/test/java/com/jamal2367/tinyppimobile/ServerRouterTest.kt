package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ReachabilityMemory
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which address is tried first.
 *
 * The whole point of the note the router keeps: a cold start away from home
 * would otherwise open by asking the local address, which is not on this
 * network and cannot say so - the request sits there for the connect timeout
 * with an empty screen behind it, and only then is the one that works tried.
 */
class ServerRouterTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099)
    private val remote = ServerConfig(enabled = true, host = "tinyppi.example.com", port = 8099)

    private val settings = AppSettings(
        primary = local,
        secondary = remote,
        connectionMode = ConnectionMode.AUTO,
    )

    private class FakeMemory(var stored: String? = null) : ReachabilityMemory {
        var forgotten = false
        override fun lastReachable(): String? = stored
        override fun remember(baseUrl: String) {
            stored = baseUrl
        }

        override fun forget() {
            stored = null
            forgotten = true
        }
    }

    @Test
    fun `with nothing remembered the order is the configured one`() {
        val router = ServerRouter(FakeMemory())
        router.update(settings)

        assertEquals(listOf(local, remote), router.candidates())
    }

    @Test
    fun `the note from the last session is tried first`() {
        val router = ServerRouter(FakeMemory(stored = remote.baseUrl))
        router.update(settings)

        assertEquals(listOf(remote, local), router.candidates())
    }

    @Test
    fun `what answered in this session outranks the note`() {
        val router = ServerRouter(FakeMemory(stored = remote.baseUrl))
        router.update(settings)
        router.markReachable(local)

        assertEquals(listOf(local, remote), router.candidates())
        assertEquals(local, router.activeServer.value?.config)
        assertEquals(true, router.activeServer.value?.isPrimary)
    }

    @Test
    fun `a note about an address that is no longer configured is ignored`() {
        val router = ServerRouter(FakeMemory(stored = "http://10.0.0.99:8099"))
        router.update(settings)

        assertEquals(listOf(local, remote), router.candidates())
    }

    @Test
    fun `the first configuration is not somebody changing an address`() {
        // It is the stored settings arriving for the first time, and the note
        // it replaces the defaults with is exactly what the first request was
        // meant to follow.
        val memory = FakeMemory(stored = remote.baseUrl)
        val router = ServerRouter(memory)
        router.update(settings)

        assertEquals(false, memory.forgotten)
        assertEquals(listOf(remote, local), router.candidates())
    }

    @Test
    fun `a changed address throws away what was learned about the old one`() {
        val memory = FakeMemory()
        val router = ServerRouter(memory)
        router.update(settings)
        router.markReachable(local)

        router.update(settings.copy(primary = local.copy(host = "192.168.1.20")))

        assertNull(router.activeServer.value)
        assertEquals(true, memory.forgotten)
    }

    @Test
    fun `nothing answering leaves the note alone`() {
        // It is only ever an order to try things in, and when neither address
        // answers there is no better one to fall back to.
        val memory = FakeMemory(stored = remote.baseUrl)
        val router = ServerRouter(memory)
        router.update(settings)
        router.markUnreachable()

        assertNull(router.activeServer.value)
        assertEquals(remote.baseUrl, memory.stored)
    }

    @Test
    fun `nothing configured is an empty list rather than a guess`() {
        val router = ServerRouter(FakeMemory())
        router.update(AppSettings())

        assertEquals(emptyList<ServerConfig>(), router.candidates())
    }
}
