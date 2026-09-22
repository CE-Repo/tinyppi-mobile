package com.jamal2367.tinyppimobile.data.model

import kotlinx.serialization.SerialName
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
    /**
     * What IMDb made of it, or TMDb where IMDb had nothing to say; 0 where
     * neither did, which is what a library that was never scraped looks like.
     */
    val rating: Double = 0.0,
    /** Which of the two that was - `imdb` or `tmdb` - for the badge to say. */
    @SerialName("rating_from") val ratingFrom: String = "",
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

/**
 * The series the box has, as the add-on's `/api/series` answers them.
 *
 * The same shelf as the films with one floor more: what comes back here is the
 * wall of shows, and the episodes of one of them are asked for only when that
 * show is opened (see [EpisodeList]). A house with ninety series in it would
 * otherwise be sent every episode of all of them to draw a wall of ninety
 * posters.
 */
@Serializable
data class SeriesLibrary(
    val shows: List<LibraryShow> = emptyList(),
    val count: Int = 0,
    /** The list's own tag, which changes only when the shelf does. */
    val tag: String = "",
)

/** One series on the wall. */
@Serializable
data class LibraryShow(
    /** Kodi's own id for it, which is what asking for its episodes names. */
    val id: Int = 0,
    val title: String = "",
    /** 0 where the library has no year for it. */
    val year: Int = 0,
    /** The poster's own tag, or empty for a show that has none. */
    val poster: String = "",
    /**
     * The tag of the picture of it at the shape a television is, or empty.
     *
     * A show carries this where a film does not, because opening one gives its
     * episodes a picture to stand under - and a poster turned on its side to
     * fill that space is a poster nobody can read.
     */
    val fanart: String = "",
    /** How many episodes the library holds; 0 where it says nothing. */
    val episodes: Int = 0,
    /**
     * How many of them have not been watched.
     *
     * How many are left rather than how many are gone: a shelf is scanned for
     * what there is still to see, and a tile saying "4" is read as four
     * waiting.
     */
    val unseen: Int = 0,
    /**
     * What IMDb made of it, or TMDb where IMDb had nothing to say; 0 where
     * neither did, which is what a library that was never scraped looks like.
     */
    val rating: Double = 0.0,
    /** Which of the two that was - `imdb` or `tmdb` - for the badge to say. */
    @SerialName("rating_from") val ratingFrom: String = "",
    /** Whether the box counts every episode of it as seen. */
    val watched: Boolean = false,
)

/** The episodes of one show, as `/api/episodes?tvshowid=` answers them. */
@Serializable
data class EpisodeList(
    val tvshowid: Int = 0,
    /** The show's own name, for the line over the list. */
    val title: String = "",
    val episodes: List<LibraryEpisode> = emptyList(),
    val count: Int = 0,
    val tag: String = "",
)

/** One episode: what its row draws, and what starting it names. */
@Serializable
data class LibraryEpisode(
    val id: Int = 0,
    /** Empty for an episode the library has no name for; the row uses [code]. */
    val title: String = "",
    /** -1 where the library files it under no season at all; 0 is a special. */
    val season: Int = -1,
    val episode: Int = -1,
    /** The still's own tag, or empty for an episode that has none. */
    val thumb: String = "",
    val duration: Int = 0,
    val watched: Boolean = false,
    /** Where the box got to last time, in seconds, or 0 to start fresh. */
    val resume: Int = 0,
) {
    /** How far through it the box got, 0 to 1, or null for one to start fresh. */
    val progress: Float?
        get() = if (resume > 0 && duration > 0) {
            (resume.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            null
        }

    /**
     * S01E04, or E04 where the library knows the number but not the season,
     * or nothing at all.
     *
     * Also what an episode with no name of its own is called: the number is
     * the only name it has ever had.
     */
    val code: String
        get() = buildString {
            if (season > 0) append("S%02d".format(season))
            if (episode >= 0) append("E%02d".format(episode))
        }
}

/** What starting an episode asks for: Kodi's own id, and nothing else. */
@Serializable
data class PlayEpisodeBody(val episodeid: Int)

/**
 * The films and episodes the box was stopped in the middle of, as the add-on's
 * `/api/continue` answers them: the last one seen first.
 *
 * One list for both, because that is how the box reads it (Kodi's own "in
 * progress" filter, sorted by when each was last played); the films screen
 * shows the films on it and the series screen the episodes. A box offering
 * only one of the two shelves sends only that half.
 */
@Serializable
data class ContinueList(
    val items: List<ContinueItem> = emptyList(),
    val count: Int = 0,
    val tag: String = "",
)

/** One title on the continue-watching row. */
@Serializable
data class ContinueItem(
    /** `movie` or `episode`: which of the two ids [id] is. */
    val kind: String = "",
    /** Kodi's own id for the film or the episode, which is what starting it names. */
    val id: Int = 0,
    /** The film's name, or the episode's own; empty for an unnamed episode. */
    val title: String = "",
    /** 0 where the library has no year for it; episodes carry none. */
    val year: Int = 0,
    /**
     * The poster's tag: the film's own, or the show's for an episode - which
     * the box hands out under the episode's id (see `MediaUrls.continuePoster`).
     */
    val poster: String = "",
    val duration: Int = 0,
    /** Where the box got to, in seconds; always more than nothing on this row. */
    val resume: Int = 0,
    /** The show an episode belongs to, for the name under its poster. */
    val show: String = "",
    val season: Int = -1,
    val episode: Int = -1,
    /** When it was last played, as Kodi writes it; the order the row is in. */
    val lastplayed: String = "",
    /**
     * What IMDb made of it, or TMDb; 0 where neither did. An episode carries
     * its show's rating, because it stands on the row as that show's poster.
     */
    val rating: Double = 0.0,
    /** Which of the two that was - `imdb` or `tmdb` - for the badge to say. */
    @SerialName("rating_from") val ratingFrom: String = "",
) {
    val isEpisode: Boolean get() = kind == "episode"

    /**
     * Which tile on the row a press is waiting on. The kind goes in with the
     * id because a film and an episode can share a number.
     */
    val key: String get() = "$kind-$id"

    /** How far through it the box got, 0 to 1, or null without a length. */
    val progress: Float?
        get() = if (resume > 0 && duration > 0) {
            (resume.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            null
        }

    /** S01E04, the way an episode row writes it; empty for a film. */
    val code: String
        get() = if (!isEpisode) "" else buildString {
            if (season > 0) append("S%02d".format(season))
            if (episode >= 0) append("E%02d".format(episode))
        }
}
