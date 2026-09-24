@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.tinyppimobile.ui.metadata

import androidx.compose.runtime.CompositionLocalProvider
import com.jamal2367.tinyppimobile.ui.components.ArrangeBackHandler
import com.jamal2367.tinyppimobile.ui.components.ArrangeableCard
import com.jamal2367.tinyppimobile.ui.components.CardScreens
import com.jamal2367.tinyppimobile.ui.components.LocalCardLayout
import com.jamal2367.tinyppimobile.ui.components.LocalCardScreen
import com.jamal2367.tinyppimobile.ui.components.allCardsHidden
import com.jamal2367.tinyppimobile.ui.components.cardArranger
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.tinyppimobile.ui.components.centredBelowTitle
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.History
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.history.ChartCard
import com.jamal2367.tinyppimobile.ui.history.HistoryViewModel
import com.jamal2367.tinyppimobile.util.Formatters
import com.jamal2367.tinyppimobile.ui.components.flashOnChange
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel
import com.jamal2367.tinyppimobile.ui.theme.CardGap
import com.jamal2367.tinyppimobile.ui.theme.ScreenEdge
import com.jamal2367.tinyppimobile.ui.components.ScreenTitle
import com.jamal2367.tinyppimobile.ui.navigation.barAwarePadding

/**
 * The Dolby Vision metadata view, as the overlay's own second window draws it.
 *
 * The same list, split the same way: the per-frame blocks - L1, L2, L4, L5, L8,
 * HDR10+ - rebuilt with every reading, the title-level ones on a slower clock,
 * and the separators decided by the add-on against whichever scene rows are
 * current, so the two halves cannot disagree about the shape of the joined list.
 *
 * Only a Dolby Vision source has an RPU to walk. Anything else sends an empty
 * list, and the screen says so rather than drawing an empty table.
 */
@Composable
fun MetadataScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel,
    historyViewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val historyState by historyViewModel.state.collectAsStateWithLifecycle()
    val rows = state.snapshot?.metadata.orEmpty()

    Scaffold { padding ->
        when {
            !state.isConfigured -> EmptyState(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.live_not_configured_title),
                message = stringResource(R.string.live_not_configured_text),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )

            rows.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.metadata_empty_title),
                message = stringResource(R.string.metadata_empty_text),
                modifier = Modifier.padding(padding),
            )

            else -> CompositionLocalProvider(LocalCardScreen provides CardScreens.METADATA) {
                MetadataList(
                rows = rows,
                chartHistory = historyState.history,
                chartRange = historyState.range,
                onChartRangeChange = historyViewModel::setRange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                )
            }
        }
    }
}

/**
 * The rows as one card per section.
 *
 * The add-on hands this over as one flat list with its own separators - the
 * overlay draws it into a Kodi list, which scrolls by one uniform item size and
 * therefore cannot give a heading a taller row - so the folding back into cards
 * happens here, where a card is a thing that exists.
 */
@Composable
private fun MetadataList(
    rows: List<MetadataRow>,
    chartHistory: History? = null,
    chartRange: ChartRange = ChartRange.TEN_MINUTES,
    onChartRangeChange: (ChartRange) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sections = remember(rows) { rows.toSections() }

    val layout = LocalCardLayout.current
    // Each section is named by the box, and filed under that name the way its
    // fold is. The first block of a view can arrive before any name at all,
    // and that one is filed under where it sits instead - there is only ever
    // one of it.
    //
    // Two sections under one name would be two cards under one key, which a
    // lazy list refuses outright, so the second is told apart by where it sits.
    val sectionIds = remember(sections) {
        val taken = mutableSetOf<String>()
        sections.mapIndexed { index, section ->
            val name = "metadata.${section.title.ifBlank { "section$index" }}"
            if (taken.add(name)) name else "$name#$index".also { taken.add(it) }
        }
    }
    val byId = sectionIds.zip(sections).toMap()
    val present = buildList {
        if (chartHistory != null) add(CARD_CHART)
        addAll(byId.keys)
    }
    val chartLabel = stringResource(R.string.history_chart)
    ArrangeBackHandler(CardScreens.METADATA, layout)

    LazyColumn(
        contentPadding = barAwarePadding(horizontal = ScreenEdge, bottom = ScreenEdge),
        // The title at the top and the cards centred under it, as on the
        // live screen: centring the whole list would carry the title down
        // into the middle with them.
        verticalArrangement = centredBelowTitle(CardGap),
        modifier = modifier,
    ) {
        item(key = "screen-title") { ScreenTitle(stringResource(R.string.nav_metadata)) }
        if (layout.isEditing(CardScreens.METADATA)) {
            cardArranger(
                screen = CardScreens.METADATA,
                cards = present.map { id ->
                    ArrangeableCard(id, if (id == CARD_CHART) chartLabel else byId[id]?.title.orEmpty())
                },
                layout = layout,
            )
            return@LazyColumn
        }
        val shown = layout.visible(CardScreens.METADATA, present)
        if (shown.isEmpty()) allCardsHidden(CardScreens.METADATA, layout)
        for (id in shown) {
            if (id == CARD_CHART) {
                chartHistory?.let { history ->
                    item(key = id) { ChartCard(history, chartRange, onChartRangeChange) }
                }
                continue
            }
            val section = byId[id] ?: continue
            item(key = id) {
                // A blank row is the overlay's way of setting a heading apart
                // in a list of fixed-height items, and a section row has
                // already been read as the card's own title. Both are dropped
                // here rather than inside the loop: a rule goes between one
                // row and the next, and a row that draws nothing would leave
                // its rule behind.
                val drawn = section.rows.filter { MetadataKind.of(it.kind) !in SKIPPED }

                SectionCard(title = section.title, foldId = id) {
                    drawn.forEachIndexed { position, row ->
                        // Ruled between the rows and not around them: what
                        // makes a list of name-and-value pairs read as a table
                        // is the line between one pair and the next, and a
                        // card already draws the outside edge.
                        if (position > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }

                        when (MetadataKind.of(row.kind)) {
                            MetadataKind.HEADINGS -> CellRow(row, heading = true)
                            MetadataKind.COLUMNS -> CellRow(row, heading = false)
                            MetadataKind.WIDE -> WideRow(row)
                            MetadataKind.SPACE, MetadataKind.SECTION -> Unit
                            MetadataKind.ROW -> ValueRow(row)
                        }
                    }
                }
            }
        }
    }
}

