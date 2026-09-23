package com.jamal2367.tinyppimobile.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
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
 * Only the page on screen is composed, and the one being swiped towards while
 * a swipe is under way. A tab nobody is looking at holds no connection and
 * runs no timer.
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
    val liveViewModel: LiveViewModel = viewModel(viewModelStoreOwner = activity)

    HorizontalPager(
        state = pagerState,
        key = { page -> destinations.getOrNull(page)?.route ?: page },
        modifier = modifier,
    ) { page ->
        when (destinations.getOrNull(page)) {
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
}
