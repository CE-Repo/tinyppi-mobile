package com.jamal2367.tinyppimobile.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jamal2367.tinyppimobile.ui.history.HistoryScreen
import com.jamal2367.tinyppimobile.ui.live.LiveScreen
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.metadata.MetadataScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsScreen

@Composable
fun TinyPpiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // The live reading outlives its tab. Two of the four screens are windows
    // onto the same snapshot, and a per-destination view model would be three
    // objects collecting the same flow - which, with the container's stop
    // timeout under them, is a stream that drops and reopens every time
    // somebody changes tabs.
    val activity = checkNotNull(LocalActivity.current) as ViewModelStoreOwner
    val liveViewModel: LiveViewModel = viewModel(viewModelStoreOwner = activity)

    NavHost(
        navController = navController,
        startDestination = Routes.LIVE,
        modifier = modifier,
    ) {
        composable(Routes.LIVE) {
            LiveScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = liveViewModel,
            )
        }

        composable(Routes.METADATA) {
            MetadataScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = liveViewModel,
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
