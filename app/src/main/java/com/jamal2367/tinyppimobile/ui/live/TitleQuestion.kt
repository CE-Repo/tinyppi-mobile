package com.jamal2367.tinyppimobile.ui.live

import androidx.annotation.StringRes
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget

/**
 * What a press on a title asks, and what the first of its answers does.
 *
 * [play] names that answer - Play, Resume, or Open for a series, which is not
 * something that can be put on - and [onPlay] is what the press used to do by
 * itself before it asked, told whether to start from the beginning instead.
 *
 * [resumable] is a film or an episode the box has a point to resume from: it
 * is offered from the beginning as well, and the point can be forgotten.
 */
internal class TitleQuestion(
    val target: MarkTarget,
    @StringRes val play: Int,
    val resumable: Boolean = false,
    val onPlay: (fromStart: Boolean) -> Unit,
)

/* What each kind of tile is called by when it is asked about. */

internal fun LibraryFilm.markTarget() = MarkTarget(MarkTarget.Kind.MOVIE, id, title)

internal fun LibraryShow.markTarget() = MarkTarget(MarkTarget.Kind.SHOW, id, title)

internal fun LibraryEpisode.markTarget() = MarkTarget(
    MarkTarget.Kind.EPISODE,
    id,
    listOf(code, title).filter { it.isNotBlank() }.joinToString(META_GAP),
)

internal fun ContinueItem.markTarget() = if (isEpisode) {
    MarkTarget(
        MarkTarget.Kind.EPISODE,
        id,
        listOf(show.ifEmpty { title }, code).filter { it.isNotBlank() }.joinToString(META_GAP),
    )
} else {
    MarkTarget(MarkTarget.Kind.MOVIE, id, title)
}
