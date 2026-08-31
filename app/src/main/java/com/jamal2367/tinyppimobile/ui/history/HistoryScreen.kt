@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.jamal2367.tinyppimobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.PlaybackEvent
import com.jamal2367.tinyppimobile.data.model.PlaybackEventKind
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.ui.components.ChartColors
import com.jamal2367.tinyppimobile.ui.components.ChartSeries
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.LuminanceChart
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatTile
import com.jamal2367.tinyppimobile.ui.components.StatusDot
import com.jamal2367.tinyppimobile.ui.theme.EventSwitch
import com.jamal2367.tinyppimobile.ui.theme.EventWarning
import com.jamal2367.tinyppimobile.util.Formatters

/**
 * What the playing title has done so far.
 *
 * The point of keeping any of this on the box: the producer sees every tick and
 * a phone only the ones it was connected for, so a screen opened halfway
 * through a film still gets the whole picture - the peak the grade ever
 * reached, every output switch, every frame that was lost.
 */
@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_history)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val history = state.history

        when {
            !state.isConfigured -> EmptyState(
                icon = Icons.Outlined.Timeline,
                title = stringResource(R.string.live_not_configured_title),
                message = stringResource(R.string.live_not_configured_text),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )

            history == null || history.isEmpty -> EmptyState(
                icon = Icons.Outlined.Timeline,
                title = stringResource(R.string.history_empty_title),
                message = state.error ?: stringResource(R.string.history_empty_text),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item { SummaryCard(state, history) }
                item { ChartCard(history, state.range, viewModel::setRange) }
                item { EventsCard(history) }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: HistoryUiState, history: History) {
    SectionCard(title = stringResource(R.string.history_summary), foldId = "history.summary") {
        state.snapshot?.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Formatters.nits(history.peak)?.let {
                StatTile(value = it, caption = stringResource(R.string.history_peak))
            }
            StatTile(
                value = history.switches.toString(),
                caption = stringResource(R.string.live_switches),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            StatTile(
                value = (state.snapshot?.session?.warnings ?: 0).toString(),
                caption = stringResource(R.string.live_warnings),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            StatTile(
                value = Formatters.elapsed(history.now),
                caption = stringResource(R.string.history_running),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChartCard(
    history: History,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
) {
    SectionCard(title = stringResource(R.string.history_chart), foldId = "history.chart") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartRange.entries.forEach { option ->
                FilterChip(
                    selected = range == option,
                    onClick = { onRangeChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }

        val peak = history.series { it.max }
        val average = history.series { it.avg }

        if (peak.isEmpty() && average.isEmpty()) {
            // Only a Dolby Vision source carries the luminance this charts, and
            // the add-on leaves it out entirely for every other grade rather
            // than send the zeroes its own getter pads an absent block with.
            Text(
                text = stringResource(R.string.history_no_luminance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@SectionCard
        }

        LuminanceChart(
            series = listOf(
                ChartSeries(
                    label = stringResource(R.string.history_peak),
                    color = ChartColors.peak,
                    points = peak,
                ),
                ChartSeries(
                    label = stringResource(R.string.history_average),
                    color = ChartColors.average,
                    points = average,
                ),
            ),
            windowSeconds = range.seconds,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EventsCard(history: History) {
    SectionCard(title = stringResource(R.string.history_events), foldId = "history.events") {
        if (history.events.isEmpty()) {
            Text(
                text = stringResource(R.string.history_events_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Newest first: what just happened is what a screen opened mid-film is
        // being opened to find out.
        history.events.asReversed().forEach { event ->
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

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.ONE_MINUTE -> R.string.history_range_1m
    ChartRange.TEN_MINUTES -> R.string.history_range_10m
    ChartRange.ALL -> R.string.history_range_all
}
