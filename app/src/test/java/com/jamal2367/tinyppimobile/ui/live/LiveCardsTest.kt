package com.jamal2367.tinyppimobile.ui.live

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.data.model.PlayingItem
import androidx.compose.ui.test.longClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.Award
import com.jamal2367.tinyppimobile.data.model.CommandAck
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

    @Test
    fun `the fine adjustments step from the reported offsets, and reset`() {
        val sent = mutableListOf<Pair<String, Double?>>()
        fake.api.command = {
            sent += it.action to it.value
            CommandAck(ok = true)
        }
        fun audioAt(seconds: Double) = fake.playing(
            Screens.film.copy(controls = PlayerControls(volume = 40, audioDelay = seconds)),
        )
        fake.playing(
            Screens.film.copy(
                controls = PlayerControls(volume = 40, audioDelay = -0.05, subtitleDelay = 0.3, subtitleOn = false),
            ),
        )
        showLive()
        val audio = str(R.string.live_audio_offset)
        val subtitles = str(R.string.live_subtitle_offset)
        // The value is said as the key's state: "Audio offset, −50 ms".
        fun shows(label: String, value: String) = rule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$label $value"),
        ).fetchSemanticsNodes().isNotEmpty()
        rule.until { shows(audio, "\u221250 ms") }
        // No subtitles shown, nothing to move.
        assertEquals(false, seen(subtitles))
        rule.onNodeWithContentDescription("$audio +").performClick()
        rule.waitForIdle()
        audioAt(0.0)
        rule.until { shows(audio, "0 ms") }
        // A reset with nothing to reset still says so to the box.
        rule.onNodeWithContentDescription(str(R.string.live_offset_reset)).performClick()
        rule.waitForIdle()
        sent.removeAt(sent.lastIndex)
        rule.onNodeWithContentDescription("$audio \u2212").performClick()
        rule.waitForIdle()
        audioAt(-0.05)
        rule.until { shows(audio, "\u221250 ms") }
        rule.onNodeWithContentDescription(str(R.string.live_offset_reset)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("audio_delay" to 0.0, "audio_delay" to -0.05, "audio_delay" to 0.0), sent)

        // Subtitles on bring their own offset.
        fake.playing(
            Screens.film.copy(
                controls = PlayerControls(volume = 40, audioDelay = 0.0, subtitleDelay = 1.5, subtitleOn = true),
            ),
        )
        rule.until { shows(subtitles, "+1.50 s") }
        // The rest of the player moving leaves the offsets where they are.
        fake.playing(
            Screens.film.copy(
                controls = PlayerControls(volume = 41, audioDelay = 0.0, subtitleDelay = 1.5, subtitleOn = true),
            ),
        )
        rule.waitForIdle()
        rule.onNodeWithContentDescription("$subtitles +").performClick()
        rule.waitForIdle()
        assertEquals(listOf("subtitle_delay" to 1.6), sent.drop(3))

        // The offsets stop at Kodi's ranges.
        fake.playing(Screens.film.copy(controls = PlayerControls(volume = 40, audioDelay = 9.98)))
        rule.until { shows(audio, "+9.98 s") }
        rule.onNodeWithContentDescription("$audio +").performClick()
        rule.waitForIdle()
        assertEquals("audio_delay" to 10.0, sent.last())
    }

    @Test
    fun `each fine adjustment comes on its own`() {
        // The captions are said as part of their keys, so the keys are what is looked for.
        fun has(description: String) = rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        val audio = "${str(R.string.live_audio_offset)} +"
        val subtitles = "${str(R.string.live_subtitle_offset)} +"
        fake.playing(Screens.film.copy(controls = PlayerControls(volume = 40, subtitleDelay = 0.2, subtitleOn = true)))
        showLive()
        rule.until { has(subtitles) }
        assertEquals(false, has(audio))
        // Subtitles off take their offset away, and the audio one comes alone.
        fake.playing(Screens.film.copy(controls = PlayerControls(volume = 40, audioDelay = 0.0, subtitleDelay = 0.2)))
        rule.until { has(audio) }
        assertEquals(false, has(subtitles))
    }

    @Test
    fun `offsets are written the way the stepper shows them`() {
        assertEquals("0 ms", offsetText(0.0004))
        assertEquals("+25 ms", offsetText(0.025))
        assertEquals("\u2212999 ms", offsetText(-0.999))
        assertEquals("+1.00 s", offsetText(1.0))
        assertEquals("\u221212.35 s", offsetText(-12.345))
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
        rated = MediaInfo(
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
                )
        showRatings()
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

    /** The ratings a title's details draw, as a row of shields on their own. */
    private var rated by mutableStateOf(MediaInfo())

    private fun showRatings() = rule.setContent {
        TinyPpiTheme {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { RatingRow(rated) }
        }
    }

    @Test
    fun `a title's ratings are drawn as their marks and said in words`() {
        rated = MediaInfo(
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
                )
        showRatings()
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
        assertEquals(26f * context.resources.displayMetrics.density, heights.first(), 0.5f)
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
        // The age rating first: on the first line, before the first score.
        val imdb = rule.onNodeWithContentDescription("IMDb 8.5").fetchSemanticsNode().boundsInRoot
        val age = rule.onNodeWithContentDescription("FSK 12").fetchSemanticsNode().boundsInRoot
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(age.center.y, imdb.center.y, 1f)
        assertEquals(true, age.right <= imdb.left)

        // A score alone - no age rating, no own rating, no place - from a
        // source the library names nothing: a plain circle and its figure.
        rated = MediaInfo(ratings = listOf(SourceRating("", 5.0)))
        rule.until { rule.onAllNodesWithContentDescription("5.0").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, rule.onAllNodesWithContentDescription("FSK 12").fetchSemanticsNodes().isEmpty())

        // An age rating that names no FSK level is written out.
        rated = MediaInfo(mpaa = "Rated PG-13")
        rule.until { rule.onAllNodesWithContentDescription("Rated PG-13").fetchSemanticsNodes().isNotEmpty() }

        // From an add-on too old to send the level beside its mark, the
        // board's plate stands alone, 2 dp in from the shield's edge all
        // round and as wide as it is drawn (30 by 22, at 22 dp tall) - in a
        // shield no narrower than 35 dp.
        rated = MediaInfo(tagline = "Only this.", mpaa = "FSK 16", age = "fsk16")
        rule.until { rule.onAllNodesWithContentDescription("FSK 16").fetchSemanticsNodes().isNotEmpty() }
        val alone = rule.onNodeWithContentDescription("FSK 16").fetchSemanticsNode().boundsInRoot
        assertEquals(35f / 26f, alone.width / alone.height, 0.05f)
        // Centred: a badge alone stands in the middle of the row, which is
        // the middle of the screen.
        assertEquals(screen.center.x, alone.center.x, 1f)

        // With its level, the age rating is a shield like the scores': the
        // board's plate on the left, the level written beside it.
        val density = context.resources.displayMetrics.density
        fun shieldOf(said: String, age: String, level: String, plateWidth: Float) {
            rated = MediaInfo(plot = "Plot.", mpaa = said, age = age, ageLevel = level)
            rule.until { rule.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isNotEmpty() }
            val shield = rule.onNodeWithContentDescription(said).fetchSemanticsNode().boundsInRoot
            assertEquals(26f * density, shield.height, 0.5f)
            // The plate, edge to edge, and the figure's own room beside it.
            assertEquals(true, shield.width > (22f * plateWidth / 22f + 14f) * density)
        }
        shieldOf("FSK 12", "fsk12", "12", 30f)
        shieldOf("Rated PG-13", "mpa_pg13", "PG-13", 32f)
        shieldOf("TV-MA", "tv_ma", "MA", 24f)
        shieldOf("15", "bbfc_15", "15", 38f)
    }

    @Test
    fun `the big awards stand last, as plates of what was won or else nominated for`() {
        rated = MediaInfo(
                    plot = "Plot.",
                    awards = listOf(
                        Award("oscar", listOf("Visual Effects (2015)"), 4),
                        Award("globe", emptyList(), 1),
                        Award("bafta", listOf("Film (2015)", "Score (2015)"), 0),
                        Award("emmy", emptyList(), 2),
                        // One there is no plate of is left out.
                        Award("cesar", listOf("Best Film (2015)"), 0),
                    ),
                )
        showRatings()
        val won = { n: Int -> context.getString(R.string.live_award_won, n) }
        val nominated = { n: Int -> context.getString(R.string.live_award_nominated, n) }
        val said = listOf("Oscar ${won(1)}", "Golden Globe ${nominated(1)}", "BAFTA ${won(2)}", "Emmy ${nominated(2)}")
        rule.until { said.all { rule.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty() } }
        // As tall as every other badge, in the order they are sent.
        val shields = said.map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        shields.forEach { assertEquals(26f * context.resources.displayMetrics.density, it.height, 0.5f) }
        assertEquals(shields.sortedBy { it.top * 10_000 + it.left }, shields)
        assertEquals(true, rule.onAllNodesWithContentDescription("cesar", substring = true).fetchSemanticsNodes().isEmpty())
        // A tap says what it won it for, and how many more it was nominated for.
        rule.onNodeWithContentDescription("Oscar ${won(1)}").performClick()
        rule.until { seen("Oscar: Visual Effects (2015) · ${nominated(4)}") }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithContentDescription("BAFTA ${won(2)}").performClick()
        rule.until { seen("BAFTA: Film (2015), Score (2015)") }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithContentDescription("Golden Globe ${nominated(1)}").performClick()
        rule.until { seen("Golden Globe · ${nominated(1)}") }
    }

    @Test
    fun `the sources MDBList brings wear their own marks and names`() {
        val sources = listOf(
            "letterboxd" to "Letterboxd", "metacriticuser" to "Metacritic User Score",
            "popcorn" to "Rotten Tomatoes Popcornmeter", "rogerebert" to "Roger Ebert", "myanimelist" to "MyAnimeList",
            "mdblist" to "MDBList",
        )
        rated = MediaInfo(ratings = sources.map { (source, _) -> SourceRating(source, 7.5) })
        showRatings()
        val heights = sources.map { (_, name) ->
            rule.onNodeWithContentDescription("$name 7.5").fetchSemanticsNode().boundsInRoot.height
        }
        // As tall as every other badge.
        heights.forEach { assertEquals(26f * context.resources.displayMetrics.density, it, 0.5f) }
    }

    @Test
    fun `the overview is a key that opens the playing title's details, with nothing to play`() {
        val asked = mutableListOf<Pair<String, Int>>()
        fake.api.details = { kind, id ->
            asked += kind to id
            TitleDetails(kind = kind, id = id, plot = "Paul joins the Fremen.")
        }
        fake.playing(told.copy(media = told.media.copy(item = PlayingItem("movie", 1))))
        showLive()
        // Its heading alone: nothing of the title in it.
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        assertEquals(false, seen("Paul joins the Fremen."))
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { seen("Paul joins the Fremen.") }
        assertEquals(listOf("movie" to 1), asked)
        // The playing title's: no keys to play it with.
        assertEquals(false, seen(str(R.string.title_play)))
        rule.onNodeWithContentDescription(str(R.string.details_close)).performClick()
        rule.until { !seen("Paul joins the Fremen.") }

        // An episode opens as one.
        fake.playing(told.copy(title = "Ghosts", media = MediaInfo(item = PlayingItem("episode", 76))))
        rule.until { seen("Ghosts") }
        rule.onNodeWithText(str(R.string.live_plot)).performClick()
        rule.until { asked.size == 2 }
        assertEquals("episode" to 76, asked.last())
        rule.onNodeWithContentDescription(str(R.string.details_close)).performClick()

        // A file the library does not know has no card.
        fake.playing(told.copy(media = MediaInfo(plot = "A plot.")))
        rule.until { !seen(str(R.string.live_plot)) }
    }

    @Test
    fun `a long press on the overview opens the arranging, as one on a heading does`() {
        val cards = CardState()
        fake.playing(told.copy(media = told.media.copy(item = PlayingItem("movie", 1))))
        rule.show(cards) { LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel) }
        rule.onNodeWithText(str(R.string.live_plot)).performTouchInput { longClick() }
        rule.until { cards.editing == CardScreens.LIVE }
    }

    @Test
    fun `the overview moves and comes off the screen like the others`() {
        val cards = CardState().apply { folds = setOf(FOLD_CONTROLS) }
        fake.playing(told.copy(media = told.media.copy(item = PlayingItem("movie", 1))))
        rule.show(cards) { LiveScreen(onOpenSettings = {}, viewModel = fake.viewModel) }

        // First among the cards that can be arranged, under the playing one.
        cards.editing = CardScreens.LIVE
        rule.onNodeWithText(str(R.string.live_plot)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cards_move_down)).onFirst().performClick()
        rule.until { cards.orders[CardScreens.LIVE]?.take(2) == listOf(FOLD_CONTROLS, FOLD_PLOT) }

        // Taken off the screen, it is gone from it once the arranging is done.
        rule.onAllNodesWithContentDescription(str(R.string.cards_hide))[1].performClick()
        rule.until { CardLayout.hiddenKey(CardScreens.LIVE, FOLD_PLOT) in cards.hidden }
        rule.onNodeWithText(str(R.string.cards_done)).performClick()
        rule.until { cards.editing == null && !seen(str(R.string.live_plot)) }
    }

    @Test
    fun `the vs10 label preview draws`() {
        rule.setContent { Vs10LabelPreview() }
        rule.onNodeWithText("Original").assertIsDisplayed()
    }
}
