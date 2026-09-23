package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import com.jamal2367.tinyppimobile.data.remote.SnapshotSource
import com.jamal2367.tinyppimobile.data.remote.StreamEvent
import com.jamal2367.tinyppimobile.data.repository.LiveSession
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.LiveState.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The loop every screen's reading comes out of: which address it opens, how
 * long it waits, and what it says while it waits.
 *
 * Run against a made-up box on a virtual clock, so the ten seconds a session
 * backs off for pass in no time and can be counted exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099)
    private val remote = ServerConfig(enabled = true, host = "tinyppi.example.com", port = 443, useHttps = true)

    private val film = Snapshot(playing = true, title = "Blade Runner", control = true)

    /** A stream per address, and the list of addresses asked for in order. */
    private class FakeSource : SnapshotSource {
        val opened = mutableListOf<ServerConfig>()
        var answer: (ServerConfig) -> Flow<StreamEvent> = { emptyFlow() }

        override fun connect(server: ServerConfig): Flow<StreamEvent> {
            opened += server
            return answer(server)
        }
    }

    private class Harness(
        val session: LiveSession,
        val router: ServerRouter,
        val source: FakeSource,
        val api: FakeApi,
        val states: MutableList<LiveState>,
    ) {
        val last: LiveState get() = states.last()
    }

    private fun TestScope.harness(settings: AppSettings): Harness {
        val router = ServerRouter()
        router.update(settings)
        val source = FakeSource()
        val api = FakeApi()
        val session = LiveSession(api, source, router, FakeApi.json)
        val states = mutableListOf<LiveState>()
        backgroundScope.launch { session.states().collect { states += it } }
        return Harness(session, router, source, api, states)
    }

    private fun both() = AppSettings(primary = local, secondary = remote, connectionMode = ConnectionMode.AUTO)

    /** A stream that opens, sends one reading, and stays open. */
    private fun live(snapshot: Snapshot): (ServerConfig) -> Flow<StreamEvent> = { server ->
        flow {
            emit(StreamEvent.Connected)
            emit(StreamEvent.State(snapshot, server))
            awaitCancellation()
        }
    }

    /** A stream that never opens: the address is not on this network. */
    private val unreachable: Flow<StreamEvent> = flow { throw IOException("no route to host") }

    @Test
    fun `nothing filled in says so and opens nothing`() = runTest {
        val h = harness(AppSettings(primary = ServerConfig(enabled = true, host = "")))
        runCurrent()

        assertEquals(Connection.NotConfigured, h.last.connection)
        assertTrue(h.source.opened.isEmpty())
    }

    @Test
    fun `a stream that opens is what the screens read`() = runTest {
        val h = harness(both())
        h.source.answer = live(film)
        runCurrent()

        assertEquals(Connection.Streaming, h.last.connection)
        assertEquals("Blade Runner", h.last.snapshot?.title)
        assertEquals(local, h.last.server)
        assertEquals(local, h.router.activeServer.value?.config)
        assertEquals(listOf(local), h.source.opened)
    }

    @Test
    fun `a local address that never opens hands over to the remote one at once`() = runTest {
        val h = harness(both())
        h.source.answer = { if (it == local) unreachable else live(film)(it) }
        runCurrent()

        assertEquals(listOf(local, remote), h.source.opened)
        assertEquals(Connection.Streaming, h.last.connection)
        assertEquals(remote, h.last.server)
    }

    @Test
    fun `with neither answering it backs off before trying the pair again`() = runTest {
        val h = harness(both())
        h.source.answer = { unreachable }
        runCurrent()

        assertEquals(listOf(local, remote), h.source.opened)
        assertEquals(Connection.Offline, h.last.connection)

        advanceTimeBy(9_999)
        runCurrent()
        assertEquals("not before the back-off is up", 2, h.source.opened.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(local, remote, local, remote), h.source.opened)
    }

    @Test
    fun `a dropped stream comes back after a short pause and keeps the reading meanwhile`() = runTest {
        val h = harness(both())
        var drops = 1
        h.source.answer = { server ->
            if (drops-- > 0) {
                flow {
                    emit(StreamEvent.Connected)
                    emit(StreamEvent.State(film, server))
                }
            } else {
                live(film.copy(title = "Blade Runner 2049"))(server)
            }
        }
        runCurrent()

        assertEquals(Connection.Connecting, h.last.connection)
        assertEquals("the screen keeps what it had", "Blade Runner", h.last.snapshot?.title)

        advanceTimeBy(3_001)
        runCurrent()

        assertEquals(Connection.Streaming, h.last.connection)
        assertEquals("Blade Runner 2049", h.last.snapshot?.title)
        assertEquals("the address that worked is tried again", listOf(local, local), h.source.opened)
    }

    @Test
    fun `every slot taken falls back to asking, and tries the stream again`() = runTest {
        val h = harness(both())
        h.source.answer = { flow { emit(StreamEvent.Busy) } }
        h.api.state = {
            buildJsonObject {
                put("playing", true)
                put("title", JsonPrimitive("Heat"))
            }
        }
        runCurrent()

        assertEquals(Connection.Busy, h.last.connection)
        assertEquals(0, h.api.count("state"))

        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(1, h.api.count("state"))
        assertEquals(Connection.Busy, h.last.connection)
        assertEquals("Heat", h.last.snapshot?.title)
        assertEquals(2, h.source.opened.size)
    }

    @Test
    fun `a refused token is said and waited out, not polled over`() = runTest {
        val h = harness(both())
        h.source.answer = { flow { emit(StreamEvent.Unauthorized) } }
        runCurrent()

        assertEquals(Connection.Unauthorized, h.last.connection)

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(Connection.Unauthorized, h.last.connection)
        assertEquals(0, h.api.count("state"))
        assertEquals(1, h.source.opened.size)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, h.source.opened.size)
    }

    @Test
    fun `a box going down is left alone for as long as it asks`() = runTest {
        val h = harness(both())
        h.source.answer = { flow { emit(StreamEvent.GoingAway(retryMillis = 20_000)) } }
        runCurrent()

        assertEquals(Connection.Offline, h.last.connection)

        advanceTimeBy(19_000)
        runCurrent()
        assertEquals(1, h.source.opened.size)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, h.source.opened.size)
    }

    @Test
    fun `reconnecting skips the back-off`() = runTest {
        val h = harness(both())
        h.source.answer = { unreachable }
        runCurrent()
        assertEquals(2, h.source.opened.size)

        h.source.answer = live(film)
        h.session.restart()
        runCurrent()

        assertEquals("tried at once rather than ten seconds on", 3, h.source.opened.size)
        assertEquals(Connection.Streaming, h.last.connection)
    }

    @Test
    fun `with live updates off it asks on the configured interval`() = runTest {
        val h = harness(both().copy(liveUpdates = false, pollIntervalSeconds = 5))
        h.api.state = { buildJsonObject { put("playing", true) } }
        runCurrent()

        assertEquals(1, h.api.count("state"))
        assertEquals(Connection.Polling, h.last.connection)
        assertTrue(h.source.opened.isEmpty())

        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(2, h.api.count("state"))
    }

    @Test
    fun `a failed ask keeps the last reading and says it is offline`() = runTest {
        val h = harness(both().copy(liveUpdates = false, pollIntervalSeconds = 1))
        h.api.state = { buildJsonObject { put("title", JsonPrimitive("Alien")) } }
        runCurrent()
        assertEquals("Alien", h.last.snapshot?.title)

        h.api.state = { throw IOException("timeout") }
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(Connection.Offline, h.last.connection)
        assertEquals("Alien", h.last.snapshot?.title)
    }

    @Test
    fun `changing the addresses starts a new session against them`() = runTest {
        val h = harness(AppSettings(primary = local, connectionMode = ConnectionMode.PRIMARY_ONLY))
        h.source.answer = live(film)
        runCurrent()
        assertEquals(listOf(local), h.source.opened)

        h.router.update(AppSettings(secondary = remote, connectionMode = ConnectionMode.SECONDARY_ONLY))
        runCurrent()

        assertEquals(listOf(local, remote), h.source.opened)
        assertEquals(remote, h.last.server)
    }
}
