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
    /**
     * When the title will be over, by the clock on the wall rather than by
     * its length: `21:47`, or `9:47 PM` on a box set to a twelve-hour clock.
     *
     * The box works it out and writes it in its own regional format, and it
     * is printed here exactly as it came - the same reasoning as every other
     * reading on this screen. Adding the remaining time to the phone's clock
     * would be a second answer to a question the television has already
     * answered, and the two would part company the moment a pause ran long or
     * the two devices disagreed about the time.
     *
     * Empty for anything with no end to name - a live stream, a title whose
     * length the box does not know yet - and empty as well against an add-on
     * older than the reading, which is why the bar has to be able to draw
     * without it.
     */
    val finish: String = "",
    val metrics: Metrics = Metrics(),
    val groups: List<InfoGroup> = emptyList(),
    val metadata: List<MetadataRow> = emptyList(),
    val vs10: Vs10State = Vs10State(),
    val logos: Logos = Logos(),
    val art: ArtTags = ArtTags(),
    val media: MediaInfo = MediaInfo(),
    /** Empty unless the add-on is set to let a client switch anything. */
    val controls: PlayerControls = PlayerControls(),
    /**
     * The playing file's chapters, already placed on the bar.
     *
     * A reading rather than a control, so it comes whether or not the box
     * lets a client switch anything. Empty for a file with fewer than two -
     * one chapter is the whole film under another name - and empty as well
     * against an add-on older than the reading.
     */
    val chapters: List<Chapter> = emptyList(),
    /**
     * Which of [chapters] the position is in, by its [Chapter.index]; 0 before
     * the first one has begun and for a file without any.
     *
     * Worked out by the box from the same list, so the highlighted dot and
     * the dots themselves can never disagree.
     */
    val chapter: Int = 0,
    val session: SessionSummary = SessionSummary(),
    /** What the title that just ended came to; empty while one is playing. */
    val last: LastPlayed = LastPlayed(),
    /**
     * Which version of the box's two shelves a request now would be answered
     * with.
     *
     * It moves whenever what the film and series lists would say moves: a film
     * watched to the end, one switched off in the middle, a scan that added a
     * series. The shelves are read once and then held on both sides, so this
     * is the whole of how the app hears that what it is drawing is no longer
     * what the box holds - without it a wall read this afternoon goes on
     * showing tonight's film as unwatched until the app is started again.
     *
     * Zero against an add-on older than the field, which is why nothing may
     * treat the first value it sees as a change (see `LiveViewModel`).
     */
    @SerialName("library") val libraryRevision: Long = 0,
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

    /** The chapter the position is in, or null before the first and without any. */
    val currentChapter: Chapter?
        get() = chapters.firstOrNull { it.index == chapter }

    /**
     * How many chapters the file has, as the last one is numbered - which is
     * what Kodi's own count says, even where the box left a malformed one out.
     */
    val chapterCount: Int
        get() = chapters.lastOrNull()?.index ?: 0
}

/**
 * One chapter of the playing file (see `chapter_list` in the add-on's
 * `web/player.py`).
 *
 * Placed and written by the box: [at] is where it starts as a percentage of
 * the length, the same scale `metrics.progress` is on, and [time] is that start
 * as a clock shaped like the position reading beside it.
 */
@Serializable
data class Chapter(
    /** Kodi's one-based number, which is what a jump to it names. */
    val index: Int = 0,
    /** Where it starts, in whole seconds. */
    val start: Long = 0,
    val at: Double = 0.0,
    val time: String = "",
    /** The file's name for it; empty when the file names none. */
    val name: String = "",
)

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
 * Which library title is playing - `movie` or `episode` and its id - as the
 * add-on says; `broadcast` without an id for a live TV or radio channel;
 * empty for a file the library does not know, and from an add-on older than
 * the field.
 */
@Serializable
data class PlayingItem(val kind: String = "", val id: Int = 0) {
    /** Whether there are details to open: a film or an episode the library holds. */
    val opensDetails: Boolean
        get() = id > 0 && (kind == "movie" || kind == "episode")

