@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.jamal2367.tinyppimobile.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.live.ContinueUiState
import com.jamal2367.tinyppimobile.ui.live.FILM_START_TIMEOUT_MS
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.live.MarkWatchedDialog
import com.jamal2367.tinyppimobile.ui.live.markTarget
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.ui.live.continueCard
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.live.dismissSearch
import com.jamal2367.tinyppimobile.ui.live.episodeList
import com.jamal2367.tinyppimobile.ui.live.filmColumns
import com.jamal2367.tinyppimobile.ui.live.filmWall
import com.jamal2367.tinyppimobile.ui.live.matching
import com.jamal2367.tinyppimobile.ui.live.seriesWall
import com.jamal2367.tinyppimobile.ui.live.shelfTop
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import kotlinx.coroutines.delay
import com.jamal2367.tinyppimobile.ui.navigation.barAwarePadding
import com.jamal2367.tinyppimobile.ui.navigation.aboveBottomBar
import com.jamal2367.tinyppimobile.ui.navigation.LocalIsCurrentPage

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
    val continuing by viewModel.continuing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

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
    ContinueReader(viewModel, state.canControl, continuing)

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
    val resumable = remember(continuing.items) { continuing.items.filterNot { it.isEpisode } }
    // What has not been seen yet, as a wall of its own under the wall of
    // everything: whether there is one at all is the library's answer, and
    // which of it is shown is the search's.
    val waiting = remember(library.films) { library.films.any { !it.watched } }
    val unseen = remember(shown) { shown.filterNot { it.watched } }
    val columns = filmColumns()
    val folds = LocalCardFolds.current
    val continueOpen = folds.isExpanded(FOLD_FILMS_CONTINUE)
    val wallOpen = folds.isExpanded(FOLD_FILMS)
    val unseenOpen = folds.isExpanded(FOLD_FILMS_UNSEEN)
    var marking by remember { mutableStateOf<MarkTarget?>(null) }
    MarkReader(marking, onDone = { marking = null }, onMark = viewModel::setWatched)

    Shelf(
        configured = state.isConfigured,
        empty = library.films.isEmpty(),
        icon = Icons.Outlined.Movie,
        refreshing = library.refreshing,
        onRefresh = viewModel::pullLibrary,
        message = message,
        onMessageShown = viewModel::consumeMessage,
        onOpenSettings = onOpenSettings,
        gap = 0.dp,
    ) {
        // The screen's name and the search box first, then two cards: what
        // was left half-watched - out of the way while somebody is searching,
        // which is looking for something else - and then the wall.
        shelfTop(
            title = { stringResource(R.string.library_title) },
            searchLabel = { stringResource(R.string.library_search) },
            search = search,
            onSearch = { search = it },
        )
        val resuming = search.isBlank() && resumable.isNotEmpty()
        if (resuming) {
            continueCard(
                items = resumable,
                columns = columns,
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                starting = continuing.starting,
                expanded = continueOpen,
                onToggle = { folds.setExpanded(FOLD_FILMS_CONTINUE, !continueOpen) },
                onPlay = viewModel::playContinuing,
                onMark = { marking = it.markTarget() },
            )
        }
        filmWall(
            films = shown,
            columns = columns,
            server = state.live.server,
            showArtwork = state.settings.showArtwork,
            starting = library.starting,
            gapAbove = resuming,
            expanded = wallOpen,
            onToggle = { folds.setExpanded(FOLD_FILMS, !wallOpen) },
            onPlay = viewModel::playFilm,
            onMark = { marking = it.markTarget() },
        )
        if (waiting) {
            filmWall(
                films = unseen,
                key = "film-unseen",
                title = { stringResource(R.string.library_unseen, it) },
                columns = columns,
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                starting = library.starting,
                gapAbove = true,
                expanded = unseenOpen,
                onToggle = { folds.setExpanded(FOLD_FILMS_UNSEEN, !unseenOpen) },
                onPlay = viewModel::playFilm,
                onMark = { marking = it.markTarget() },
            )
        }
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
    val continuing by viewModel.continuing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(state.snapshot?.libraryRevision) {
        state.snapshot?.let { viewModel.noteLibraryVersion(it.libraryRevision) }
    }

    LaunchedEffect(state.canControl, series.read, series.starting) {
        if (state.canControl) viewModel.refreshSeries()
    }
    ContinueReader(viewModel, state.canControl, continuing)

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
    val resumable = remember(continuing.items) { continuing.items.filter { it.isEpisode } }
    // The shows with an episode still waiting, under the wall of all of them.
    val waiting = remember(series.shows) { series.shows.any { it.isWaiting } }
    val unseen = remember(shown) { shown.filter { it.isWaiting } }
    val columns = filmColumns()
    val folds = LocalCardFolds.current
    val continueOpen = folds.isExpanded(FOLD_SERIES_CONTINUE)
    val wallOpen = folds.isExpanded(FOLD_SERIES)
    val unseenOpen = folds.isExpanded(FOLD_SERIES_UNSEEN)
    val open = series.open
    var marking by remember { mutableStateOf<MarkTarget?>(null) }
    MarkReader(marking, onDone = { marking = null }, onMark = viewModel::setWatched)

    // The way out of a show is the way back, and on this screen the system's
    // own back gesture is the one nearest a thumb. Without this it would leave
    // the shelf altogether, which is a whole tab further than anybody pressing
    // it meant to go.
    // Only on the tab in front: composed beside another one, it would take
    // back away from a screen that has nothing to do with it.
    BackHandler(enabled = open != null && LocalIsCurrentPage.current) { viewModel.closeShow() }

    Shelf(
        configured = state.isConfigured,
        // A show that is open is a screen with something on it whatever the
        // shelf behind it holds.
        empty = series.shows.isEmpty() && open == null,
        icon = Icons.Outlined.Tv,
        refreshing = series.refreshing,
        // Inside a show this reads that show's episodes again as well as the
        // shelf behind it, which is what somebody looking at an episode list
        // is pulling for.
        onRefresh = viewModel::pullSeries,
        message = message,
        onMessageShown = viewModel::consumeMessage,
        onOpenSettings = onOpenSettings,
        // The cards of the wall part themselves; an open show is rows the
        // list spaces out.
        gap = if (open != null) CardGap else 0.dp,
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
                onMark = { marking = it.markTarget() },
            )
            return@Shelf
        }
        // The same head and the same two cards as on the films screen: the
        // episodes left half-watched, whichever show they belong to, and then
        // the wall.
        shelfTop(
            title = { stringResource(R.string.series_title) },
            searchLabel = { stringResource(R.string.series_search) },
            search = search,
            onSearch = { search = it },
        )
        val resuming = search.isBlank() && resumable.isNotEmpty()
        if (resuming) {
            continueCard(
                items = resumable,
                columns = columns,
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                starting = continuing.starting,
                expanded = continueOpen,
                onToggle = { folds.setExpanded(FOLD_SERIES_CONTINUE, !continueOpen) },
                onPlay = viewModel::playContinuing,
                onMark = { marking = it.markTarget() },
            )
        }
        seriesWall(
            shows = shown,
            columns = columns,
            server = state.live.server,
            showArtwork = state.settings.showArtwork,
            opening = series.opening,
            gapAbove = resuming,
            expanded = wallOpen,
            onToggle = { folds.setExpanded(FOLD_SERIES, !wallOpen) },
            onOpen = viewModel::openShow,
            onMark = { marking = it.markTarget() },
        )
        if (waiting) {
            seriesWall(
                shows = unseen,
                key = "series-unseen",
                title = { stringResource(R.string.series_unseen_all, it) },
                columns = columns,
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                opening = series.opening,
                gapAbove = true,
                expanded = unseenOpen,
                onToggle = { folds.setExpanded(FOLD_SERIES_UNSEEN, !unseenOpen) },
                onOpen = viewModel::openShow,
                onMark = { marking = it.markTarget() },
            )
        }
    }
}

