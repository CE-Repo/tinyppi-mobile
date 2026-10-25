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
        assertEquals(listOf("Alien", "Brazil", "Cube", "Dune", "Eraserhead"), titles(films.arranged(ShelfOrder().by(ShelfSort.TITLE), none)))
        assertEquals(listOf("Dune", "Brazil", "Alien", "Cube", "Eraserhead"), titles(films.arranged(ShelfOrder().by(ShelfSort.YEAR), none)))
        assertEquals(listOf("Alien", "Brazil", "Dune", "Cube", "Eraserhead"), titles(films.arranged(ShelfOrder().by(ShelfSort.RATING), none)))
        assertEquals(listOf("Cube", "Dune", "Alien", "Brazil", "Eraserhead"), titles(films.arranged(ShelfOrder().by(ShelfSort.ADDED), none)))
        assertEquals(listOf("Cube", "Alien", "Brazil", "Dune", "Eraserhead"), titles(films.arranged(ShelfOrder().by(ShelfSort.RUNTIME), none)))
    }

    @Test
    fun `every order runs either way, the titles without its value still last`() {
        val none = ShelfFilter()
        fun down(sort: ShelfSort) = titles(films.arranged(ShelfOrder(sort, descending = true), none))
        fun up(sort: ShelfSort) = titles(films.arranged(ShelfOrder(sort, descending = false), none))
        assertEquals(listOf("Eraserhead", "Dune", "Cube", "Brazil", "Alien"), down(ShelfSort.TITLE))
        assertEquals(listOf("Alien", "Brazil", "Dune", "Cube", "Eraserhead"), up(ShelfSort.YEAR))
        assertEquals(listOf("Dune", "Brazil", "Alien", "Cube", "Eraserhead"), up(ShelfSort.RATING))
        assertEquals(listOf("Alien", "Dune", "Cube", "Brazil", "Eraserhead"), up(ShelfSort.ADDED))
        assertEquals(listOf("Brazil", "Alien", "Cube", "Dune", "Eraserhead"), down(ShelfSort.RUNTIME))
    }

    @Test
    fun `an order is kept as its name and way, and one this build does not know is the box's own`() {
        assertEquals("TITLE", ShelfOrder().encoded())
        assertEquals("YEAR:down", ShelfOrder().by(ShelfSort.YEAR).encoded())
        assertEquals(ShelfOrder(ShelfSort.ADDED, descending = true), ShelfOrder.decoded("ADDED:down"))
        assertEquals(ShelfOrder(ShelfSort.RATING, descending = false), ShelfOrder.decoded("RATING"))
        assertEquals(ShelfOrder(), ShelfOrder.decoded("SHUFFLE:down"))
        assertEquals(ShelfOrder(), ShelfOrder.decoded(null))
        assertEquals(ShelfOrder(ShelfSort.TITLE, descending = true), ShelfOrder().flipped())
        // Picking an order starts it its own way round.
        assertEquals(ShelfOrder(ShelfSort.RUNTIME, descending = false), ShelfOrder(ShelfSort.YEAR, true).by(ShelfSort.RUNTIME))
        val kept = mutableListOf<Pair<String, String>>()
        val orders = ShelfOrders(mapOf("films" to "YEAR")) { shelf, order -> kept += shelf to order }
        assertEquals(ShelfOrder(ShelfSort.YEAR), orders.of("films"))
        assertEquals(ShelfOrder(), orders.of("series"))
        orders.set("series", ShelfOrder(ShelfSort.ADDED, descending = true))
        assertEquals(listOf("series" to "ADDED:down"), kept)
        ShelfOrders().set("films", ShelfOrder())
    }

    @Test
    fun `the filters narrow together`() {
        assertEquals(listOf("Alien", "Cube"), titles(films.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(genre = "Sci-Fi"))))
        assertEquals(listOf("Cube"), titles(films.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(genre = "Sci-Fi", resolution = "2160p"))))
        assertEquals(listOf("Brazil"), titles(films.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(video = "dv", audio = "truehd"))))
        assertEquals(emptyList<String>(), titles(films.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(audio = "flac"))))
    }

    @Test
    fun `Dolby Vision is offered by profile where the shelf holds more than one`() {
        fun dv(id: Int, profile: String) = LibraryFilm(id, "Film $id", video = "dv", dvProfile = profile)
        val shelf = listOf(dv(1, "8.1"), dv(2, "7 MEL"), dv(3, "5"), dv(4, "7 FEL"), dv(5, ""), dv(6, "odd"),
                           LibraryFilm(7, "Flat", video = "sdr"))
        assertEquals(listOf("dv", "dv/5", "dv/7 FEL", "dv/7 MEL", "dv/8.1", "dv/odd", "sdr"), videoChoices(shelf))
        assertEquals(listOf("dv", "sdr"), videoChoices(listOf(dv(1, "8.1"), dv(2, "8.1"), LibraryFilm(3, "Flat", video = "sdr"))))
        assertEquals(listOf("Film 4"), titles(shelf.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(video = "dv/7 FEL"))))
        assertEquals(6, shelf.arranged(ShelfOrder().by(ShelfSort.TITLE), ShelfFilter(video = "dv")).size)
        assertEquals("Dolby Vision P8.1", FormatNames.video("dv/8.1"))
        assertEquals("HDR10+", FormatNames.video("hdr10plus"))
    }

    @Test
    fun `shows sort and filter by genre`() {
        val shows = listOf(
            LibraryShow(1, "Andor", 2022, rating = 8.4, genres = listOf("Sci-Fi"), added = "2026-01-01"),
            LibraryShow(2, "Bluey", 2018, genres = listOf("Family")),
            LibraryShow(3, "Columbo", 1971, rating = 8.2, genres = listOf("Crime")),
        )
        assertEquals(listOf("Andor", "Columbo", "Bluey"), shows.arranged(ShelfOrder().by(ShelfSort.RATING), "").map { it.title })
        assertEquals(listOf("Bluey"), shows.arranged(ShelfOrder().by(ShelfSort.YEAR), "Family").map { it.title })
        // A show has no running time: the order is the shelf's own.
        assertEquals(listOf("Andor", "Bluey", "Columbo"), shows.arranged(ShelfOrder().by(ShelfSort.RUNTIME), "").map { it.title })
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
