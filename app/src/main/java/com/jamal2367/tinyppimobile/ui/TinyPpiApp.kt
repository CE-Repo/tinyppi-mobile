@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.jamal2367.tinyppimobile.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.jamal2367.tinyppimobile.BuildConfig
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.remote.ReleaseId
import com.jamal2367.tinyppimobile.di.AppContainer
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination
import com.jamal2367.tinyppimobile.ui.components.HdrGrade
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.jamal2367.tinyppimobile.ui.navigation.BAR_BOTTOM
import com.jamal2367.tinyppimobile.ui.navigation.BAR_HEIGHT
import com.jamal2367.tinyppimobile.ui.navigation.FloatingNavigationBar
import com.jamal2367.tinyppimobile.ui.navigation.LocalBottomBarSpace
import com.jamal2367.tinyppimobile.ui.navigation.rememberBarVisibility
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.jamal2367.tinyppimobile.ui.navigation.TinyPpiPager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TinyPpiApp(container: AppContainer) {
    val liveViewModel: LiveViewModel = viewModel()
    val liveState by liveViewModel.state.collectAsStateWithLifecycle()
    val library by liveViewModel.library.collectAsStateWithLifecycle()
    val series by liveViewModel.series.collectAsStateWithLifecycle()
    val showMetadata = HdrGrade.of(liveState.snapshot?.sourceType.orEmpty()) == HdrGrade.DOLBY_VISION
    val showHistory = liveState.snapshot?.let { it.playing || it.last.isPresent } == true
    // A shelf the box has said it will not offer is a tab that leads to a line
    // of apology. Both are on until it says so - which it can only say once
    // something has asked, and both stay on for a box nobody has asked yet.
    val showFilms = library.offered
    val showSeries = series.offered

    val destinations = remember(showMetadata, showHistory, showFilms, showSeries) {
        TopLevelDestination.entries.filter {
            (it != TopLevelDestination.METADATA || showMetadata) &&
                (it != TopLevelDestination.HISTORY || showHistory) &&
                (it != TopLevelDestination.FILMS || showFilms) &&
                (it != TopLevelDestination.SERIES || showSeries)
        }
    }

    // Wide enough for a rail: a tablet or an unfolded phone should not waste a
    // whole edge on a bar the height of a thumb.
    //
    // Measured on the shorter edge, so it is the device that decides and not
    // the way it is being held: a phone turned sideways is over 720dp wide and
    // would have swung its bar to the side, which is the one place a thumb
    // holding the phone cannot comfortably get to.
    val useRail = LocalConfiguration.current.smallestScreenWidthDp >= 600

    // Belongs to the shell rather than to a screen: what it carries - a newer
    // release - is about the app itself and would otherwise disappear the
    // moment someone changed tabs.
    val snackbarHostState = remember { SnackbarHostState() }

    // Which tab is open is remembered by the tab and not by its place in the
    // row: the row changes under it - the metadata tab arrives with a Dolby
    // Vision title and goes with it - and the page number of what somebody is
    // reading moves with it.
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.LIVE) }
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(current).coerceAtLeast(0),
    ) { destinations.size }
    val latestDestinations by rememberUpdatedState(destinations)
    val scope = rememberCoroutineScope()

    // A swipe that has come to rest is a tab that has been opened.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            latestDestinations.getOrNull(page)?.let { current = it }
        }
    }

    // The row changed: stay on the tab that was open, wherever it now is. A tab
    // that has gone altogether hands over to the nearest one before it.
    LaunchedEffect(destinations) {
        val target = destinations.indexOf(current).takeIf { it >= 0 }
            ?: destinations.indexOfLast { it.ordinal < current.ordinal }.coerceAtLeast(0)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        destinations.getOrNull(target)?.let { current = it }
    }

    fun open(destination: TopLevelDestination) {
        val page = destinations.indexOf(destination)
        if (page < 0) return
        current = destination
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    // What the bar lights up. The page under the middle of the screen, so the
    // pill moves across as soon as a swipe is past half-way rather than when
    // it has finished.
    val shown = destinations.getOrNull(pagerState.currentPage)

    // Back from any tab but the first is back to the first, the way it was
    // when the tabs were a back stack. A screen with somewhere of its own to
    // go back to - an open series - is composed after this and asked first.
    BackHandler(enabled = current != TopLevelDestination.LIVE) {
        open(TopLevelDestination.LIVE)
    }

    UpdateCheck(container, snackbarHostState)
    DoubleBackToExit(
        enabled = current == TopLevelDestination.LIVE,
        snackbarHostState = snackbarHostState,
    )

    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            TinyPpiNavigationRail(
                destinations = destinations,
                selected = shown,
                onSelect = ::open,
            )
            Box(Modifier.weight(1f)) {
                TinyPpiPager(
                    destinations = destinations,
                    pagerState = pagerState,
                    onOpenSettings = { open(TopLevelDestination.SETTINGS) },
                    modifier = Modifier.fillMaxSize(),
                )
                // No scaffold on this branch to hand the host to, so it is
                // placed where one would have put it: along the bottom, and
                // clear of the system's gesture bar.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    } else {
        // The bar floats over the screens rather than taking a strip of its
        // own, so the screens run the full height of the phone - under the
        // bar and the system's gesture bar both - and leave room at the foot
        // of their lists for the bar to sit over (see LocalBottomBarSpace).
        val hazeState = rememberHazeState()
        val barVisibility = rememberBarVisibility()
        val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Without the expressive spring's overshoot: a room that bounced past
        // nothing would be a negative padding, which is a crash.
        val animatedSpace by animateDpAsState(
            targetValue = navigationBar + if (barVisibility.visible) BAR_HEIGHT + BAR_BOTTOM else 0.dp,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "bar-space",
        )
        val barSpace = animatedSpace.coerceAtLeast(0.dp)

        // A new tab is a new place to be, and whoever just pressed the bar or
        // swiped to get there should not find it gone from under their thumb.
        LaunchedEffect(shown) { barVisibility.visible = true }

        CompositionLocalProvider(LocalBottomBarSpace provides barSpace) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(barVisibility),
            ) {
                TinyPpiPager(
                    destinations = destinations,
                    pagerState = pagerState,
                    onOpenSettings = { open(TopLevelDestination.SETTINGS) },
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        // Each screen's own scaffold would otherwise stop its
                        // list short of the gesture bar; the list runs on under
                        // it and pads its own end instead.
                        .consumeWindowInsets(WindowInsets.navigationBars),
                )
                FloatingNavigationBar(
                    destinations = destinations,
                    selected = shown,
                    onSelect = ::open,
                    hazeState = hazeState,
                    visible = barVisibility.visible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                // Over the bar's place rather than under it, where the bar
                // would cover it.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = barSpace),
                )
            }
        }
    }
}

