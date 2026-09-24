package com.jamal2367.tinyppimobile

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.di.AppContainer
import com.jamal2367.tinyppimobile.ui.TinyPpiApp
import com.jamal2367.tinyppimobile.ui.components.CardFolds
import com.jamal2367.tinyppimobile.ui.components.CardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
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

    // Only while something is playing. A snapshot outlives the film in it -
    // the box goes on answering with `playing` false and whatever art it last
    // held - and reading the poster off that would keep the app painted in a
    // title that ended at the closing credits.
    return rememberArtworkAccent(
        live.snapshot
            ?.takeIf { it.playing }
            ?.let { MediaUrls.art(live.server, it.art, MediaUrls.ArtKind.POSTER) }
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

/**
 * Where the cards on each screen go, wired to where that is remembered, and
 * which screen is being rearranged.
 *
 * The screen being rearranged is held here and not stored: an app opened
 * tomorrow should open on its cards, not on the list of them it was left on.
 */
@Composable
private fun rememberCardLayout(container: AppContainer, settings: AppSettings): CardLayout {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = container.settingsRepository

    return remember(settings.cardOrders, settings.cardHidden, editing) {
        CardLayout(
            orders = settings.cardOrders,
            hidden = settings.cardHidden,
            editing = editing,
            onOrder = { screen, order -> scope.launch { repository.setCardOrder(screen, order) } },
            onHidden = { screen, id, hidden ->
                scope.launch { repository.setCardHidden(screen, id, hidden) }
            },
            onEditing = { editing = it },
            onReset = { screen -> scope.launch { repository.resetCards(screen) } },
        )
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

    /**
     * The same instance every screen draws from - same owner, same factory -
     * so a volume button pressed here fails into the snackbar those screens
     * already show, rather than into nothing.
     */
    private val liveViewModel: LiveViewModel by viewModels { LiveViewModel.Factory }

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
                        ArtworkAccentTheme(
                            accent = playingColour(container, settings),
                            intensity = settings.adaptiveColorIntensity,
                        ) {
                        CompositionLocalProvider(
                            LocalCardFolds provides rememberCardFolds(container, settings),
                            LocalCardLayout provides rememberCardLayout(container, settings),
                        ) {
                            TinyPpiApp(container = container, hiddenTabs = settings.hiddenTabs)
                        }
                    }
                }
            }
        }
    }

    /**
     * The phone's volume buttons, handed to the box while something plays.
     *
     * Taken here rather than in a composable because a key event reaches a
     * composable only through whatever holds focus, and on a screen of cards
     * nothing does - so it falls through to the activity, which is here.
     * Both halves of a press are consumed, so the system's own volume panel
     * does not open over the screen as well.
     *
     * Held down, a button repeats far faster than the box needs to be told
     * anything - so only every few repeats is passed on, which walks the level
     * along at about the pace the on-screen keys can be tapped.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode) || !volumeKeysDriveTheBox()) return super.onKeyDown(keyCode, event)

        if (event.repeatCount % VOLUME_REPEAT_EVERY == 0) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) liveViewModel.volumeUp() else liveViewModel.volumeDown()
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode) || !volumeKeysDriveTheBox()) return super.onKeyUp(keyCode, event)
        return true
    }

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    private fun volumeKeysDriveTheBox(): Boolean {
        val state = liveViewModel.state.value
        return state.settings.volumeKeys && state.canControlPlayback
    }

    private companion object {
        /** How many of a held button's repeats go by for each one sent on. */
        const val VOLUME_REPEAT_EVERY = 4
    }
}
