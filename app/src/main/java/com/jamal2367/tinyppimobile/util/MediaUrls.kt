package com.jamal2367.tinyppimobile.util

import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.RelatedTitle
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import java.net.URLEncoder

/**
 * The addresses of the pictures a box serves.
 *
 * Absolute rather than relative, because they are handed to the image loader
 * rather than to Retrofit: nothing retargets them at whichever box is
 * answering, so the box has to be named here. The token is not: the add-on
 * reads it from the header only, which the image loader's client adds for the
 * box the address names (see `BoxTokenInterceptor`).
 */
object MediaUrls {

    /** Which picture of the playing title to ask for. */
    enum class ArtKind(val id: String) {
        POSTER("poster"),
        FANART("fanart");

        fun tagOf(tags: ArtTags): String = when (this) {
            POSTER -> tags.poster
            FANART -> tags.fanart
        }
    }

    /**
     * The poster or the fanart of what is playing, or null when there is none.
     *
     * The address carries the picture's own tag, which changes only when the
     * picture does. That is what lets a poster be fetched once per film however
     * often a screen is reopened: the next film asks a different address rather
     * than the same one twice, and the box answers this one with a week of
     * cache and an immutable.
     *
     * Null for a title whose library entry has no such picture - and for one
     * with no library entry at all, which is most of what a file player plays.
     */
    fun art(server: ServerConfig?, tags: ArtTags, kind: ArtKind): String? {
        val box = server ?: return null
        val tag = kind.tagOf(tags).takeIf { it.isNotBlank() } ?: return null
        return buildString {
            append(box.baseUrl)
            append("/api/art?kind=")
            append(kind.id)
            append("&v=")
            append(tag.encoded())
        }
    }

    /**
     * The poster of one of the library's films, or null where it has none.
     *
     * The same address as the playing title's artwork with the film named on
     * it, and the same bargain: the tag changes only when the picture does, so
     * a wall of posters scrolled past twice is fetched once. Which is what
     * makes a wall of five hundred affordable at all - the loader asks for the
     * dozen on screen, and holds them.
     */
    fun filmPoster(server: ServerConfig?, film: LibraryFilm): String? =
        shelfArt(server, "poster", "movieid", film.id, film.poster)

    /** The poster of one of the library's series, or null where it has none. */
    fun showPoster(server: ServerConfig?, show: LibraryShow): String? =
        shelfArt(server, "poster", "tvshowid", show.id, show.poster)

    /**
     * The picture of a series at the shape a television is, or null.
     *
     * Addressed by the show and a tag rather than by a model, because what
     * asks for it is the open show's episode list, which is holding the tag
     * and not the tile it came off.
     */
    fun showFanart(server: ServerConfig?, showId: Int, tag: String): String? =
        shelfArt(server, "fanart", "tvshowid", showId, tag)

    /**
     * The still of one episode, or null where it has none.
     *
     * A still rather than a poster, and at the shape a television picture is:
     * every episode of a show shares the show's poster, and a list of forty
     * rows each carrying the same picture says nothing about any of them.
     */
    fun episodeStill(server: ServerConfig?, episode: LibraryEpisode): String? =
        shelfArt(server, "thumb", "episodeid", episode.id, episode.thumb)

    /**
     * The poster on the continue-watching row: a film's own, or an episode's
     * show's - which the box files under the episode's id, so an episode of a
     * show nobody has opened still has one.
     */
    fun continuePoster(server: ServerConfig?, item: ContinueItem): String? =
        if (item.isEpisode) {
            shelfArt(server, "poster", "episodeid", item.id, item.poster)
        } else {
            shelfArt(server, "poster", "movieid", item.id, item.poster)
        }

    /**
     * One of a title's pictures for its details - `poster`, `fanart` or
     * `thumb` - addressed by the id its kind goes by; null without a tag.
     * A target without an id is the playing title, whose details come from
     * the snapshot: its pictures are asked for as the playing title's are.
     */
    fun details(server: ServerConfig?, target: MarkTarget, kind: String, tag: String): String? =
        if (target.id <= 0) playingArt(server, kind, tag) else shelfArt(
            server,
            kind,
            when (target.kind) {
                MarkTarget.Kind.MOVIE -> "movieid"
                MarkTarget.Kind.SHOW -> "tvshowid"
                MarkTarget.Kind.EPISODE -> "episodeid"
            },
            target.id,
            tag,
        )

    /**
     * The portrait of one of a title's cast or crew, by the tag its details
     * gave it (see `_portrait` in the add-on's `web/library/details.py`), or
     * null where it has none. The tag is the picture's name and its version
     * both, so the box answers it as immutable.
     */
    fun portrait(server: ServerConfig?, tag: String): String? {
        val box = server ?: return null
        val picture = tag.takeIf { it.isNotBlank() }?.encoded() ?: return null
        return "${box.baseUrl}/api/art?kind=thumb&portrait=$picture&v=$picture"
    }

    /**
     * The poster of a title a details page leads on to: the library's own
     * where it holds the title, else TMDb's through the box (a portrait tag,
     * see [portrait]); null where it has neither.
     */
    fun related(server: ServerConfig?, title: RelatedTitle): String? {
        if (title.held) return shelfArt(server, "poster", if (title.isShow) "tvshowid" else "movieid", title.id, title.poster)
        val box = server ?: return null
        val picture = title.portrait.takeIf { it.isNotBlank() }?.encoded() ?: return null
        return "${box.baseUrl}/api/art?kind=poster&portrait=$picture&v=$picture"
    }

    /** One picture of the playing title, by its kind and tag; null without a tag. */
    private fun playingArt(server: ServerConfig?, kind: String, tag: String): String? {
        val box = server ?: return null
        val picture = tag.takeIf { it.isNotBlank() }?.encoded() ?: return null
        return "${box.baseUrl}/api/art?kind=$kind&v=$picture"
    }

    /**
     * One picture off one of the library shelves.
     *
     * The three shelves address their art identically bar which kind and which
     * id -- the film wall's bargain in [filmPoster] is the bargain here too --
     * so they are built in one place rather than three.
     */
    private fun shelfArt(
        server: ServerConfig?,
        kind: String,
        name: String,
        id: Int,
        tag: String,
    ): String? {
        val box = server ?: return null
        val picture = tag.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            append(box.baseUrl)
            append("/api/art?kind=")
            append(kind)
            append("&")
            append(name)
            append("=")
            append(id)
            append("&v=")
            append(picture.encoded())
        }
    }

    private fun String.encoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
