package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.model.CommandAck
import com.jamal2367.tinyppimobile.data.model.CommandBody
import com.jamal2367.tinyppimobile.data.model.ContinueList
import com.jamal2367.tinyppimobile.data.model.Hello
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.EpisodeList
import com.jamal2367.tinyppimobile.data.model.Library
import com.jamal2367.tinyppimobile.data.model.ModeBody
import com.jamal2367.tinyppimobile.data.model.PlayBody
import com.jamal2367.tinyppimobile.data.model.PlayEpisodeBody
import com.jamal2367.tinyppimobile.data.model.SeriesLibrary
import com.jamal2367.tinyppimobile.data.model.WatchedBody
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Every endpoint the add-on's web server offers, less the two that only a
 * browser wants: `/api/stream` is read by [SnapshotStream], and `/api/art` is
 * an address handed to the image loader rather than a call made here.
 *
 * The paths are relative: the host they end up at is decided per request by
 * [FailoverInterceptor].
 */
interface TinyPpiApi {

    /**
     * What the box is, before anything has been asked of it.
     *
     * Deliberately unauthenticated on the far side - it carries no player
     * state, only what a client needs to know before it can ask for any - so
     * this is the one call that tells a reachable box with a wrong token apart
     * from an address that answers nothing at all.
     */
    @GET("api/hello")
    suspend fun hello(): Hello

    /**
     * The current snapshot, whole.
     *
     * Answered as a [JsonObject] rather than as the model: the same merge that
     * folds a delta frame onto a base works on objects, and a state fetched by
     * asking has to be able to serve as that base. The decode happens once, in
     * [SnapshotStream].
     */
    @GET("api/state")
    suspend fun state(): JsonObject

    /** The playing title's chart samples and its event list. */
    @GET("api/history")
    suspend fun history(): History

    /**
     * The films the box has, for the wall the live screen shows while nothing
     * is playing.
     *
     * Refused with a 403 by a box whose owner has switched the library card
     * off, or one that will not be told what to play at all - both of which
     * are answers rather than failures, and the screen simply has no wall.
     */
    @GET("api/library")
    suspend fun library(): Library

    /**
     * The series the box has, for the wall under the films.
     *
     * Refused with a 403 the same way the films are, and by a setting of its
     * own: a box can offer the one shelf and not the other.
     */
    @GET("api/series")
    suspend fun series(): SeriesLibrary

    /**
     * The episodes of one series.
     *
     * Asked for when a show is opened and not before, which is why it is a
     * call of its own rather than part of the shelf.
     */
    @GET("api/episodes")
    suspend fun episodes(@Query("tvshowid") showId: Int): EpisodeList

    /**
     * The films and episodes left half-watched, the last one seen first, for
     * the row at the top of both shelves.
     *
     * Refused with a 403 where neither shelf is offered, and unknown (404) to
     * an add-on older than the row - both of which simply mean no row.
     */
    @GET("api/continue")
    suspend fun continuing(): ContinueList

    /** Put the driver into one of the VS10 modes the snapshot offered. */
    @POST("api/mode")
    suspend fun setMode(@Body body: ModeBody): CommandAck

    /** One transport command. */
    @POST("api/command")
    suspend fun command(@Body body: CommandBody): CommandAck

    /**
     * Put one of the library's films on the television.
     *
     * Its own endpoint rather than a transport command: every one of those
     * acts on a player that is already running, and this is the one call that
     * starts one. Resumed by the box where the library holds a point to
     * resume from, the same as pressing the film in Kodi's own window.
     */
    @POST("api/play")
    suspend fun play(@Body body: PlayBody): CommandAck

    /**
     * Put one episode of a series on the television.
     *
     * The same route a film is started through -- it is one act, something in
     * the library being started -- and which of the two it is, is which id the
     * body carries.
     */
    @POST("api/play")
    suspend fun playEpisode(@Body body: PlayEpisodeBody): CommandAck

    /**
     * Mark a film, a series or one episode as seen or unseen in the box's own
     * library.
     *
     * Unknown (404) to an add-on older than the dialog that asks, which the
     * screen reports the way it reports any other refusal.
     */
    @POST("api/watched")
    suspend fun setWatched(@Body body: WatchedBody): CommandAck
}
