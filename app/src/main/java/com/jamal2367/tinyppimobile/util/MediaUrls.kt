package com.jamal2367.tinyppimobile.util

import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import java.net.URLEncoder

/**
 * The addresses of the pictures a box serves.
 *
 * Absolute rather than relative, because they are handed to the image loader
 * rather than to Retrofit: nothing retargets them at whichever box is
 * answering, so the box has to be named here. The token travels as `?token=`
 * for the same reason - an image request carries no headers of the loader's
 * own that the caller can set per URL.
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
            box.tokenParameter()?.let(::append)
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
    fun filmPoster(server: ServerConfig?, film: LibraryFilm): String? {
        val box = server ?: return null
        val tag = film.poster.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            append(box.baseUrl)
            append("/api/art?kind=poster&movieid=")
            append(film.id)
            append("&v=")
            append(tag.encoded())
            box.tokenParameter()?.let(::append)
        }
    }

    private fun ServerConfig.tokenParameter(): String? =
        token.trim().takeIf { it.isNotBlank() }?.let { "&token=${it.encoded()}" }

    private fun String.encoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