/**
 * Asks GitHub once per launch whether a newer release has been published, and
 * offers a way to it.
 *
 * A snackbar rather than a dialog: nobody opened the app to be told about the
 * app, so it says its piece along the bottom, waits, and goes. It is late on
 * purpose - what is playing is what someone opened this for, and the first
 * seconds belong to that.
 */
@Composable
private fun UpdateCheck(container: AppContainer, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    // The message names a release that is not known until the answer comes
    // back, so it is formatted rather than read at composition - through the
    // resources of the composition, which follow a configuration change, and
    // not through the context's, which do not.
    val resources = LocalResources.current
    val apiUrl = stringResource(R.string.update_check_url)
    val releasesUrl = stringResource(R.string.update_releases_url)
    val showLabel = stringResource(R.string.update_show)

    // Survives a rotation, so turning the phone over does not ask GitHub the
    // same question again - or answer it a second time.
    var checked by rememberSaveable { mutableStateOf(false) }

    // Keyed on nothing, so noting that the check has run does not restart the
    // very effect that is running it.
    LaunchedEffect(Unit) {
        if (checked) return@LaunchedEffect
        checked = true
        delay(UPDATE_CHECK_DELAY_MS.milliseconds)

        val update = container.updateChecker.findNewerRelease(
            apiUrl = apiUrl,
            current = ReleaseId.of(BuildConfig.VERSION_NAME, "build-${BuildConfig.BUILD_NUMBER}"),
            fallbackUrl = releasesUrl,
        ) ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.update_available, update.name),
            actionLabel = showLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, update.url.toUri()))
            }
        }
    }
}

/**
 * Makes leaving the app take two presses of back rather than one.
 *
 * The live screen is one tab of five and back out of it is back out of
 * everything, which is a long way to fall for a thumb that meant to close a
 * menu. The second press has to come while the first is still being announced;
 * after that the count starts again.
 */
@Composable
private fun DoubleBackToExit(enabled: Boolean, snackbarHostState: SnackbarHostState) {
    val activity = LocalActivity.current
    val message = stringResource(R.string.exit_confirm)
    val scope = rememberCoroutineScope()

    // Deliberately not saved across a rotation: an app turned over between the
    // two presses is not being left, and half a gesture from before should not
    // be waiting to close it.
    var armed by remember { mutableStateOf(false) }

    // Only on the first tab: back anywhere else goes to it first (see the
    // handler in the shell), and a screen with a way back of its own - added
    // later, so asked first - takes it before either.
    BackHandler(enabled = enabled) {
        if (armed) {
            activity?.finish()
            return@BackHandler
        }

        armed = true
        scope.launch {
            // Whatever is on screen goes first. The host shows one snackbar at
            // a time and queues the rest, so an update notice still sitting
            // there would hold this one back until it timed out - and the
            // window would be open, with nothing on screen saying so.
            snackbarHostState.currentSnackbarData?.dismiss()
            try {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            } finally {
                // The window is exactly as long as the message is up, however
                // it ended - timed out, swiped away, or the composition gone.
                armed = false
            }
        }
    }
}

@Composable
private fun TinyPpiNavigationRail(
    destinations: List<TopLevelDestination>,
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    WideNavigationRail {
        destinations.forEach { destination ->
            val isSelected = destination == selected
            WideNavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.tabLabelRes),
                        maxLines = 1,
                    )
                },
                railExpanded = false,
            )
        }
    }
}

/** How long the live screen gets to itself before the update check speaks up. */
private const val UPDATE_CHECK_DELAY_MS = 5_000L
