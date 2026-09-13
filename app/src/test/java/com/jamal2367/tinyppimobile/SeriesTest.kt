package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.util.MediaUrls
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the series wall reads what the add-on's `/api/series` and
 * `/api/episodes` send, and asks for the right pictures.
 *
 * The shapes here are the ones `web/library.py` actually writes: a show or an
 * episode carries what its tile or its row draws and nothing else, and
 * everything the library has no answer for is left out - which is why every
 * field has a default, and why a season it says nothing about has to read as
 * "no season" rather than as season zero, which is where the specials live.
 */
class SeriesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val box = ServerConfig(
        enabled = true,
        host = "192.168.1.10",
        port = 8099,
        token = "AB CD",
    )

    @Test
    fun `a shelf reads as its series`() {
        val shelf = json.decodeFromString(
            SeriesLibrary.serializer(),
            """
            {"count":2,"tag":"2-9f0a1b2c","shows":[
              {"id":4,"title":"The Wire","year":2002,"poster":"723bceb2",
               "episodes":60,"unseen":3,"rating":9.3,"rating_from":"imdb",
               "fanart":"5c1d9e2f"},
              {"id":9,"title":"Chernobyl","year":2019,"poster":"ff75a665",
               "episodes":5,"unseen":0,"watched":true,
               "rating":8.7,"rating_from":"tmdb"}
            ]}
            """.trimIndent(),
        )

        assertEquals(2, shelf.shows.size)
        assertEquals("2-9f0a1b2c", shelf.tag)

        val wire = shelf.shows[0]
        assertEquals(3, wire.unseen)
        assertFalse(wire.watched)
        assertEquals(9.3, wire.rating, 0.001)
        assertEquals("imdb", wire.ratingFrom)
        // The picture its episode list stands under, at the shape a television
        // is - which only a show carries.
        assertEquals("5c1d9e2f", wire.fanart)

        val chernobyl = shelf.shows[1]
        assertEquals(0, chernobyl.unseen)
        assertTrue(chernobyl.watched)
        // The other house, where that is the one the library holds.
        assertEquals("tmdb", chernobyl.ratingFrom)
    }

    @Test
    fun `a series with no library entry at all still reads`() {
        val show = json.decodeFromString(LibraryShow.serializer(), """{"id":7}""")

        assertEquals(7, show.id)
        assertEquals("", show.title)
        assertEquals(0, show.unseen)
        assertFalse(show.watched)
        assertEquals("", show.fanart)
        assertEquals(0.0, show.rating, 0.0)
        assertEquals("", show.ratingFrom)
    }

    @Test
    fun `an episode list reads as its episodes`() {
        val opened = json.decodeFromString(
            EpisodeList.serializer(),
            """
            {"tvshowid":4,"title":"The Wire","count":3,"tag":"4-3-1f3a9c04",
             "episodes":[
              {"id":102,"title":"A Special","season":0,"episode":1},
              {"id":100,"title":"Old Cases","season":1,"episode":4,
               "thumb":"9a1b2c3d","duration":3540,"watched":true},
              {"id":101,"title":"","season":1,"episode":5,"duration":3600,
               "resume":900}
            ]}
            """.trimIndent(),
        )

        assertEquals("The Wire", opened.title)
        assertEquals(3, opened.episodes.size)

        val (special, seen, halfWatched) = opened.episodes
        // Season zero is where Kodi files the specials, and it is a season the
        // list has an answer for - unlike one it says nothing about at all.
        assertEquals(0, special.season)
        assertEquals("E01", special.code)

        assertTrue(seen.watched)
        assertNull(seen.progress)
        assertEquals("S01E04", seen.code)
        // The length the row now writes beside that number.
        assertEquals(3540, seen.duration)

        assertEquals(0.25f, halfWatched.progress!!, 0.001f)
        // No name of its own, so the row calls it by its number.
        assertEquals("", halfWatched.title)
        assertEquals("S01E05", halfWatched.code)
    }

    @Test
    fun `an episode the library files under no season says so`() {
        val episode = json.decodeFromString(LibraryEpisode.serializer(), """{"id":7}""")

        assertEquals(-1, episode.season)
        assertEquals(-1, episode.episode)
        assertEquals("", episode.code)
        assertNull(episode.progress)
    }

    @Test
    fun `a series cover is addressed by the show and its own tag`() {
        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=fanart&tvshowid=4&v=5c1d9e2f&token=AB+CD",
            MediaUrls.showFanart(box, 4, "5c1d9e2f"),
        )
        // A show the library scraped no fanart for is not addressed at all,
        // and the episode list draws its stand-in instead.
        assertNull(MediaUrls.showFanart(box, 4, ""))
    }

    @Test
    fun `a poster is addressed by the series and its own tag`() {
        val show = LibraryShow(id = 4, title = "The Wire", poster = "723bceb2")

        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=poster&tvshowid=4&v=723bceb2&token=AB+CD",
            MediaUrls.showPoster(box, show),
        )
    }

    @Test
    fun `a still is addressed by the episode and its own tag`() {
        val episode = LibraryEpisode(id = 100, title = "Old Cases", thumb = "9a1b2c3d")

        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=thumb&episodeid=100&v=9a1b2c3d&token=AB+CD",
            MediaUrls.episodeStill(box, episode),
        )
    }

    @Test
    fun `nothing without a picture of its own is addressed at all`() {
        assertNull(MediaUrls.showPoster(box, LibraryShow(id = 4, title = "The Wire")))
        assertNull(MediaUrls.episodeStill(box, LibraryEpisode(id = 100)))
        assertNull(MediaUrls.showPoster(null, LibraryShow(id = 4, poster = "723bceb2")))
    }
}
