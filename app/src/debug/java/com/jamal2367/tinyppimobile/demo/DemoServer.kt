package com.jamal2367.tinyppimobile.demo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
import kotlinx.serialization.json.add
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
 * seek, a chapter back, on or to one, stop, the volume, the tracks, VS10, and
 * starting a film or an episode off a shelf.
 */
object DemoServer {
    private const val TAG = "DemoServer"
    private const val PORT = 8099
    private val ADDED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** What the film the box starts on says about itself. */
    private const val DUNE_TAGLINE = "Long live the fighters."

    /** The formats the made-up films are cut in, and the genres of the made-up shows, handed out by id. */
    private val FILM_VIDEO = listOf("dv", "hdr10", "hdr10plus", "sdr")
    private val FILM_AUDIO = listOf("truehd", "eac3", "dtshd_ma", "ac3")
    private val DV_PROFILES = listOf("5", "7 FEL", "8.1")
    private val SHOW_GENRES = listOf("Drama", "Science Fiction", "Crime", "Mystery")
    private const val DUNE_PLOT = "Paul Atreides lives among the Fremen now, learning the desert at Chani's side " +
        "while the Harkonnens tighten their grip on Arrakis. As the prophecy the Fremen believe in closes " +
        "around him, he has to decide how much of himself to give up to bring down the men who destroyed " +
        "his family."

    /** How the film the box starts on is rated, every way the library can. */
    private const val DUNE_MPAA = "FSK 12"
    private const val DUNE_AGE = "fsk12"
    private const val DUNE_AGE_LEVEL = "12"
    private const val DUNE_TOP250 = 27

    /** The big awards Dune: Part Two won or was nominated for, as the add-on's list has them. */
    private val DUNE_AWARDS = listOf(
        DemoAward("oscar", listOf("Visual Effects (2025)", "Sound (2025)"), 3),
        DemoAward("globe", emptyList(), 2),
    )
    private const val DUNE_MINE = 9

    /** Who made it. */
    private val DUNE_CREDITS = Credits(
        directors = listOf("Denis Villeneuve"),
        cast = listOf(
            "Timothée Chalamet", "Zendaya", "Rebecca Ferguson", "Javier Bardem", "Josh Brolin", "Austin Butler",
            "Florence Pugh", "Dave Bautista", "Christopher Walken", "Léa Seydoux",
        ),
        studios = listOf("Legendary Pictures"),
        writers = listOf("Denis Villeneuve", "Jon Spaihts"),
        roles = listOf(
            "Paul Atreides", "Chani", "Lady Jessica", "Stilgar", "Gurney Halleck", "Feyd-Rautha",
            "Princess Irulan", "Rabban", "Emperor Shaddam IV", "Lady Margot Fenring",
        ),
    )

    /** The show that wears an Emmy and a TV age rating on its details. */
    private const val SHOGUN = 103

    /** The TMDb ids of the people the demo links to their TMDb page; the rest lead to TMDb's search. */
    private val PEOPLE = mapOf(
        "Timothée Chalamet" to 1190668, "Zendaya" to 505710, "Rebecca Ferguson" to 933238, "Javier Bardem" to 3810,
        "Josh Brolin" to 16851, "Florence Pugh" to 1373737, "Christopher Walken" to 4690, "Léa Seydoux" to 121529,
        "Dave Bautista" to 543530, "Denis Villeneuve" to 137427, "Hans Zimmer" to 947,
        "Cillian Murphy" to 2037, "Emily Blunt" to 5081, "Matt Damon" to 1892, "Robert Downey Jr." to 3223,
        "Christopher Nolan" to 525,
    )

    /** What a film's chapters are called on the pretend box. */
    private val FILM_CHAPTERS = listOf(
        "Opening", "Arrakis", "The Fremen", "Sietch Tabr", "The Water of Life", "Sandworm",
        "The South", "Prophecy", "The Gathering", "Arrakeen", "The Duel", "Credits",
    )

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

