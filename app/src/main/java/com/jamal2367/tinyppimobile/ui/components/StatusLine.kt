package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
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
import com.jamal2367.tinyppimobile.ui.theme.accentText
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
 */
@Composable
fun StatusLine(
    connection: LiveState.Connection,
    serverLabel: String?,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
) {
    val dot by animateColorAsState(targetValue = connection.dotColor(), label = "statusDot")

    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val ground = LocalArtworkAccent.current
        ?.takeIf { accented }
        ?.let { artworkTint(it, container) }
        ?: container

    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
            .clip(PillShape)
            .background(ground)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color = dot)
        Text(
            text = stringResource(connection.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.accentText,
        )
        if (serverLabel != null && connection.namesAServer()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = serverLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.accentText,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
