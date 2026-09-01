package com.jamal2367.tinyppimobile.data.model

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One complete reading of what the box is playing.
 *
 * The shape is the add-on's own: `SnapshotBuilder.build` in
 * `resources/lib/web/snapshot.py` produces exactly these keys, and the
 * dashboard the add-on ships draws the same ones. Everything carries a default
 * because the two answers are not the same size - a stopped player sends five
 * keys, a playing one sends twenty - and a screen must not have to know which
 * of the two it is holding before it can read a field.
 */
@Serializable
data class Snapshot(
    /**
     * Which pass this is. Counts up once per producer tick and is what a delta
     * frame is stamped with, so a merged snapshot can be told from the one it
     * was merged onto.
     */
    val seq: Long = 0,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val title: String = "",
    /** Empty whenever the add-on's own file-name setting is switched off. */
    val filename: String = "",
    /** What the source is graded as: `dolbyvision`, `hdr10`, `hlg`, or empty for SDR. */
    @SerialName("hdr_type") val hdrType: String = "",
    /** Which overlay layout the add-on decided on; usually the source again. */
    val effective: String = "",
    /** What is actually leaving the box, in the same vocabulary as [hdrType]. */
    @SerialName("output_type") val outputType: String = "",
    val time: String = "",
    val duration: String = "",
    val metrics: Metrics = Metrics(),
    val groups: List<InfoGroup> = emptyList(),
    val metadata: List<MetadataRow> = emptyList(),
    val vs10: Vs10State = Vs10State(),
    val logos: Logos = Logos(),
    val art: ArtTags = ArtTags(),
    val media: MediaInfo = MediaInfo(),
    /** Empty unless the add-on is set to let a client switch anything. */
    val controls: PlayerControls = PlayerControls(),
    val session: SessionSummary = SessionSummary(),
    /** What the title that just ended came to; empty while one is playing. */
    val last: LastPlayed = LastPlayed(),
    /** Whether this server accepts commands. Added per response, not by the builder. */
    val control: Boolean = false,
    /** Whether a stream would be turned away right now - every slot is taken. */
    @SerialName("streams_full") val streamsFull: Boolean = false,
) {
    /** The source token, with SDR spelled out rather than left blank. */
    val sourceType: String
        get() = hdrType.ifBlank { "sdr" }

    /**
     * Whether a conversion is happening: what goes out is not what came in.
     *
     * Both sides are written in the same vocabulary on purpose (see
     * `_output_hdr_type` in the add-on), so this is a comparison and not a
     * table of pairs that would have to be kept in step with one.
     */
    val isConverting: Boolean
        get() = playing && outputType.isNotBlank() &&
            !outputType.equals(sourceType, ignoreCase = true)
}

/** The numeric side of a snapshot: what is charted rather than printed. */
@Serializable
data class Metrics(
    /** The Dolby Vision L1 luminance of the scene on screen, in nits. */
    val l1: L1Nits = L1Nits(),
    /** The L5 letterbox the RPU declares: left, right, top, bottom, in coded pixels. */
    val bars: List<Double>? = null,
    /** The coded frame [bars] are offsets into. */
    val frame: Frame? = null,
    val aspect: Double? = null,
    @SerialName("fps_in") val fpsIn: Double? = null,
    @SerialName("fps_drop") val fpsDrop: Double? = null,
    @SerialName("fps_out") val fpsOut: Double? = null,
    val progress: Double? = null,
    val cpu: Double? = null,
    @SerialName("cpu_temp") val cpuTemp: Double? = null,
    val memory: Double? = null,
    val cache: Double? = null,
) {
    /** The four L5 offsets, only when all four arrived. */
    val activeArea: ActiveArea?
        get() {
            val values = bars ?: return null
            if (values.size != 4) return null
            return ActiveArea(values[0], values[1], values[2], values[3])
        }
}

@Serializable
data class L1Nits(
    val min: Double? = null,
    val max: Double? = null,
    val avg: Double? = null,
) {
    val isEmpty: Boolean get() = min == null && max == null && avg == null
}

@Serializable
data class Frame(val w: Int = 0, val h: Int = 0) {
    val isUsable: Boolean get() = w > 0 && h > 0
}

