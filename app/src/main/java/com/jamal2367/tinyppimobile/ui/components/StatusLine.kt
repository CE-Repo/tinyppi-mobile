package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
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
import com.jamal2367.tinyppimobile.ui.theme.LocalArtworkAccent
import com.jamal2367.tinyppimobile.ui.theme.PillShape
import com.jamal2367.tinyppimobile.ui.theme.StatusDown
import com.jamal2367.tinyppimobile.ui.theme.StatusLive
import com.jamal2367.tinyppimobile.ui.theme.StatusWaiting
import com.jamal2367.tinyppimobile.ui.theme.artworkTint

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
 * While a title is playing the pill takes the same tint as the card under it,
 * so the two read as one piece of the same screen rather than as a plain grey
 * lozenge sitting on a coloured one. That is what [accented] is for, and why it
 * is the caller's answer rather than a colour read from the theme: the colour
 * of the last film outlives the film, and a line about the connection is about
 * the box rather than about anything that was playing on it.
 *
 * The tint is the ground only. The words on it are plain: the state and the
 * address are the two things on this line anyone reads, and they read fastest
 * in the colour text is normally set in - the ground under them is already
 * saying everything the accent had to say here.
 */
@Composable
fun StatusLine(
    connection: LiveState.Connection,
    serverLabel: String?,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    onReconnect: (() -> Unit)? = null,
) {
    val dot by animateColorAsState(targetValue = connection.dotColor(), label = "statusDot")

    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val ground = LocalArtworkAccent.current
        ?.takeIf { accented }
        ?.let { artworkTint(it, container) }
        ?: container

    // No hairline. A card is outlined because it is stacked against other
    // cards in nearly its own colour and something has to say where one ends;
    // this pill is a lozenge on the open page with nothing under it to be
    // confused with, and the outline was drawing a second edge just inside the
    // shape's own.
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(ground)
            .then(
                if (onReconnect != null) {
                    Modifier.clickable(
                        onClick = onReconnect,
                        onClickLabel = stringResource(R.string.status_reconnect),
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
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

        // At the end of the line, after the address it acts on: the pill says
        // which box and how it is answering, and this is what to do about the
        // answer. Drawn only where a caller has something for it to do.
        //
        // The mark and not the target. A 16dp button is half the size a finger
        // is owed, and growing it to the 48 the guidance asks for would have
        // made the pill taller than the line it is - so the whole pill answers
        // the tap and the icon is what says so.
        if (onReconnect != null) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }
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

/** How big the refresh at the end of the line is drawn - the height of the words. */
private val ICON_SIZE = 16.dp
