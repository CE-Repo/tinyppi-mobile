package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.InfoRowData
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.repository.LiveState
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme

/*
 * The shared pieces of the screens, drawn with made-up readings for the
 * editor's preview pane - in both themes, since the app is used in the dark
 * in front of a television as often as in daylight.
 *
 * Every figure here is the kind of thing the add-on sends, so a change to a
 * card can be judged against what it will actually be showing. Nothing in this
 * file is reached from the app; the shrinker drops it from a release build.
 */

/** The theme and the ground every card sits on, for a preview. */
@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    TinyPpiTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FormatBadgePreview() = PreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FormatBadge(text = "4K")
        FormatBadge(text = "DV P7.6 FEL", arrowSuffix = "HDR10")
        FormatBadge(text = "Atmos")
    }
}

@PreviewLightDark
@Composable
private fun StatusPreview() = PreviewSurface {
    StatusLine(connection = LiveState.Connection.Streaming, serverLabel = "192.168.1.10:8099")
    StatusLine(connection = LiveState.Connection.Busy, serverLabel = "192.168.1.10:8099")
    StatusLine(connection = LiveState.Connection.Offline, serverLabel = null, onReconnect = {})
    StatusRow(connection = LiveState.Connection.Unauthorized, serverLabel = "tinyppi.example.com:443")
}

@PreviewLightDark
@Composable
private fun GroupCardPreview() = PreviewSurface {
    GroupCard(
        InfoGroup(
            id = "video",
            title = "Video",
            rows = listOf(
                InfoRowData(id = "res", label = "Resolution", value = "3840×2160", detail = "23.976 fps"),
                InfoRowData(id = "codec", label = "Codec", value = "HEVC Main 10"),
                InfoRowData(id = "bitrate", label = "Bitrate", value = "58.2 Mbit/s"),
                InfoRowData(id = "max", label = "MaxCLL / MaxFALL", value = "1000 / 400 nits"),
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun EventsCardPreview() = PreviewSurface {
    EventsCard(
        events = listOf(
            PlaybackEvent(t = 12.0, pos = "0:00:12", kind = "vs10", from = "DV", to = "HDR10"),
            PlaybackEvent(t = 1805.0, pos = "0:30:05", kind = "audio", from = "English TrueHD", to = "German DTS"),
            PlaybackEvent(t = 3620.0, pos = "1:00:20", kind = "cache_low", value = 4.0),
        ),
        foldId = "preview.events",
    )
    EventsCard(events = emptyList(), foldId = "preview.events.empty")
}

@PreviewLightDark
@Composable
private fun LuminanceChartPreview() = PreviewSurface {
    // A grade that sits around a hundred nits and flares twice past a
    // thousand - which is what the chart is there to show.
    val peaks = (0..120).map { second ->
        val flare = if (second in 40..48 || second in 90..95) 1_100.0 else 0.0
        second.toDouble() to (120.0 + 60.0 * kotlin.math.sin(second / 7.0) + flare)
    }
    val average = peaks.map { (t, v) -> t to v * 0.18 }
    LuminanceChart(
        series = listOf(
            ChartSeries(label = "MaxCLL", color = Color(0xFFF4C04F), points = peaks),
            ChartSeries(label = "MaxFALL", color = Color(0xFF7FB2FF), points = average),
        ),
        windowSeconds = 120,
    )
}

@Preview
@Composable
private fun EmptyStatePreview() = PreviewSurface {
    EmptyState(
        icon = Icons.Outlined.PlayCircle,
        title = "Nothing is playing",
        message = "Start something on the box and it appears here.",
        actionLabel = "Open settings",
        onAction = {},
    )
}
