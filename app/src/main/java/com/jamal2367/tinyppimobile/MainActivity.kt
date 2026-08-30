package com.jamal2367.tinyppimobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.ui.TinyPpiApp
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.ui.theme.isDarkTheme
import com.jamal2367.tinyppimobile.util.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    /**
     * Whether the question of local network access has been answered yet.
     *
     * The screens are held back until it has: they open a connection the
     * moment they exist, and one opened while the dialog is still up is one
     * that was refused before anyone decided anything.
     */
    private val networkAccessDecided = MutableStateFlow(false)

    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Either answer lets the app carry on: granted it works, denied it
            // says so on screen rather than blaming the box.
            networkAccessDecided.value = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (LocalNetworkAccess.isGranted(this)) {
            networkAccessDecided.value = true
        } else {
            requestLocalNetwork.launch(LocalNetworkAccess.PERMISSION)
        }

        val container = (application as TinyPpiApplication).container
        val settingsFlow = container.settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppSettings())

        // Whether the screen should be held awake, which is a window flag and
        // not something a composable can set. Collected as its own flow so
        // turning the setting on does not run through the whole tree.
        val keepAwakeFlow = container.settingsRepository.settings
            .map { it.keepScreenOn }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

        setContent {
            val settings by settingsFlow.collectAsState()
            val decided by networkAccessDecided.collectAsState()
            val keepAwake by keepAwakeFlow.collectAsState()

            // A phone propped against the television is one use of this app,
            // and its screen going out after thirty seconds is the whole
            // reason the setting exists. Cleared again the moment it is turned
            // off, rather than left set until the app is closed.
            DisposableEffect(keepAwake) {
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // The bars are transparent and the app paints underneath them, so
            // the clock and the icons up there belong to whatever the app is
            // showing - not to whatever the system is set to. Left to the
            // platform, a phone on dark with the app on light gets white icons
            // on a white bar, and the time disappears.
            val dark = isDarkTheme(settings.themeMode)
            DisposableEffect(dark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                onDispose {}
            }

            TinyPpiTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                // Nothing but the theme's background until then - the dialog is
                // covering this anyway, and it is gone within a moment.
                if (decided) {
                    TinyPpiApp(container = container)
                }
            }
        }
    }
}
