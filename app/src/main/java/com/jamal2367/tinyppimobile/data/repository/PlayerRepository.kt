package com.jamal2367.tinyppimobile.data.repository

import com.jamal2367.tinyppimobile.data.model.ApiErrorBody
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.ModeBody
import com.jamal2367.tinyppimobile.data.model.PlayerAction
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.data.remote.NoServerConfiguredException
import com.jamal2367.tinyppimobile.data.remote.ServerUnreachableException
import com.jamal2367.tinyppimobile.data.remote.TinyPpiApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Everything the app asks of a box that is not the live stream.
 *
 * One layer above Retrofit, for the reason the layer usually exists: every
 * failure comes back as an [ApiFailure] a screen can put words to, rather than
 * as whichever of four unrelated exception types happened to be thrown.
 */
class PlayerRepository(
    private val api: TinyPpiApi,
    private val json: Json,
) {

    /** What the box is - the cheapest call there is, and the only unauthenticated one. */
    suspend fun hello(): Hello = call { api.hello() }

    /** The playing title's chart samples and its event list. */
    suspend fun history(): History = call { api.history() }

    /**
     * Put the driver into one of the VS10 modes the snapshot offered.
     *
     * Only the modes that came with the snapshot are worth sending: the far
     * side checks the name against the same set the on-screen dialog draws,
     * and one that is not in it is refused rather than guessed at.
     */
    suspend fun setMode(mode: String) {
        call { api.setMode(ModeBody(mode)) }
    }

    /** One transport command, with the value the action takes. */
    suspend fun command(action: PlayerAction, value: Double? = null) {
        call { api.command(CommandBody(action.id, value)) }
    }

    suspend fun playPause() = command(PlayerAction.PLAY_PAUSE)

    suspend fun stop() = command(PlayerAction.STOP)

    /** Step to the chapter before this one. Refused on a file with no chapters. */
    suspend fun previousChapter() = command(PlayerAction.CHAPTER_PREVIOUS)

    /** Step to the chapter after this one. Refused on a file with no chapters. */
    suspend fun nextChapter() = command(PlayerAction.CHAPTER_NEXT)

    /** Jump [seconds] forwards, or back for a negative number. */
    suspend fun seekBy(seconds: Int) = command(
        PlayerAction.SEEK,
        seconds.toDouble().coerceIn(
            -PlayerAction.SEEK_LIMIT_SECONDS,
            PlayerAction.SEEK_LIMIT_SECONDS,
        ),
    )

    /** Jump to a point in the running time, 0 to 100. */
    suspend fun seekTo(percent: Float) =
        command(PlayerAction.SEEK_PERCENT, percent.toDouble().coerceIn(0.0, 100.0))

    suspend fun setVolume(level: Int) =
        command(PlayerAction.VOLUME, level.coerceIn(0, 100).toDouble())

    suspend fun toggleMute() = command(PlayerAction.MUTE)

    suspend fun selectAudio(index: Int) = command(PlayerAction.AUDIO, index.toDouble())

    /** Pick a subtitle track, or pass null to switch them off. */
    suspend fun selectSubtitle(index: Int?) = command(
        PlayerAction.SUBTITLE,
        index?.toDouble() ?: PlayerAction.SUBTITLES_OFF,
    )

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ApiFailure) {
        throw failure
    } catch (http: HttpException) {
        throw http.toFailure()
    } catch (unreachable: ServerUnreachableException) {
        throw ApiFailure.Unreachable(
            serverLabel = unreachable.attempted.joinToString(", ") { it.label },
            timedOut = unreachable.cause is SocketTimeoutException,
            cause = unreachable.cause,
        )
    } catch (_: NoServerConfiguredException) {
        throw ApiFailure.NotConfigured
    } catch (io: IOException) {
        throw ApiFailure.Unreachable(null, io is SocketTimeoutException, io)
    } catch (malformed: SerializationException) {
        throw ApiFailure.Malformed(malformed)
    }

    private fun HttpException.toFailure(): ApiFailure {
        val body = response()?.errorBody()?.string()
        return ApiFailure.Api(code(), body.errorMessage() ?: message())
    }

    private fun String?.errorMessage(): String? = this?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
        ?.error
        ?.takeIf { it.isNotBlank() }
}