    /**
     * Back to how the box starts: the first film playing where it was left,
     * nothing marked, every setting as it was. For the tests, which share the
     * one box between them.
     */
    internal fun reset() = synchronized(lock) {
        playingTitle = "Dune: Part Two"
        playingTag = "movie-1"
        playingItem = "movie" to 1
        playingYear = 2024
        playingGenre = "Science Fiction"
        playingTagline = DUNE_TAGLINE
        playingPlot = DUNE_PLOT
        playingRatings = filmRatings(films.first())
        playingMpaa = DUNE_MPAA
        playingAge = DUNE_AGE
        playingAgeLevel = DUNE_AGE_LEVEL
        playingTop250 = DUNE_TOP250
        playingAwards = DUNE_AWARDS
        playingMine = DUNE_MINE
        playingCredits = DUNE_CREDITS
        playingShow = ""
        playingSeason = ""
        playingEpisode = ""
        durationSeconds = 166 * 60
        positionSeconds = 71.0 * 60
        paused = false
        volume = 72
        muted = false
        audioCurrent = 0
        subtitleCurrent = 1
        subtitleOn = true
        vs10Output = ""
        switches = 2
        marked.clear()
        cleared.clear()
        libraryRevision++
    }

    /* --- The pretend library --------------------------------------------- */

    private class Film(
        val id: Int, val title: String, val year: Int, val minutes: Int, val rating: Double, val genre: String,
        val watched: Boolean = false, val resumeMinutes: Int = 0, val tagline: String = "", val plot: String = "",
        val mpaa: String = "", val age: String = "", val ageLevel: String = "", val top250: Int = 0,
        val mine: Int = 0, val credits: Credits = Credits(), val awards: List<DemoAward> = emptyList(),
        val tmdb: FromTmdb? = null,
    )

    /** One house's score, out of ten; [certified] for Rotten Tomatoes' seals. */
    private class DemoRating(val source: String, val rating: Double, val votes: Int, val certified: Boolean = false)

    /**
     * What the add-on adds to a film's details from TMDb: where it was made,
     * when it first ran in cinemas, what it cost and took, and the rest of
     * its crew under their jobs.
     */
    private class FromTmdb(
        val countries: List<String>, val cinema: String, val budget: Long, val revenue: Long,
        val crew: List<Pair<String, String>>,
    )

    /** One of the big awards a title won or was nominated for, as the add-on sends it. */
    private class DemoAward(val award: String, val won: List<String>, val nominated: Int)

    /** Who made a title: its directors, cast and studios. */
    private class Credits(
        val directors: List<String> = emptyList(),
        val cast: List<String> = emptyList(),
        val studios: List<String> = emptyList(),
        val writers: List<String> = emptyList(),
        val roles: List<String> = emptyList(),
    )
    private class Show(val id: Int, val title: String, val year: Int, val rating: Double, val seasons: Int, val perSeason: Int, val seenUpTo: Int) {
        /** Every episode as season and number, in the order they are watched. */
        val episodes: List<Pair<Int, Int>> = (1..seasons).flatMap { season -> (1..perSeason).map { season to it } }

        /** Whether it has been started and not finished, for the continue row. */
        val inProgress: Boolean get() = seenUpTo < episodes.size
    }

