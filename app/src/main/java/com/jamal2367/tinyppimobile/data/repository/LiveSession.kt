package com.jamal2367.tinyppimobile.data.repository

import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ServerRouter
import com.jamal2367.tinyppimobile.data.remote.SnapshotStream
import com.jamal2367.tinyppimobile.data.remote.StreamEvent
import com.jamal2367.tinyppimobile.data.remote.TinyPpiApi
import com.jamal2367.tinyppimobile.data.remote.asSnapshotObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How the app is currently getting its readings, and the last one it got.
 *
 * The two are one value on purpose: a snapshot with nothing saying where it
 * came from is a screen that cannot tell "playing nothing" from "not connected
 * to anything", and those are the two states this app spends most of its life
 * in.
 */
data class LiveState(
    val connection: Connection = Connection.Connecting,
    val snapshot: Snapshot? = null,
    /** Which of the two addresses the reading came from. */
    val server: ServerConfig? = null,
) {
    val isLive: Boolean get() = connection == Connection.Streaming

    /** Whether there is anything at all to draw. */
    val hasSnapshot: Boolean get() = snapshot != null

    enum class Connection {
        /** Nothing is filled in to connect to. */
        NotConfigured,

        /** On its way; nothing has answered yet. */
        Connecting,

        /** The event stream is open and readings arrive as they happen. */
        Streaming,

        /** Live updates are off, or the stream would not open: asking instead. */
        Polling,

        /** Every stream slot on the box is taken. */
        Busy,

        /** The box wants a token it has not been given. */
        Unauthorized,

        /** Nothing answered. */
        Offline,
    }
}

/**
 * The one live connection the whole app shares.
 *
 * Every screen reads this instead of opening a stream of its own: the add-on
 * caps concurrent streams at six and fans an event out to each of them, so four
 * screens from one phone would spend four of those six slots showing the same
 * thing.
 *
 * When live updates are switched off - or the stream is refused - it keeps the
 * same shape by asking `/api/state` on a timer. The screens cannot tell the
 * difference except through [LiveState.connection], which is exactly what the
 * status line is for.
 */
