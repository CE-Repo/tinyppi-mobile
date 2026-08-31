@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.tinyppimobile.ui.metadata

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.components.flashOnChange
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel

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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.snapshot?.metadata.orEmpty()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_metadata)) }) },
    ) { padding ->
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

            else -> MetadataList(
                rows = rows,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
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
private fun MetadataList(rows: List<MetadataRow>, modifier: Modifier = Modifier) {
    val sections = remember(rows) { rows.toSections() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        items(sections.size) { index ->
            val section = sections[index]
            // The box names its own sections. The first block of a view can
            // arrive before any name at all, and that one is filed under where
            // it sits instead - there is only ever one of it.
            val foldId = "metadata.${section.title.ifBlank { "section$index" }}"

            SectionCard(title = section.title, foldId = foldId) {
                section.rows.forEach { row ->
                    when (MetadataKind.of(row.kind)) {
                        MetadataKind.HEADINGS -> CellRow(row, heading = true)
                        MetadataKind.COLUMNS -> CellRow(row, heading = false)
                        MetadataKind.WIDE -> WideRow(row)
                        // A blank row is the overlay's way of setting a heading
                        // apart in a list of fixed-height items; a card already
                        // has air around its title, so it is dropped rather
                        // than drawn as an empty line.
                        MetadataKind.SPACE, MetadataKind.SECTION -> Unit
                        MetadataKind.ROW -> ValueRow(row)
                    }
                }
            }
        }
    }
}

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
 * A row of a trim table: a name and a fixed set of cells.
 *
 * Scrolled sideways rather than wrapped. A trim table is read down its columns
 * - the same slot means the same thing on every row, which is the only reason
 * the cells travel as a list rather than as one string - and a row that wraps
 * onto a second line breaks exactly that.
 */
@Composable
private fun CellRow(row: MetadataRow, heading: Boolean) {
    val cells = row.cells.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(96.dp),
        )
        cells.forEach { cell ->
            Text(
                text = cell.ifBlank { "–" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (heading) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.End,
                // A heading names the columns and never moves; only the cells
                // under it are worth lighting up, and each on its own.
                modifier = if (heading) {
                    Modifier.width(76.dp)
                } else {
                    Modifier
                        .width(76.dp)
                        .flashOnChange(cell)
                },
            )
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