/**
 * The seen-or-unseen question, while a held finger has one open.
 *
 * Held by the screen rather than by the view model: it is a question on the
 * screen in front of somebody, and one left open when they switch tabs is a
 * question about a title they are no longer looking at.
 */
@Composable
private fun MarkReader(
    target: MarkTarget?,
    onDone: () -> Unit,
    onMark: (MarkTarget, Boolean) -> Unit,
) {
    val asked = target ?: return
    MarkWatchedDialog(
        target = asked,
        onDismiss = onDone,
        onMark = { watched ->
            onDone()
            onMark(asked, watched)
        },
    )
}

/** Whether a show has an episode still to be seen. */
private val LibraryShow.isWaiting: Boolean
    get() = !watched && unseen > 0

/**
 * Keeps the continue-watching row read for whichever shelf is showing it.
 *
 * Read on arrival and again whenever the box says its library moved (which
 * marks it unread), the same as the shelves; and the tile a press is waiting
 * on gives itself back if the title never arrives.
 */
@Composable
private fun ContinueReader(
    viewModel: LiveViewModel,
    canControl: Boolean,
    continuing: ContinueUiState,
) {
    LaunchedEffect(canControl, continuing.read, continuing.starting) {
        if (canControl) viewModel.refreshContinuing()
    }
    LaunchedEffect(continuing.starting) {
        if (continuing.starting != null) {
            delay(FILM_START_TIMEOUT_MS)
            viewModel.continuingStarted()
        }
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
    refreshing: Boolean,
    onRefresh: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    onOpenSettings: () -> Unit,
    gap: Dp = CardGap,
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

    // The one line a pull that could not be answered leaves behind. Nowhere
    // else on this screen says whether the box is reachable - that line lives
    // on the live screen - so a pull into a box that is off would otherwise
    // spin and give nothing back.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onMessageShown()
    }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.aboveBottomBar(),
            )
        },
    ) { padding ->
        // A box nobody has named cannot be pulled for anything: there is no
        // address to ask, and the thing to do about it is the button under the
        // line, not a gesture.
        if (!configured) {
            EmptyState(
                icon = icon,
                title = stringResource(R.string.live_not_configured_title),
                message = stringResource(R.string.live_not_configured_text),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                dismissSearch(focus, keyboard)
                onRefresh()
            },
            state = pullState,
            // The expressive indicator rather than the plain one, to match the
            // shapes the rest of the app is drawn with.
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // A shelf with nothing on it is the one most worth pulling - the
            // box was off when the screen was opened, or its library setting
            // was - so the line saying so goes in a list of its own rather
            // than standing on the screen unable to be dragged. One item at
            // the size of the viewport, so it is centred exactly as it was.
            if (empty) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            icon = icon,
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.library_empty_text),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                state = listState,
                contentPadding = barAwarePadding(horizontal = ScreenEdge, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxSize(),
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
    return viewModel(viewModelStoreOwner = activity, factory = LiveViewModel.Factory)
}

/**
 * What the six shelf cards are remembered by. Named for the card rather than
 * its heading, so a translation or a rename does not open anything again.
 */
private const val FOLD_FILMS_CONTINUE = "shelf.films.continue"
private const val FOLD_FILMS = "shelf.films"
private const val FOLD_SERIES_CONTINUE = "shelf.series.continue"
private const val FOLD_SERIES = "shelf.series"
private const val FOLD_FILMS_UNSEEN = "shelf.films.unseen"
private const val FOLD_SERIES_UNSEEN = "shelf.series.unseen"
