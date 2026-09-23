package com.jamal2367.tinyppimobile.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.ui.history.HistoryScreen
import com.jamal2367.tinyppimobile.ui.library.FilmsScreen
import com.jamal2367.tinyppimobile.ui.library.SeriesScreen
import com.jamal2367.tinyppimobile.ui.live.LiveScreen
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.metadata.MetadataScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsScreen

/**
 * The tabs, side by side, so a swipe to either side is the tab next door.
 *
 * A pager rather than a navigation graph: every screen here is a tab and none
 * is a place further in, so there is no stack to keep - only an order, which
 * is the order of the bar. Each page is keyed by its tab, so a screen scrolled
 * half-way down is found there again after the pager has let it go.
 *
 * The tabs either side of the one on screen are composed as well, before
 * anybody swipes to them. Composed only once the swipe had begun, a screen was
 * being built - its list laid out, its posters asked for - in the very frames
 * the swipe was supposed to be moving it, and the swipe stuttered. What a
 * neighbour must not do while it waits off screen - run a timer, take the
 * back gesture - it asks [LocalIsCurrentPage] about first.
 */
@Composable
fun TinyPpiPager(
    destinations: List<TopLevelDestination>,
    pagerState: PagerState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The live reading outlives its tab. Two of the screens are windows onto
    // the same snapshot, and a view model each would be several objects
    // collecting the same flow.
    val activity = checkNotNull(LocalActivity.current) as ViewModelStoreOwner
    val liveViewModel: LiveViewModel =
        viewModel(viewModelStoreOwner = activity, factory = LiveViewModel.Factory)

    HorizontalPager(
        state = pagerState,
        key = { page -> destinations.getOrNull(page)?.route ?: page },
        beyondViewportPageCount = 1,
        modifier = modifier,
    ) { page ->
        CompositionLocalProvider(LocalIsCurrentPage provides (page == pagerState.currentPage)) {
            Page(destinations.getOrNull(page), liveViewModel, onOpenSettings)
        }
    }
}

/**
 * Whether the page this is read on is the one in front.
 *
 * True everywhere outside the pager, so a screen shown on its own behaves as
 * it always did.
 */
val LocalIsCurrentPage = compositionLocalOf { true }

@Composable
private fun Page(
    destination: TopLevelDestination?,
    liveViewModel: LiveViewModel,
    onOpenSettings: () -> Unit,
) {
    when (destination) {
        TopLevelDestination.LIVE -> LiveScreen(
            onOpenSettings = onOpenSettings,
            viewModel = liveViewModel,
        )
        // Both shelves read the same view model the live screen does: one
        // shelf held once, however many screens draw it.
        TopLevelDestination.FILMS -> FilmsScreen(
            onOpenSettings = onOpenSettings,
            viewModel = liveViewModel,
        )
        TopLevelDestination.SERIES -> SeriesScreen(
            onOpenSettings = onOpenSettings,
            viewModel = liveViewModel,
        )
        TopLevelDestination.METADATA -> MetadataScreen(
            onOpenSettings = onOpenSettings,
            viewModel = liveViewModel,
        )
        TopLevelDestination.HISTORY -> HistoryScreen(onOpenSettings = onOpenSettings)
        TopLevelDestination.SETTINGS -> SettingsScreen()
        null -> Unit
    }
}