    /** The title as its details page is asked for, under [title]. */
    fun target(title: String) =
        MarkTarget(if (kind == "episode") MarkTarget.Kind.EPISODE else MarkTarget.Kind.MOVIE, id, title)
}

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
    /**
     * The slogan, as the library has it - empty for a file it does not know
     * and for most episodes, and against an add-on older than the field.
     */
    val tagline: String = "",
    /** What the title is about, with the library's own line breaks. */
    val plot: String = "",
    /**
     * Every source's rating the library holds, the known ones first (see
     * `web/ratings.py` in the add-on). An episode's are its own.
     */
    val ratings: List<SourceRating> = emptyList(),
    /**
     * The age rating as the scraper wrote it - `FSK 12`, `Rated PG-13` - and
     * for an episode its show's.
     */
    val mpaa: String = "",
    /**
     * The mark the add-on read [mpaa] as - `fsk12`, `mpa_pg13`, `tv_ma`,
     * `bbfc_15` - which is drawn as the left of a shield: the board's name
     * on the level's colour. Empty for an age rating it could not place,
     * which is written out instead.
     */
    val age: String = "",
    /**
     * The level written beside [age]'s mark - `12`, `PG-13`, `MA`, `15` -
     * as its board writes it; empty where there is no mark, and from an
     * add-on too old to send it, whose mark then stands alone.
     */
    @SerialName("age_level") val ageLevel: String = "",
    /** The reader's own rating, 1 to 10; 0 for none. */
    @SerialName("userrating") val userRating: Int = 0,
    /** The place in the IMDb Top 250; 0 for none. */
    val top250: Int = 0,
    /**
     * The big awards it won or was nominated for - the Academy Awards, the
     * Golden Globes, the BAFTAs, the Primetime Emmys - in the order they are
     * shown (see `web/awards.py` in the add-on). An episode's are its show's.
     */
    val awards: List<Award> = emptyList(),
    /**
     * Who made it, as the add-on splits Kodi's readings into names: every
     * director, the first studio alone, and the cast's first twenty in
     * billing order with whether there are more. The studio stands in
     * the card of what is playing, the director and the cast in the plot
     * card. An episode's studio is its show's, and its cast its own guests
     * and then the show's.
     */
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    @SerialName("cast_more") val castMore: Boolean = false,
    val studios: List<String> = emptyList(),
    /** Which library title is playing, for the overview card to open its details. */
    val item: PlayingItem = PlayingItem(),
    /** Every genre, every writer, and who each of [cast] played ("" for none known). */
    val genres: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
) {
    /**
     * Whether the snapshot says anything a details page could show - a plot,
     * a rating, who made it or is in it - for a title the box hands out no
     * details for: a broadcast, a file the library does not know.
     */
    val hasOverview: Boolean
        get() = plot.isNotBlank() || ratings.isNotEmpty() || userRating > 0 || top250 > 0 ||
            mpaa.isNotBlank() || awards.isNotEmpty() || directors.isNotEmpty() || writers.isNotEmpty() ||
            cast.isNotEmpty()

    /**
     * What the snapshot says of the title, as a details page draws it: each
     * of the cast with who they played, under the playing title's own
     * pictures ([art]).
     */
    fun details(art: ArtTags): TitleDetails = TitleDetails(
        kind = item.kind,
        ratings = ratings,
        plot = plot,
        tagline = tagline,
        show = show,
        mpaa = mpaa,
        age = age,
        ageLevel = ageLevel,
        year = year.toIntOrNull() ?: 0,
        userrating = userRating,
        top250 = top250,
        awards = awards,
        genres = genres,
        directors = directors,
        writers = writers,
        studios = studios,
        cast = cast.mapIndexed { index, name -> CastMember(name, if (index < roles.size) roles[index] else "") },
        castMore = castMore,
        poster = art.poster,
        fanart = art.fanart,
    )

    /** `S02E07`, when this is an episode of something. */
    val episodeLabel: String?
        get() {
            val s = season.toIntOrNull() ?: return null
            val e = episode.toIntOrNull() ?: return null
            return String.format(Locale.ROOT, "S%02dE%02d", s, e)
        }
}

/**
 * One of the big awards the playing title won or was nominated for: `oscar`,
 * `globe`, `bafta` or `emmy`, what it won by category and year
 * (`Visual Effects (2015)`), and how many more it was nominated for.
 */
@Serializable
data class Award(
    val award: String = "",
    val won: List<String> = emptyList(),
    val nominated: Int = 0,
)

/**
 * One source's rating of the playing title, out of ten: `imdb`, `tmdb`,
 * `trakt`, `tvdb`, `metacritic`, or whatever else an NFO named.
 */
@Serializable
data class SourceRating(
    val source: String = "",
    val rating: Double = 0.0,
    /** How many voted; 0 where the library does not say. */
    val votes: Int = 0,
    /**
     * Rotten Tomatoes certified it: Certified Fresh for `rottentomatoes`,
     * Verified Hot for `popcorn`. Sent by the add-on only when true.
     */
    val certified: Boolean = false,
)

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
    /**
     * The audio and the subtitle offsets in force, in seconds. Null where the
     * box reports none: an add-on older than the fine adjustments.
     */
    @SerialName("audio_delay") val audioDelay: Double? = null,
    @SerialName("subtitle_delay") val subtitleDelay: Double? = null,
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