    private val films = listOf(
        Film(
            1, "Dune: Part Two", 2024, 166, 8.5, "Science Fiction", resumeMinutes = 71, tagline = DUNE_TAGLINE,
            plot = DUNE_PLOT, mpaa = DUNE_MPAA, age = DUNE_AGE, ageLevel = DUNE_AGE_LEVEL, top250 = DUNE_TOP250,
            mine = DUNE_MINE, credits = DUNE_CREDITS, awards = DUNE_AWARDS,
            tmdb = FromTmdb(
                countries = listOf("United States of America", "Canada"), cinema = "2024-02-29",
                budget = 190_000_000, revenue = 714_844_358,
                crew = listOf(
                    "Mary Parent" to "Producer", "Cale Boyter" to "Producer", "Tanya Lapointe" to "Producer",
                    "Hans Zimmer" to "Original Music Composer", "Greig Fraser" to "Director of Photography",
                    "Joe Walker" to "Editor", "Francine Maisler" to "Casting", "Patrice Vermette" to "Production Design",
                    "Jacqueline West" to "Costume Design", "Paul Lambert" to "Visual Effects Supervisor",
                    "Frank Herbert" to "Novel",
                ),
            ),
        ),
        // A slogan and a plot for the first two only: the rest play without
        // them, the way a file the library knows little about does.
        Film(
            2, "Oppenheimer", 2023, 180, 8.3, "Drama", resumeMinutes = 40,
            // Rated by the American board, as a scraper set to the US writes it.
            tagline = "The world forever changes.", mpaa = "Rated R", age = "mpa_r", ageLevel = "R",
            plot = "J. Robert Oppenheimer is asked to lead the race for the atomic bomb from a laboratory " +
                "built in the New Mexico desert. Years after Los Alamos, a closed security hearing turns " +
                "his own past against him.",
            credits = Credits(
                directors = listOf("Christopher Nolan"),
                cast = listOf(
                    "Cillian Murphy", "Emily Blunt", "Matt Damon", "Robert Downey Jr.", "Florence Pugh", "Josh Hartnett",
                    "Casey Affleck",
                ),
                studios = listOf("Syncopy", "Atlas Entertainment"),
                writers = listOf("Christopher Nolan"),
                // Who the last of them played the library does not say.
                roles = listOf(
                    "J. Robert Oppenheimer", "Kitty Oppenheimer", "Leslie Groves", "Lewis Strauss", "Jean Tatlock",
                    "Ernest Lawrence",
                ),
            ),
            // Rated by the house it is a favourite of as well.
            top250 = 61, mine = 10,
            tmdb = FromTmdb(
                countries = listOf("United Kingdom", "United States of America"), cinema = "2023-07-20",
                budget = 100_000_000, revenue = 975_811_333,
                crew = listOf(
                    "Emma Thomas" to "Producer", "Charles Roven" to "Producer",
                    "Ludwig Göransson" to "Original Music Composer", "Hoyte van Hoytema" to "Director of Photography",
                    "Jennifer Lame" to "Editor", "John Papsidera" to "Casting", "Ruth De Jong" to "Production Design",
                    "Ellen Mirojnick" to "Costume Design", "Kai Bird" to "Novel", "Martin J. Sherwin" to "Novel",
                ),
            ),
            awards = listOf(
                DemoAward(
                    "oscar",
                    listOf(
                        "Best Picture (2024)", "Actor in a Leading Role (2024)", "Actor in a Supporting Role (2024)",
                        "Cinematography (2024)", "Directing (2024)", "Film Editing (2024)", "Music (Original Score) (2024)",
                    ),
                    6,
                ),
                DemoAward("globe", listOf("Best Motion Picture – Drama (2024)", "Best Director – Motion Picture (2024)"), 2),
                DemoAward("bafta", listOf("Best Film (2024)", "Best Director (2024)"), 0),
            ),
        ),
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
        // Rated by the British board, as a scraper set to the UK writes it.
        Film(14, "1917", 2019, 119, 8.2, "War", watched = true, mpaa = "15", age = "bbfc_15", ageLevel = "15"),
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

    /**
     * A film's scores, made up from its rating: IMDb's with a count of votes
     * in proportion, and TMDb's a little under it with far fewer.
     */
    private fun filmRatings(film: Film): List<DemoRating> {
        val votes = (film.rating * 72_000).toInt()
        val scraped = listOf(DemoRating("imdb", film.rating, votes), DemoRating("tmdb", film.rating - 0.3, votes / 90))
        // The films the library knows best wear every shield there is: every
        // house MDBList gathers, both of Rotten Tomatoes' seals, and MDBList's
        // own score; the rest as a scraper leaves them.
        if (film.plot.isEmpty()) return scraped
        return scraped + listOf(
            DemoRating("trakt", film.rating - 0.1, votes / 20),
            DemoRating("tvdb", film.rating - 0.4, votes / 300),
            DemoRating("letterboxd", film.rating + 0.3, votes * 2),
            DemoRating("metacritic", film.rating - 0.6, 67),
            DemoRating("metacriticuser", film.rating - 0.2, 2_400),
            DemoRating("rottentomatoes", (film.rating + 0.7).coerceAtMost(9.9), 410, certified = true),
            DemoRating("popcorn", (film.rating + 1.0).coerceAtMost(9.9), 10_000, certified = true),
            DemoRating("rogerebert", 8.8, 0),
            DemoRating("myanimelist", film.rating - 0.5, 1_200),
            DemoRating("mdblist", film.rating + 0.1, 0),
        )
    }

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

    /** Which library title is playing, as kind and id, for its details. */
    private var playingItem = "movie" to 1
    private var playingYear = 2024
    private var playingGenre = "Science Fiction"
    private var playingTagline = DUNE_TAGLINE
    private var playingPlot = DUNE_PLOT
    private var playingRatings = filmRatings(films.first())
    private var playingMpaa = DUNE_MPAA
    private var playingAge = DUNE_AGE
    private var playingAgeLevel = DUNE_AGE_LEVEL
    private var playingTop250 = DUNE_TOP250
    private var playingAwards = DUNE_AWARDS
    private var playingMine = DUNE_MINE
    private var playingCredits = DUNE_CREDITS
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
    private var audioDelay = 0.0
    private var subtitleDelay = 0.0
    private var vs10Output = ""
    private var seq = 0L
    private var switches = 2
    private var libraryRevision = 1L

    /**
     * What has been marked seen or unseen from the app, by film id or by
     * episode id - the two never collide, an episode's id carrying its show's
     * in the thousands. Anything not in here is as the made-up library has it.
     */
    private val marked = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    private fun filmWatched(film: Film) = marked[film.id] ?: film.watched

    /** Films whose resume point has been forgotten from the app. */
    private val cleared: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Whether a film still stands on the continue-watching row. */
    private val Film.resumesAt: Boolean
        get() = resumeMinutes > 0 && id !in cleared && !filmWatched(this)

    private fun episodeWatched(show: Show, season: Int, episode: Int, n: Int) =
        marked[episodeId(show, season, episode)] ?: (n <= show.seenUpTo)

    private fun unseenOf(show: Show): Int = show.episodes.withIndex()
        .count { (index, episode) -> !episodeWatched(show, episode.first, episode.second, index + 1) }
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

    /* --- Chapters ---------------------------------------------------------- */

    /**
     * The playing title cut into equal chapters, the way the box sends them:
     * a film gets twelve with names, an episode six without - most episodes
     * name none.
     */
    private val chapterCount get() = if (playingShow.isBlank()) FILM_CHAPTERS.size else 6

    private val chapterLength get() = durationSeconds / chapterCount

    /** The chapter the position is in, one-based like Kodi's. */
    private fun chapterNow(): Int = (positionSeconds / chapterLength).toInt().coerceAtMost(chapterCount - 1) + 1

    private fun chapterStart(index: Int): Double = ((index - 1) * chapterLength).toDouble()

    private fun chapters() = buildJsonArray {
        repeat(chapterCount) { offset ->
            val index = offset + 1
            val start = chapterStart(index)
            add(buildJsonObject {
                put("index", index)
                put("start", start.toInt())
                put("at", start * 100 / durationSeconds)
                put("time", clock(start))
                put("name", if (playingShow.isBlank()) FILM_CHAPTERS[index - 1] else "")
            })
        }
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
                    put("events", 5)
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
            // No file name: the add-on sends one only with its own setting for
            // it switched on, and the card is meant to look as it does without.
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
                put("tagline", playingTagline)
                put("plot", playingPlot)
                putJsonArray("ratings") {
                    playingRatings.forEach { add(rating(it)) }
                }
                put("mpaa", playingMpaa)
                // The mark the add-on reads the age rating as, and its level.
                put("age", playingAge)
                put("age_level", playingAgeLevel)
                put("userrating", playingMine)
                put("top250", playingTop250)
                put("awards", awardsJson(playingAwards))
                putJsonObject("item") { put("kind", playingItem.first); put("id", playingItem.second) }
                // Who made it, as the add-on sends it: every director and the
                // whole cast, the studio the first alone.
                putJsonArray("directors") { playingCredits.directors.forEach { add(it) } }
                putJsonArray("cast") { playingCredits.cast.forEach { add(it) } }
                putJsonArray("roles") { playingCredits.roles.forEach { add(it) } }
                putJsonArray("writers") { playingCredits.writers.forEach { add(it) } }
                putJsonArray("genres") { add(playingGenre) }
                put("cast_more", false)
                putJsonArray("studios") { playingCredits.studios.take(1).forEach { add(it) } }
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
                put("chapters", chapterCount)
                put("audio_delay", audioDelay)
                put("subtitle_delay", subtitleDelay)
            }
            put("chapters", chapters())
            put("chapter", chapterNow())
            putJsonObject("session") {
                put("seq", 5)
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
                add(event(310.0, "0:05:10", "temperature", null, null, 77.0))
                add(event(640.0, "0:10:40", "vs10", "DV-LL", "HDR10"))
                add(event(655.0, "0:10:55", "vs10", "HDR10", "DV-LL"))
            }
            put("seq", 5)
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
                    put("watched", filmWatched(f)); put("resume", if (filmWatched(f) || f.id in cleared) 0 else f.resumeMinutes * 60)
                    put("added", addedDaysAgo((f.id * 7) % films.size, f.id))
                    putJsonArray("genres") { add(f.genre) }
                    put("video", FILM_VIDEO[f.id % FILM_VIDEO.size])
                    if (f.id % FILM_VIDEO.size == 0) put("dv_profile", DV_PROFILES[f.id % DV_PROFILES.size])
                    put("resolution", if (f.id % 3 == 0) "1080p" else "2160p")
                    put("audio", FILM_AUDIO[f.id % FILM_AUDIO.size])
                })
            }
        }
        put("count", films.size)
        put("tag", "demo-$libraryRevision")
    }

    /**
     * When a made-up title arrived, the way Kodi writes it, counted back from
     * today so the row of what arrived last always looks like last week. The
     * callers spread the days across the library with a step that shares no
     * factor with its size, so no two titles land on the same day and the row
     * is not simply the list read backwards.
     */
    private fun addedDaysAgo(days: Int, id: Int): String =
        LocalDateTime.now()
            .minusDays(days.toLong())
            .withHour(8 + id % 12)
            .withMinute(id * 7 % 60)
            .withSecond(0)
            .format(ADDED_FORMAT)

    private fun series() = buildJsonObject {
        putJsonArray("shows") {
            shows.forEach { s ->
                val total = s.seasons * s.perSeason
                add(buildJsonObject {
                    put("id", s.id); put("title", s.title); put("year", s.year)
                    put("poster", "show-${s.id}"); put("fanart", "fanart-${s.id}")
                    val unseen = unseenOf(s)
                    put("episodes", total); put("unseen", unseen)
                    put("rating", s.rating); put("rating_from", "imdb")
                    put("watched", unseen == 0)
                    put("added", addedDaysAgo(((s.id - 100) * 4) % shows.size, s.id))
                    putJsonArray("genres") { add(SHOW_GENRES[s.id % SHOW_GENRES.size]) }
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
                show.episodes.forEachIndexed { index, (season, episode) ->
                    val n = index + 1
                    add(buildJsonObject {
                        put("id", episodeId(show, season, episode))
                        put("title", episodeTitle(season, episode))
                        put("season", season); put("episode", episode)
                        put("thumb", "thumb-${episodeId(show, season, episode)}")
                        put("duration", 52 * 60)
                        put("watched", episodeWatched(show, season, episode, n))
                        put("resume", if (n == show.seenUpTo + 1) 19 * 60 else 0)
                    })
                }
            }
            put("count", show.seasons * show.perSeason)
            put("tag", "demo-$libraryRevision")
        }
    }

    private fun continuing() = buildJsonObject {
        putJsonArray("items") {
            films.filter { it.resumesAt }.forEachIndexed { i, f ->
                add(buildJsonObject {
                    put("kind", "movie"); put("id", f.id); put("title", f.title); put("year", f.year)
                    put("poster", "movie-${f.id}"); put("duration", f.minutes * 60); put("resume", f.resumeMinutes * 60)
                    put("lastplayed", "2026-09-%02d 21:00:00".format(22 - i))
                    put("rating", f.rating); put("rating_from", "imdb")
                })
            }
            shows.filter { it.inProgress }.take(6).forEach { s ->
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
        // The episode after the one each show was stopped in: what comes next.
        putJsonArray("next") {
            shows.filter { it.seenUpTo + 1 < it.episodes.size }.take(6).forEach { s ->
                val (season, episode) = s.episodes[s.seenUpTo + 1]
                add(buildJsonObject {
                    put("kind", "episode"); put("id", episodeId(s, season, episode))
                    put("title", episodeTitle(season, episode)); put("poster", "show-${s.id}")
                    put("duration", 52 * 60); put("show", s.title); put("season", season); put("episode", episode)
                    put("rating", s.rating); put("rating_from", "imdb")
                })
            }
        }
        put(
            "count",
            films.count { it.resumesAt } +
                shows.count { it.inProgress }.coerceAtMost(6),
        )
        put("tag", "demo-$libraryRevision")
    }

    /** One title's details, as `/api/details` answers them; null for a title the box does not have. */
    private fun details(query: Map<String, String>): JsonObject? {
        query["movieid"]?.toIntOrNull()?.let { id -> return films.firstOrNull { it.id == id }?.let(::filmDetails) }
        query["tvshowid"]?.toIntOrNull()?.let { id ->
            val show = shows.firstOrNull { it.id == id } ?: return null
            return buildJsonObject {
                put("kind", "tvshow"); put("id", show.id); put("title", show.title); put("year", show.year)
                put("season", show.seasons)
                put("poster", "show-${show.id}"); put("fanart", "fanart-${show.id}")
                putJsonArray("genres") { add(SHOW_GENRES[show.id % SHOW_GENRES.size]) }
                putJsonArray("ratings") { add(rating(DemoRating("imdb", show.rating, (show.rating * 40_000).toInt()))) }
                // Shōgun wears the shields the films do not: a US TV age
                // rating and the Emmys it won.
                if (show.id == SHOGUN) {
                    put("mpaa", "TV-MA"); put("age", "tv_ma"); put("age_level", "MA")
                    put("awards", awardsJson(listOf(DemoAward("emmy", listOf("Outstanding Drama Series (2024)"), 7))))
                }
            }
        }
        val id = query["episodeid"]?.toIntOrNull() ?: return null
        val show = showOfEpisode(id) ?: return null
        val season = id % 1000 / 100
        val episode = id % 100
        return buildJsonObject {
            put("kind", "episode"); put("id", id); put("title", episodeTitle(season, episode))
            put("show", show.title); put("tvshowid", show.id); put("season", season); put("episode", episode)
            put("duration", 52 * 60); put("thumb", "thumb-$id")
            putJsonArray("ratings") { add(rating(DemoRating("imdb", show.rating, 0))) }
        }
    }

    private fun filmDetails(film: Film) = buildJsonObject {
        put("kind", "movie"); put("id", film.id); put("title", film.title); put("year", film.year)
        put("duration", film.minutes * 60); put("plot", film.plot); put("tagline", film.tagline)
        put("mpaa", film.mpaa); put("userrating", film.mine); put("top250", film.top250)
        put("age", film.age); put("age_level", film.ageLevel)
        // The same awards the overview card shows for the film.
        put("awards", awardsJson(film.awards))
        put("poster", "movie-${film.id}"); put("fanart", "fanart-${film.id}")
        putJsonArray("genres") { add(film.genre) }
        putJsonArray("directors") { film.credits.directors.forEach { add(it) } }
        putJsonArray("studios") { film.credits.studios.forEach { add(it) } }
        putJsonArray("writers") { film.credits.writers.forEach { add(it) } }
        val tmdb = film.tmdb
        putJsonArray("cast") {
            film.credits.cast.forEachIndexed { index, name ->
                add(buildJsonObject {
                    put("name", name); put("role", film.credits.roles.getOrNull(index).orEmpty())
                    if (tmdb != null) person(name)
                })
            }
        }
        if (tmdb != null) {
            // What the add-on adds with a TMDb key: Kodi's directors and
            // writers under their codes, then the rest of the crew under
            // their jobs, each with their TMDb id and a portrait where it has
            // them (a name without an id leads to TMDb's search).
            putJsonArray("crew") {
                val kodi = film.credits.directors.map { it to "director" } + film.credits.writers.map { it to "writer" }
                (kodi + tmdb.crew).forEach { (name, role) ->
                    add(buildJsonObject { put("name", name); put("role", role); person(name) })
                }
            }
            putJsonArray("countries") { tmdb.countries.forEach { add(it) } }
            put("cinema", tmdb.cinema); put("budget", tmdb.budget); put("revenue", tmdb.revenue)
        }
        putJsonArray("ratings") { filmRatings(film).forEach { add(rating(it)) } }
        put("video", FILM_VIDEO[film.id % FILM_VIDEO.size])
        if (film.id % FILM_VIDEO.size == 0) put("dv_profile", DV_PROFILES[film.id % DV_PROFILES.size])
        put("resolution", if (film.id % 3 == 0) "1080p" else "2160p")
        put("audio", FILM_AUDIO[film.id % FILM_AUDIO.size])
    }

    private fun awardsJson(awards: List<DemoAward>) = buildJsonArray {
        awards.forEach { award ->
            add(
                buildJsonObject {
                    put("award", award.award)
                    putJsonArray("won") { award.won.forEach { add(it) } }
                    put("nominated", award.nominated)
                },
            )
        }
    }

    private fun rating(rating: DemoRating) = buildJsonObject {
        put("source", rating.source); put("rating", rating.rating); put("votes", rating.votes)
        if (rating.certified) put("certified", true)
    }

    /** A person's TMDb id, where the demo knows it, and their portrait. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.person(name: String) {
        PEOPLE[name]?.let { put("tmdb", it) }
        put("thumbnail", "person-${abs(name.hashCode())}")
    }

    /* --- Commands --------------------------------------------------------- */

    private fun command(path: String, body: String): JsonObject = synchronized(lock) {
        tick()
        val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val fromStart = payload["resume"]?.jsonPrimitive?.content == "false"
        when (path) {
            "/api/play" -> {
                payload["movieid"]?.jsonPrimitive?.int?.let { id -> films.firstOrNull { it.id == id } }?.let { f ->
                    playingTitle = f.title; playingTag = "movie-${f.id}"; playingYear = f.year; playingGenre = f.genre
                    playingItem = "movie" to f.id
                    playingTagline = f.tagline; playingPlot = f.plot
                    playingRatings = filmRatings(f); playingMpaa = f.mpaa; playingAge = f.age; playingAgeLevel = f.ageLevel
                    playingTop250 = f.top250; playingMine = f.mine; playingCredits = f.credits; playingAwards = f.awards
                    playingShow = ""; playingSeason = ""; playingEpisode = ""
                    durationSeconds = f.minutes * 60
                    positionSeconds = if (fromStart || filmWatched(f)) 0.0 else f.resumeMinutes * 60.0
                }
                payload["episodeid"]?.jsonPrimitive?.int?.let { id ->
                    val show = showOfEpisode(id) ?: return@let
                    val season = id / 100 % 10
                    val episode = id % 100
                    playingTitle = episodeTitle(season, episode); playingTag = "show-${show.id}"; playingYear = show.year
                    playingItem = "episode" to id
                    playingGenre = "Drama"; playingShow = show.title; playingSeason = "$season"; playingEpisode = "$episode"
                    // An episode has a plot and, as most do, no slogan.
                    playingTagline = ""
                    playingPlot = "Season $season, episode $episode of ${show.title}, made up for the demo box like every episode on it."
                    // Rated on its own, with no count, and its show's age rating.
                    playingRatings = listOf(DemoRating("tvdb", show.rating, 0))
                    playingMpaa = "TV-MA"; playingAge = "tv_ma"; playingAgeLevel = "MA"; playingTop250 = 0; playingMine = 0
                    playingAwards = emptyList()
                    // Its show's studio, its own director, and its guests
                    // before the show's cast.
                    playingCredits = Credits(
                        directors = listOf("Alex Example"),
                        cast = listOf("Sam Guest", "Robin Lead", "Kim Second"), studios = listOf("Demo Studios"),
                    )
                    durationSeconds = 52 * 60; positionSeconds = 0.0
                }
                paused = false
            }
            "/api/watched" -> {
                val watched = payload["watched"]?.jsonPrimitive?.content == "true"
                payload["movieid"]?.jsonPrimitive?.int?.let { marked[it] = watched }
                payload["episodeid"]?.jsonPrimitive?.int?.let { marked[it] = watched }
                payload["tvshowid"]?.jsonPrimitive?.int?.let { id -> shows.firstOrNull { it.id == id } }?.let { show ->
                    show.episodes.forEach { (season, episode) -> marked[episodeId(show, season, episode)] = watched }
                }
                libraryRevision++
            }
            "/api/resume" -> {
                // Where the film got to is forgotten; its tick is left alone.
                payload["movieid"]?.jsonPrimitive?.int?.let { id ->
                    films.firstOrNull { it.id == id }?.let { cleared += it.id }
                }
                libraryRevision++
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
                    "chapter_next" -> positionSeconds = chapterStart((chapterNow() + 1).coerceAtMost(chapterCount))
                    "chapter_previous" -> positionSeconds = chapterStart((chapterNow() - 1).coerceAtLeast(1))
                    "chapter_goto" -> positionSeconds = chapterStart((value?.toInt() ?: 1).coerceIn(1, chapterCount))
                    "volume_up" -> volume = (volume + 5).coerceAtMost(100)
                    "volume_down" -> volume = (volume - 5).coerceAtLeast(0)
                    "mute" -> muted = !muted
                    "audio" -> { audioCurrent = value?.toInt() ?: 0; switches++ }
                    "subtitle" -> {
                        val index = value?.toInt() ?: -1
                        subtitleOn = index >= 0
                        if (index >= 0) subtitleCurrent = index
                    }
                    "audio_delay" -> audioDelay = value ?: 0.0
                    "subtitle_delay" -> subtitleDelay = value ?: 0.0
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
        query["portrait"]?.let { return portrait(it) }
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

    /** A person's made-up portrait: a figure on a square of colour, the same one for the same tag. */
    private fun portrait(tag: String): ByteArray = art.getOrPut("portrait/$tag") {
        val size = 300
        val (from, to) = palettes[abs(tag.hashCode()) % palettes.size]
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from.toInt(), to.toInt(), Shader.TileMode.CLAMP)
        })
        val figure = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
        canvas.drawCircle(150f, 118f, 58f, figure)
        canvas.drawOval(45f, 198f, 255f, 382f, figure)
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
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
            if (current.isEmpty() || paint.measureText(candidate) <= width) current = candidate
            else { lines += current; current = word }
        }
        lines += current
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
                val method = parts[0]
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
                    path == "/api/details" -> details(query)?.let { respondJson(out, it) }
                        ?: respond(out, 404, "application/json", """{"error":"no such title"}""".toByteArray())
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
        try {
            while (true) {
                out.write("event: state\ndata: ${json.encodeToString(JsonObject.serializer(), snapshot())}\n\n".toByteArray())
                out.flush()
                Thread.sleep(1000)
            }
        } catch (_: IOException) {
            // The app closed the stream.
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
