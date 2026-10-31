package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.FakeApi
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Airing
import com.jamal2367.tinyppimobile.data.model.FilmSeries
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.RelatedTitle
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.util.MediaUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Where a details page leads on to - a film's series, the library's recommendations - and a series' state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class RelatedTitlesTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int) = context.getString(id)
    private fun has(value: String) = rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()

    private val series = FilmSeries(
        "Dune Collection",
        listOf(
            RelatedTitle("movie", 1, "Dune", 2021, id = 11, poster = "p11"),
            RelatedTitle("movie", 2, "Dune: Part Two", 2024, id = 12, current = true),
            RelatedTitle("movie", 3, "Dune: Part Three", 2026, portrait = "t3"),
            RelatedTitle("movie", 4, "Dune Messiah"),
        ),
    )
    private val picks = listOf(RelatedTitle("tvshow", 9, "Andor", 2022, id = 19))

    private class Links : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun cards() = rule.onAllNodesWithTag("related-card")

    // Wide enough for every card of the row to be laid out at once.
    @Test
    @Config(qualifiers = "w1280dp-h4000dp")
    fun `a film's series and the recommendations are rows of posters that lead on`() {
        val links = Links()
        val opened = mutableListOf<RelatedTitle>()
        rule.show {
            CompositionLocalProvider(LocalUriHandler provides links) {
                // Stacked as the page stacks them.
                Column { DetailsBody(TitleDetails(collection = series, recommended = picks), Screens.box) { opened += it } }
            }
        }
        rule.waitForIdle()
        for (shown in listOf("Dune Collection (4)", "${str(R.string.details_recommended)} (1)", "2021", "2026", "Andor")) {
            assertTrue(shown, has(shown))
        }
        // The page's own film is marked; the ones elsewhere carry TMDb's name.
        val marks = rule.onAllNodesWithTag("related-mark", useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf(str(R.string.details_this_film), "TMDb", "TMDb"), marks)
        // Held titles open their own pages, others their TMDb pages; the page's own title leads nowhere.
        val clickable = cards().fetchSemanticsNodes().map { SemanticsActions.OnClick in it.config }
        assertEquals(listOf(true, false, true, true, true), clickable)
        cards()[0].performClick()
        cards()[2].performClick()
        cards()[3].performClick()
        cards()[4].performClick()
        rule.waitForIdle()
        assertEquals(listOf(11, 19), opened.map { it.id })
        assertEquals(
            listOf("https://www.themoviedb.org/movie/3", "https://www.themoviedb.org/movie/4"),
            links.opened,
        )
    }

    @Test
    fun `without a way to open them, held titles lead nowhere and nothing is no row`() {
        rule.show { Column { DetailsBody(TitleDetails(collection = FilmSeries("Empty"), recommended = picks)) } }
        rule.waitForIdle()
        assertFalse(has("Empty (0)"))
        assertTrue(has("Andor"))
        assertTrue(cards().fetchSemanticsNodes().none { SemanticsActions.OnClick in it.config })
        assertEquals(1, rule.onAllNodesWithTag("related-row").fetchSemanticsNodes().size)
    }

    @Test
    fun `a link that cannot be opened is let go`() {
        rule.show {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) = throw IllegalStateException("no browser")
            }) {
                DetailsBody(TitleDetails(recommended = listOf(RelatedTitle("tvshow", 5, "Away"))))
            }
        }
        cards()[0].performClick()
        rule.waitForIdle()
        assertTrue(has("Away"))
    }

    @Test
    fun `a series' state, network, size and episodes either side of today stand among its facts`() {
        rule.show {
            DetailsBody(
                TitleDetails(
                    status = "returning", networks = listOf("Apple TV+", "Other"), tmdbSeasons = 2, tmdbEpisodes = 19,
                    nextEpisode = Airing(3, 1, "2027-01-16", "Hello"), lastEpisode = Airing(12, 110),
                ),
            )
        }
        rule.waitForIdle()
        val day = cinemaDay("2027-01-16", Locale.getDefault())
        for (shown in listOf(
            str(R.string.details_status), str(R.string.details_status_returning), "Apple TV+, Other",
            str(R.string.details_seasons_episodes), "2/19", "S03E01 · Hello · $day", "S12E110",
        )) {
            assertTrue(shown, has(shown))
        }
    }

    @Test
    fun `every state has its words, an unknown one is written as it came, and half a size is none`() {
        var details by mutableStateOf(TitleDetails())
        rule.show { DetailsBody(details) }
        for ((code, words) in listOf(
            "ended" to str(R.string.details_status_ended), "canceled" to str(R.string.details_status_canceled),
            "production" to str(R.string.details_status_production), "planned" to str(R.string.details_status_planned),
            "pilot" to str(R.string.details_status_pilot), "rumoured" to "rumoured",
        )) {
            details = TitleDetails(status = code, tmdbSeasons = 2)
            rule.waitForIdle()
            assertTrue(code, has(words))
            assertFalse(has(str(R.string.details_seasons_episodes)))
        }
    }

    @Test
    fun `an airing says what is known of it`() {
        assertEquals("S00E02", airing(Airing(0, 2), Locale.US))
        assertEquals("S01E02 · soon", airing(Airing(1, 2, "soon"), Locale.US))
    }

    @Test
    fun `a related title's poster is the library's or TMDb's, through the box`() {
        val box = Screens.box
        assertEquals(
            "${box.baseUrl}/api/art?kind=poster&movieid=11&v=p11",
            MediaUrls.related(box, series.parts[0]),
        )
        assertEquals(
            "${box.baseUrl}/api/art?kind=poster&tvshowid=19&v=p",
            MediaUrls.related(box, picks[0].copy(poster = "p")),
        )
        assertEquals("${box.baseUrl}/api/art?kind=poster&portrait=t3&v=t3", MediaUrls.related(box, series.parts[2]))
        assertNull(MediaUrls.related(box, picks[0]))
        assertNull(MediaUrls.related(box, series.parts[3]))
        assertNull(MediaUrls.related(null, series.parts[2]))
    }

    @Test
    fun `a related title knows its kind, its page and its target`() {
        val show = RelatedTitle("tvshow", 9, "Andor", id = 19)
        assertTrue(show.isShow && show.held)
        assertEquals("https://www.themoviedb.org/tv/9", show.page)
        assertEquals(MarkTarget(MarkTarget.Kind.SHOW, 19, "Andor"), show.markTarget())
        val film = RelatedTitle("movie", 3, "Away")
        assertFalse(film.isShow || film.held)
        assertEquals(MarkTarget(MarkTarget.Kind.MOVIE, 0, "Away"), film.markTarget())
    }

    @Test
    fun `the box's answer is read with its series, recommendations and a show's state`() {
        val read = FakeApi.json.decodeFromString(
            TitleDetails.serializer(),
            """{"kind":"tvshow","id":7,"title":"Andor","ratings":[],
               "collection":{"name":"S","parts":[{"kind":"movie","tmdb":1,"title":"A","year":2001,"id":3,"poster":"p","current":true},
                                                 {"kind":"movie","tmdb":2,"title":"B","portrait":"t"}]},
               "recommended":[{"kind":"tvshow","tmdb":9,"title":"C","id":19}],
               "status":"ended","networks":["Disney+"],"tmdb_seasons":2,"tmdb_episodes":24,
               "next_episode":{"season":3,"episode":1,"title":"N"},
               "last_episode":{"season":2,"episode":12,"date":"2025-05-13"}}""",
        )
        assertEquals(FilmSeries("S", listOf(
            RelatedTitle("movie", 1, "A", 2001, 3, "p", current = true), RelatedTitle("movie", 2, "B", portrait = "t"),
        )), read.collection)
        assertEquals(listOf(RelatedTitle("tvshow", 9, "C", id = 19)), read.recommended)
        assertEquals("ended", read.status)
        assertEquals(listOf("Disney+"), read.networks)
        assertEquals(2 to 24, read.tmdbSeasons to read.tmdbEpisodes)
        assertEquals(Airing(3, 1, title = "N"), read.nextEpisode)
        assertEquals(Airing(2, 12, "2025-05-13"), read.lastEpisode)
        // An older box says none of it.
        val older = FakeApi.json.decodeFromString(TitleDetails.serializer(), """{"kind":"movie","id":1,"title":"X"}""")
        assertNull(older.collection)
        assertTrue(older.recommended.isEmpty() && older.networks.isEmpty())
        assertNull(older.nextEpisode)
    }

    @Test
    fun `where the screen has no way of its own, a held title opens its details with nothing to press`() {
        val fake = FakeLive()
        fake.playing()
        fake.api.details = { kind, id ->
            if (id == 1) TitleDetails(kind = kind, id = id, recommended = listOf(RelatedTitle("movie", 5, "Next one", id = 5)))
            else TitleDetails(kind = kind, id = id, plot = "Plot $id")
        }
        rule.show { DetailsShown(fake.viewModel, actions = null) }
        rule.runOnIdle { fake.viewModel.openDetails(MarkTarget(MarkTarget.Kind.MOVIE, 1, "First")) }
        rule.waitUntil { has("Next one") }
        cards()[0].performClick()
        rule.waitUntil { has("Plot 5") }
        assertEquals(MarkTarget(MarkTarget.Kind.MOVIE, 5, "Next one"), fake.viewModel.details.value?.target)
        assertFalse(has(str(R.string.title_play)))
        // Back to the page it was led on from, then closed.
        rule.runOnIdle { assertEquals(1, fake.viewModel.backDetails()?.page?.target?.id) }
        rule.waitUntil { has("Next one") }
        rule.runOnIdle { assertNull(fake.viewModel.backDetails()) }
    }

    @Test
    @Config(qualifiers = "w1280dp-h4000dp")
    fun `the rows follow new titles, links, servers and type sizes`() {
        val short = RelatedTitle("movie", 1, "A", id = 1)
        var details by mutableStateOf(TitleDetails(recommended = listOf(short)))
        var server by mutableStateOf<com.jamal2367.tinyppimobile.data.prefs.ServerConfig?>(null)
        var open by mutableStateOf<((RelatedTitle) -> Unit)?>(null)
        var nameSize by mutableStateOf(14)
        val opened = mutableListOf<Int>()
        rule.show {
            val typography = androidx.compose.material3.MaterialTheme.typography
            androidx.compose.material3.MaterialTheme(
                typography = typography.copy(labelLarge = typography.labelLarge.copy(fontSize = nameSize.sp)),
            ) {
                Column { DetailsBody(details, server, open) }
            }
        }
        rule.waitForIdle()
        // The same titles under a new box, a new way to open them, and new type.
        server = Screens.box
        rule.waitForIdle()
        open = { opened += it.id }
        rule.waitForIdle()
        nameSize = 18
        rule.waitForIdle()
        cards()[0].performClick()
        // A name long enough to wrap between two that do not.
        details = TitleDetails(recommended = listOf(
            short, RelatedTitle("movie", 2, "A title long enough to need a second line or a third", id = 2), short.copy(id = 3),
        ))
        rule.waitForIdle()
        cards()[2].performClick()
        rule.waitForIdle()
        assertEquals(listOf(1, 3), opened)
    }
}
