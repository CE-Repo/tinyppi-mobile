package com.jamal2367.tinyppimobile.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The running title's whole past: the luminance chart and the event list.
 *
 * Asked for when a screen opens and again whenever [SessionSummary.seq] moves,
 * rather than sent five times a second with the snapshot - an hour of samples
 * is three arrays of 3600 numbers, and it changes once a second at most.
 */
@Serializable
data class History(
    /** The session's age as the answer left the box, in seconds. */
    val now: Double = 0.0,
    /** Seconds between samples. */
    val step: Double = 1.0,
    /** Each sample's age, in seconds since the title started. */
    val t: List<Double> = emptyList(),
    /** The scene's peak luminance per sample, in nits. Null where none was read. */
    val max: List<Double?> = emptyList(),
    /** The scene's average luminance per sample, in nits. */
    val avg: List<Double?> = emptyList(),
    val events: List<PlaybackEvent> = emptyList(),
    /** The event counter this history was taken at. */
    val seq: Int = 0,
    val switches: Int = 0,
) {
    val isEmpty: Boolean get() = t.isEmpty() && events.isEmpty()

    /** The highest luminance the title ever reached, or null for a grade that carries none. */
    val peak: Double? get() = max.filterNotNull().maxOrNull()

    /** The samples as pairs, oldest first, with the gaps left out. */
    fun series(pick: (History) -> List<Double?>): List<Pair<Double, Double>> {
        val values = pick(this)
        return t.indices.mapNotNull { index ->
            val value = values.getOrNull(index) ?: return@mapNotNull null
            t[index] to value
        }
    }
}

/**
 * One thing that happened while the title ran.
 *
 * A transition carries [from] and [to]; a threshold carries [value]. Which of
 * the two a row is follows from its [kind], and [PlaybackEventKind] names the
 * ones this build knows - anything else is still shown, under its own name.
 */
@Serializable
data class PlaybackEvent(
    /** Seconds since the title started. */
    val t: Double = 0.0,
    /** Where in the film it happened, as the player prints it. */
    val pos: String = "",
    val kind: String = "",
    @Serializable(with = LooseStringSerializer::class) val from: String? = null,
    @Serializable(with = LooseStringSerializer::class) val to: String? = null,
    val value: Double? = null,
) {
    val isTransition: Boolean get() = from != null && to != null

    val eventKind: PlaybackEventKind? get() = PlaybackEventKind.of(kind)
}

/**
 * The events the add-on writes down, and what each one is.
 *
 * [isSwitch] and [isWarning] mirror `SWITCH_KINDS` and `WARNING_KINDS` in the
 * add-on's own session log, which is what the two counters on a snapshot count.
 */
enum class PlaybackEventKind(val id: String) {
    VS10("vs10"),
    MODE("mode"),
    AUDIO("audio"),
    SUBTITLE("subtitle"),
    CACHE_LOW("cache_low"),
    CACHE_RECOVERED("cache_recovered"),
    TEMPERATURE("temperature"),
    CPU("cpu"),
    FPS("fps");

    val isSwitch: Boolean get() = this in setOf(VS10, MODE, AUDIO, SUBTITLE)

    val isWarning: Boolean get() = this in setOf(CACHE_LOW, TEMPERATURE, CPU)

    companion object {
        fun of(id: String): PlaybackEventKind? = entries.firstOrNull { it.id == id }

        /** The token the add-on sends for subtitles that were switched off. */
        const val SUBTITLES_OFF = "__off__"
    }
}

/**
 * A JSON value read as text, whatever it arrived as.
 *
 * An event's two sides are whatever the reading is: a VS10 output is a string,
 * a frame rate is a number, and both travel under the same two keys. They are
 * only ever printed, so the number becomes its own text rather than the model
 * gaining a second shape for it.
 */
internal object LooseStringSerializer : KSerializer<String?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LooseString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return element.toString()
        return primitive.content
    }

    // Nothing in this app ever writes an event back to a box - the two POST
    // bodies carry an action and a number - so this side exists only because
    // the interface has two halves. `encodeNull` is the correct answer for a
    // nullable value and is still marked experimental; the opt-in is narrower
    // than one on the file would be.
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** What `/api/hello` says before anything has been asked of the box. */
@Serializable
data class Hello(
    val name: String = "",
    val version: String = "",
    /** Whether reading the state needs the token, not only writing to it. */
    @SerialName("auth_read") val authRead: Boolean = false,
    /** Whether this box lets a client switch anything at all. */
    val control: Boolean = false,
    /** How often the add-on rebuilds its snapshot. */
    @SerialName("interval_ms") val intervalMs: Int = 200,
    /** The dashboard's own chrome, localized through Kodi's string table. */
    val strings: Map<String, String> = emptyMap(),
)

/** What the two POST endpoints answer with. */
@Serializable
data class CommandAck(
    val ok: Boolean = false,
    val action: String = "",
    val mode: String = "",
)

/** The one shape every failure from this API arrives in. */
@Serializable
data class ApiErrorBody(
    val error: String = "",
    /** Only on a refused stream: how long to stay away. */
    @SerialName("retry_ms") val retryMs: Long? = null,
)
