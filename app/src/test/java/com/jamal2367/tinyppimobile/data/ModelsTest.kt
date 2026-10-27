package com.jamal2367.tinyppimobile.data

import com.jamal2367.tinyppimobile.data.model.ActiveArea
import com.jamal2367.tinyppimobile.data.model.ApiErrorBody
import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.Award
import com.jamal2367.tinyppimobile.data.model.Chapter
import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CastMember
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.Frame
import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.L1Nits
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.PlayingItem
import com.jamal2367.tinyppimobile.data.model.SourceRating
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.data.model.Metrics
import com.jamal2367.tinyppimobile.data.model.ModeBody
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10Option
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.remote.NoServerConfiguredException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import com.jamal2367.tinyppimobile.data.model.LooseStringSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The small rules the models carry: which readings count as present, which
 * shapes count as usable, and how the failures describe themselves.
 */
class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val withDefaults = Json { encodeDefaults = true }

    @Test
    fun `a snapshot is converting only while playing into another format`() {
        assertFalse(Snapshot(playing = false, outputType = "hdr10").isConverting)
        assertFalse(Snapshot(playing = true, outputType = "").isConverting)
        assertFalse(Snapshot(playing = true, hdrType = "hdr10", outputType = "HDR10").isConverting)
        assertTrue(Snapshot(playing = true, hdrType = "dolbyvision", outputType = "hdr10").isConverting)
        assertEquals("sdr", Snapshot().sourceType)
    }

    @Test
    fun `the chapters arrive placed, with the one playing named by its number`() {
        val snapshot = json.decodeFromString<Snapshot>(
            """{"playing": true, "chapter": 2, "chapters": [
                {"index": 1, "start": 0, "at": 0.0, "time": "00:00:00", "name": "Opening"},
                {"index": 2, "start": 1800, "at": 25.0, "time": "00:30:00"},
                {"index": 4, "start": 3600, "at": 50.0, "time": "01:00:00", "name": "End"}]}""",
        )
        assertEquals(Chapter(2, 1800, 25.0, "00:30:00", ""), snapshot.currentChapter)
        assertEquals(4, snapshot.chapterCount)
        // Older add-ons send neither, and nothing is playing before the first.
        assertEquals(emptyList<Chapter>(), Snapshot().chapters)
        assertNull(Snapshot().currentChapter)
        assertEquals(0, Snapshot().chapterCount)
        assertNull(snapshot.copy(chapter = 0).currentChapter)
    }

    @Test
    fun `the active area needs exactly four bars`() {
        assertNull(Metrics().activeArea)
        assertNull(Metrics(bars = listOf(1.0, 2.0)).activeArea)
        assertEquals(ActiveArea(1.0, 2.0, 3.0, 4.0), Metrics(bars = listOf(1.0, 2.0, 3.0, 4.0)).activeArea)
    }

    @Test
    fun `l1 is empty only with no reading at all`() {
        assertTrue(L1Nits().isEmpty)
        assertFalse(L1Nits(min = 0.0).isEmpty)
        assertFalse(L1Nits(max = 1.0).isEmpty)
        assertFalse(L1Nits(avg = 1.0).isEmpty)
    }

    @Test
    fun `a frame is usable with both sides`() {
        assertFalse(Frame().isUsable)
        assertFalse(Frame(w = 3840).isUsable)
        assertTrue(Frame(3840, 2160).isUsable)
    }

    @Test
    fun `bars on either side or edge count`() {
        assertFalse(ActiveArea(0.0, 0.0, 0.0, 0.0).isPillarboxed)
        assertTrue(ActiveArea(1.0, 0.0, 0.0, 0.0).isPillarboxed)
        assertTrue(ActiveArea(0.0, 1.0, 0.0, 0.0).isPillarboxed)
        assertFalse(ActiveArea(0.0, 0.0, 0.0, 0.0).isLetterboxed)
        assertTrue(ActiveArea(0.0, 0.0, 1.0, 0.0).isLetterboxed)
        assertTrue(ActiveArea(0.0, 0.0, 0.0, 1.0).isLetterboxed)
    }

    @Test
    fun `metadata rows with cells are tables`() {
        assertFalse(MetadataRow().isTable)
        assertTrue(MetadataRow(cells = listOf("1")).isTable)
    }

    @Test
    fun `an episode label needs a season and an episode number`() {
        assertNull(MediaInfo().episodeLabel)
        assertNull(MediaInfo(season = "1", episode = "x").episodeLabel)
        assertEquals("S01E02", MediaInfo(season = "1", episode = "2").episodeLabel)
    }

    @Test
    fun `the overview opens the details of a film or an episode the library holds`() {
        assertEquals(SourceRating("", 0.0, 0), SourceRating())
        assertEquals(Award("", emptyList(), 0), Award())
        assertEquals("", MediaInfo().age)
        assertEquals("", MediaInfo().ageLevel)
        assertFalse(MediaInfo().item.opensDetails)
        assertTrue(PlayingItem("movie", 3).opensDetails)
        assertTrue(PlayingItem("episode", 7).opensDetails)
        assertFalse(PlayingItem("movie", 0).opensDetails)
        assertFalse(PlayingItem("channel", 4).opensDetails)
        assertEquals(MarkTarget(MarkTarget.Kind.MOVIE, 3, "Heat"), PlayingItem("movie", 3).target("Heat"))
        assertEquals(MarkTarget(MarkTarget.Kind.EPISODE, 7, "Pilot"), PlayingItem("episode", 7).target("Pilot"))
    }

    @Test
    fun `a title the box hands out no details for has the snapshot's to show`() {
        assertFalse(MediaInfo().hasOverview)
        assertFalse(MediaInfo(plot = "  ", item = PlayingItem("movie", 3)).hasOverview)
        for (told in listOf(
            MediaInfo(plot = "News."), MediaInfo(ratings = listOf(SourceRating("imdb", 7.0))),
            MediaInfo(userRating = 8), MediaInfo(top250 = 3), MediaInfo(mpaa = "FSK 12"),
            MediaInfo(awards = listOf(Award("oscar"))), MediaInfo(directors = listOf("D")),
            MediaInfo(writers = listOf("W")), MediaInfo(cast = listOf("C")),
        )) {
            assertTrue(told.toString(), told.hasOverview)
        }

        val details = MediaInfo(
            year = "2026", plot = "News.", tagline = "Live.", genres = listOf("News"),
            cast = listOf("Jens Riewa", "Judith Rakers"), roles = listOf("Sprecher"), castMore = true,
            directors = listOf("D"), writers = listOf("W"), studios = listOf("ARD"),
            mpaa = "FSK 0", age = "fsk0", ageLevel = "0", userRating = 7, top250 = 0,
            item = PlayingItem("episode", 0),
        ).details(ArtTags(poster = "p1", fanart = "f1"))
        assertEquals("episode", details.kind)
        assertEquals(2026, details.year)
        assertEquals(listOf(CastMember("Jens Riewa", "Sprecher"), CastMember("Judith Rakers", "")), details.cast)
        assertTrue(details.castMore)
        assertEquals(listOf("News"), details.genres)
        assertEquals(listOf("W"), details.writers)
        assertEquals("fsk0", details.age)
        assertEquals(7, details.userrating)
        assertEquals("p1", details.poster)
        assertEquals("f1", details.fanart)
        // A year that is no number is none.
        assertEquals(0, MediaInfo(year = "n/a").details(ArtTags()).year)
    }

    @Test
    fun `controls are empty without tracks and volume`() {
        assertTrue(PlayerControls().isEmpty)
        assertFalse(PlayerControls(audio = listOf(Track())).isEmpty)
        assertFalse(PlayerControls(subtitle = listOf(Track(1, "English"))).isEmpty)
        assertFalse(PlayerControls(volume = 3).isEmpty)
        assertFalse(PlayerControls(chapters = 1).hasChapters)
        assertTrue(PlayerControls(chapters = 2).hasChapters)
        assertEquals("x", Vs10Option(label = "x").label)
    }

    @Test
    fun `progress needs a resume point and a duration`() {
        assertNull(LibraryFilm(resume = 10).progress)
        assertNull(LibraryFilm(duration = 10).progress)
        assertEquals(0.5f, LibraryFilm(resume = 5, duration = 10).progress)
        assertNull(LibraryEpisode(resume = 10).progress)
        assertNull(LibraryEpisode(duration = 10).progress)
        assertNull(ContinueItem(resume = 10).progress)
        assertNull(ContinueItem(duration = 10).progress)
        assertEquals(1f, ContinueItem(resume = 20, duration = 10).progress)
    }

    @Test
    fun `episode codes leave out what is not known`() {
        assertEquals("", LibraryEpisode().code)
        assertEquals("S01E00", LibraryEpisode(season = 1, episode = 0).code)
        assertEquals("", ContinueItem(kind = "movie", season = 1, episode = 1).code)
        assertEquals("", ContinueItem(kind = "episode").code)
        assertEquals("S02E03", ContinueItem(kind = "episode", season = 2, episode = 3).code)
        assertEquals("E03", ContinueItem(kind = "episode", season = 0, episode = 3).code)
    }

    @Test
    fun `history is empty without samples and events`() {
        assertTrue(History().isEmpty)
        assertFalse(History(t = listOf(1.0)).isEmpty)
        assertFalse(History(events = listOf(PlaybackEvent())).isEmpty)
    }

    @Test
    fun `a transition has both ends`() {
        assertFalse(PlaybackEvent().isTransition)
        assertFalse(PlaybackEvent(from = "a").isTransition)
        assertFalse(PlaybackEvent(to = "a").isTransition)
        assertTrue(PlaybackEvent(from = "a", to = "b").isTransition)
    }

    @Test
    fun `event ends are read loosely and written back`() {
        val event = json.decodeFromString(
            PlaybackEvent.serializer(),
            """{"from":null,"to":{"a":1},"kind":"mode"}""",
        )
        assertNull(event.from)
        assertEquals("""{"a":1}""", event.to)

        assertEquals(
            """{"from":"x"}""",
            Json.encodeToString(PlaybackEvent.serializer(), PlaybackEvent(from = "x")),
        )
        val all = withDefaults.encodeToString(PlaybackEvent.serializer(), PlaybackEvent())
        assertTrue(all.contains(""""from":null"""))
    }

    // The abstract encoder and decoder are how a serializer is driven by hand.
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `event ends outside json read as plain strings`() {
        val decoder = object : AbstractDecoder() {
            override val serializersModule = EmptySerializersModule()
            override fun decodeElementIndex(descriptor: SerialDescriptor): Int = 0
            override fun decodeString(): String = "x"
        }
        assertEquals("x", LooseStringSerializer.deserialize(decoder))

        assertNull(Json.decodeFromString(PlaybackEvent.serializer(), """{"from":null}""").from)
        assertNull(Json.decodeFromJsonElement(LooseStringSerializer, kotlinx.serialization.json.JsonNull))

        val written = mutableListOf<String?>()
        val encoder = object : AbstractEncoder() {
            override val serializersModule = EmptySerializersModule()
            override fun encodeNull() { written += null }
            override fun encodeString(value: String) { written += value }
        }
        LooseStringSerializer.serialize(encoder, null)
        LooseStringSerializer.serialize(encoder, "y")
        assertEquals(listOf(null, "y"), written)
    }

    @Test
    fun `the bodies and answers carry what they were given`() {
        assertEquals("hdr10", ModeBody("hdr10").mode)
        assertEquals(CommandBody("seek", 1.0), CommandBody("seek", 1.0))
        assertEquals("TinyPPI", Hello(name = "TinyPPI").name)
        assertEquals(200, Hello().intervalMs)
        assertEquals("x", ApiErrorBody(error = "x").error)
        assertNull(ApiErrorBody().retryMs)
        assertFalse(CommandAck().ok)
    }

    @Test
    fun `failures describe themselves`() {
        assertEquals("No server configured", ApiFailure.NotConfigured.message)
        assertEquals("Server unreachable", ApiFailure.Unreachable(null, false, null).message)
        assertEquals("gone", ApiFailure.Unreachable("box", true, IOException("gone")).message)
        assertEquals("Server unreachable", ApiFailure.Unreachable("box", true, IOException()).message)
        assertEquals("HTTP 500", ApiFailure.Api(500, null).message)
        assertEquals("nope", ApiFailure.Api(401, "nope").message)
        assertTrue(ApiFailure.Api(401, null).isUnauthorized)
        assertFalse(ApiFailure.Api(500, null).isUnauthorized)
        assertTrue(ApiFailure.Api(403, null).isControlDisabled)
        assertFalse(ApiFailure.Api(500, null).isControlDisabled)
        assertTrue(ApiFailure.Api(503, null).isBusy)
        assertFalse(ApiFailure.Api(500, null).isBusy)
        assertEquals("Malformed response", ApiFailure.Malformed(null).message)
        assertEquals("bad", ApiFailure.Malformed(IllegalStateException("bad")).message)
        assertEquals("Malformed response", ApiFailure.Malformed(IllegalStateException()).message)
        assertEquals("No server configured", NoServerConfiguredException().message)
    }

    @Test
    fun `the not-configured failure stays one object through serialization`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ObjectOutputStream(out).use { it.writeObject(ApiFailure.NotConfigured) }
        }.toByteArray()
        val read = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertSame(ApiFailure.NotConfigured, read)
    }
}
