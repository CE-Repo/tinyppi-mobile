package com.jamal2367.tinyppimobile.data.model

import kotlinx.serialization.Serializable

/**
 * The films the box has, as the add-on's `/api/library` answers them.
 *
 * Read only while nothing is playing, which is when the screen has room to
 * offer them and a reader has a reason to look: the wall of posters is what
 * the live screen shows instead of a line saying nothing is on.
 *
 * The add-on reads its video database once and holds the answer (see
 * `resources/lib/web/library.py`), so a phone asking whenever a film ends
 * costs a request rather than a query.
 */
@Serializable
data class Library(
    val movies: List<LibraryFilm> = emptyList(),
    val count: Int = 0,
    /**
     * The list's own tag, which changes only when the library does.
     *
     * Not used to decide anything here - the app asks for the whole list and
     * takes what comes - but it is what lets the add-on answer a browser with
     * an empty 304, and it is worth keeping so a future read can do the same.
     */
    val tag: String = "",
)

/** One film on the wall. */
@Serializable
data class LibraryFilm(
    /** Kodi's own id for it, which is what starting one names. */
    val id: Int = 0,
    val title: String = "",
    /** 0 where the library has no year for it. */
    val year: Int = 0,
    /**
     * The poster's own tag, or empty for a film that has none.
     *
     * A name for the picture rather than the picture's address: the address is
     * built from it and the film's id (see `MediaUrls.filmPoster`), and it
     * changes only when the artwork does - which is what lets a poster be
     * fetched once however often the screen is reopened.
     */
    val poster: String = "",
    /** How long it runs, in seconds; 0 where the library does not know. */
    val duration: Int = 0,
    /** Whether the box counts it as seen. */
    val watched: Boolean = false,
    /**
     * How far the box got last time, in seconds, or 0 for a film that would
     * start from the beginning.
     *
     * Only a point worth resuming from is sent: a film started and stopped
     * again leaves a second or two behind, and a progress bar a pixel wide
     * says nothing. Pressing such a film resumes it, the same as pressing it
     * in Kodi's own window.
     */
    val resume: Int = 0,
) {
    /** How far through it the box got, 0 to 1, or null for one to start fresh. */
    val progress: Float?
        get() = if (resume > 0 && duration > 0) {
            (resume.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            null
        }
}

/** What starting a film asks for: Kodi's own id, and nothing else. */
@Serializable
data class PlayBody(val movieid: Int)
