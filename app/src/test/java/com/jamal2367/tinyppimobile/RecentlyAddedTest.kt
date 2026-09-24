package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.ui.live.RECENT_LIMIT
import com.jamal2367.tinyppimobile.ui.live.newest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the row of what arrived last reads the date the add-on sends with each
 * film and show, puts the newest first, and holds no more than it should.
 */
class RecentlyAddedTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun theDateIsRead() {
        val library = json.decodeFromString<Library>(
            """{"movies":[{"id":7,"title":"Dune","added":"2026-09-22 20:15:00"}],"count":1,"tag":"x"}""",
        )
        assertEquals("2026-09-22 20:15:00", library.movies.single().added)

        val shows = json.decodeFromString<SeriesLibrary>(
            """{"shows":[{"id":3,"title":"Severance","added":"2026-09-20 08:00:00"}],"count":1,"tag":"y"}""",
        )
        assertEquals("2026-09-20 08:00:00", shows.shows.single().added)
    }

    @Test
    fun anOlderAddOnSendsNoDateAndThereIsNoRow() {
        val library = json.decodeFromString<Library>(
            """{"movies":[{"id":7,"title":"Dune"}],"count":1,"tag":"x"}""",
        )
        assertTrue(newest(library.movies, { it.added }, { it.id }).isEmpty())
    }

    @Test
    fun newestFirstAndTiesByTheLaterId() {
        val films = listOf(
            LibraryFilm(id = 1, title = "A", added = "2026-01-01 10:00:00"),
            LibraryFilm(id = 2, title = "B", added = "2026-09-01 10:00:00"),
            LibraryFilm(id = 3, title = "C", added = "2026-09-01 10:00:00"),
            LibraryFilm(id = 4, title = "D"),
        )
        assertEquals(listOf(3, 2, 1), newest(films, { it.added }, { it.id }).map { it.id })
    }

    @Test
    fun theRowHoldsTen() {
        val films = (1..25).map { LibraryFilm(id = it, title = "$it", added = "2026-09-%02d 12:00:00".format(it)) }
        val row = newest(films, { it.added }, { it.id })
        assertEquals(RECENT_LIMIT, row.size)
        assertEquals(25, row.first().id)
    }
}
