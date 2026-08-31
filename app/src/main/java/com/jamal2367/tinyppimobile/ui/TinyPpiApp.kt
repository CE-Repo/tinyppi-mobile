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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
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
import androidx.core.net.toUri
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jamal2367.tinyppimobile.BuildConfig
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.remote.ReleaseId
import com.jamal2367.tinyppimobile.di.AppContainer
import com.jamal2367.tinyppimobile.ui.navigation.TinyPpiNavHost
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination
import com.jamal2367.tinyppimobile.ui.theme.accentText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TinyPpiApp(container: AppContainer) {
    val navController = rememberNavController()

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

    val backStackEntry by navController.currentBackStackEntryAsState()

    // Back on the first screen with nothing to return to is back out of the
    // app, and that is the only place the second press is asked for. Anywhere
    // else the navigation still has somewhere to go, and it goes there.
    val atRoot = backStackEntry != null && navController.previousBackStackEntry == null

    UpdateCheck(container, snackbarHostState)
    DoubleBackToExit(enabled = atRoot, snackbarHostState = snackbarHostState)

    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            TinyPpiNavigationRail(navController)
            Box(Modifier.weight(1f)) {
                TinyPpiNavHost(navController = navController)
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
        Scaffold(
            // This shell holds no top bar, so it must not claim the status bar
            // either: each screen has a top bar of its own that pads for it and
            // draws underneath it. Left at the default, the inset would be
            // counted twice and every screen would start a status bar's height
            // too low.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { TinyPpiNavigationBar(navController) },
            // Above the navigation bar rather than over it, which is what the
            // scaffold does with a host it is given.
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            TinyPpiNavHost(
                navController = navController,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
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

    // Back somewhere else on the stack is a screen to return to, and the
    // navigation's own handler - added later, so asked first - takes it.
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
private fun TinyPpiNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destinations = remember { TopLevelDestination.entries }

    // Material's short bar rather than the tall one: 64dp with the label under
    // the icon, instead of 80dp with a strip of nothing above it. The system's
    // own gesture bar is added underneath rather than eaten into.
    ShortNavigationBar {
        destinations.forEach { destination ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.route == destination.route } == true
            ShortNavigationBarItem(
                selected = selected,
                onClick = { navController.switchTo(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.tabLabelRes),
                        color = if (selected) {
                            MaterialTheme.colorScheme.accentText
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun TinyPpiNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destinations = remember { TopLevelDestination.entries }

    WideNavigationRail {
        destinations.forEach { destination ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.route == destination.route } == true
            WideNavigationRailItem(
                selected = selected,
                onClick = { navController.switchTo(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
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

/**
 * Switch tabs the way a bar is expected to behave: one entry per tab on the
 * back stack, each remembering where it was left.
 */
private fun NavHostController.switchTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** How long the live screen gets to itself before the update check speaks up. */
private const val UPDATE_CHECK_DELAY_MS = 5_000L
