package com.jamal2367.tinyppimobile.testing

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.jamal2367.tinyppimobile.FakeApi
import com.jamal2367.tinyppimobile.data.model.ArtTags
import com.jamal2367.tinyppimobile.data.model.Frame
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.InfoRowData
import com.jamal2367.tinyppimobile.data.model.MediaInfo
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.data.model.Metrics
import com.jamal2367.tinyppimobile.data.model.PlayerControls
import com.jamal2367.tinyppimobile.data.model.SessionSummary
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.model.Track
import com.jamal2367.tinyppimobile.data.model.Vs10Option
import com.jamal2367.tinyppimobile.data.model.Vs10State
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.data.repository.PlayerRepository
import com.jamal2367.tinyppimobile.ui.components.CardFolds
import com.jamal2367.tinyppimobile.ui.components.CardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardFolds
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.live.LocalShelfOrders
import com.jamal2367.tinyppimobile.ui.live.ShelfOrders
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Shadows.shadowOf

/** A box, a live reading and the settings, all made up and all changeable by the test. */
class FakeLive {
    val api = FakeApi()
    val live = MutableStateFlow(LiveState())
    val settings = MutableStateFlow(AppSettings(primary = Screens.box))
    var reconnects = 0

    val viewModel = LiveViewModel(
        repository = PlayerRepository(api, FakeApi.json),
        liveState = live,
        settings = settings,
        reconnectLive = { reconnects++ },
        describe = { it.message ?: it::class.simpleName.orEmpty() },
    )

    fun playing(snapshot: Snapshot = Screens.film, connection: LiveState.Connection = LiveState.Connection.Streaming) {
        live.value = LiveState(connection = connection, snapshot = snapshot, server = Screens.box)
    }
}

/** What the card folds and the arranging write, kept so a test can read it back. */
class CardState {
    var folds by mutableStateOf(emptySet<String>())
    var orders by mutableStateOf(emptyMap<String, List<String>>())
    var hidden by mutableStateOf(emptySet<String>())
    var editing by mutableStateOf<String?>(null)
    val resets = mutableListOf<String>()
    var shelfOrders by mutableStateOf(emptyMap<String, String>())

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        val folds = CardFolds(folds) { id, moved -> this.folds = if (moved) this.folds + id else this.folds - id }
        val layout = CardLayout(
            orders = orders,
            hidden = hidden,
            editing = editing,
            onOrder = { screen, order -> orders = orders + (screen to order) },
            onHidden = { screen, id, hide ->
                val key = CardLayout.hiddenKey(screen, id)
                hidden = if (hide) hidden + key else hidden - key
            },
            onEditing = { editing = it },
            onReset = { screen -> resets += screen; orders = orders - screen },
        )
        val orders = ShelfOrders(shelfOrders) { shelf, order -> shelfOrders = shelfOrders + (shelf to order) }
        CompositionLocalProvider(
            LocalCardFolds provides folds,
            LocalCardLayout provides layout,
            LocalShelfOrders provides orders,
            content = content,
        )
    }
}

object Screens {
    val box = ServerConfig(enabled = true, host = "192.168.1.10", port = 8099)

    /** A Dolby Vision film playing, with every card the overlay prints. */
    val film = Snapshot(
        seq = 1,
        playing = true,
        title = "Dune: Part Two",
        filename = "/media/Dune.Part.Two.2160p.mkv",
        hdrType = "dolbyvision",
        effective = "dolbyvision",
        outputType = "dolbyvision",
        time = "1:11:00",
        duration = "2:46:00",
        finish = "22:10",
        control = true,
        metrics = Metrics(frame = Frame(3840, 2160), progress = 42.0),
        groups = listOf(
            InfoGroup(
                id = "video",
                title = "Video",
                rows = listOf(
                    InfoRowData(id = "video.32002", label = "Dolby Vision", value = "Profile 7.6 FEL", detail = "(CMv4.0)"),
                    InfoRowData(id = "l1", label = "L1 Luminance", value = "1000 nits"),
                    InfoRowData(id = "empty", label = "Nothing", value = ""),
                ),
            ),
            InfoGroup(
                id = "audio",
                title = "Audio",
                rows = listOf(InfoRowData(id = "audio.32045", label = "Codec", value = "TrueHD 7.1", detail = "(Atmos)")),
            ),
        ),
        metadata = listOf(
            MetadataRow(kind = "section", name = "Level 1"),
            MetadataRow(kind = "row", name = "Max PQ", value = "3000"),
            MetadataRow(kind = "headings", name = "Target", cells = listOf("100", "600")),
            MetadataRow(kind = "columns", name = "Slope", cells = listOf("2048", "2051")),
        ),
        vs10 = Vs10State(
            options = listOf(
                Vs10Option("hdr10", "Dolby Vision -> HDR10"),
                Vs10Option("sdr8", "Dolby Vision -> SDR"),
                Vs10Option("off", "Original"),
            ),
            output = "DV-LL",
        ),
        art = ArtTags(poster = "movie-1"),
        media = MediaInfo(year = "2024", genre = "Science Fiction"),
        controls = PlayerControls(
            audio = listOf(Track(0, "English TrueHD"), Track(1, "Deutsch DD+")),
            subtitle = listOf(Track(0, "English"), Track(1, "Deutsch")),
            audioCurrent = 0,
            subtitleCurrent = 1,
            subtitleOn = true,
            volume = 72,
            chapters = 24,
        ),
        session = SessionSummary(seq = 5, switches = 2, warnings = 1),
    )

    /**
     * Wait for [condition], running the main looper as well as the frames:
     * the view models' work is posted to it and the rule alone does not run it.
     */
    fun ComposeContentTestRule.until(timeoutMs: Long = 10_000, condition: () -> Boolean) = waitUntil(timeoutMs) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    /** Shows [content] the way the app does: in its theme, with folds and arranging live. */
    fun ComposeContentTestRule.show(cards: CardState = CardState(), content: @Composable () -> Unit) {
        setContent {
            TinyPpiTheme {
                cards.Provide(content)
            }
        }
    }
}
