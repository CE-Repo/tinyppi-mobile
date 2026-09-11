package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.util.MediaUrls
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the film wall reads what the add-on's `/api/library` sends, and asks
 * for the right pictures.
 *
 * The shapes here are the ones `web/library.py` actually writes: a film
 * carries what a tile draws and nothing else, and everything but the id and
 * the title is left out where the library has none - which is why every field
 * has a default and why the two halves of a resume point have to be checked
 * together before a bar is drawn.
 */
class LibraryTest {

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
    fun `a library reads as its films`() {
        val library = json.decodeFromString(
            Library.serializer(),
            """
            {"count":2,"tag":"2-1f3a9c04","movies":[
              {"id":3,"title":"Blade","year":1998,"poster":"9a1b2c3d",
               "duration":7140,"resume":1200},
              {"id":9,"title":"Dune","year":2021,"duration":9300,"watched":true}
            ]}
            """.trimIndent(),
        )

        assertEquals(2, library.movies.size)
        assertEquals("2-1f3a9c04", library.tag)

        val blade = library.movies[0]
        assertEquals(3, blade.id)
        assertEquals(1998, blade.year)
        assertFalse(blade.watched)
        assertEquals(0.168f, blade.progress!!, 0.001f)

        val dune = library.movies[1]
        assertTrue(dune.watched)
        // No resume point, so no bar - and no poster, so the tile draws the
        // stand-in rather than asking for a picture that is not there.
        assertNull(dune.progress)
        assertNull(MediaUrls.filmPoster(box, dune))
    }

    @Test
    fun `a film with no library entry at all still reads`() {
        val film = json.decodeFromString(LibraryFilm.serializer(), """{"id":7}""")

        assertEquals(7, film.id)
        assertEquals("", film.title)
        assertEquals(0, film.year)
        assertNull(film.progress)
    }

    @Test
    fun `a resume point without a length draws no bar`() {
        // The add-on sends both or neither, but a bar drawn from a position
        // with nothing to measure it against would be a bar of any width.
        val film = LibraryFilm(id = 1, title = "Heat", resume = 900, duration = 0)

        assertNull(film.progress)
    }

    @Test
    fun `a poster is addressed by the film and its own tag`() {
        val film = LibraryFilm(id = 3, title = "Blade", poster = "9a1b2c3d")

        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=poster&movieid=3&v=9a1b2c3d&token=AB+CD",
            MediaUrls.filmPoster(box, film),
        )
    }

    @Test
    fun `a box that is not there is addressed not at all`() {
        val film = LibraryFilm(id = 3, title = "Blade", poster = "9a1b2c3d")

        assertNull(MediaUrls.filmPoster(null, film))
    }

    @Test
    fun `a box with no token leaves the address without one`() {
        val film = LibraryFilm(id = 3, title = "Blade", poster = "9a1b2c3d")

        assertEquals(
            "http://192.168.1.10:8099/api/art?kind=poster&movieid=3&v=9a1b2c3d",
            MediaUrls.filmPoster(box.copy(token = "  "), film),
        )
    }
}
