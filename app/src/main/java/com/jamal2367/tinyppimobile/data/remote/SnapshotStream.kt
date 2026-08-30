package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.URLEncoder

/** One thing that happened on the connection to a box. */
sealed interface StreamEvent {

    /** The stream is open; what follows is live. */
    data object Connected : StreamEvent

    /** A complete reading. */
    data class State(val snapshot: Snapshot, val server: ServerConfig) : StreamEvent

    /**
     * The box is shutting its server down, and says how long to stay away.
     *
     * Worth obeying rather than treating as any other drop: the add-on goes
     * down with Kodi, and every connection it is still accepting is a thread
     * Kodi waits on before it can exit.
     */
    data class GoingAway(val retryMillis: Long) : StreamEvent

    /** Every stream slot on the box is taken. */
    data object Busy : StreamEvent

    /** The box wants a token, and did not get one it accepts. */
    data object Unauthorized : StreamEvent

    /** The stream is gone; the client will try again shortly. */
    data object Disconnected : StreamEvent
}

/**
 * One connection to a box's `/api/stream`, for as long as it lasts.
 *
 * Deliberately one connection and not a retry loop: what to do after a stream
 * ends is a question about the whole app - whether live updates are still on,
 * which address to try next, whether anything is on screen to show it to - and
 * that belongs where those are known. [com.jamal2367.tinyppimobile.data.repository.LiveSession]
 * is where.
 *
 * The token travels as `?token=`: a browser cannot put a header on an
 * `EventSource`, which is why the add-on accepts both, and using the same form
 * here keeps this client on the path the add-on is actually exercised over.
 */
class SnapshotStream(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /**
     * Open a stream to [server] and emit what arrives until it closes.
     *
     * The first frame is a whole snapshot and every one after it is a delta
     * measured against the last, so the base is kept here: it is per
     * connection, not per app - two clients can be at different points, and a
     * reconnect is sent the whole thing again whatever this one holds.
     */
    fun connect(server: ServerConfig): Flow<StreamEvent> = callbackFlow {
        var base: JsonObject? = null

        val request = Request.Builder()
            .url(server.streamUrl())
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .apply {
                if (server.token.isNotBlank()) {
                    header(FailoverInterceptor.TOKEN_HEADER, server.token.trim())
                }
            }
            .build()

        val listener = object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(StreamEvent.Connected)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                when (type) {
                    "state" -> {
                        val frame = decode(data) ?: return
                        base = frame
                        emitSnapshot(frame)
                    }

                    "delta" -> {
                        val frame = decode(data) ?: return
                        // Nothing to measure against: the add-on always opens
                        // with a whole snapshot, so this is a frame that
                        // arrived out of order. Skipping it costs one tick;
                        // guessing at a base would cost a wrong screen.
                        val current = base ?: return
                        val merged = SnapshotMerge.merge(current, frame)
                        base = merged
                        emitSnapshot(merged)
                    }

                    "bye" -> {
                        val retry = decode(data)
                            ?.get("retry_ms")
                            ?.let { (it as? JsonPrimitive)?.longOrNull }
                            ?: DEFAULT_GOING_AWAY_MS
                        trySend(StreamEvent.GoingAway(retry))
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                // A refusal is worth reporting, because both of the ones this
                // server hands out are things a reader can act on: a wrong
                // token, and a box with every slot taken. Anything else is a
                // dropped connection, which is the normal state of a phone.
                when (response?.code) {
                    401 -> trySend(StreamEvent.Unauthorized)
                    503 -> trySend(StreamEvent.Busy)
                }
                close()
            }

            private fun emitSnapshot(frame: JsonObject) {
                val snapshot = runCatching {
                    json.decodeFromJsonElement(Snapshot.serializer(), frame)
                }.getOrNull() ?: return
                trySend(StreamEvent.State(snapshot, server))
            }

            private fun decode(data: String): JsonObject? = runCatching {
                json.parseToJsonElement(data).asSnapshotObject()
            }.getOrNull()
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun ServerConfig.streamUrl(): String = buildString {
        append(baseUrl)
        append("/api/stream")
        if (token.isNotBlank()) {
            append("?token=")
            append(URLEncoder.encode(token.trim(), Charsets.UTF_8.name()))
        }
    }

    private companion object {
        /** What to wait when a parting frame carries no interval of its own. */
        const val DEFAULT_GOING_AWAY_MS = 20_000L
    }
}
