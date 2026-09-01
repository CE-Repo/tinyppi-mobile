@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.jamal2367.tinyppimobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.ui.components.ChartColors
import com.jamal2367.tinyppimobile.ui.components.ChartSeries
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.EventsCard
import com.jamal2367.tinyppimobile.ui.components.LuminanceChart
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.StatTile
import com.jamal2367.tinyppimobile.util.Formatters
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import kotlinx.coroutines.delay

/**
 * What the playing title has done so far.
 *
 * The point of keeping any of this on the box: the producer sees every tick and
 * a phone only the ones it was connected for, so a screen opened halfway
 * through a film still gets the whole picture - the peak the grade ever
 * reached, every output switch, every frame that was lost.
 */
/**
 * Asks for the history again every few seconds while a title is running.
 *
 * The event counter that otherwise drives the fetch counts events, not samples,
 * so a quiet film leaves the chart standing still - this is what keeps it
 * moving. Only while something is playing, because a finished title's history
 * is finished, and only while this screen is in front of someone: it is bound
 * to the lifecycle, so a tab switch or a pocket stops it rather than leaving a
 * timer asking the box about a screen nobody is looking at.
 *
 * Once per sample, which is as often as there is anything new to draw: the box
 * writes the chart a point a second. A fifth of the snapshot stream's rate, and
 * a request that carries a chart rather than the whole of one.
 */
@Composable
private fun ChartHeartbeat(playing: Boolean, onBeat: () -> Unit) {
    if (!playing) return

    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(CHART_BEAT_MS)
                onBeat()
            }
        }
    }
}

@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChartHeartbeat(playing = state.snapshot?.playing == true, onBeat = viewModel::refresh)

    Scaffold { padding ->
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
                contentPadding = PaddingValues(ScreenEdge),
                verticalArrangement = Arrangement.spacedBy(CardGap, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item { SummaryCard(state, history) }
                item { EventsCard(events = history.events, foldId = "history.events") }
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
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Formatters.nits(history.peak)?.let {
                StatTile(
                    value = it,
                    caption = stringResource(R.string.history_peak),
                    modifier = Modifier.weight(1f),
                )
            }
            StatTile(
                value = history.switches.toString(),
                caption = stringResource(R.string.live_switches),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = (state.snapshot?.session?.warnings ?: 0).toString(),
                caption = stringResource(R.string.live_warnings),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = Formatters.elapsed(history.now),
                caption = stringResource(R.string.history_running),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ChartCard(
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

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.ONE_MINUTE -> R.string.history_range_1m
    ChartRange.TEN_MINUTES -> R.string.history_range_10m
    ChartRange.ALL -> R.string.history_range_all
}

/** How often the chart asks for the samples it has gained since. */
private const val CHART_BEAT_MS = 1_000L
