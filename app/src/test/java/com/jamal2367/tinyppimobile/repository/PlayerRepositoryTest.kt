package com.jamal2367.tinyppimobile.repository

import com.jamal2367.tinyppimobile.FakeApi
import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.ModeBody
import com.jamal2367.tinyppimobile.data.model.PlayEpisodeBody
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.model.ResumeBody
import com.jamal2367.tinyppimobile.data.model.WatchedBody
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.remote.NoServerConfiguredException
import com.jamal2367.tinyppimobile.data.remote.ServerUnreachableException
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Every call the screens make, and every failure turned into words a screen can use. */
class PlayerRepositoryTest {

    private val api = FakeApi()
    private val repository = PlayerRepository(api, FakeApi.json)
    private val sent = mutableListOf<CommandBody>()

    init {
        api.command = { sent += it; CommandAck(ok = true) }
    }

    @Test
    fun `the transport buttons send their actions`() = runBlocking {
        repository.playPause()
        repository.stop()
        repository.previousChapter()
        repository.nextChapter()
        repository.goToChapter(7)
        repository.seekBy(-99_999)
        repository.seekBy(30)
        repository.seekTo(150f)
        repository.volumeUp()
        repository.volumeDown()
        repository.toggleMute()
        repository.selectAudio(2)
        repository.selectSubtitle(1)
        repository.selectSubtitle(null)
        assertEquals(
            listOf(
                CommandBody("playpause"), CommandBody("stop"), CommandBody("chapter_previous"),
                CommandBody("chapter_next"), CommandBody("chapter_goto", 7.0),
                CommandBody("seek", -3600.0), CommandBody("seek", 30.0),
                CommandBody("seek_percent", 100.0), CommandBody("volume_up"), CommandBody("volume_down"),
                CommandBody("mute"), CommandBody("audio", 2.0), CommandBody("subtitle", 1.0),
                CommandBody("subtitle", -1.0),
            ),
            sent,
        )
    }

    @Test
    fun `the fine adjustments send their values within Kodi's ranges`() = runBlocking {
        repository.setAudioDelay(0.05)
        repository.setAudioDelay(-25.0)
        repository.setSubtitleDelay(1.5)
        repository.setSubtitleDelay(99.0)
        assertEquals(
            listOf(
                CommandBody("audio_delay", 0.05), CommandBody("audio_delay", -10.0),
                CommandBody("subtitle_delay", 1.5), CommandBody("subtitle_delay", 60.0),
            ),
            sent,
        )
    }

    @Test
    fun `details name the title by the id its kind goes by`() = runBlocking {
        val movie = repository.details(MarkTarget(MarkTarget.Kind.MOVIE, 1, "a"))
        val show = repository.details(MarkTarget(MarkTarget.Kind.SHOW, 2, "b"))
        val episode = repository.details(MarkTarget(MarkTarget.Kind.EPISODE, 3, "c"))
        assertEquals(listOf("movie" to 1, "tvshow" to 2, "episode" to 3),
                     listOf(movie, show, episode).map { it.kind to it.id })
    }

