package com.jamal2367.tinyppimobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.live.FILM_START_TIMEOUT_MS
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.live.dismissSearch
import com.jamal2367.tinyppimobile.ui.live.episodeList
import com.jamal2367.tinyppimobile.ui.live.filmColumns
import com.jamal2367.tinyppimobile.ui.live.filmWall
import com.jamal2367.tinyppimobile.ui.live.matching
import com.jamal2367.tinyppimobile.ui.live.seriesWall
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import kotlinx.coroutines.delay

/**
 * The two shelves, as screens of their own.
 *
 * The live screen offers the same walls while the box is playing nothing,
 * which is the offer somebody comes across. These are the same walls gone
 * looking for: reachable from the bar at any time, including while a film is
 * running - somebody deciding what to put on next should not have to stop what
 * is on to go and look.
 *
 * They draw nothing themselves. The walls are [com.jamal2367.tinyppimobile.ui.live]'s
 * and so is the state behind them: one view model holds both shelves for every
 * screen that shows them, so opening a tab does not send the box a question it
 * has already answered.
 */

/** The films the box has, as a wall of posters. A press starts one. */
@Composable
fun FilmsScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel = sharedLiveViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()

    // What the box says its shelves are at. A film watched to the end or
    // switched off in the middle moves it, and this is where a screen standing
    // open through that hears about it: without it the wall went on drawing
    // what it drew when it was opened until the app was started again.
    LaunchedEffect(state.snapshot?.libraryRevision) {
        state.snapshot?.let { viewModel.noteLibraryVersion(it.libraryRevision) }
    }

    // Read when the screen arrives rather than when the box falls idle, which
    // is what the live screen waits for: this shelf is opened on purpose, and
    // whoever opened it is looking at it now. And read again whenever the mark
    // above says what is drawn is the old answer.
    LaunchedEffect(state.canControl, library.read, library.starting) {
        if (state.canControl) viewModel.refreshLibrary()
    }

    // The tile that was pressed gives itself back if the film never arrives,
    // the same as on the live screen.
    LaunchedEffect(library.starting) {
        if (library.starting != null) {
            delay(FILM_START_TIMEOUT_MS)
            viewModel.filmStarted()
        }
    }

    var search by rememberSaveable { mutableStateOf("") }
    val shown = remember(library.films, search) { matching(library.films, search) }
    val columns = filmColumns()

    Shelf(
        configured = state.isConfigured,
        empty = library.films.isEmpty(),
        icon = Icons.Outlined.Movie,
        onOpenSettings = onOpenSettings,
    ) {
        filmWall(
            films = shown,
            columns = columns,
            server = state.live.server,
            showArtwork = state.settings.showArtwork,
            starting = library.starting,
            search = search,
            onSearch = { search = it },
            onPlay = viewModel::playFilm,
        )
    }
}

/**
 * The series the box has.
 *
 * A press opens one, and the screen becomes that show's episodes - the same
 * two floors the live screen offers, because a series is not something that
 * can be put on and an episode is.
 */
@Composable
fun SeriesScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel = sharedLiveViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()

    LaunchedEffect(state.snapshot?.libraryRevision) {
        state.snapshot?.let { viewModel.noteLibraryVersion(it.libraryRevision) }
    }

    LaunchedEffect(state.canControl, series.read, series.starting) {
        if (state.canControl) viewModel.refreshSeries()
    }

    LaunchedEffect(series.starting) {
        if (series.starting != null) {
            delay(FILM_START_TIMEOUT_MS)
            viewModel.episodeStarted()
        }
    }

    var search by rememberSaveable { mutableStateOf("") }
    val shown = remember(series.shows, search) {
        matching(series.shows, search, { it.title }, { it.year })
    }
    val columns = filmColumns()
    val open = series.open

    // The way out of a show is the way back, and on this screen the system's
    // own back gesture is the one nearest a thumb. Without this it would leave
    // the shelf altogether, which is a whole tab further than anybody pressing
    // it meant to go.
    BackHandler(enabled = open != null) { viewModel.closeShow() }

    Shelf(
        configured = state.isConfigured,
        // A show that is open is a screen with something on it whatever the
        // shelf behind it holds.
        empty = series.shows.isEmpty() && open == null,
        icon = Icons.Outlined.Tv,
        onOpenSettings = onOpenSettings,
    ) {
        if (open != null) {
            episodeList(
                open = open,
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                starting = series.starting,
                openSeasons = series.openSeasons,
                onBack = viewModel::closeShow,
                onSeason = viewModel::toggleSeason,
                onPlay = viewModel::playEpisode,
            )
            return@Shelf
        }
        seriesWall(
            shows = shown,
            columns = columns,
            server = state.live.server,
            showArtwork = state.settings.showArtwork,
            opening = series.opening,
            search = search,
            onSearch = { search = it },
            onOpen = viewModel::openShow,
        )
    }
}

/**
 * What both shelves are: a list, or a line saying why there is no list.
 *
 * The scrolling half is shared because the two are the same screen with a
 * different wall in them - and because the one rule that matters on both is
 * the same rule: a finger on the wall is a finger done typing, so the search
 * field lets go as soon as the list is dragged (see the live screen, which
 * says why it watches for a drag rather than for any scroll at all).
 */
@Composable
private fun Shelf(
    configured: Boolean,
    empty: Boolean,
    icon: ImageVector,
    onOpenSettings: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dismissSearch(focus, keyboard)
        }
    }

    Scaffold { padding ->
        when {
            !configured -> EmptyState(
                icon = icon,
                title = stringResource(R.string.live_not_configured_title),
                message = stringResource(R.string.live_not_configured_text),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )

            empty -> EmptyState(
                icon = icon,
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_text),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = ScreenEdge, end = ScreenEdge, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                content = content,
            )
        }
    }
}

/**
 * The one live view model, held by the activity.
 *
 * The same object the live screen reads, for the reason the nav host gives:
 * a per-destination model is another collector on the same flow, and with the
 * container's stop timeout under it that is a stream that drops and reopens
 * every time somebody changes tabs.
 */
@Composable
private fun sharedLiveViewModel(): LiveViewModel {
    val activity = checkNotNull(LocalActivity.current) as ViewModelStoreOwner
    return viewModel(viewModelStoreOwner = activity)
}
