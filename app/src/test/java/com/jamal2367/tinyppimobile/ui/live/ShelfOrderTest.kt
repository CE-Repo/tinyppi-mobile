package com.jamal2367.tinyppimobile.ui.live

import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order and the filters over a shelf, and what each filter offers. */
class ShelfOrderTest {

    private val films = listOf(
        LibraryFilm(1, "Alien", 1979, duration = 7020, rating = 8.5, added = "2026-09-01",
                    genres = listOf("Horror", "Sci-Fi"), video = "sdr", resolution = "1080p", audio = "dts"),
        LibraryFilm(2, "Brazil", 1985, duration = 8520, rating = 7.9,
                    genres = listOf("Comedy"), video = "dv", resolution = "2160p", audio = "truehd"),
        LibraryFilm(3, "Cube", duration = 5400, added = "2026-09-03",
                    genres = listOf("Sci-Fi"), video = "hdr10", resolution = "2160p", audio = "truehd"),
        LibraryFilm(4, "Dune", 2021, rating = 6.0, added = "2026-09-02"),
        LibraryFilm(5, "Eraserhead"),
    )

    private fun titles(list: List<LibraryFilm>) = list.map { it.title }

    @Test
    fun `every order puts the titles without its value last`() {
        val none = ShelfFilter()
        assertEquals(listOf("Alien", "Brazil", "Cube", "Dune", "Eraserhead"), titles(films.arranged(ShelfSort.TITLE, none)))
        assertEquals(listOf("Dune", "Brazil", "Alien", "Cube", "Eraserhead"), titles(films.arranged(ShelfSort.YEAR, none)))
        assertEquals(listOf("Alien", "Brazil", "Dune", "Cube", "Eraserhead"), titles(films.arranged(ShelfSort.RATING, none)))
        assertEquals(listOf("Cube", "Dune", "Alien", "Brazil", "Eraserhead"), titles(films.arranged(ShelfSort.ADDED, none)))
        assertEquals(listOf("Cube", "Alien", "Brazil", "Dune", "Eraserhead"), titles(films.arranged(ShelfSort.RUNTIME, none)))
    }

    @Test
    fun `the filters narrow together`() {
        assertEquals(listOf("Alien", "Cube"), titles(films.arranged(ShelfSort.TITLE, ShelfFilter(genre = "Sci-Fi"))))
        assertEquals(listOf("Cube"), titles(films.arranged(ShelfSort.TITLE, ShelfFilter(genre = "Sci-Fi", resolution = "2160p"))))
        assertEquals(listOf("Brazil"), titles(films.arranged(ShelfSort.TITLE, ShelfFilter(video = "dv", audio = "truehd"))))
        assertEquals(emptyList<String>(), titles(films.arranged(ShelfSort.TITLE, ShelfFilter(audio = "flac"))))
    }

    @Test
    fun `shows sort and filter by genre`() {
        val shows = listOf(
            LibraryShow(1, "Andor", 2022, rating = 8.4, genres = listOf("Sci-Fi"), added = "2026-01-01"),
            LibraryShow(2, "Bluey", 2018, genres = listOf("Family")),
            LibraryShow(3, "Columbo", 1971, rating = 8.2, genres = listOf("Crime")),
        )
        assertEquals(listOf("Andor", "Columbo", "Bluey"), shows.arranged(ShelfSort.RATING, "").map { it.title })
        assertEquals(listOf("Bluey"), shows.arranged(ShelfSort.YEAR, "Family").map { it.title })
        // A show has no running time: the order is the shelf's own.
        assertEquals(listOf("Andor", "Bluey", "Columbo"), shows.arranged(ShelfSort.RUNTIME, "").map { it.title })
    }

    @Test
    fun `each filter offers what the shelf holds, in its order`() {
        assertEquals(listOf("Comedy", "drama", "Horror", "Sci-Fi"),
                     genresOf(listOf(listOf("Sci-Fi", "Horror"), listOf("drama", "Comedy"), listOf("Sci-Fi"))))
        assertEquals(listOf("dv", "hdr10", "sdr"), formatsOf(films.map { it.video }, FormatNames.VIDEO))
        assertEquals(listOf("truehd", "dts"), formatsOf(films.map { it.audio }, FormatNames.AUDIO))
        assertEquals("4K", FormatNames.of(FormatNames.RESOLUTION, "2160p"))
        assertEquals("weird", FormatNames.of(FormatNames.AUDIO, "weird"))
    }
}