class LiveSession(
    private val api: TinyPpiApi,
    private val stream: SnapshotStream,
    private val router: ServerRouter,
    private val json: Json,
) {

    /**
     * What a session is built against, and therefore what has to end one.
     *
     * A session is a loop that can sit inside a stream for two hours, so it
     * cannot be the thing that notices a changed setting: it is restarted from
     * outside whenever one of these moves. Everything else in the settings -
     * the theme, the chart range - leaves an open connection alone.
     */
    private data class SessionConfig(
        val servers: List<ServerConfig>,
        val liveUpdates: Boolean,
        val pollSeconds: Int,
        /**
         * Not a setting, and not read by the session: it is here so that
         * asking to reconnect counts as a change like any other, and the same
         * restart the settings get is the one a reader gets.
         */
        val restart: Int = 0,
    )

    /**
     * How many times a reader has asked for this to start over.
     *
     * A session restarts itself on a schedule that assumes nobody is watching:
     * it backs off after a refusal and waits out its poll interval, which is
     * right for a phone in a pocket and wrong for one in a hand in front of a
     * box that has just come back. Bumping this is the way to say "now" - it
     * is part of what a session is built against, so moving it tears the
     * current one down and builds a fresh one against the same settings.
     */
    private val restarts = MutableStateFlow(0)

    /** Drop whatever is open and connect again, without waiting out a back-off. */
    fun restart() {
        restarts.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun states(): Flow<LiveState> = combine(router.settingsFlow, restarts) { settings, restart ->
        SessionConfig(
            servers = settings.servers(),
            liveUpdates = settings.liveUpdates,
            pollSeconds = settings.pollIntervalSeconds,
            restart = restart,
        )
    }
        .distinctUntilChanged()
        // The stored settings arriving for the first time is itself a change:
        // what came before them was the defaults, which point at nothing. A
        // session started against those would otherwise sit on its
        // not-configured back-off for fifteen seconds while a perfectly good
        // address was already on disk.
        .flatMapLatest(::session)

    private fun session(config: SessionConfig): Flow<LiveState> = flow {
        var attempt = 0
        var last = LiveState()

        suspend fun publish(state: LiveState) {
            last = state
            emit(state)
        }

        if (config.servers.isEmpty()) {
            publish(LiveState(connection = LiveState.Connection.NotConfigured))
            return@flow
        }

        while (true) {
            if (!config.liveUpdates) {
                // Asked for rather than streamed. Still through the failover
                // interceptor, so the walk between the two addresses is the
                // same one every other request makes.
                publish(pollOnce(last))
                delay(config.pollSeconds.seconds)
                continue
            }

            // The stream addresses a box directly rather than going through the
            // failover interceptor, so it walks the same list itself: an
            // address that never opens hands over to the next one.
            val server = config.servers[attempt % config.servers.size]

            var opened = false
            var refused: LiveState.Connection? = null
            var goingAway = 0L

            try {
                stream.connect(server).collect { event ->
                    when (event) {
                        StreamEvent.Connected -> {
                            opened = true
                            router.markReachable(server)
                            publish(
                                last.copy(
                                    connection = LiveState.Connection.Streaming,
                                    server = server,
                                )
                            )
                        }

                        is StreamEvent.State -> {
                            opened = true
                            publish(
                                LiveState(
                                    connection = LiveState.Connection.Streaming,
                                    snapshot = event.snapshot,
                                    server = event.server,
                                )
                            )
                        }

                        is StreamEvent.GoingAway -> goingAway = event.retryMillis

                        StreamEvent.Busy -> refused = LiveState.Connection.Busy

                        StreamEvent.Unauthorized -> refused = LiveState.Connection.Unauthorized

                        StreamEvent.Disconnected -> Unit
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A stream that fell over is not an error to report: the loop
                // is about to open another one, and every phone drops them.
            }

            // Read out of the captured variables once: what the collector wrote
            // is what the decision below is made on.
            val refusal = refused
            val away = goingAway

            when {
                refusal == LiveState.Connection.Busy -> {
                    // Every slot is taken - usually by a dashboard left open in
                    // a browser somewhere. That is about the connection and not
                    // about the readings, so they are asked for instead: they
                    // still arrive, just not as they happen. A slot frees the
                    // moment that tab is closed, so the stream is tried again
                    // between polls rather than given up on.
                    publish(last.copy(connection = LiveState.Connection.Busy, server = server))
                    delay(BUSY_RETRY.seconds)
                    publish(pollOnce(last).copy(connection = LiveState.Connection.Busy))
                }

                refusal == LiveState.Connection.Unauthorized -> {
                    // Nothing here will fix a wrong token, and asking for the
                    // state would only be refused the same way - which would
                    // replace the one message that says what to do about it
                    // with "offline". So it says so and waits.
                    publish(last.copy(connection = refusal, server = server))
                    delay(UNAUTHORIZED_RETRY.seconds)
                }

                away > 0L -> {
                    // The box is going down with Kodi, and every connection it
                    // is still accepting is a thread Kodi waits on before it
                    // can exit. The interval it named is worth obeying.
                    publish(last.copy(connection = LiveState.Connection.Offline))
                    delay(away.milliseconds)
                }

                opened -> {
                    attempt = 0
                    publish(last.copy(connection = LiveState.Connection.Connecting))
                    delay(RECONNECT.seconds)
                }

                else -> {
                    // Never even opened: try the other address, and back off
                    // once both have been tried, so a box that is simply not
                    // there is not hammered.
                    attempt++
                    router.markUnreachable()
                    publish(LiveState(connection = LiveState.Connection.Offline))
                    if (attempt % config.servers.size == 0) delay(FAILED_RETRY.seconds)
                }
            }
        }
    }

    /**
     * One reading, asked for rather than streamed.
     *
     * Keeps the snapshot it had on a failed pass: a box that missed one answer
     * has not stopped playing, and a screen that empties itself every time a
     * request times out is worse than one that says it is offline over the
     * reading it still holds.
     */
    private suspend fun pollOnce(previous: LiveState): LiveState = try {
        val snapshot = json.decodeFromJsonElement(
            Snapshot.serializer(),
            api.state().asSnapshotObject(),
        )
        LiveState(
            connection = LiveState.Connection.Polling,
            snapshot = snapshot,
            server = router.activeServer.value?.config,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        previous.copy(connection = LiveState.Connection.Offline)
    }

    private companion object {
        /** What the add-on's own dashboard waits after a stream it had. */
        const val RECONNECT = 3L
        const val FAILED_RETRY = 10L

        /** A slot frees the moment a forgotten tab is closed, so this is short. */
        const val BUSY_RETRY = 5L

        /** Nothing but a corrected token will change this answer, so this is not. */
        const val UNAUTHORIZED_RETRY = 30L
    }
}
