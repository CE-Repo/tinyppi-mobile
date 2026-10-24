package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.Metrics
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.testing.CardState
import com.jamal2367.tinyppimobile.testing.FakeLive
import com.jamal2367.tinyppimobile.testing.Screens
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.components.CardLayout
import com.jamal2367.tinyppimobile.ui.components.CardScreens
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cards of the live screen against every shape of reading they handle. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveCardsTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val fake = FakeLive()

    private fun showLive(accent: Color? = null) = rule.show(CardState().apply { folds = setOf(FOLD_CONTROLS) }) {
        ArtworkAccentTheme(accent = accent) {
            LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel)
        }
    }

    private fun seen(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a new reading redraws the cards without rebuilding what has not moved`() {
        fake.playing()
        showLive(accent = Color(0xFF2266CC))
        rule.onNodeWithContentDescription(str(R.string.live_stop)).assertIsDisplayed()
        for (second in 1..3) {
            fake.playing(Screens.film.copy(time = "1:11:0$second", metrics = Screens.film.metrics.copy(progress = 42.0 + second)))
            rule.waitForIdle()
        }
        rule.onNodeWithText("1:11:03").assertIsDisplayed()

        // The long press, as accessibility services send it.
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ContentDescription, listOf("Dune: Part Two"),
        ).not()).let { }
    }

    @Test
    fun `dragging the bar shows where it would land`() {
        fake.playing()
        showLive()
        val bar = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center)
        }
        rule.waitForIdle()
        rule.until { !seen("1:11:00") }
        bar.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, fake.api.count("command:seek_percent"))

        // A duration the app cannot read leaves the box's own clock up.
        fake.playing(Screens.film.copy(duration = "soon", time = ""))
        bar.performTouchInput {
            down(centerLeft + Offset(20f, 0f))
            moveTo(center)
        }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.value_na)).assertIsDisplayed()
        bar.performTouchInput { up() }
    }

    @Test
    fun `tracks in every combination`() {
        fake.playing(
            Screens.film.copy(
                controls = PlayerControls(audio = listOf(Track(0, "English")), audioCurrent = 5, volume = null),
            ),
        )
        showLive()
        rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, str(R.string.live_track_unknown))).assertExists()

        fake.playing(Screens.film.copy(controls = PlayerControls(subtitle = listOf(Track(0, "English")), subtitleOn = false)))
        rule.until {
            rule.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, str(R.string.live_subtitles_off)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        fake.playing(Screens.film.copy(controls = PlayerControls()))
        rule.waitForIdle()
    }

    private fun badgesFor(snapshot: Snapshot) {
        fake.playing(snapshot)
        rule.waitForIdle()
    }

    @Test
    fun `the source badge names every grade and conversion`() {
        fake.playing(Screens.film.copy(hdrType = "hdr10", outputType = "sdr", groups = emptyList()))
        showLive()
        rule.waitForIdle()
        badgesFor(Screens.film.copy(groups = emptyList(), metadata = emptyList()))
        for ((output, label) in listOf(
            "SDR" to "SDR", "HDR10+" to "HDR10+", "hdr10plus" to "HDR10+", "HDR10" to "HDR10", "HLG" to "HLG", "DV-LL" to null,
        )) {
            badgesFor(Screens.film.copy(outputType = "", vs10 = Vs10State(output = output)))
        }
        // An output the source already is draws no arrow.
        badgesFor(Screens.film.copy(hdrType = "hdr10", outputType = "", vs10 = Vs10State(output = "HDR10")))
        badgesFor(Screens.film.copy(outputType = "dolbyvision", playing = true))
    }

    // Drawn for real: the colours are read off the pixels.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the rating badges wear their houses' colours`() {
        fun pixels(said: String) = rule.onNodeWithContentDescription(said).captureToImage().toPixelMap().let { map ->
            (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }
        }
        fun near(a: Color, b: Color) =
            maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue)) < 0.04f
        fun holds(said: String, colour: Color) = pixels(said).any { near(it, colour) }
        // Each house's own colour, as the shields draw it.
        val houses = mapOf(
            "FSK 12" to Color(0xFF00A651),
            "Metacritic 7.4" to Color(0xFF66CC33),
            "Trakt 8.8" to Color(0xFF9F42C6),
            "Rotten Tomatoes 9.1" to Color(0xFFF9D320),
            str(R.string.live_rating_top250, 17) to Color(0xFFF5C518),
        )
        fake.playing(
            told.copy(
                media = told.media.copy(
                    ratings = listOf(
                        SourceRating("imdb", 8.5, 0),
                        SourceRating("metacritic", 7.4, 0),
                        SourceRating("trakt", 8.8, 0),
                        SourceRating("rottentomatoes", 9.1, 0),
                        SourceRating("tvdb", 7.9, 0),
                        SourceRating("filmaffinity", 7.0, 0),
                    ),
                    userRating = 9,
                    top250 = 17,
                    mpaa = "FSK 12",
                    age = "fsk12",
                    ageLevel = "12",
                ),
            ),
        )
        showLive()
        letOut()
        // Read once the screen has settled: a frame caught mid-draw can miss one.
        val expected = houses + mapOf(
            // IMDb's yellow is a thin line on a dark ground of its own.
            "IMDb 8.5" to Color(0xFF121212),
            "TVDB 7.9" to Color(0xFF555555),
        )
        expected.forEach { (said, colour) -> rule.until { holds(said, colour) } }
    }

    @Test
    fun `a film with little known about it still draws`() {
        fake.settings.value = fake.settings.value.copy(showArtwork = false)
        fake.live.value = LiveState(
            connection = LiveState.Connection.Busy,
            snapshot = Screens.film.copy(
                metrics = Metrics(),
                media = MediaInfo(),
                art = ArtTags(),
                vs10 = Vs10State(),
            ),
            server = null,
        )
        showLive()
        rule.onNodeWithText(str(R.string.status_busy)).assertIsDisplayed()
        fake.playing(Screens.film.copy(media = MediaInfo(year = "", genre = "Drama")))
        rule.until { seen("Drama") }
        fake.playing(Screens.film.copy(media = MediaInfo(year = "2001", genre = "")))
        rule.until { seen("2001") }
        fake.live.value = LiveState(connection = LiveState.Connection.Unauthorized, snapshot = Screens.film, server = Screens.box)
        rule.until { seen(str(R.string.status_unauthorized)) }
        rule.onNodeWithText(str(R.string.status_unauthorized)).performClick()
        assertEquals(1, fake.reconnects)
    }

    @Test
    fun `a message is shown for as long as a snackbar is, then let go`() {
        fake.api.command = { throw ApiFailure.Api(500, "no") }
        fake.playing()
        showLive()
        rule.onNodeWithContentDescription(str(R.string.live_stop)).performClick()
        rule.until { seen("no") }
        rule.mainClock.advanceTimeBy(10_000)
        rule.until { fake.viewModel.message.value == null }
    }

    /** The film with what its library says about it. */
    private val told = Screens.film.copy(
        media = Screens.film.media.copy(tagline = "Long live the fighters.", plot = "Paul joins the Fremen."),
    )

    @Test
    fun `the plot card says the plot, with its slogan where there is one`() {
        fake.playing(told)
        showLive()
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onNodeWithText("Long live the fighters.").assertIsDisplayed()
        rule.onNodeWithText("Paul joins the Fremen.").assertIsDisplayed()

        // An episode: a plot without a slogan.
        fake.playing(told.copy(media = MediaInfo(plot = "An episode.")))
        rule.until { seen("An episode.") }
        assertEquals(false, seen("Long live the fighters."))

        // A slogan without a plot: too little for a card.
        fake.playing(told.copy(media = MediaInfo(tagline = "Only this.")))
        rule.until { !seen(str(R.string.live_plot)) }
        assertEquals(false, seen("Only this."))

        // A plot again, and the card is back.
        fake.playing(told)
        rule.until { seen("Paul joins the Fremen.") }

        // A file the library does not know: no card at all.
        fake.playing(told.copy(media = MediaInfo()))
        rule.until { !seen(str(R.string.live_plot)) }
    }

    @Test
    fun `the overview card draws every rating as its mark and says it in words`() {
        fake.playing(
            told.copy(
                media = told.media.copy(
                    ratings = listOf(
                        SourceRating("imdb", 8.5, 612_345),
                        SourceRating("tmdb", 8.2, 1),
                        SourceRating("rottentomatoes", 9.2, 0, certified = true),
                        SourceRating("popcorn", 8.8, 0),
                        // Only Rotten Tomatoes certifies.
                        SourceRating("filmaffinity", 8.9, 3, certified = true),
                    ),
                    userRating = 9,
                    top250 = 27,
                    mpaa = "FSK 12",
                    age = "fsk12",
                    ageLevel = "12",
                ),
            ),
        )
        showLive()
        letOut()
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        // Each pill is heard as one sentence; the drawing and the figures are not heard apart.
        for (said in listOf(
            // How many voted is neither shown nor heard.
            "IMDb 8.5",
            "TMDb 8.2",
            // Certified Fresh is heard; Verified Hot is not, where it was not given.
            "Rotten Tomatoes · Certified Fresh 9.2",
            "Rotten Tomatoes Popcornmeter 8.8",
            "Filmaffinity 8.9",
            "${str(R.string.live_rating_mine)} 9",
            str(R.string.live_rating_top250, 27),
            "FSK 12",
        )) {
            rule.onNodeWithContentDescription(said).assertIsDisplayed()
        }
        // Every badge one height: the scores' shields, the heart's, the
        // place's and the age rating's.
        val heights = listOf(
            "FSK 12", "IMDb 8.5", "Filmaffinity 8.9",
            "${str(R.string.live_rating_mine)} 9", str(R.string.live_rating_top250, 27),
        ).map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.height }
        assertEquals(heights.map { heights.first() }, heights)
        assertEquals(30f * context.resources.displayMetrics.density, heights.first(), 0.5f)
        assertEquals(false, seen("8.5"))
        // A tap on one shows over it the house a mark is from, and not
        // what it gave: that stands on the shield already.
        assertEquals(false, seen("Filmaffinity"))
        rule.onNodeWithContentDescription("Filmaffinity 8.9").performClick()
        rule.until { seen("Filmaffinity") }
        assertEquals(false, seen("Filmaffinity 8.9"))
        // And only for a moment.
        rule.mainClock.advanceTimeBy(5_000)
        rule.until { !seen("Filmaffinity") }
        // The ratings stand under the plot, the age rating first: on the
        // first line, before the first score.
        val plot = rule.onNodeWithText("Paul joins the Fremen.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val imdb = rule.onNodeWithContentDescription("IMDb 8.5").fetchSemanticsNode().boundsInRoot
        val age = rule.onNodeWithContentDescription("FSK 12").fetchSemanticsNode().boundsInRoot
        assertEquals(true, age.top >= plot.bottom)
        // The slogan and the plot centred: a line alone stands in the middle
        // of the card, which is the middle of the screen.
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val slogan = rule.onNodeWithText("Long live the fighters.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(screen.center.x, slogan.center.x, 1f)
        assertEquals(screen.center.x, plot.center.x, 1f)
        // Right over the plot, a few dp over it: the two read as one text.
        assertEquals(4f * context.resources.displayMetrics.density, plot.top - slogan.bottom, 0.5f)
        assertEquals(age.center.y, imdb.center.y, 1f)
        assertEquals(true, age.right <= imdb.left)

        // A score alone - no age rating, no own rating, no place - from a
        // source the library names nothing: a plain circle and its figure.
        fake.playing(told.copy(media = told.media.copy(ratings = listOf(SourceRating("", 5.0)))))
        rule.until { rule.onAllNodesWithContentDescription("5.0").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, rule.onAllNodesWithContentDescription("FSK 12").fetchSemanticsNodes().isEmpty())

        // An age rating that names no FSK level is written out.
        fake.playing(told.copy(media = told.media.copy(mpaa = "Rated PG-13")))
        rule.until { rule.onAllNodesWithContentDescription("Rated PG-13").fetchSemanticsNodes().isNotEmpty() }

        // Ratings without a plot: the card for them alone, and no slogan.
        // From an add-on too old to send the level beside its mark, the
        // board's plate stands alone, 2 dp in from the shield's edge all
        // round and as wide as it is drawn (30 by 22, at 26 dp tall).
        fake.playing(told.copy(media = MediaInfo(tagline = "Only this.", mpaa = "FSK 16", age = "fsk16")))
        rule.until { rule.onAllNodesWithContentDescription("FSK 16").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, seen(str(R.string.live_plot)))
        val alone = rule.onNodeWithContentDescription("FSK 16").fetchSemanticsNode().boundsInRoot
        assertEquals((26f * 30f / 22f + 4f) / 30f, alone.width / alone.height, 0.05f)
        // Centred: a badge alone stands in the middle of the card, which is
        // the middle of the screen.
        assertEquals(screen.center.x, alone.center.x, 1f)
        assertEquals(false, seen("Only this."))

        // With its level, the age rating is a shield like the scores': the
        // board's plate on the left, the level written beside it.
        val density = context.resources.displayMetrics.density
        fun shieldOf(said: String, age: String, level: String, plateWidth: Float) {
            fake.playing(told.copy(media = MediaInfo(plot = "Plot.", mpaa = said, age = age, ageLevel = level)))
            letOut()
            rule.until { rule.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isNotEmpty() }
            val shield = rule.onNodeWithContentDescription(said).fetchSemanticsNode().boundsInRoot
            assertEquals(30f * density, shield.height, 0.5f)
            // The plate, edge to edge, and the figure's own room beside it.
            assertEquals(true, shield.width > (26f * plateWidth / 22f + 14f) * density)
        }
        shieldOf("FSK 12", "fsk12", "12", 30f)
        shieldOf("Rated PG-13", "mpa_pg13", "PG-13", 32f)
        shieldOf("TV-MA", "tv_ma", "MA", 24f)
        shieldOf("15", "bbfc_15", "15", 38f)
    }

    @Test
    fun `the sources MDBList brings wear their own marks and names`() {
        val sources = listOf(
            "letterboxd" to "Letterboxd", "metacriticuser" to "Metacritic User Score",
            "popcorn" to "Rotten Tomatoes Popcornmeter", "rogerebert" to "Roger Ebert", "myanimelist" to "MyAnimeList",
            "mdblist" to "MDBList",
        )
        fake.playing(told.copy(media = told.media.copy(ratings = sources.map { (source, _) -> SourceRating(source, 7.5) })))
        showLive()
        letOut()
        val heights = sources.map { (_, name) ->
            rule.onNodeWithContentDescription("$name 7.5").fetchSemanticsNode().boundsInRoot.height
        }
        // As tall as every other badge.
        heights.forEach { assertEquals(30f * context.resources.displayMetrics.density, it, 0.5f) }
    }

    @Test
    fun `the overview card has the director and the cast over the ratings, centred lines`() {
        fake.playing(
            told.copy(
                media = told.media.copy(
                    ratings = listOf(SourceRating("imdb", 8.5, 0)),
                    genre = "Science Fiction",
                    directors = listOf("Denis Villeneuve"),
                    cast = listOf("Timothée Chalamet", "Zendaya", "Rebecca Ferguson", "Javier Bardem", "Josh Brolin"),
                    castMore = true,
                    studios = listOf("Legendary Pictures"),
                ),
            ),
        )
        showLive()
        letOut()
        // Under the plot and over the ratings, cut with an ellipsis, and
        // centred: it stands in the middle of the card, which is the middle
        // of the screen.
        val cast = rule.onNodeWithText(
            "${str(R.string.live_credit_cast)}\nTimothée Chalamet, Zendaya, Rebecca Ferguson, Javier Bardem, Josh Brolin, …",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val plot = rule.onNodeWithText("Paul joins the Fremen.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val imdb = rule.onNodeWithContentDescription("IMDb 8.5").fetchSemanticsNode().boundsInRoot
        // The director first, under its heading, then the cast.
        val director = rule.onNodeWithText("${str(R.string.live_credit_director)}\nDenis Villeneuve", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(true, director.top >= plot.bottom)
        assertEquals(true, cast.top >= director.bottom)
        // Then the ratings, under a heading of their own, centred like the rest.
        val heading = rule.onNodeWithText(str(R.string.live_ratings), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(true, heading.top >= cast.bottom)
        assertEquals(true, imdb.top >= heading.bottom)
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screen.center.x, heading.center.x, 1f)
        assertEquals(screen.center.x, cast.center.x, 1f)
        assertEquals(screen.center.x, director.center.x, 1f)
        // The genre, the studio and the director are said once: the first
        // two in the card above, the director here.
        for (above in listOf("Science Fiction", "Legendary Pictures", "Denis Villeneuve")) {
            assertEquals(1, rule.onAllNodesWithText(above, substring = true).fetchSemanticsNodes().size)
        }

        // A longer cast, from an add-on that did not cut it, is cut here: the
        // first twenty, and an ellipsis.
        val many = List(25) { "Actor ${it + 1}" }
        fake.playing(told.copy(media = told.media.copy(cast = many)))
        rule.until { seen("${str(R.string.live_credit_cast)}\n${many.take(20).joinToString(", ")}, …") }

        // A cast the add-on did not cut ends without one.
        fake.playing(told.copy(media = told.media.copy(cast = listOf("Somebody", "Somebody Else"))))
        rule.until { seen("${str(R.string.live_credit_cast)}\nSomebody, Somebody Else") }

        // Ratings and a cast with no plot: the rule between them still.
        fake.playing(told.copy(media = MediaInfo(mpaa = "FSK 16", age = "fsk16", ageLevel = "16", cast = listOf("Somebody"))))
        rule.until { seen("${str(R.string.live_credit_cast)}\nSomebody") }
        assertEquals(true, rule.onAllNodesWithContentDescription("FSK 16").fetchSemanticsNodes().isNotEmpty())

        // A studio alone is no credit, and draws no card.
        fake.playing(told.copy(media = MediaInfo(studios = listOf("A24"))))
        rule.until { !seen(str(R.string.live_plot)) }
        assertEquals(true, seen("A24"))

        // A director alone is an overview, and both of two are named.
        fake.playing(told.copy(media = MediaInfo(directors = listOf("Joel Coen", "Ethan Coen"))))
        rule.until { seen("${str(R.string.live_credit_director)}\nJoel Coen, Ethan Coen") }
        assertEquals(true, rule.onAllNodesWithText(str(R.string.live_credit_cast), substring = true).fetchSemanticsNodes().isEmpty())

        // A cast alone is still an overview.
        fake.playing(told.copy(media = MediaInfo(cast = listOf("Somebody"))))
        rule.until { seen("${str(R.string.live_credit_cast)}\nSomebody") }
        assertEquals(true, seen(str(R.string.live_plot)))
        assertEquals(false, seen("Paul joins the Fremen."))
    }

    /** The plot, pressable to [label] it: a button that says what a tap does. */
    private fun folds(label: Int) = SemanticsMatcher("taps to ${str(label)}") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == str(label) &&
            !it.config.contains(SemanticsProperties.Disabled) &&
            it.config.getOrNull(SemanticsProperties.Role) == Role.Button
    }

    private fun foldable(label: Int) = rule.onAllNodes(folds(label)).fetchSemanticsNodes().isNotEmpty()

    /** Lets out a folded overview - its credits and ratings - where it is folded. */
    private fun letOut() {
        rule.waitForIdle()
        if (foldable(R.string.live_plot_more)) {
            rule.onNode(folds(R.string.live_plot_more)).performClick()
            rule.until { foldable(R.string.live_plot_less) }
            // Past the tap's ripple, which would tint what is read off the pixels.
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
        }
    }

    /** Where [text] is drawn, on its own rather than as part of the plot it folds with. */
    private fun bounds(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    // Drawn for real: how many lines a plot takes is measured.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long plot is cut to five lines, and a tap on it lets the rest out`() {
        val long = List(40) { "Paul joins the Fremen and walks the desert." }.joinToString(" ")
        fake.playing(told.copy(media = told.media.copy(plot = long)))
        showLive()
        val cut = bounds(long)
        // No button of its own: the plot is the button.
        assertEquals(false, seen(str(R.string.live_plot_more)))
        rule.onNode(folds(R.string.live_plot_more)).assertIsDisplayed()

        // Let out: all of it, and another tap takes it back.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }
        assertEquals(true, bounds(long).height > cut.height * 2)
        // A tap on the plot itself: the middle of all it lets out may be a
        // rating's shield, whose tap is its own.
        rule.onNodeWithText(long, useUnmergedTree = true).performClick()
        rule.until { foldable(R.string.live_plot_more) }
        assertEquals(cut.height, bounds(long).height, 0.5f)

        // A new plot starts cut, even after the last was let out.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }
        val other = "$long Again."
        fake.playing(told.copy(media = told.media.copy(plot = other)))
        rule.until { seen(other) && foldable(R.string.live_plot_more) }
        // And a new title, even with the same plot - the next episode, the
        // same film under another name.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }
        fake.playing(told.copy(title = "Another title", media = told.media.copy(plot = other)))
        rule.until { seen("Another title") && foldable(R.string.live_plot_more) }
        // And after the playback ended with it let out, for the next title:
        // the card that comes back is not handed the last one's state.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }
        fake.playing(told.copy(playing = false))
        rule.until { seen(str(R.string.live_idle_title)) }
        fake.playing(told.copy(title = "A third title", media = told.media.copy(plot = other)))
        rule.until { seen("A third title") && foldable(R.string.live_plot_more) }
        // The same title again, back from nothing playing, as it was left.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }

        // A short one with nothing under it is nothing to tap.
        fake.playing(told.copy(media = MediaInfo(plot = "Paul joins the Fremen.")))
        rule.until { seen("Paul joins the Fremen.") }
        assertEquals(false, foldable(R.string.live_plot_more))
        assertEquals(false, foldable(R.string.live_plot_less))
    }

    // Drawn for real: how many lines a plot takes is measured.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long plot let out still folds once it is brought back, with nothing under it`() {
        val long = List(40) { "Paul joins the Fremen and walks the desert." }.joinToString(" ")
        val restoring = StateRestorationTester(rule)
        restoring.setContent {
            TinyPpiTheme { CardState().Provide { PlotCard(MediaInfo(plot = long), "Dune: Part Two") } }
        }
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { foldable(R.string.live_plot_less) }

        // Turned, or scrolled away and back: let out as it was, and still a
        // plot that folds - no credits or ratings under it say so for it.
        restoring.emulateSavedInstanceStateRestore()
        rule.until { foldable(R.string.live_plot_less) }
        rule.onNode(folds(R.string.live_plot_less)).performClick()
        rule.until { foldable(R.string.live_plot_more) }
    }

    // Drawn for real: whether the plot is cut is measured.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the credits hang on the plot, and come out with the rest of a cut one`() {
        val long = List(40) { "Paul joins the Fremen and walks the desert." }.joinToString(" ")
        val credit = "${str(R.string.live_credit_cast)}\nTimothée Chalamet, Zendaya"
        val director = "${str(R.string.live_credit_director)}\nDenis Villeneuve"
        fake.playing(
            told.copy(
                media = told.media.copy(
                    plot = long,
                    directors = listOf("Denis Villeneuve"),
                    cast = listOf("Timothée Chalamet", "Zendaya"),
                    ratings = listOf(SourceRating("imdb", 8.5, 0)),
                ),
            ),
        )
        showLive()
        rule.onNode(folds(R.string.live_plot_more)).assertIsDisplayed()
        assertEquals(false, seen(credit))
        assertEquals(false, seen(director))
        assertEquals(false, seen(str(R.string.live_ratings)))

        // Let out: under the whole plot, the director over the cast, and the
        // ratings last; a tap on a shield is the shield's, and one on the
        // credits folds it all again, as one on the plot does.
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { seen(credit) && seen(director) && seen(str(R.string.live_ratings)) }
        assertEquals(true, bounds(director).top >= bounds(long).bottom)
        assertEquals(true, bounds(credit).top >= bounds(director).bottom)
        assertEquals(true, bounds(str(R.string.live_ratings)).top >= bounds(credit).bottom)
        rule.onNodeWithContentDescription("IMDb 8.5", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        assertEquals(true, seen(credit))
        rule.onNodeWithText(credit, useUnmergedTree = true).performClick()
        rule.until { !seen(credit) && !seen(str(R.string.live_ratings)) }

        // Under a plot that fits they fold all the same: there is more to
        // show than its lines.
        fake.playing(told.copy(media = told.media.copy(cast = listOf("Timothée Chalamet", "Zendaya"))))
        rule.until { seen("Paul joins the Fremen.") && foldable(R.string.live_plot_more) }
        assertEquals(false, seen(credit))
        rule.onNode(folds(R.string.live_plot_more)).performClick()
        rule.until { seen(credit) }
        // A plot that fits with nothing under it is no button.
        fake.playing(told.copy(media = told.media.copy(cast = emptyList(), directors = emptyList(), ratings = emptyList(),
            userRating = 0, top250 = 0, mpaa = "")))
        rule.until { !foldable(R.string.live_plot_more) && !foldable(R.string.live_plot_less) }
    }

    @Test
    fun `the plot card folds, moves and comes off the screen like the others`() {
        val cards = CardState().apply { folds = setOf(FOLD_CONTROLS) }
        fake.playing(told)
        rule.show(cards) { LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel) }

        // Folded from its heading, and open again from it.
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { FOLD_PLOT in cards.folds && !seen("Paul joins the Fremen.") }
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { FOLD_PLOT !in cards.folds && seen("Paul joins the Fremen.") }

        // First among the cards that can be arranged, under the playing one.
        cards.editing = CardScreens.LIVE
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_down)).onFirst().performClick()
        rule.until { cards.orders[CardScreens.LIVE]?.take(2) == listOf(FOLD_CONTROLS, FOLD_PLOT) }

        // Taken off the screen, it is gone from it once the arranging is done.
        rule.onAllNodesWithContentDescription(str(R.string.cards_hide))[1].performClick()
        rule.until { CardLayout.hiddenKey(CardScreens.LIVE, FOLD_PLOT) in cards.hidden }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        rule.until { cards.editing == null && !seen("Paul joins the Fremen.") }
    }

    @Test
    fun `the vs10 label preview draws`() {
        rule.setContent { Vs10LabelPreview() }
        rule.onNodeWithText("Original").assertIsDisplayed()
    }
}
