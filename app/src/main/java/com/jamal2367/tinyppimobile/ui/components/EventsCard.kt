package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.model.PlaybackEventKind
import com.jamal2367.tinyppimobile.ui.theme.EventSwitch
import com.jamal2367.tinyppimobile.ui.theme.EventWarning
import com.jamal2367.tinyppimobile.util.Formatters

/**
 * What has happened to the playing title, newest first.
 *
 * The same card on the live screen and in the history: the events are the one
 * part of a title's past that is worth having in front of you while it plays -
 * a mode that switched, a cache that ran dry - and the two screens are read for
 * different reasons by the same person. Each keeps its own fold, hence
 * [foldId].
 */
@Composable
fun EventsCard(events: List<PlaybackEvent>, foldId: String) {
    SectionCard(title = stringResource(R.string.history_events), foldId = foldId) {
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.history_events_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Newest first: what just happened is what a screen opened mid-film is
        // being opened to find out.
        events.asReversed().forEach { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun EventRow(event: PlaybackEvent) {
    val kind = event.eventKind

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(
            color = if (kind?.isWarning == true) EventWarning else EventSwitch,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(kind.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = event.describe(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = event.pos.ifBlank { Formatters.elapsed(event.t) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What an event came to, in one line.
 *
 * A transition names where it went rather than both ends: the list is read
 * downwards and the row under it already says where it came from.
 */
@Composable
private fun PlaybackEvent.describe(): String {
    val off = stringResource(R.string.live_subtitles_off)

    fun state(value: String?): String = when {
        value == null -> "–"
        value == PlaybackEventKind.SUBTITLES_OFF -> off
        else -> value
    }

    return when {
        isTransition -> state(to)
        eventKind == PlaybackEventKind.TEMPERATURE -> Formatters.celsius(value) ?: "–"
        eventKind == PlaybackEventKind.CPU -> Formatters.percent(value) ?: "–"
        eventKind == PlaybackEventKind.CACHE_LOW -> Formatters.percent(value) ?: "–"
        eventKind == PlaybackEventKind.CACHE_RECOVERED -> Formatters.percent(value) ?: "–"
        value != null -> Formatters.trimmed(value, 1)
        else -> "–"
    }
}

private fun PlaybackEventKind?.labelRes(): Int = when (this) {
    PlaybackEventKind.VS10 -> R.string.event_vs10
    PlaybackEventKind.MODE -> R.string.event_mode
    PlaybackEventKind.AUDIO -> R.string.event_audio
    PlaybackEventKind.SUBTITLE -> R.string.event_subtitle
    PlaybackEventKind.CACHE_LOW -> R.string.event_cache_low
    PlaybackEventKind.CACHE_RECOVERED -> R.string.event_cache_recovered
    PlaybackEventKind.TEMPERATURE -> R.string.event_temperature
    PlaybackEventKind.CPU -> R.string.event_cpu
    PlaybackEventKind.FPS -> R.string.event_fps
    null -> R.string.event_unknown
}
