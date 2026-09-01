package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.StatusDown
import com.jamal2367.tinyppimobile.ui.theme.StatusLive
import com.jamal2367.tinyppimobile.ui.theme.StatusWaiting

/**
 * How the app is getting its readings, in one line.
 *
 * This app spends most of its life showing a picture that is either live or a
 * few seconds old, and the difference is invisible in the reading itself - a
 * frozen snapshot of a paused film looks exactly like a live one. So the state
 * of the connection is on screen at all times rather than only when it breaks,
 * the same way the add-on's own dashboard keeps its status light lit.
 *
 * The address is named beside it: in automatic mode that is the only way to
 * tell from the outside which of the two boxes answered.
 *
 * The words are plain: the state and the address are the two things on this
 * line anyone reads, and they read fastest in the colour text is normally set
 * in. Only the dot carries colour, and it carries the one thing worth it.
 */
@Composable
fun StatusLine(
    connection: LiveState.Connection,
    serverLabel: String?,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
) {
    // No hairline. A card is outlined because it is stacked against other
    // cards in nearly its own colour and something has to say where one ends;
    // this pill is a lozenge on the open page with nothing under it to be
    // confused with, and the outline was drawing a second edge just inside the
    // shape's own.
    StatusContent(
        connection = connection,
        serverLabel = serverLabel,
        onReconnect = onReconnect,
        modifier = modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .reconnectable(connection, onReconnect)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/**
 * The same line, for a card that is already a card.
 *
 * While a title is playing the line goes inside the card about that title
 * rather than standing over it. The two were always the one piece of screen -
 * the pill took the card's own tint so that they would read as one - and a
 * lozenge drawn on a surface it is already the colour of is an outline around
 * nothing. Here it is simply the card's first row.
 *
 * No ground and no shape of its own, so it inherits the wash the card is
 * painted in and lands where the card's padding puts it.
 */
@Composable
fun StatusRow(
    connection: LiveState.Connection,
    serverLabel: String?,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
) {
    StatusContent(
        connection = connection,
        serverLabel = serverLabel,
        onReconnect = onReconnect,
        // Out to the card's edges and back in again, so a tap lands anywhere
        // along the row rather than only on the words - and the ripple runs
        // the width of the card, which is what says the whole row is the
        // target.
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .reconnectable(connection, onReconnect)
            .padding(vertical = 2.dp),
    )
}

@Composable
private fun StatusContent(
    connection: LiveState.Connection,
    serverLabel: String?,
    onReconnect: (() -> Unit)?,
    modifier: Modifier,
) {
    val dot by animateColorAsState(targetValue = connection.dotColor(), label = "statusDot")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color = dot)
        Text(
            text = stringResource(connection.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (serverLabel != null && connection.namesAServer()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = serverLabel,
                style = MaterialTheme.typography.labelSmall,
                // The same ink as the state at the other end. The two are the
                // one sentence this line has to say - which box, and how it is
                // answering - and setting the address a shade under the state
                // made it read as a footnote to it.
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Answers a tap where connecting again would change anything, and not otherwise. */
@Composable
private fun Modifier.reconnectable(
    connection: LiveState.Connection,
    onReconnect: (() -> Unit)?,
): Modifier = if (onReconnect != null && connection.invitesReconnecting()) {
    clickable(onClick = onReconnect, onClickLabel = stringResource(R.string.status_reconnect))
} else {
    this
}

private fun LiveState.Connection.dotColor(): Color = when (this) {
    LiveState.Connection.Streaming -> StatusLive
    LiveState.Connection.Polling -> StatusLive
    LiveState.Connection.Connecting -> StatusWaiting
    LiveState.Connection.Busy -> StatusWaiting
    LiveState.Connection.Unauthorized -> StatusDown
    LiveState.Connection.Offline -> StatusDown
    LiveState.Connection.NotConfigured -> StatusDown
}

private fun LiveState.Connection.labelRes(): Int = when (this) {
    LiveState.Connection.Streaming -> R.string.status_live
    LiveState.Connection.Polling -> R.string.status_polling
    LiveState.Connection.Connecting -> R.string.status_connecting
    LiveState.Connection.Busy -> R.string.status_busy
    LiveState.Connection.Unauthorized -> R.string.status_unauthorized
    LiveState.Connection.Offline -> R.string.status_offline
    LiveState.Connection.NotConfigured -> R.string.status_not_configured
}

/**
 * Whether naming an address alongside this state says anything true.
 *
 * "Not configured" names none, and after a failure the address that was last
 * reached is not the one that just refused to answer.
 */
private fun LiveState.Connection.namesAServer(): Boolean = when (this) {
    LiveState.Connection.NotConfigured -> false
    else -> true
}

/**
 * Whether connecting again would change anything from this state.
 *
 * A reader is offered the tap only where it has something to do. Live and
 * polling are already answering, so the only thing a restart could do there is
 * interrupt a working connection - and the pill is under a thumb on a
 * scrolling screen, where an accidental tap should cost nothing. With nothing
 * configured there is no address to reach, and a busy box has said what it has
 * to say: every slot is taken, and asking again does not free one.
 *
 * What is left is the three a reader watches and waits on: waiting to get in,
 * turned away, and gone.
 */
private fun LiveState.Connection.invitesReconnecting(): Boolean = when (this) {
    LiveState.Connection.Connecting -> true
    LiveState.Connection.Unauthorized -> true
    LiveState.Connection.Offline -> true
    LiveState.Connection.Streaming -> false
    LiveState.Connection.Polling -> false
    LiveState.Connection.Busy -> false
    LiveState.Connection.NotConfigured -> false
}
