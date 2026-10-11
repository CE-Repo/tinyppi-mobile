package com.jamal2367.tinyppimobile.data

import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.BoxTokenInterceptor
import com.jamal2367.tinyppimobile.data.remote.ReachabilityMemory
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored settings, and the router that reads them. */
class SettingsModelTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099)
    private val remote = ServerConfig(enabled = true, host = "box.example.com", port = 443, useHttps = true)

    @Test
    fun `a server is usable only enabled, named and on a real port`() {
        assertTrue(local.isUsable)
        assertFalse(local.copy(enabled = false).isUsable)
        assertFalse(local.copy(host = " ").isUsable)
        assertFalse(local.copy(port = 0).isUsable)
        assertFalse(local.copy(port = 70000).isUsable)
        assertEquals("", ServerConfig().label)
        assertEquals("192.168.1.10:8099", local.label)
        assertEquals(1024..65535, ServerConfig.PORT_RANGE)
    }

    @Test
    fun `cards count as arranged once moved or hidden`() {
        assertFalse(AppSettings().cardsArranged)
        assertTrue(AppSettings(cardOrders = mapOf("live" to listOf("a"))).cardsArranged)
        assertTrue(AppSettings(cardHidden = setOf("live/a")).cardsArranged)
        assertNull(ChartRange.ALL.seconds)
    }

    @Test
    fun `the mode picks which servers are tried`() {
        val both = AppSettings(primary = local, secondary = remote)
        assertEquals(listOf(local, remote), both.servers())
        assertEquals(listOf(local), both.copy(connectionMode = ConnectionMode.PRIMARY_ONLY).servers())
        assertEquals(listOf(remote), both.copy(connectionMode = ConnectionMode.SECONDARY_ONLY).servers())
        assertFalse(AppSettings().isConfigured)
    }

    private class Memory(var stored: String? = null) : ReachabilityMemory {
        var forgets = 0
        override fun lastReachable() = stored
        override fun remember(baseUrl: String) { stored = baseUrl }
        override fun forget() { forgets++; stored = null }
    }

    @Test
    fun `changing any address or the mode forgets what answered`() {
        val base = AppSettings(primary = local, secondary = remote)
        for (changed in listOf(
            base.copy(primary = local.copy(port = 9000)),
            base.copy(secondary = remote.copy(port = 8443)),
            base.copy(connectionMode = ConnectionMode.PRIMARY_ONLY),
        )) {
            val memory = Memory()
            val router = ServerRouter(memory)
            router.update(base)
            router.markReachable(remote)
            router.update(changed)
            assertNull(router.activeServer.value)
            assertEquals(1, memory.forgets)
        }
    }

    @Test
    fun `an unrelated setting keeps what answered`() {
        val memory = Memory()
        val router = ServerRouter(memory)
        val base = AppSettings(primary = local, secondary = remote)
        router.update(base)
        router.markReachable(remote)
        router.update(base.copy(keepScreenOn = true))
        assertEquals(remote, router.activeServer.value?.config)
        assertFalse(router.activeServer.value!!.isPrimary)
        assertEquals(0, memory.forgets)
        router.markUnreachable()
        assertNull(router.activeServer.value)
    }

    @Test
    fun `the note is read once and the remembered server goes first`() {
        val memory = Memory(remote.baseUrl)
        val router = ServerRouter(memory)
        router.update(AppSettings(primary = local, secondary = remote))
        assertEquals(listOf(remote, local), router.candidates())
        memory.stored = local.baseUrl
        assertEquals(listOf(remote, local), router.candidates())
        router.markReachable(local)
        assertTrue(router.activeServer.value!!.isPrimary)
        assertEquals(listOf(local, remote), router.candidates())
    }

    @Test
    fun `a server that is not the primary is never marked primary`() {
        val router = ServerRouter()
        router.update(AppSettings(primary = local.copy(enabled = false), secondary = remote))
        router.markReachable(local)
        assertFalse(router.activeServer.value!!.isPrimary)
        assertNull(ReachabilityMemory.None.lastReachable())
        ReachabilityMemory.None.remember("x")
        ReachabilityMemory.None.forget()
    }

    @Test
    fun `a box with no host never claims an address`() {
        val router = ServerRouter().apply {
            update(AppSettings(primary = ServerConfig(enabled = true, host = "", token = "T"), secondary = remote.copy(token = "R")))
        }
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(BoxTokenInterceptor(router))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        client.newCall(Request.Builder().url("http://box.example.com:443/").build()).execute().close()
        assertNull(sent?.header("X-TinyPPI-Token"))
        client.newCall(Request.Builder().url("https://other.example.com:443/").build()).execute().close()
        assertNull(sent?.header("X-TinyPPI-Token"))
    }
}
