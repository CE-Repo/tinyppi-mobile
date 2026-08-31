package com.jamal2367.tinyppimobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.di.AppContainer
import com.jamal2367.tinyppimobile.ui.TinyPpiApp
import com.jamal2367.tinyppimobile.ui.components.CardFolds
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.theme.ArtworkAccentTheme
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import com.jamal2367.tinyppimobile.ui.theme.isDarkTheme
import com.jamal2367.tinyppimobile.ui.theme.rememberArtworkAccent
import com.jamal2367.tinyppimobile.util.LocalNetworkAccess
import com.jamal2367.tinyppimobile.util.MediaUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The colour of what is playing, or null while the reader wants none.
 *
 * Read here rather than on the live screen because the whole app wears it - the
 * tabs along the bottom included, and those belong to the shell.
 *
 * Nothing is collected at all while the switch is off, so a reader who does not
 * want this does not pay for it: the live reading is shared and reference
 * counted, and not asking for it here leaves it exactly as it was.
 */
@Composable
private fun playingColour(container: AppContainer, settings: AppSettings): Color? {
    if (!settings.adaptiveColor || !settings.showArtwork) return null

    // Lifecycle-aware, so a backgrounded app still lets go of the box's stream
    // rather than holding one of its six slots open to keep a colour warm.
    val live by container.liveState.collectAsStateWithLifecycle()

    return rememberArtworkAccent(
        live.snapshot?.let { MediaUrls.art(live.server, it.art, MediaUrls.ArtKind.POSTER) }
    )
}

/**
 * Which cards are folded shut, wired to where that is remembered.
 *
 * Built at the root because a card is drawn on every screen and folding one is
 * the same act wherever it happens - there is nothing screen-shaped about it
 * for a view model to own.
 */
@Composable
private fun rememberCardFolds(container: AppContainer, settings: AppSettings): CardFolds {
    val scope = rememberCoroutineScope()

    return remember(settings.cardFolds) {
        CardFolds(settings.cardFolds) { id, moved ->
            scope.launch { container.settingsRepository.setCardFold(id, moved) }
        }
    }
}

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

            TinyPpiTheme(themeMode = settings.themeMode) {
                // Nothing but the theme's background until then - the dialog is
                // covering this anyway, and it is gone within a moment.
                if (decided) {
                    ArtworkAccentTheme(accent = playingColour(container, settings)) {
                        CompositionLocalProvider(
                            LocalCardFolds provides rememberCardFolds(container, settings),
                        ) {
                            TinyPpiApp(container = container)
                        }
                    }
                }
            }
        }
    }
}
