@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.tinyppimobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.ui.components.EmptyState
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.live.LiveViewModel

/**
 * Every reading the overlay prints, in the cards it prints them in.
 *
 * Nothing here is composed by this app: the add-on renders each row the way
 * the on-screen overlay draws it - same formatting, same units, same labels,
 * translated through Kodi's own string table - and groups them the way the
 * overlay's panels are grouped. So a row that gains a unit on the television
 * gains it here, and neither can drift from the other.
 *
 * A row the stream does not carry is left out by the add-on rather than sent
 * blank, and a whole card whose source cannot carry it goes the same way,
 * which is what keeps this readable on a phone.
 */
@Composable
fun DetailsScreen(
    onOpenSettings: () -> Unit,
    viewModel: LiveViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_details)) }) },
    ) { padding ->
        val groups = snapshot?.groups.orEmpty()

        when {
            !state.isConfigured -> EmptyState(
                icon = Icons.Outlined.Assessment,
                title = stringResource(R.string.live_not_configured_title),
                message = stringResource(R.string.live_not_configured_text),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )

            groups.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Assessment,
                title = stringResource(R.string.details_empty_title),
                message = stringResource(R.string.details_empty_text),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(items = groups, key = InfoGroup::id) { group ->
                    GroupCard(group)
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: InfoGroup) {
    // The box names its own groups, and the name is what the fold is filed
    // under - a group that comes back next week comes back folded as it was.
    SectionCard(title = group.title, foldId = "details.${group.id}") {
        group.rows.forEach { row ->
            ReadingRow(
                label = row.label,
                value = row.value,
                detail = row.detail,
            )
        }
    }
}

/**
 * One reading: its name on the left, its value on the right, and the
 * parenthesised extra under the value.
 *
 * The extra is what the overlay draws in its accent colour - a decoder behind
 * a codec, an average behind a live bitrate - and it is dimmed here for the
 * same reason: it qualifies the reading rather than being one.
 */
@Composable
private fun ReadingRow(label: String, value: String, detail: String) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        Column(
            modifier = Modifier.weight(1.1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
