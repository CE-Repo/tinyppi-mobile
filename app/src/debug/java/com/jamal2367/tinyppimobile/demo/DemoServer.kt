package com.jamal2367.tinyppimobile.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sin

/**
 * A pretend TinyPPI box, for trying the app out away from the real one.
 *
 * Debug builds only. It listens on 127.0.0.1:8099 on the phone itself and
 * answers the same web API the add-on does - the event stream, the history,
 * both shelves, the continue-watching row, the pictures and the commands - out
 * of made-up data: a Dolby Vision film playing, a shelf of films and one of
 * series, posters drawn on the spot. Point the app at `127.0.0.1` in the
 * settings and every screen has something on it.
 *
 * The commands do what they say, as far as a pretend box can: play and pause,
 * seek, stop, the volume, the tracks, VS10, and starting a film or an episode
 * off a shelf.
 */
object DemoServer {
    private const val TAG = "DemoServer"
    private const val PORT = 8099

    @Volatile private var started = false
    private val json = Json { encodeDefaults = true }
    private val art = ConcurrentHashMap<String, ByteArray>()
    private val lock = Any()

    fun start(context: Context) {
        if (started) return
        started = true
        thread(name = "demo-server", isDaemon = true) {
            try {
                ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1")).use { server ->
                    Log.i(TAG, "Demo box listening on 127.0.0.1:$PORT")
                    while (true) {
                        val socket = server.accept()
                        thread(name = "demo-connection", isDaemon = true) { serve(socket) }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Demo box could not start", e)
            }
        }
    }

    /* --- The pretend library --------------------------------------------- */

    private class Film(val id: Int, val title: String, val year: Int, val minutes: Int, val rating: Double, val genre: String, val watched: Boolean = false, val resumeMinutes: Int = 0)
    private class Show(val id: Int, val title: String, val year: Int, val rating: Double, val seasons: Int, val perSeason: Int, val seenUpTo: Int)

    private val films = listOf(
        Film(1, "Dune: Part Two", 2024, 166, 8.5, "Science Fiction", resumeMinutes = 71),
        Film(2, "Oppenheimer", 2023, 180, 8.3, "Drama", resumeMinutes = 40),
        Film(3, "Blade Runner 2049", 2017, 164, 8.0, "Science Fiction", resumeMinutes = 132),
        Film(4, "Mad Max: Fury Road", 2015, 120, 8.1, "Action", watched = true),
        Film(5, "Interstellar", 2014, 169, 8.7, "Science Fiction", watched = true),
        Film(6, "Arrival", 2016, 116, 7.9, "Drama", resumeMinutes = 58),
        Film(7, "The Batman", 2022, 176, 7.8, "Crime", resumeMinutes = 104),
        Film(8, "Sicario", 2015, 121, 7.6, "Thriller", watched = true),
        Film(9, "Tenet", 2020, 150, 7.3, "Action", resumeMinutes = 22),
        Film(10, "Joker", 2019, 122, 8.4, "Drama", watched = true),
        Film(11, "Gravity", 2013, 91, 7.7, "Science Fiction"),
        Film(12, "Top Gun: Maverick", 2022, 130, 8.2, "Action"),
        Film(13, "Avatar: The Way of Water", 2022, 192, 7.5, "Adventure"),
        Film(14, "1917", 2019, 119, 8.2, "War", watched = true),
        Film(15, "Alien: Romulus", 2024, 119, 7.1, "Horror"),
        Film(16, "Heat", 1995, 170, 8.3, "Crime", watched = true),
        Film(17, "No Time to Die", 2021, 163, 7.3, "Action"),
        Film(18, "The Revenant", 2015, 156, 8.0, "Western"),
        Film(19, "Everything Everywhere All at Once", 2022, 139, 7.8, "Comedy"),
        Film(20, "Prisoners", 2013, 153, 8.1, "Thriller"),
        Film(21, "Mission: Impossible - Dead Reckoning", 2023, 163, 7.7, "Action"),
        Film(22, "The Martian", 2015, 144, 8.0, "Science Fiction", watched = true),
        Film(23, "Parasite", 2019, 132, 8.5, "Thriller"),
        Film(24, "Inception", 2010, 148, 8.8, "Science Fiction", watched = true),
    )

    private val shows = listOf(
        Show(101, "Severance", 2022, 8.7, 2, 9, 14),
        Show(102, "The Last of Us", 2023, 8.7, 2, 8, 8),
        Show(103, "Shōgun", 2024, 8.6, 1, 10, 10),
        Show(104, "Andor", 2022, 8.5, 2, 12, 20),
        Show(105, "True Detective", 2014, 8.9, 3, 8, 24),
        Show(106, "Dark", 2017, 8.7, 3, 8, 12),
        Show(107, "Silo", 2023, 8.1, 2, 10, 4),
        Show(108, "Fallout", 2024, 8.3, 1, 8, 3),
        Show(109, "Chernobyl", 2019, 9.3, 1, 5, 5),
    )

    private fun episodeId(show: Show, season: Int, episode: Int) = show.id * 1000 + season * 100 + episode
    private fun showOfEpisode(id: Int) = shows.firstOrNull { it.id == id / 1000 }
    private fun episodeTitle(season: Int, episode: Int) = listOf(
        "Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are", "The Grim Barbarity",
        "Hide and Seek", "Defiant Jazz", "What's for Dinner?", "The We We Are", "Hello, Ms. Cobel",
        "Goodbye, Mrs. Selvig", "Who Is Alive?",
    )[(season * 7 + episode) % 12]

    /* --- What the box is doing ------------------------------------------- */

    private var playingTitle: String? = "Dune: Part Two"
    private var playingTag = "movie-1"
    private var playingYear = 2024
    private var playingGenre = "Science Fiction"
    private var playingShow = ""
    private var playingSeason = ""
    private var playingEpisode = ""
    private var durationSeconds = 166 * 60
    private var positionSeconds = 71.0 * 60
    private var paused = false
    private var lastTick = System.currentTimeMillis()
    private var volume = 72
    private var muted = false
    private var audioCurrent = 0
    private var subtitleCurrent = 1
    private var subtitleOn = true
    private var vs10Output = ""
    private var seq = 0L
    private var switches = 2
    private var libraryRevision = 1L
    private var sessionStart = System.currentTimeMillis()

    private fun tick() {
        val now = System.currentTimeMillis()
        if (playingTitle != null && !paused) {
            positionSeconds = (positionSeconds + (now - lastTick) / 1000.0).coerceAtMost(durationSeconds.toDouble())
        }
        lastTick = now
    }

    /** A made-up but film-shaped luminance curve: mostly dim, with bright spikes. */
    private fun peakAt(t: Double): Double {
        val base = 180 + 140 * sin(t / 37.0) + 90 * sin(t / 11.0)
        val spike = if ((t / 23.0).toInt() % 5 == 0) 900 + 700 * abs(sin(t / 3.0)) else 0.0
        return (base + spike).coerceIn(40.0, 1000.0)
    }

    private fun averageAt(t: Double): Double = (peakAt(t) * 0.18 + 12 * sin(t / 5.0)).coerceAtLeast(3.0)

    private fun clock(seconds: Double): String {
        val s = seconds.toInt()
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    }

    private fun snapshot(): JsonObject = synchronized(lock) {
        tick()
        seq++
        val title = playingTitle
        if (title == null) {
            return buildJsonObject {
                put("seq", seq)
                put("playing", false)
                put("control", true)
                put("library", libraryRevision)
                putJsonArray("groups") {}
                putJsonObject("metrics") {}
                putJsonObject("last") {
                    put("title", "Dune: Part Two")
                    put("position", clock(positionSeconds))
                    put("ago", ((System.currentTimeMillis() - lastTick) / 1000).toInt())
                    put("switches", switches)
                    put("warnings", 1)
                    put("peak", 1000.0)
                    put("events", 6)
                }
            }
        }
        val t = positionSeconds
        val output = vs10Output
        buildJsonObject {
            put("seq", seq)
            put("playing", true)
            put("paused", paused)
            put("title", title)
            put("filename", "/storage/media/${title.replace(Regex("[^A-Za-z0-9]+"), ".")}.2160p.UHD.BluRay.DV.HDR.TrueHD.Atmos.mkv")
            put("hdr_type", "dolbyvision")
            put("effective", "dolbyvision")
            put("output_type", output.ifBlank { "dolbyvision" })
            put("time", clock(t))
            put("duration", clock(durationSeconds.toDouble()))
            val finish = java.util.Calendar.getInstance().apply { add(java.util.Calendar.SECOND, (durationSeconds - t).toInt()) }
            put("finish", String.format(Locale.ROOT, "%d:%02d", finish.get(java.util.Calendar.HOUR_OF_DAY), finish.get(java.util.Calendar.MINUTE)))
            put("control", true)
            put("library", libraryRevision)
            putJsonObject("metrics") {
                putJsonObject("l1") {
                    put("min", 0.0)
                    put("max", peakAt(t))
                    put("avg", averageAt(t))
                }
                putJsonArray("bars") { add(JsonPrimitive(0)); add(JsonPrimitive(0)); add(JsonPrimitive(138)); add(JsonPrimitive(138)) }
                putJsonObject("frame") { put("w", 3840); put("h", 2160) }
                put("aspect", 2.39)
                put("fps_in", 23.976)
                put("fps_drop", 0.0)
                put("fps_out", 23.976)
                put("progress", t / durationSeconds * 100)
                put("cpu", 28 + 6 * sin(t / 9.0))
                put("cpu_temp", 57 + 2 * sin(t / 30.0))
                put("memory", 41.0)
                put("cache", 100.0)
            }
            putJsonArray("groups") {
                add(group("video", "Video", listOf(
                    row("video.32000", "Display mode", "3840x2160p23", ""),
                    row("video.32001", "Video codec", "HEVC Main 10", ""),
                    row("video.32002", "Dolby Vision", "Profile 7.6 FEL", "(CMv4.0)"),
                    row("video.32099", "Bit depth", "12 bit", "(dv)"),
                    row("video.32003", "Bitrate", "${(58 + 14 * sin(t / 4.0)).toInt()} Mbit/s", ""),
                    row("video.32004", "Mastering display", "1000 nits", "(P3-D65)"),
                    row("video.32005", "MaxCLL / MaxFALL", "1000 / 268 nits", ""),
                )))
                add(group("audio", "Audio", listOf(
                    row("audio.32045", "Codec", "TrueHD 7.1", "(Atmos)"),
                    row("audio.32046", "Sample rate", "48 kHz", "24 bit"),
                    row("audio.32047", "Passthrough", "On", ""),
                )))
                add(group("system", "System", listOf(
                    row("system.1", "Frames dropped", "0", ""),
                    row("system.2", "Cache", "100 %", ""),
                )))
            }
            putJsonArray("metadata") {
                add(meta("section", "Level 1"))
                add(meta("row", "Min PQ", "0"))
                add(meta("row", "Max PQ", "${(peakAt(t) * 3.2).toInt()}"))
                add(meta("row", "Avg PQ", "${(averageAt(t) * 9).toInt()}"))
                add(meta("section", "Level 2"))
                add(metaCells("headings", "Target", listOf("100", "600", "1000")))
                add(metaCells("columns", "Slope", listOf("2048", "2048", "2048")))
                add(metaCells("columns", "Offset", listOf("2048", "2051", "2048")))
                add(metaCells("columns", "Power", listOf("2048", "2044", "2048")))
                add(meta("section", "Level 6"))
                add(meta("row", "MaxCLL", "1000"))
                add(meta("row", "MaxFALL", "268"))
            }
            putJsonObject("vs10") {
                putJsonArray("options") {
                    add(buildJsonObject { put("mode", "hdr10"); put("label", "Dolby Vision -> HDR10") })
                    add(buildJsonObject { put("mode", "sdr8"); put("label", "Dolby Vision -> SDR") })
                    add(buildJsonObject { put("mode", "off"); put("label", "Original") })
                }
                put("output", when (output) { "hdr10" -> "HDR10"; "sdr" -> "SDR"; else -> "DV-LL" })
            }
            putJsonObject("art") { put("poster", playingTag); put("fanart", "") }
            putJsonObject("media") {
                put("year", playingYear.toString())
                put("genre", playingGenre)
                put("show", playingShow)
                put("season", playingSeason)
                put("episode", playingEpisode)
            }
            putJsonObject("controls") {
                putJsonArray("audio") {
                    add(track(0, "English - TrueHD Atmos 7.1"))
                    add(track(1, "Deutsch - DD+ Atmos 5.1"))
                    add(track(2, "English - AC3 2.0 Commentary"))
                }
                putJsonArray("subtitle") {
                    add(track(0, "English"))
                    add(track(1, "Deutsch (Forced)"))
                    add(track(2, "Deutsch"))
                }
                put("audio_current", audioCurrent)
                put("subtitle_current", subtitleCurrent)
                put("subtitle_on", subtitleOn)
                put("volume", volume)
                put("muted", muted)
                put("chapters", 24)
            }
            putJsonObject("session") {
                put("seq", 6)
                put("switches", switches)
                put("warnings", 1)
            }
        }
    }

    private fun group(id: String, title: String, rows: List<JsonObject>) = buildJsonObject {
        put("id", id)
        put("title", title)
        put("rows", JsonArray(rows))
    }

    private fun row(id: String, label: String, value: String, detail: String) = buildJsonObject {
        put("id", id); put("label", label); put("value", value); put("detail", detail)
    }

    private fun meta(kind: String, name: String, value: String = "") = buildJsonObject {
        put("kind", kind); put("name", name); put("value", value)
    }

    private fun metaCells(kind: String, name: String, cells: List<String>) = buildJsonObject {
        put("kind", kind); put("name", name); put("cells", JsonArray(cells.map(::JsonPrimitive)))
    }

    private fun track(index: Int, label: String) = buildJsonObject { put("index", index); put("label", label) }

    private fun history(): JsonObject = synchronized(lock) {
        tick()
        val now = (System.currentTimeMillis() - sessionStart) / 1000.0 + 900
        val times = (0..now.toInt() step 2).map { it.toDouble() }
        buildJsonObject {
            put("now", now)
            put("step", 2.0)
            put("t", JsonArray(times.map(::JsonPrimitive)))
            put("max", JsonArray(times.map { JsonPrimitive(peakAt(it)) }))
            put("avg", JsonArray(times.map { JsonPrimitive(averageAt(it)) }))
            putJsonArray("events") {
                add(event(4.0, "0:00:04", "mode", "SDR", "DV"))
                add(event(12.0, "0:00:12", "audio", "Deutsch", "English"))
                add(event(310.0, "0:05:10", "cache_low", null, null, 18.0))
                add(event(318.0, "0:05:18", "cache_recovered", null, null, 100.0))
                add(event(640.0, "0:10:40", "vs10", "DV-LL", "HDR10"))
                add(event(655.0, "0:10:55", "vs10", "HDR10", "DV-LL"))
            }
            put("seq", 6)
            put("switches", switches)
        }
    }

    private fun event(t: Double, pos: String, kind: String, from: String?, to: String?, value: Double? = null) = buildJsonObject {
        put("t", t); put("pos", pos); put("kind", kind)
        from?.let { put("from", it) }
        to?.let { put("to", it) }
        value?.let { put("value", it) }
    }

    private fun library() = buildJsonObject {
        putJsonArray("movies") {
            films.forEach { f ->
                add(buildJsonObject {
                    put("id", f.id); put("title", f.title); put("year", f.year)
                    put("poster", "movie-${f.id}"); put("duration", f.minutes * 60)
                    put("rating", f.rating); put("rating_from", "imdb")
                    put("watched", f.watched); put("resume", f.resumeMinutes * 60)
                })
            }
        }
        put("count", films.size)
        put("tag", "demo-$libraryRevision")
    }

    private fun series() = buildJsonObject {
        putJsonArray("shows") {
            shows.forEach { s ->
                val total = s.seasons * s.perSeason
                add(buildJsonObject {
                    put("id", s.id); put("title", s.title); put("year", s.year)
                    put("poster", "show-${s.id}"); put("fanart", "fanart-${s.id}")
                    put("episodes", total); put("unseen", total - s.seenUpTo)
                    put("rating", s.rating); put("rating_from", "imdb")
                    put("watched", s.seenUpTo >= total)
                })
            }
        }
        put("count", shows.size)
        put("tag", "demo-$libraryRevision")
    }

    private fun episodes(showId: Int): JsonObject {
        val show = shows.firstOrNull { it.id == showId } ?: return buildJsonObject { put("error", "unknown show") }
        return buildJsonObject {
            put("tvshowid", show.id)
            put("title", show.title)
            putJsonArray("episodes") {
                var n = 0
                for (season in 1..show.seasons) for (episode in 1..show.perSeason) {
                    n++
                    add(buildJsonObject {
                        put("id", episodeId(show, season, episode))
                        put("title", episodeTitle(season, episode))
                        put("season", season); put("episode", episode)
                        put("thumb", "thumb-${episodeId(show, season, episode)}")
                        put("duration", 52 * 60)
                        put("watched", n <= show.seenUpTo)
                        put("resume", if (n == show.seenUpTo + 1 && show.seenUpTo > 0) 19 * 60 else 0)
                    })
                }
            }
            put("count", show.seasons * show.perSeason)
            put("tag", "demo-$libraryRevision")
        }
    }

    private fun continuing() = buildJsonObject {
        putJsonArray("items") {
            films.filter { it.resumeMinutes > 0 }.forEachIndexed { i, f ->
                add(buildJsonObject {
                    put("kind", "movie"); put("id", f.id); put("title", f.title); put("year", f.year)
                    put("poster", "movie-${f.id}"); put("duration", f.minutes * 60); put("resume", f.resumeMinutes * 60)
                    put("lastplayed", "2026-09-%02d 21:00:00".format(22 - i))
                    put("rating", f.rating); put("rating_from", "imdb")
                })
            }
            shows.filter { it.seenUpTo in 1 until it.seasons * it.perSeason }.take(6).forEach { s ->
                val season = s.seenUpTo / s.perSeason + 1
                val episode = s.seenUpTo % s.perSeason + 1
                add(buildJsonObject {
                    put("kind", "episode"); put("id", episodeId(s, season, episode))
                    put("title", episodeTitle(season, episode)); put("poster", "show-${s.id}")
                    put("duration", 52 * 60); put("resume", 19 * 60)
                    put("show", s.title); put("season", season); put("episode", episode)
                    put("lastplayed", "2026-09-19 20:00:00")
                    put("rating", s.rating); put("rating_from", "imdb")
                })
            }
        }
        put(
            "count",
            films.count { it.resumeMinutes > 0 } +
                shows.count { it.seenUpTo in 1 until it.seasons * it.perSeason }.coerceAtMost(6),
        )
        put("tag", "demo-$libraryRevision")
    }

    /* --- Commands --------------------------------------------------------- */

    private fun command(path: String, body: String): JsonObject = synchronized(lock) {
        tick()
        val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        when (path) {
            "/api/play" -> {
                payload["movieid"]?.jsonPrimitive?.int?.let { id -> films.firstOrNull { it.id == id } }?.let { f ->
                    playingTitle = f.title; playingTag = "movie-${f.id}"; playingYear = f.year; playingGenre = f.genre
                    playingShow = ""; playingSeason = ""; playingEpisode = ""
                    durationSeconds = f.minutes * 60; positionSeconds = f.resumeMinutes * 60.0
                }
                payload["episodeid"]?.jsonPrimitive?.int?.let { id ->
                    val show = showOfEpisode(id) ?: return@let
                    val season = id / 100 % 10
                    val episode = id % 100
                    playingTitle = episodeTitle(season, episode); playingTag = "show-${show.id}"; playingYear = show.year
                    playingGenre = "Drama"; playingShow = show.title; playingSeason = "$season"; playingEpisode = "$episode"
                    durationSeconds = 52 * 60; positionSeconds = 0.0
                }
                paused = false
            }
            "/api/mode" -> {
                vs10Output = when (payload["mode"]?.jsonPrimitive?.content) {
                    "hdr10" -> "hdr10"
                    "sdr8" -> "sdr"
                    else -> ""
                }
                switches++
            }
            "/api/command" -> {
                val value = payload["value"]?.jsonPrimitive?.let { runCatching { it.double }.getOrNull() }
                when (payload["action"]?.jsonPrimitive?.content) {
                    "playpause" -> paused = !paused
                    "stop" -> { playingTitle = null; libraryRevision++ }
                    "seek" -> positionSeconds = (positionSeconds + (value ?: 0.0)).coerceIn(0.0, durationSeconds.toDouble())
                    "seek_percent" -> positionSeconds = durationSeconds * (value ?: 0.0) / 100
                    "chapter_next" -> positionSeconds = (positionSeconds + 420).coerceAtMost(durationSeconds.toDouble())
                    "chapter_previous" -> positionSeconds = (positionSeconds - 420).coerceAtLeast(0.0)
                    "volume_up" -> volume = (volume + 5).coerceAtMost(100)
                    "volume_down" -> volume = (volume - 5).coerceAtLeast(0)
                    "mute" -> muted = !muted
                    "audio" -> { audioCurrent = value?.toInt() ?: 0; switches++ }
                    "subtitle" -> {
                        val index = value?.toInt() ?: -1
                        subtitleOn = index >= 0
                        if (index >= 0) subtitleCurrent = index
                    }
                }
            }
        }
        buildJsonObject { put("ok", true); put("action", path.substringAfterLast('/')) }
    }

    /* --- Pictures --------------------------------------------------------- */

    private val palettes = listOf(
        0xFF1D3557 to 0xFFE63946, 0xFF0B3D2E to 0xFF8FD694, 0xFF2B1055 to 0xFFD53369,
        0xFF3A1C08 to 0xFFF4A259, 0xFF0F2027 to 0xFF2C7A99, 0xFF42275A to 0xFFB06AB3,
        0xFF141E30 to 0xFF4A7AB0, 0xFF5C2A09 to 0xFFE0B050, 0xFF1F1C2C to 0xFF928DAB,
        0xFF093028 to 0xFF3CA87A, 0xFF3E0E12 to 0xFFC0392B, 0xFF16222A to 0xFF4A8BA3,
    )

    private fun picture(query: Map<String, String>): ByteArray {
        val kind = query["kind"] ?: "poster"
        val tag = query["v"] ?: "x"
        return art.getOrPut("$kind/$tag") {
            val wide = kind == "fanart" || kind == "thumb"
            val (w, h) = if (wide) 480 to 270 else 300 to 450
            val label = labelFor(tag)
            val (from, to) = palettes[abs(tag.hashCode()) % palettes.size]
            val bitmap = createBitmap(w, h)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, w * 0.6f, h.toFloat(), from.toInt(), to.toInt(), Shader.TileMode.CLAMP)
            })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 255, 255, 255)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = if (wide) 30f else 34f
                letterSpacing = 0.04f
            }
            val lines = wrap(label.uppercase(Locale.ROOT), text, w - 40f)
            var y = h - 28f - (lines.size - 1) * text.textSize * 1.1f
            lines.forEach { line -> canvas.drawText(line, 20f, y, text); y += text.textSize * 1.1f }
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }
    }

    private fun labelFor(tag: String): String {
        val id = tag.substringAfter('-').toIntOrNull() ?: return "TinyPPI"
        return when {
            tag.startsWith("movie-") -> films.firstOrNull { it.id == id }?.title
            tag.startsWith("show-") || tag.startsWith("fanart-") -> shows.firstOrNull { it.id == id }?.title
            tag.startsWith("thumb-") -> "S%02dE%02d".format(id / 100 % 10, id % 100)
            else -> null
        } ?: "TinyPPI"
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width || current.isEmpty()) current = candidate
            else { lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    /* --- HTTP ------------------------------------------------------------- */

    private fun serve(socket: Socket) {
        socket.use {
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
                }
                val parts = requestLine.split(' ')
                val method = parts.getOrElse(0) { "GET" }
                val target = parts.getOrElse(1) { "/" }
                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (length > 0) String(readBytes(input, length), Charsets.UTF_8) else ""
                val out = socket.getOutputStream()

                when {
                    path == "/api/stream" -> stream(out)
                    path == "/api/art" -> respond(out, 200, "image/png", picture(query))
                    method == "POST" -> respondJson(out, command(path, body))
                    path == "/api/hello" -> respondJson(out, buildJsonObject {
                        put("name", "TinyPPI Demo"); put("version", "demo"); put("auth_read", false)
                        put("control", true); put("interval_ms", 1000)
                    })
                    path == "/api/state" -> respondJson(out, snapshot())
                    path == "/api/history" -> respondJson(out, history())
                    path == "/api/library" -> respondJson(out, library())
                    path == "/api/series" -> respondJson(out, series())
                    path == "/api/episodes" -> respondJson(out, episodes(query["tvshowid"]?.toIntOrNull() ?: 0))
                    path == "/api/continue" -> respondJson(out, continuing())
                    else -> respond(out, 404, "application/json", """{"error":"not found"}""".toByteArray())
                }
            } catch (_: IOException) {
                // The app hung up - a closed stream or a cancelled request.
            }
        }
    }

    private fun stream(out: OutputStream) {
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n").toByteArray(),
        )
        out.flush()
        while (true) {
            out.write("event: state\ndata: ${json.encodeToString(JsonObject.serializer(), snapshot())}\n\n".toByteArray())
            out.flush()
            Thread.sleep(1000)
        }
    }

    private fun respondJson(out: OutputStream, body: JsonObject) =
        respond(out, 200, "application/json", json.encodeToString(JsonObject.serializer(), body).toByteArray())

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = if (code == 200) "OK" else "Not Found"
        out.write(
            ("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(body)
        out.flush()
    }

    /** Exactly [length] bytes, or as many as arrive before the app hangs up. */
    private fun readBytes(input: BufferedInputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n == -1) break
            read += n
        }
        return buffer.copyOf(read)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) bytes.write(b)
        }
        return bytes.toString("UTF-8")
    }
}
