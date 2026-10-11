package com.jamal2367.tinyppimobile.ui.navigation

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import com.jamal2367.tinyppimobile.ui.settings.SettingsViewModel
import com.jamal2367.tinyppimobile.ui.history.HistoryViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
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
    modifier: Modifier,
) {
    // The live reading outlives its tab. Two of the screens are windows onto
    // the same snapshot, and a view model each would be several objects
    // collecting the same flow.
    // Always composed inside the activity.
    val activity = LocalActivity.current!! as ViewModelStoreOwner
    val liveViewModel: LiveViewModel =
        viewModel(viewModelStoreOwner = activity, factory = LiveViewModel.Factory)

    HorizontalPager(
        state = pagerState,
        key = { page -> pageKey(destinations, page) },
        beyondViewportPageCount = 1,
        modifier = modifier,
    ) { page ->
        CompositionLocalProvider(LocalIsCurrentPage provides (page == pagerState.currentPage)) {
            Page(destinations.getOrNull(page), liveViewModel, activity, onOpenSettings)
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

/**
 * A page's key: its tab's route, or the bare index for a page the row no
 * longer has - which the pager can still ask about for the frame in which a
 * tab leaves the bar.
 */
internal fun pageKey(destinations: List<TopLevelDestination>, page: Int): Any =
    destinations.getOrNull(page)?.route ?: page

@Composable
internal fun Page(
    destination: TopLevelDestination?,
    liveViewModel: LiveViewModel,
    owner: ViewModelStoreOwner,
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
            historyViewModel = owner.model(HistoryViewModel::class.java),
        )
        TopLevelDestination.HISTORY -> HistoryScreen(
            onOpenSettings = onOpenSettings,
            viewModel = owner.model(HistoryViewModel::class.java),
        )
        null -> Unit
        // SETTINGS, the one left.
        else -> SettingsScreen(owner.model(SettingsViewModel::class.java))
    }
}

/**
 * The activity's own instance of a screen's view model - asked for only once
 * that screen is drawn, so a tab nobody opens fetches nothing.
 */
private fun <T : ViewModel> ViewModelStoreOwner.model(type: Class<T>): T =
    ViewModelProvider(this, ScreenViewModels)[type]

/**
 * Builds a screen's view model on the application its activity belongs to.
 *
 * The default factory would do the same, but it keeps the first application
 * it is handed in a static field: one per process, so on a phone the same
 * thing. Under Robolectric every test brings an application of its own, and
 * the static field kept handing the screens the first test's - with the
 * first test's settings and the first test's box behind it.
 */
private object ScreenViewModels : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        modelClass.getConstructor(Application::class.java)
            .newInstance(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
}