/** The luminance chart, filed under the name its fold has always had. */
private const val CARD_CHART = "history.chart"

@Composable
private fun ValueRow(row: MetadataRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.value.ifBlank { "–" },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .flashOnChange(row.value),
        )
    }
}

/** One line across the full width, for a value with no name worth giving it. */
@Composable
private fun WideRow(row: MetadataRow) {
    Text(
        text = listOf(row.name, row.value).filter { it.isNotBlank() }.joinToString(" "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .flashOnChange(row.value),
    )
}

/**
 * A row of a trim table: a name over a grid of cells.
 *
 * A trim table has six columns of figures and a phone has room for three, so
 * something has to give. What gives is the line, not the alignment: the name
 * takes a line of its own and the cells run underneath it in a fixed number of
 * columns, so the second line of one row sits under the second line of the row
 * above and a column can still be read down.
 *
 * The alternative was scrolling the row sideways, which kept every figure on
 * one line and put half of them off the screen - a table nobody could read
 * without dragging it, and one where the heading scrolled separately from the
 * figures it named.
 *
 * A short last line is padded rather than allowed to spread: three figures in
 * the width of six would sit under the wrong headings.
 */
@Composable
private fun CellRow(row: MetadataRow, heading: Boolean) {
    val cells = row.cells.orEmpty()
    val weight = if (heading) FontWeight.SemiBold else FontWeight.Normal

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = weight,
        )

        cells.chunked(TRIM_COLUMNS).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                line.forEach { cell ->
                    Text(
                        // Written out, the way this app writes every other
                        // luminance: a column of `1.9 k` beside `850` has to be
                        // converted in the head before it can be read down.
                        // Column names are left alone.
                        text = (if (heading) cell else Formatters.expandThousands(cell))
                            .ifBlank { "–" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (heading) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = weight,
                        textAlign = TextAlign.End,
                        // A heading names the columns and never moves; only the
                        // cells under it are worth lighting up, one at a time.
                        modifier = if (heading) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                                .weight(1f)
                                .flashOnChange(cell)
                        },
                    )
                }

                repeat(TRIM_COLUMNS - line.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The row kinds the add-on's metadata builder produces.
 *
 * Named here rather than compared as strings at each branch, and anything a
 * future add-on adds falls back to a plain name-and-value row rather than
 * disappearing off the screen.
 */
private enum class MetadataKind(val id: String) {
    /** Opens a section; everything after it belongs to it. */
    SECTION("section"),

    /** A name and a value. */
    ROW("row"),

    /** One line across the full width, for a value with no name. */
    WIDE("wide"),

    /** A blank line, which is how the overlay sets its headings apart. */
    SPACE("space"),

    /** The column names of a trim table. */
    HEADINGS("headings"),

    /** One row of readings under them. */
    COLUMNS("columns");

    companion object {
        fun of(id: String): MetadataKind = entries.firstOrNull { it.id == id } ?: ROW
    }
}

private data class MetadataSection(val title: String, val rows: List<MetadataRow>)

/**
 * The flat list folded into sections at its own headings.
 *
 * Anything before the first heading belongs to a section of its own, so a list
 * that opens with rows rather than a heading still has all of them on screen.
 */
private fun List<MetadataRow>.toSections(): List<MetadataSection> {
    val sections = mutableListOf<MetadataSection>()
    var title = ""
    var current = mutableListOf<MetadataRow>()

    fun flush() {
        if (current.isEmpty()) return
        sections += MetadataSection(title, current.toList())
        current = mutableListOf()
    }

    for (row in this) {
        if (MetadataKind.of(row.kind) == MetadataKind.SECTION) {
            flush()
            title = row.name.ifBlank { row.value }
            continue
        }
        if (MetadataKind.of(row.kind) == MetadataKind.SPACE) continue
        current += row
    }
    flush()
    return sections
}

/** The kinds of row that draw nothing, and so are not rows at all here. */
private val SKIPPED = setOf(MetadataKind.SPACE, MetadataKind.SECTION)

/**
 * How many figures of a trim table go on one line.
 *
 * Three is what a phone holds at a size that can be read; the sixteen-hundredth
 * of a nit in the last column is not worth squinting at.
 */
private const val TRIM_COLUMNS = 3
