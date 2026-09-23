package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.ModeBody
import com.jamal2367.tinyppimobile.data.model.PlayBody
import com.jamal2367.tinyppimobile.data.model.PlayEpisodeBody
import com.jamal2367.tinyppimobile.data.model.ResumeBody
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.WatchedBody
import com.jamal2367.tinyppimobile.data.remote.TinyPpiApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A box made up for a test.
 *
 * Every answer is a function the test can replace - return something, or
 * throw the failure a real box would have produced - and every call is
 * counted, so a test can say not only what the app showed but how often it
 * asked.
 */
class FakeApi : TinyPpiApi {

    var state: suspend () -> JsonObject = { JsonObject(emptyMap()) }
    var library: suspend () -> Library = { Library() }
    var series: suspend () -> SeriesLibrary = { SeriesLibrary() }
    var episodes: suspend (Int) -> EpisodeList = { EpisodeList(tvshowid = it) }
    var continuing: suspend () -> ContinueList = { ContinueList() }
    var history: suspend () -> History = { History() }
    var command: suspend (CommandBody) -> CommandAck = { CommandAck(ok = true, action = it.action) }
    var play: suspend (Int) -> CommandAck = { CommandAck(ok = true) }
    /** Every film start as it was sent, resume flag and all. */
    val plays = mutableListOf<PlayBody>()
    var clearResume: suspend (ResumeBody) -> CommandAck = { CommandAck(ok = true) }
    var playEpisode: suspend (Int) -> CommandAck = { CommandAck(ok = true) }
    var setWatched: suspend (WatchedBody) -> CommandAck = { CommandAck(ok = true) }

    val calls = mutableListOf<String>()

    fun count(name: String): Int = calls.count { it == name }

    override suspend fun hello(): Hello {
        calls += "hello"
        return Hello()
    }

    override suspend fun state(): JsonObject {
        calls += "state"
        return state.invoke()
    }

    override suspend fun history(): History {
        calls += "history"
        return history.invoke()
    }

    override suspend fun library(): Library {
        calls += "library"
        return library.invoke()
    }

    override suspend fun series(): SeriesLibrary {
        calls += "series"
        return series.invoke()
    }

    override suspend fun episodes(showId: Int): EpisodeList {
        calls += "episodes"
        return episodes.invoke(showId)
    }

    override suspend fun continuing(): ContinueList {
        calls += "continuing"
        return continuing.invoke()
    }

    override suspend fun setMode(body: ModeBody): CommandAck {
        calls += "mode"
        return CommandAck(ok = true, mode = body.mode)
    }

    override suspend fun command(body: CommandBody): CommandAck {
        calls += "command:${body.action}"
        return command.invoke(body)
    }

    override suspend fun play(body: PlayBody): CommandAck {
        calls += "play"
        plays += body
        return play.invoke(body.movieid)
    }

    override suspend fun playEpisode(body: PlayEpisodeBody): CommandAck {
        calls += "playEpisode"
        return playEpisode.invoke(body.episodeid)
    }

    override suspend fun clearResume(body: ResumeBody): CommandAck {
        calls += "clearResume"
        return clearResume.invoke(body)
    }

    override suspend fun setWatched(body: WatchedBody): CommandAck {
        calls += "setWatched"
        return setWatched.invoke(body)
    }

    companion object {
        /** The app's own settings for reading a box - lenient, as the container's are. */
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
