@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jamal2367.tinyppimobile.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.ui.components.ArrangeBackHandler
import com.jamal2367.tinyppimobile.ui.components.ArrangeableCard
import com.jamal2367.tinyppimobile.ui.components.CardScreens
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardScreen
import com.jamal2367.tinyppimobile.ui.components.allCardsHidden
import com.jamal2367.tinyppimobile.ui.components.cardArranger
import com.jamal2367.tinyppimobile.ui.components.GroupCard
import com.jamal2367.tinyppimobile.ui.components.StatusLine
import com.jamal2367.tinyppimobile.ui.navigation.aboveBottomBar
import com.jamal2367.tinyppimobile.ui.navigation.barAwarePadding
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import com.jamal2367.tinyppimobile.util.MediaUrls

/**
 * What the box is playing, and what can be done to it.
 *
 * The screen anyone opens this app for: the title with its poster, how it is
 * graded, what is leaving the box, and - on a box that allows it - the
 * transport row and the VS10 conversions.
 */
@Composable
fun LiveScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel = viewModel(factory = LiveViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    val poster = state.snapshot
        ?.takeIf { state.settings.showArtwork }
        ?.let { MediaUrls.art(state.live.server, it.art, MediaUrls.ArtKind.POSTER) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.aboveBottomBar(),
            )
        },
    ) { padding ->
        val snapshot = state.snapshot

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Only where there is no film: while one runs the line goes into
            // the list instead, directly above the card it belongs to, and
            // scrolls away with it. With nothing playing there is no card for
            // it to sit over, and whether the box is answering at all is the
            // one thing the screen still has to say - so it stays at the top.
            if (snapshot?.playing != true) {
                StatusLine(
                    connection = state.live.connection,
                    serverLabel = state.live.server?.label,
                    onReconnect = viewModel::reconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenEdge, vertical = 8.dp),
                )
            }

            when {
                !state.isConfigured -> EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_not_configured_title),
                    message = stringResource(R.string.live_not_configured_text),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                snapshot == null -> EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_waiting_title),
                    message = stringResource(R.string.live_waiting_text),
                )

                else -> CompositionLocalProvider(LocalCardScreen provides CardScreens.LIVE) {
                    LiveContent(
                    snapshot = snapshot,
                    connection = state.live.connection,
                    serverLabel = state.live.server?.label,
                    poster = poster,
                    showArtwork = state.settings.showArtwork,
                    canControl = state.canControlPlayback,
                    viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveContent(
    snapshot: Snapshot,
    connection: LiveState.Connection,
    serverLabel: String?,
    poster: String?,
    showArtwork: Boolean,
    canControl: Boolean,
    viewModel: LiveViewModel,
) {
    val layout = LocalCardLayout.current
    val labels = LiveCardLabels(
        nowPlaying = stringResource(R.string.cards_now_playing),
        controls = stringResource(R.string.live_transport),
        vs10 = stringResource(R.string.live_vs10),
    )
    ArrangeBackHandler(CardScreens.LIVE, layout)

    LazyColumn(
        contentPadding = barAwarePadding(horizontal = ScreenEdge, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!snapshot.playing) {
            // Nothing is on, and this screen is about what is. What could be
            // instead has two places of its own in the bar (see ui/library),
            // reachable whether or not anything is playing - which is more
            // than this screen could offer, since it only had room for them
            // while the box was doing nothing.
            item {
                EmptyState(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.live_idle_title),
                    message = stringResource(R.string.live_idle_text),
                    modifier = Modifier.height(320.dp),
                )
            }
            return@LazyColumn
        }

        // Every card the screen has now, in the order it draws them until the
        // reader moves one: what is playing, what can be done to it, and the
        // readings the overlay prints (see CardLayout).
        val groups = snapshot.groups.associateBy { "$GROUP_PREFIX${it.id}" }
        val present = buildList {
            add(CARD_NOW_PLAYING)
            if (canControl) add(FOLD_CONTROLS)
            if (snapshot.vs10.options.isNotEmpty()) add(FOLD_VS10)
            addAll(groups.keys)
        }

        if (layout.isEditing(CardScreens.LIVE)) {
            cardArranger(
                screen = CardScreens.LIVE,
                cards = present.map { id ->
                    ArrangeableCard(
                        id,
                        when (id) {
                            CARD_NOW_PLAYING -> labels.nowPlaying
                            FOLD_CONTROLS -> labels.controls
                            FOLD_VS10 -> labels.vs10
                            else -> groups[id]?.title.orEmpty()
                        },
                    )
                },
                layout = layout,
            )
            return@LazyColumn
        }

        val shown = layout.visible(CardScreens.LIVE, present)
        if (shown.isEmpty()) {
            allCardsHidden(CardScreens.LIVE, layout)
            return@LazyColumn
        }

        // Keyed by the card's own name, so a card keeps its place in the list
        // and its fold as the box adds and drops panels mid-film.
        for (id in shown) {
            when (id) {
                CARD_NOW_PLAYING -> item(key = id) {
                    NowPlayingCard(
                        snapshot = snapshot,
                        connection = connection,
                        serverLabel = serverLabel,
                        poster = poster,
                        showArtwork = showArtwork,
                        canControl = canControl,
                        viewModel = viewModel,
                    )
                }

                FOLD_CONTROLS -> item(key = id) {
                    ControlsCard(
                        snapshot = snapshot,
                        viewModel = viewModel,
                    )
                }

                FOLD_VS10 -> item(key = id) {
                    Vs10Card(
                        snapshot.vs10,
                        canControl = snapshot.control,
                        viewModel = viewModel,
                    )
                }

                // The readings the overlay prints, on the same screen rather
                // than behind a tab of their own. They answer questions the
                // card at the top raises - what this file actually is, what
                // the box is doing with it - and an answer a tab away is an
                // answer nobody goes and gets.
                else -> groups[id]?.let { group ->
                    item(key = id) { GroupCard(group) }
                }
            }
        }
    }
}

/** What the arranging list calls the cards that do not carry a heading of the box's. */
private class LiveCardLabels(val nowPlaying: String, val controls: String, val vs10: String)

/**
 * What each card on this screen is remembered by.
 *
 * Written out rather than taken from the heading: the heading is translated,
 * and a card folded away in German should still be folded in English.
 */
internal const val FOLD_CONTROLS = "live.controls"
internal const val FOLD_VS10 = "live.vs10"

/** The card of what is playing, which has no fold to be named after. */
internal const val CARD_NOW_PLAYING = "live.now"

/** What a card of the box's readings is named by, before the group's own id. */
internal const val GROUP_PREFIX = "details."
