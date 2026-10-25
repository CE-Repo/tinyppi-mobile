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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.components.ArrangeBackHandler
import com.jamal2367.tinyppimobile.ui.components.ArrangeableCard
import com.jamal2367.tinyppimobile.ui.components.CardScreens
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardScreen
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle
import com.jamal2367.tinyppimobile.ui.components.allCardsHidden
import com.jamal2367.tinyppimobile.ui.components.cardArranger
import com.jamal2367.tinyppimobile.ui.live.FilmTile
import com.jamal2367.tinyppimobile.ui.live.ShowTile
import com.jamal2367.tinyppimobile.ui.live.newest
import com.jamal2367.tinyppimobile.ui.live.posterRowCard
import com.jamal2367.tinyppimobile.util.MediaUrls
import com.jamal2367.tinyppimobile.ui.live.ContinueUiState
import com.jamal2367.tinyppimobile.ui.live.FILM_START_TIMEOUT_MS
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.live.TitleQuestion
import com.jamal2367.tinyppimobile.ui.live.markTarget
import com.jamal2367.tinyppimobile.data.model.LibraryEpisode
import com.jamal2367.tinyppimobile.data.model.ContinueItem
import com.jamal2367.tinyppimobile.data.model.LibraryFilm
import com.jamal2367.tinyppimobile.data.model.LibraryShow
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.ui.live.continueCard
import com.jamal2367.tinyppimobile.ui.live.DetailsActions
import com.jamal2367.tinyppimobile.ui.live.DetailsPage
import com.jamal2367.tinyppimobile.ui.live.DetailsShown
import com.jamal2367.tinyppimobile.ui.live.FormatNames
import com.jamal2367.tinyppimobile.ui.live.ShelfChoice
import com.jamal2367.tinyppimobile.ui.live.ShelfFilter
import com.jamal2367.tinyppimobile.ui.live.ShelfFilterKind
import com.jamal2367.tinyppimobile.ui.live.LocalShelfOrders
import com.jamal2367.tinyppimobile.ui.live.ShelfOrder
import com.jamal2367.tinyppimobile.ui.live.ShelfSort
import com.jamal2367.tinyppimobile.ui.live.arranged
import com.jamal2367.tinyppimobile.ui.live.formatsOf
import com.jamal2367.tinyppimobile.ui.live.genresOf
import com.jamal2367.tinyppimobile.ui.live.shelfTools
import com.jamal2367.tinyppimobile.ui.live.videoChoices
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.live.dismissSearch
import com.jamal2367.tinyppimobile.ui.live.SeasonsDialog
import com.jamal2367.tinyppimobile.ui.live.OpenShow
import com.jamal2367.tinyppimobile.ui.live.SeasonsPage
import com.jamal2367.tinyppimobile.ui.live.filmColumns
import com.jamal2367.tinyppimobile.ui.live.filmWall
import com.jamal2367.tinyppimobile.ui.live.matching
import com.jamal2367.tinyppimobile.ui.live.seriesWall
import com.jamal2367.tinyppimobile.ui.live.shelfTop
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    viewModel: LiveViewModel,
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
    // The filters, kept with the search across a turn of the phone, narrow
    // every card on the screen the search does; the order is kept between
    // launches.
    val orders = LocalShelfOrders.current
    val order = orders.of(SHELF_FILMS)
    var genre by rememberSaveable { mutableStateOf("") }
    var video by rememberSaveable { mutableStateOf("") }
    var resolution by rememberSaveable { mutableStateOf("") }
    var audio by rememberSaveable { mutableStateOf("") }
    val filter = ShelfFilter(genre, video, resolution, audio)
    val shown = remember(library.films, search, order, filter) {
        matching(library.films, search).arranged(order, filter)
    }
    val choices = remember(library.films) {
        FilmChoices(
            genres = genresOf(library.films.map { it.genres }),
            video = videoChoices(library.films),
            resolution = formatsOf(library.films.map { it.resolution }, FormatNames.RESOLUTION),
            audio = formatsOf(library.films.map { it.audio }, FormatNames.AUDIO),
        )
    }
    val resumable = remember(continuing.items) { continuing.items.filterNot { it.isEpisode } }
    // What has not been seen yet, as a wall of its own under the
    // continue-watching row: whether there is one at all is the library's
    // answer, and which of it is shown is the search's.
    val waiting = remember(library.films) { library.films.any { !it.watched } }
    val unseen = remember(shown) { shown.filterNot { it.watched } }
    val recent = remember(library.films) { newest(library.films, { it.added }, { it.id }) }
    val columns = filmColumns()
    val folds = LocalCardFolds.current
    val continueOpen = folds.isExpanded(FOLD_FILMS_CONTINUE)
    val recentOpen = folds.isExpanded(FOLD_FILMS_RECENT)
    val wallOpen = folds.isExpanded(FOLD_FILMS)
    val unseenOpen = folds.isExpanded(FOLD_FILMS_UNSEEN)
    val layout = LocalCardLayout.current
    val editing = layout.isEditing(CardScreens.FILMS)
    val labels = listOf(
        ArrangeableCard(FOLD_FILMS_CONTINUE, stringResource(R.string.continue_count, resumable.size)),
        ArrangeableCard(FOLD_FILMS_RECENT, stringResource(R.string.recent_title)),
        ArrangeableCard(FOLD_FILMS_UNSEEN, stringResource(R.string.library_unseen, unseen.size)),
        ArrangeableCard(FOLD_FILMS, stringResource(R.string.library_all, shown.size)),
    )
    ArrangeBackHandler(CardScreens.FILMS, layout)
    var detailed by remember { mutableStateOf<DetailsActions?>(null) }
    val openDetails: (TitleQuestion) -> Unit = { question ->
        detailed = question.detailsActions(viewModel)
        viewModel.openDetails(question.target)
    }
    DetailsShown(viewModel, detailed)
    val detailsContinue: (ContinueItem) -> Unit = { item ->
        val question = TitleQuestion(item.markTarget(), if (item.resume > 0) R.string.title_resume else R.string.title_play, resumable = item.resume > 0) { fromStart ->
            viewModel.playContinuing(item, fromStart)
        }
        openDetails(question)
    }
    val detailsFilm: (LibraryFilm) -> Unit = { film ->
        openDetails(TitleQuestion(
            film.markTarget(),
            if (film.resume > 0) R.string.title_resume else R.string.title_play,
            resumable = film.resume > 0,
            watched = film.watched,
        ) { fromStart -> viewModel.playFilm(film, fromStart) })
    }

    Shelf(
        configured = state.isConfigured,
        empty = library.films.isEmpty(),
        icon = Icons.Outlined.Movie,
        refreshing = library.refreshing,
        onRefresh = viewModel::pullLibrary,
        message = message,
        onMessageShown = viewModel::consumeMessage,
        onOpenSettings = onOpenSettings,
        // The cards of the wall part themselves; the arranging rows are
        // spaced out by the list.
        gap = if (editing) CardGap else 0.dp,
        screen = CardScreens.FILMS,
    ) {
        // The screen's name and the search box first, then the cards: what
        // was left half-watched and what arrived last - both out of the way
        // while somebody is searching, which is looking for something else -
        // what is still waiting to be watched, and the wall. In that order
        // until the reader moves them (see CardLayout).
        if (editing) {
            arrangeTop { stringResource(R.string.library_title) }
            cardArranger(CardScreens.FILMS, labels, layout)
            return@Shelf
        }
        shelfTop(
            title = { stringResource(R.string.library_title) },
            searchLabel = { stringResource(R.string.library_search) },
            search = search,
            onSearch = { search = it },
        )
        shelfTools(
            key = "films",
            sorts = ShelfSort.entries,
            order = order,
            onOrder = { orders.set(SHELF_FILMS, it) },
            filters = listOf(
                ShelfChoice(ShelfFilterKind.GENRE, choices.genres, genre) { genre = it },
                ShelfChoice(ShelfFilterKind.VIDEO, choices.video, video, FormatNames::video) { video = it },
                ShelfChoice(ShelfFilterKind.RESOLUTION, choices.resolution, resolution, { FormatNames.of(FormatNames.RESOLUTION, it) }) {
                    resolution = it
                },
                ShelfChoice(ShelfFilterKind.AUDIO, choices.audio, audio, { FormatNames.of(FormatNames.AUDIO, it) }) { audio = it },
            ),
            onReset = {
                genre = ""
                video = ""
                resolution = ""
                audio = ""
            },
        )
        val shownCards = layout.visible(CardScreens.FILMS, labels.map { it.id })
        if (shownCards.isEmpty()) allCardsHidden(CardScreens.FILMS, layout)
        // Whether a card has been drawn above the next one, which parts itself
        // from it; the first card sits straight under the search box.
        var above = false
        for (id in shownCards) {
            when (id) {
                FOLD_FILMS_CONTINUE -> if (search.isBlank() && resumable.isNotEmpty()) {
                    continueCard(
                        onDetails = detailsContinue,
                        items = resumable,
                        columns = columns,
                        server = state.live.server,
                        showArtwork = state.settings.showArtwork,
                        starting = continuing.starting,
                        gapAbove = above,
                        expanded = continueOpen,
                        onToggle = { folds.setExpanded(FOLD_FILMS_CONTINUE, !continueOpen) },
                        onPlay = detailsContinue,
                    )
                    above = true
                }

                FOLD_FILMS_RECENT -> if (search.isBlank() && recent.isNotEmpty()) {
                    posterRowCard(
                        key = "film-recent",
                        title = { stringResource(R.string.recent_title) },
                        items = recent,
                        itemKey = { it.id },
                        columns = columns,
                        gapAbove = above,
                        expanded = recentOpen,
                        onToggle = { folds.setExpanded(FOLD_FILMS_RECENT, !recentOpen) },
                    ) { film, modifier ->
                        FilmTile(
                            film = film,
                            poster = if (state.settings.showArtwork) {
                                MediaUrls.filmPoster(state.live.server, film)
                            } else {
                                null
                            },
                            starting = library.starting == film.id,
                            enabled = library.starting == null,
                            onPlay = { detailsFilm(film) },
                            onDetails = { detailsFilm(film) },
                            modifier = modifier,
                        )
                    }
                    above = true
                }

                // A row that scrolls sideways, like the two above it, rather
                // than a second wall: it is a way in to what is still waiting,
                // and the wall of everything is the card under it. A search
                // nothing unseen answers takes the row away with it.
                FOLD_FILMS_UNSEEN -> if (waiting && unseen.isNotEmpty()) {
                    posterRowCard(
                        key = "film-unseen",
                        title = { stringResource(R.string.library_unseen, unseen.size) },
                        items = unseen,
                        itemKey = { it.id },
                        columns = columns,
                        gapAbove = above,
                        expanded = unseenOpen,
                        onToggle = { folds.setExpanded(FOLD_FILMS_UNSEEN, !unseenOpen) },
                    ) { film, modifier ->
                        FilmTile(
                            film = film,
                            poster = if (state.settings.showArtwork) {
                                MediaUrls.filmPoster(state.live.server, film)
                            } else {
                                null
                            },
                            starting = library.starting == film.id,
                            enabled = library.starting == null,
                            onPlay = { detailsFilm(film) },
                            onDetails = { detailsFilm(film) },
                            modifier = modifier,
                        )
                    }
                    above = true
                }

                // The wall, the one card left.
                else -> {
                    filmWall(
                        films = shown,
                        columns = columns,
                        server = state.live.server,
                        showArtwork = state.settings.showArtwork,
                        starting = library.starting,
                        gapAbove = above,
                        expanded = wallOpen,
                        onToggle = { folds.setExpanded(FOLD_FILMS, !wallOpen) },
                        onPlay = detailsFilm,
                        onDetails = detailsFilm,
                    )
                    above = true
                }
            }
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
    viewModel: LiveViewModel,
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
    val orders = LocalShelfOrders.current
    val order = orders.of(SHELF_SERIES).takeIf { it.sort in SERIES_SORTS } ?: ShelfOrder()
    var genre by rememberSaveable { mutableStateOf("") }
    val shown = remember(series.shows, search, order, genre) {
        matching(series.shows, search, { it.title }, { it.year }).arranged(order, genre)
    }
    val genres = remember(series.shows) { genresOf(series.shows.map { it.genres }) }
    val resumable = remember(continuing.items) { continuing.items.filter { it.isEpisode } }
    val nextUp = continuing.next
    // The shows with an episode still waiting, under the continue-watching row.
    val waiting = remember(series.shows) { series.shows.any { it.isWaiting } }
    val unseen = remember(shown) { shown.filter { it.isWaiting } }
    val recent = remember(series.shows) { newest(series.shows, { it.added }, { it.id }) }
    val columns = filmColumns()
    val folds = LocalCardFolds.current
    val continueOpen = folds.isExpanded(FOLD_SERIES_CONTINUE)
    val nextOpen = folds.isExpanded(FOLD_SERIES_NEXT)
    val recentOpen = folds.isExpanded(FOLD_SERIES_RECENT)
    val wallOpen = folds.isExpanded(FOLD_SERIES)
    val unseenOpen = folds.isExpanded(FOLD_SERIES_UNSEEN)
    val open = series.open
    val layout = LocalCardLayout.current
    val editing = layout.isEditing(CardScreens.SERIES)
    val labels = listOf(
        ArrangeableCard(FOLD_SERIES_CONTINUE, stringResource(R.string.continue_count, resumable.size)),
        ArrangeableCard(FOLD_SERIES_NEXT, stringResource(R.string.next_up_count, nextUp.size)),
        ArrangeableCard(FOLD_SERIES_RECENT, stringResource(R.string.recent_title)),
        ArrangeableCard(FOLD_SERIES_UNSEEN, stringResource(R.string.series_unseen_all, unseen.size)),
        ArrangeableCard(FOLD_SERIES, stringResource(R.string.series_all, shown.size)),
    )
    ArrangeBackHandler(CardScreens.SERIES, layout)
    var detailed by remember { mutableStateOf<DetailsActions?>(null) }
    val openDetails: (TitleQuestion) -> Unit = { question ->
        detailed = question.detailsActions(viewModel)
        viewModel.openDetails(question.target)
    }
    // A show's details have a place of their own, under the show's seasons:
    // opening the show leaves them open there, and leaving the seasons finds
    // them as they were - the same window throughout, so neither way is a
    // page that closes, a shelf that flashes and a page that opens again.
    var showActions by remember { mutableStateOf<DetailsActions?>(null) }
    val detailsShow: (LibraryShow) -> Unit = { show ->
        val question = TitleQuestion(show.markTarget(), R.string.title_open, watched = show.watched) {
            viewModel.openShow(show)
        }
        showActions = question.detailsActions(viewModel, closes = false)
        viewModel.openDetails(question.target)
    }
    val leaveShow: () -> Unit = {
        viewModel.popDetails()
        viewModel.closeShow()
    }
    // An episode on the rows of things to go on with offers the way to its
    // show as well: the show's own page, where it is on the shelf.
    val detailsContinue: (ContinueItem) -> Unit = { item ->
        val question = TitleQuestion(item.markTarget(), if (item.resume > 0) R.string.title_resume else R.string.title_play, resumable = item.resume > 0) { fromStart ->
            viewModel.playContinuing(item, fromStart)
        }
        val show = series.shows.firstOrNull { item.isEpisode && it.id == item.tvshowid }
        detailed = question.detailsActions(
            viewModel,
            onShow = show?.let {
                {
                    viewModel.closeDetails()
                    detailsShow(it)
                }
            },
        )
        viewModel.openDetails(question.target)
    }
    val details by viewModel.details.collectAsStateWithLifecycle()
    val underDetails by viewModel.underDetails.collectAsStateWithLifecycle()
    val showPage = underDetails ?: details?.takeIf { it.target.kind == MarkTarget.Kind.SHOW }
    // A show closed under its page some other way - scanned away from the
    // shelf - brings the page it was opened over back to the front.
    LaunchedEffect(open == null, underDetails) {
        if (open == null && underDetails != null) viewModel.popDetails()
    }
    val detailsEpisode: (LibraryEpisode) -> Unit = { episode ->
        openDetails(TitleQuestion(
            episode.markTarget(),
            if (episode.resume > 0) R.string.title_resume else R.string.title_play,
            resumable = episode.resume > 0,
            watched = episode.watched,
        ) { fromStart -> viewModel.playEpisode(episode, fromStart) })
    }

    // The show that is open, as a page of its own over the shelf, its seasons
    // folded away in it. Back closes it, as it does a title's details. Only
    // on the tab in front, for the reason the details give (see DetailsShown):
    // it stays open behind the live tab an episode was started for, and is
    // there again on coming back to choose the next one.
    // The show the seasons are for: the one that is open, or - while its
    // episodes are still being read - the one being opened from its page,
    // whose seasons already slide in over it and wait for the episodes there.
    val seasonsOf = open ?: showPage?.takeIf { it.target.id == series.opening }
        ?.let { page -> series.shows.firstOrNull { it.id == page.target.id } }
        ?.let { OpenShow(it.id, it.title, it.fanart) }
    val seasons: (@Composable () -> Unit)? = seasonsOf?.let { shown ->
        {
            SeasonsPage(
                open = shown,
                // Its poster and year are the shelf's, which the show is on.
                show = series.shows.firstOrNull { it.id == shown.id },
                server = state.live.server,
                showArtwork = state.settings.showArtwork,
                loading = open == null,
                starting = series.starting,
                openSeasons = series.openSeasons,
                onDismiss = leaveShow,
                onSeason = viewModel::toggleSeason,
                onPlay = detailsEpisode,
                onDetails = detailsEpisode,
            )
        }
    }
    // The seasons are the show's page turned over, in the same window: they
    // slide in over its details and back out to them (see DetailsDialog).
    DetailsPage(
        viewModel,
        showPage,
        showActions,
        over = seasons,
        onDismiss = if (seasons != null) leaveShow else viewModel::closeDetails,
    )
    // A show open with no page of its own under it has a window of its own.
    if (open != null && showPage == null && LocalIsCurrentPage.current) {
        SeasonsDialog(
            open = open,
            // Its poster and year are the shelf's, which the open show is on.
            show = series.shows.firstOrNull { it.id == open.id },
            server = state.live.server,
            showArtwork = state.settings.showArtwork,
            adaptive = state.settings.adaptiveColor,
            intensity = state.settings.adaptiveColorIntensity,
            starting = series.starting,
            openSeasons = series.openSeasons,
            onDismiss = leaveShow,
            onSeason = { viewModel.toggleSeason(it) },
            onPlay = detailsEpisode,
            onDetails = detailsEpisode,
        )
    }
    // After the seasons, so an episode's details stand over them.
    DetailsShown(viewModel, detailed, except = MarkTarget.Kind.SHOW)

    Shelf(
        configured = state.isConfigured,
        // A show that is open is a screen with something on it whatever the
        // shelf behind it holds.
        // A show can only be open off a shelf that has it.
        empty = series.shows.isEmpty(),
        icon = Icons.Outlined.Tv,
        refreshing = series.refreshing,
        onRefresh = viewModel::pullSeries,
        message = message,
        onMessageShown = viewModel::consumeMessage,
        onOpenSettings = onOpenSettings,
        // The cards of the wall part themselves; the arranging rows are rows
        // the list spaces out.
        gap = if (editing) CardGap else 0.dp,
        screen = CardScreens.SERIES,
    ) {
        // The same head and the same cards as on the films screen: the
        // episodes left half-watched, whichever show they belong to, the shows
        // that gained an episode last, the shows with one still waiting, and
        // then the wall.
        if (editing) {
            arrangeTop { stringResource(R.string.series_title) }
            cardArranger(CardScreens.SERIES, labels, layout)
            return@Shelf
        }
        shelfTop(
            title = { stringResource(R.string.series_title) },
            searchLabel = { stringResource(R.string.series_search) },
            search = search,
            onSearch = { search = it },
        )
        shelfTools(
            key = "series",
            sorts = SERIES_SORTS,
            order = order,
            onOrder = { orders.set(SHELF_SERIES, it) },
            filters = listOf(ShelfChoice(ShelfFilterKind.GENRE, genres, genre) { genre = it }),
            onReset = { genre = "" },
        )
        val shownCards = layout.visible(CardScreens.SERIES, labels.map { it.id })
        if (shownCards.isEmpty()) allCardsHidden(CardScreens.SERIES, layout)
        var above = false
        for (id in shownCards) {
            when (id) {
                FOLD_SERIES_CONTINUE -> if (search.isBlank() && resumable.isNotEmpty()) {
                    continueCard(
                        onDetails = detailsContinue,
                        items = resumable,
                        columns = columns,
                        server = state.live.server,
                        showArtwork = state.settings.showArtwork,
                        starting = continuing.starting,
                        gapAbove = above,
                        expanded = continueOpen,
                        onToggle = { folds.setExpanded(FOLD_SERIES_CONTINUE, !continueOpen) },
                        onPlay = detailsContinue,
                    )
                    above = true
                }

                // What comes after: the next unseen episode of each show
                // being watched, started from its beginning.
                FOLD_SERIES_NEXT -> if (search.isBlank() && nextUp.isNotEmpty()) {
                    continueCard(
                        onDetails = detailsContinue,
                        items = nextUp,
                        key = "next-up",
                        title = { stringResource(R.string.next_up_count, nextUp.size) },
                        columns = columns,
                        server = state.live.server,
                        showArtwork = state.settings.showArtwork,
                        starting = continuing.starting,
                        gapAbove = above,
                        expanded = nextOpen,
                        onToggle = { folds.setExpanded(FOLD_SERIES_NEXT, !nextOpen) },
                        onPlay = detailsContinue,
                    )
                    above = true
                }

                FOLD_SERIES_RECENT -> if (search.isBlank() && recent.isNotEmpty()) {
                    posterRowCard(
                        key = "series-recent",
                        title = { stringResource(R.string.recent_title) },
                        items = recent,
                        itemKey = { it.id },
                        columns = columns,
                        gapAbove = above,
                        expanded = recentOpen,
                        onToggle = { folds.setExpanded(FOLD_SERIES_RECENT, !recentOpen) },
                    ) { show, modifier ->
                        ShowTile(
                            show = show,
                            poster = if (state.settings.showArtwork) {
                                MediaUrls.showPoster(state.live.server, show)
                            } else {
                                null
                            },
                            opening = series.opening == show.id,
                            enabled = series.opening == null,
                            onOpen = { detailsShow(show) },
                            onDetails = { detailsShow(show) },
                            modifier = modifier,
                        )
                    }
                    above = true
                }

                // A sideways row, as on the films screen.
                FOLD_SERIES_UNSEEN -> if (waiting && unseen.isNotEmpty()) {
                    posterRowCard(
                        key = "series-unseen",
                        title = { stringResource(R.string.series_unseen_all, unseen.size) },
                        items = unseen,
                        itemKey = { it.id },
                        columns = columns,
                        gapAbove = above,
                        expanded = unseenOpen,
                        onToggle = { folds.setExpanded(FOLD_SERIES_UNSEEN, !unseenOpen) },
                    ) { show, modifier ->
                        ShowTile(
                            show = show,
                            poster = if (state.settings.showArtwork) {
                                MediaUrls.showPoster(state.live.server, show)
                            } else {
                                null
                            },
                            opening = series.opening == show.id,
                            enabled = series.opening == null,
                            onOpen = { detailsShow(show) },
                            onDetails = { detailsShow(show) },
                            modifier = modifier,
                        )
                    }
                    above = true
                }

                // The wall, the one card left.
                else -> {
                    seriesWall(
                        shows = shown,
                        columns = columns,
                        server = state.live.server,
                        showArtwork = state.settings.showArtwork,
                        opening = series.opening,
                        gapAbove = above,
                        expanded = wallOpen,
                        onToggle = { folds.setExpanded(FOLD_SERIES, !wallOpen) },
                        onOpen = detailsShow,
                        onDetails = detailsShow,
                    )
                    above = true
                }
            }
        }
    }
}

/**
 * Only the primary action closes the page - unless it [closes] not, as a
 * show's does, whose seasons open over it; state changes update it in place.
 */
private fun TitleQuestion.detailsActions(
    viewModel: LiveViewModel,
    closes: Boolean = true,
    onShow: (() -> Unit)? = null,
) = DetailsActions(
    play = play,
    resumable = resumable,
    watched = watched,
    onShow = onShow,
    onMark = { watched ->
        viewModel.setWatched(target, watched)
    },
    onClearResume = {
        viewModel.clearResume(target)
    },
) { fromStart ->
    if (!fromStart && closes) viewModel.closeDetails()
    onPlay(fromStart)
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
    gap: Dp,
    screen: String,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions
            .onEach { interaction -> if (interaction is DragInteraction.Start) dismissSearch(focus, keyboard) }
            .launchIn(this)
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

            // The headings on the shelf answer a long press by opening this
            // screen for rearranging (see SectionHeading).
            CompositionLocalProvider(LocalCardScreen provides screen) {
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
}

/**
 * What the nine shelf cards are remembered by - folded, moved or taken off. Named for the card rather than
 * its heading, so a translation or a rename does not open anything again.
 */
private const val FOLD_FILMS_CONTINUE = "shelf.films.continue"
private const val FOLD_FILMS = "shelf.films"
private const val FOLD_SERIES_CONTINUE = "shelf.series.continue"
private const val FOLD_SERIES_NEXT = "shelf.series.next"
private const val FOLD_SERIES = "shelf.series"
private const val FOLD_FILMS_UNSEEN = "shelf.films.unseen"
private const val FOLD_SERIES_UNSEEN = "shelf.series.unseen"
private const val FOLD_FILMS_RECENT = "shelf.films.recent"
private const val FOLD_SERIES_RECENT = "shelf.series.recent"

/** A series has no running time to be put in order by. */
private val SERIES_SORTS = ShelfSort.entries - ShelfSort.RUNTIME

/** What each shelf's order is kept under. */
private const val SHELF_FILMS = "films"
private const val SHELF_SERIES = "series"

/** The values each film filter offers, read off the shelf. */
private class FilmChoices(
    val genres: List<String>,
    val video: List<String>,
    val resolution: List<String>,
    val audio: List<String>,
)

/** The screen's name alone over the arranging rows: there is no wall to search. */
private fun LazyListScope.arrangeTop(title: @Composable () -> String) {
    item(key = "shelf-title") { ScreenTitle(text = title()) }
}