/** The L5 offsets, named. */
data class ActiveArea(
    val left: Double,
    val right: Double,
    val top: Double,
    val bottom: Double,
) {
    val isPillarboxed: Boolean get() = left > 0 || right > 0
    val isLetterboxed: Boolean get() = top > 0 || bottom > 0
}

/**
 * One card of printed readings, as the overlay groups them.
 *
 * A row the stream does not carry is left out by the add-on rather than sent
 * blank, so a card is exactly as long as there is something to say.
 */
@Serializable
data class InfoGroup(
    val id: String = "",
    val title: String = "",
    val rows: List<InfoRowData> = emptyList(),
)

@Serializable
data class InfoRowData(
    val id: String = "",
    val label: String = "",
    val value: String = "",
    /** The parenthesised extra the overlay draws in its accent colour. */
    val detail: String = "",
)

/**
 * One row of the Dolby Vision metadata view.
 *
 * A trim-table row carries [cells] and every other row a single [value] - the
 * on-screen view draws each cell in a fixed slot, so the two shapes are kept
 * apart rather than joined into one string.
 */
@Serializable
data class MetadataRow(
    val kind: String = "",
    val name: String = "",
    val value: String = "",
    val cells: List<String>? = null,
) {
    val isTable: Boolean get() = cells != null
}

/** The VS10 conversions this source can be put through, and what is going out now. */
@Serializable
data class Vs10State(
    val options: List<Vs10Option> = emptyList(),
    /** The Amlogic output mode, as the driver reports it. */
    val output: String = "",
)

@Serializable
data class Vs10Option(val mode: String = "", val label: String = "")

/** The graphics the overlay draws for this format, as paths under `/media/`. */
@Serializable
data class Logos(val video: String = "", val audio: String = "")

/**
 * A short tag per artwork kind, changing only when the picture does.
 *
 * Hung on the image's address so a poster is fetched once per film rather than
 * once per snapshot.
 */
@Serializable
data class ArtTags(val poster: String = "", val fanart: String = "")

@Serializable
data class MediaInfo(
    val year: String = "",
    val genre: String = "",
    val show: String = "",
    val season: String = "",
    val episode: String = "",
) {
    /** `S02E07`, when this is an episode of something. */
    val episodeLabel: String?
        get() {
            val s = season.toIntOrNull() ?: return null
            val e = episode.toIntOrNull() ?: return null
            return String.format(Locale.ROOT, "S%02dE%02d", s, e)
        }
}

/** The switchable side of the player. Empty unless the server allows control. */
@Serializable
data class PlayerControls(
    val audio: List<Track> = emptyList(),
    val subtitle: List<Track> = emptyList(),
    @SerialName("audio_current") val audioCurrent: Int = -1,
    @SerialName("subtitle_current") val subtitleCurrent: Int = -1,
    @SerialName("subtitle_on") val subtitleOn: Boolean = false,
    val volume: Int? = null,
    val muted: Boolean = false,
    /** How many chapters the playing file has; 0 when it has none. */
    val chapters: Int = 0,
) {
    val isEmpty: Boolean
        get() = audio.isEmpty() && subtitle.isEmpty() && volume == null

    /**
     * Whether stepping between chapters leads anywhere.
     *
     * One chapter is the whole file under another name, so it is no more
     * navigable than none at all.
     */
    val hasChapters: Boolean get() = chapters > 1
}

@Serializable
data class Track(val index: Int = 0, val label: String = "")

/** The two figures small enough to travel with every snapshot. */
@Serializable
data class SessionSummary(
    /**
     * How many events the running title has produced.
     *
     * The one thing that says the history is worth fetching again: it counts
     * events rather than passes, so a number that has not moved means nothing
     * new has been written down.
     */
    val seq: Int = 0,
    val switches: Int = 0,
    val warnings: Int = 0,
)

/** What the title that just finished came to. */
@Serializable
data class LastPlayed(
    val title: String = "",
    val position: String = "",
    /** Seconds since it ended. */
    val ago: Int = 0,
    val switches: Int = 0,
    val warnings: Int = 0,
    val peak: Double? = null,
    val events: Int = 0,
) {
    val isPresent: Boolean get() = title.isNotBlank()
}