    @Test
    fun `the shelves and the library calls go to their endpoints`() = runBlocking {
        val bodies = mutableListOf<Any>()
        api.setWatched = { bodies += it; CommandAck(ok = true) }
        api.clearResume = { bodies += it; CommandAck(ok = true) }
        api.playEpisode = { bodies += PlayEpisodeBody(it); CommandAck(ok = true) }
        api.history = {
            History(events = listOf(PlaybackEvent(kind = "cache_low"), PlaybackEvent(kind = "mode")))
        }

        assertEquals(listOf("mode"), repository.history().events.map { it.kind })
        repository.library()
        repository.series()
        repository.episodes(4)
        repository.continuing()
        repository.playFilm(1)
        repository.playFilm(2, fromStart = true)
        repository.playEpisode(3)
        repository.playEpisode(4, fromStart = true)
        repository.setWatched(MarkTarget(MarkTarget.Kind.MOVIE, 1, "a"), true)
        repository.setWatched(MarkTarget(MarkTarget.Kind.SHOW, 2, "b"), false)
        repository.setWatched(MarkTarget(MarkTarget.Kind.EPISODE, 3, "c"), true)
        repository.setWatched(MarkTarget(MarkTarget.Kind.EPISODE, 3, "c"), false)
        repository.setWatched(MarkTarget(MarkTarget.Kind.MOVIE, 1, "a"), false)
        repository.setWatched(MarkTarget(MarkTarget.Kind.SHOW, 2, "b"), true)
        repository.clearResume(MarkTarget(MarkTarget.Kind.MOVIE, 1, "a"))
        repository.clearResume(MarkTarget(MarkTarget.Kind.EPISODE, 3, "c"))
        repository.clearResume(MarkTarget(MarkTarget.Kind.SHOW, 2, "b"))
        repository.setMode("hdr10")

        assertEquals(listOf(null, false), api.plays.map { it.resume })
        assertEquals(
            listOf<Any>(
                PlayEpisodeBody(3), PlayEpisodeBody(4),
                WatchedBody(movieid = 1, watched = true), WatchedBody(tvshowid = 2, watched = false),
                WatchedBody(episodeid = 3, watched = true), WatchedBody(episodeid = 3, watched = false),
                WatchedBody(movieid = 1, watched = false), WatchedBody(tvshowid = 2, watched = true),
                ResumeBody(movieid = 1), ResumeBody(episodeid = 3),
            ),
            bodies,
        )
        assertTrue(api.calls.containsAll(listOf("library", "series", "episodes", "continuing", "mode")))
        assertEquals(ModeBody("hdr10"), ModeBody("hdr10"))
    }

    private fun failureOf(thrown: Throwable): Throwable = runBlocking {
        api.command = { throw thrown }
        try {
            repository.playPause()
            fail()
            error("unreachable")
        } catch (e: Throwable) {
            e
        }
    }

    private fun http(code: Int, body: String) = HttpException(Response.error<Any>(code, body.toResponseBody()))

    @Test
    fun `failures come back as api failures`() {
        val cancelled = CancellationException("gone")
        assertSame(cancelled, failureOf(cancelled))
        assertSame(ApiFailure.NotConfigured, failureOf(ApiFailure.NotConfigured))
        assertEquals(ApiFailure.Api(403, "control disabled"), failureOf(http(403, """{"error":"control disabled"}""")))
        for (body in listOf("", "not json", """{"error":" "}""")) {
            assertEquals(ApiFailure.Api(500, "Response.error()"), failureOf(http(500, body)))
        }
        val local = ServerConfig(enabled = true, host = "a", port = 1)
        val remote = ServerConfig(enabled = true, host = "b", port = 2)
        val timeout = SocketTimeoutException("slow")
        assertEquals(
            ApiFailure.Unreachable("a:1, b:2", true, timeout),
            failureOf(ServerUnreachableException(listOf(local, remote), timeout)),
        )
        val refused = IOException("refused")
        assertEquals(
            ApiFailure.Unreachable("a:1", false, refused),
            failureOf(ServerUnreachableException(listOf(local), refused)),
        )
        assertSame(ApiFailure.NotConfigured, failureOf(NoServerConfiguredException()))
        assertEquals(ApiFailure.Unreachable(null, false, refused), failureOf(refused))
        assertEquals(ApiFailure.Unreachable(null, true, timeout), failureOf(timeout))
        val bad = SerializationException("bad")
        assertEquals(ApiFailure.Malformed(bad), failureOf(bad))
    }

    @Test
    fun `an http failure without its response still has a status`() {
        val original = http(502, "x")
        val copy = ObjectInputStream(
            ByteArrayInputStream(ByteArrayOutputStream().also { ObjectOutputStream(it).use { o -> o.writeObject(original) } }.toByteArray()),
        ).use { it.readObject() } as HttpException
        assertEquals(ApiFailure.Api(502, "Response.error()"), failureOf(copy))
        assertEquals(ApiFailure.Api(200, "OK"), failureOf(HttpException(Response.success("x"))))
    }
}
