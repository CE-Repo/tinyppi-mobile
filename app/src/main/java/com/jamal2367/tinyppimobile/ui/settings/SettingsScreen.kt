@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.jamal2367.tinyppimobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.tinyppimobile.BuildConfig
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.ui.components.InfoRow
import com.jamal2367.tinyppimobile.ui.components.SectionCard
import com.jamal2367.tinyppimobile.ui.theme.accentText

/**
 * Where the two addresses live.
 *
 * The whole reason there are two: the box under the television is reached by
 * its address on the network, and the same box from a train is reached by
 * whatever way in was published for it - a different scheme, a different port,
 * usually a different token. Which one the app uses is not a switch the reader
 * has to flip on the way out of the door: in automatic mode the local one is
 * tried first and the remote one takes over the moment it does not answer.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_servers_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Which of the two actually answered last - in automatic
                    // mode that is the only way to tell from the outside.
                    state.activeServerLabel?.let { label ->
                        Text(
                            text = stringResource(
                                if (state.activeIsPrimary) {
                                    R.string.server_active_primary
                                } else {
                                    R.string.server_active_secondary
                                }
                            ) + " · " + label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.accentText,
                        )
                    }
                }
            }

            item {
                ServerCard(
                    title = stringResource(R.string.settings_server_primary),
                    foldId = "settings.server_primary",
                    config = settings.primary,
                    test = state.primaryTest,
                    isActive = state.activeServerLabel != null && state.activeIsPrimary,
                    onSave = { viewModel.saveServer(ServerSlot.PRIMARY, it) },
                    onTest = { viewModel.testConnection(ServerSlot.PRIMARY, it) },
                    onEdit = { viewModel.clearTest(ServerSlot.PRIMARY) },
                    sanitizeHost = viewModel::sanitizeHost,
                    portFromHost = viewModel::portFromHost,
                    schemeFromHost = viewModel::schemeFromHost,
                )
            }

            item {
                ServerCard(
                    title = stringResource(R.string.settings_server_secondary),
                    foldId = "settings.server_secondary",
                    config = settings.secondary,
                    test = state.secondaryTest,
                    isActive = state.activeServerLabel != null && !state.activeIsPrimary &&
                        settings.secondary.label == state.activeServerLabel,
                    onSave = { viewModel.saveServer(ServerSlot.SECONDARY, it) },
                    onTest = { viewModel.testConnection(ServerSlot.SECONDARY, it) },
                    onEdit = { viewModel.clearTest(ServerSlot.SECONDARY) },
                    sanitizeHost = viewModel::sanitizeHost,
                    portFromHost = viewModel::portFromHost,
                    schemeFromHost = viewModel::schemeFromHost,
                )
            }

            item { ConnectionModeCard(settings.connectionMode, viewModel::setConnectionMode) }
            item { UpdatesCard(settings, viewModel) }
            item { AppearanceCard(settings, viewModel) }
            item { AboutCard() }
        }
    }
}

@Composable
private fun ServerCard(
    title: String,
    foldId: String,
    config: ServerConfig,
    test: TestState,
    isActive: Boolean,
    onSave: (ServerConfig) -> Unit,
    onTest: (ServerConfig) -> Unit,
    onEdit: () -> Unit,
    sanitizeHost: (String) -> String,
    portFromHost: (String) -> Int?,
    schemeFromHost: (String) -> Boolean?,
) {
    var tokenVisible by rememberSaveable { mutableStateOf(false) }

    // What is on the screen, which is not yet what the app uses: an address is
    // wrong for as long as it is being typed, and every keystroke stored would
    // be one more address the app goes off and tries to reach. Kept field by
    // field so the list can put the card away while it is scrolled past
    // without the half-typed address going with it.
    var enabled by rememberSaveable(config) { mutableStateOf(config.enabled) }
    var useHttps by rememberSaveable(config) { mutableStateOf(config.useHttps) }
    var hostText by rememberSaveable(config) { mutableStateOf(config.host) }
    var portText by rememberSaveable(config) { mutableStateOf(config.port.toString()) }
    var token by rememberSaveable(config) { mutableStateOf(config.token) }

    val draft = config.copy(
        enabled = enabled,
        useHttps = useHttps,
        host = hostText,
        // A port field left empty or half-deleted is not a port; the stored one
        // stands until a usable number is typed over it.
        port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: config.port,
        token = token,
    )
    val unsaved = draft != config

    SectionCard(
        title = title,
        foldId = foldId,
        trailing = {
            if (isActive) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.accentText,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        SwitchRow(
            label = stringResource(R.string.settings_server_enabled),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                onEdit()
            },
        )

        OutlinedTextField(
            value = hostText,
            onValueChange = { raw ->
                // A pasted `http://coreelec.local:8099/` should fill all three
                // fields rather than end up in the host as it stands.
                hostText = sanitizeHost(raw)
                portFromHost(raw)?.let { portText = it.toString() }
                schemeFromHost(raw)?.let { useHttps = it }
                onEdit()
            },
            label = { Text(stringResource(R.string.settings_host)) },
            placeholder = { Text("192.168.1.10") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = portText,
                onValueChange = { raw ->
                    portText = raw.filter { it.isDigit() }.take(5)
                    onEdit()
                },
                label = { Text(stringResource(R.string.settings_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                SwitchRow(
                    label = stringResource(R.string.settings_https),
                    checked = useHttps,
                    onCheckedChange = {
                        useHttps = it
                        onEdit()
                    },
                )
            }
        }

        OutlinedTextField(
            value = token,
            onValueChange = {
                // The add-on mints tokens out of an upper-case alphabet with
                // no I, O, 0 or 1 in it, because a token is read off a
                // television and typed on a phone. Upper-casing here is the
                // difference between that and an error message.
                token = it.uppercase().take(TOKEN_FIELD_LIMIT)
                onEdit()
            },
            label = { Text(stringResource(R.string.settings_token)) },
            supportingText = { Text(stringResource(R.string.settings_token_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.Characters,
            ),
            visualTransformation = if (tokenVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (tokenVisible) {
                                R.string.settings_token_hide
                            } else {
                                R.string.settings_token_show
                            }
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onTest(draft) },
                enabled = test != TestState.Running,
            ) {
                Text(
                    stringResource(
                        if (test == TestState.Running) {
                            R.string.settings_test_running
                        } else {
                            R.string.settings_test
                        }
                    )
                )
            }
            Button(onClick = { onSave(draft) }, enabled = unsaved) {
                Text(stringResource(R.string.settings_save))
            }
        }

        // The address that was tested is the one on screen, not the one in
        // use - so the result stays readable next to an unsaved change.
        TestResult(test)

        if (unsaved) {
            Text(
                text = stringResource(R.string.settings_unsaved),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun TestResult(test: TestState, modifier: Modifier = Modifier) {
    when (test) {
        TestState.Idle -> Unit

        TestState.Running -> LoadingIndicator(modifier = modifier.size(26.dp))

        is TestState.Ok -> Column(modifier = modifier) {
            ResultLine(
                icon = Icons.Outlined.CheckCircle,
                tint = MaterialTheme.colorScheme.accentText,
                text = stringResource(R.string.settings_test_ok, test.version),
            )
            // Reachable and refusing every reading is the one outcome that
            // looks like success and is not, so it is said in as many words.
            if (!test.tokenAccepted) {
                ResultLine(
                    icon = Icons.Outlined.WarningAmber,
                    tint = MaterialTheme.colorScheme.error,
                    text = stringResource(R.string.settings_test_token_bad),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!test.control) {
                ResultLine(
                    icon = Icons.Outlined.WarningAmber,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = stringResource(R.string.settings_test_no_control),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is TestState.Failed -> ResultLine(
            icon = Icons.Outlined.ErrorOutline,
            tint = MaterialTheme.colorScheme.error,
            text = test.message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

@Composable
private fun ResultLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ConnectionModeCard(current: ConnectionMode, onSelect: (ConnectionMode) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_mode), foldId = "settings.mode") {
        ConnectionMode.entries.forEach { mode ->
            val (labelRes, descriptionRes) = when (mode) {
                ConnectionMode.AUTO ->
                    R.string.settings_mode_auto to R.string.settings_mode_auto_desc

                ConnectionMode.PRIMARY_ONLY ->
                    R.string.settings_mode_primary to R.string.settings_mode_primary_desc

                ConnectionMode.SECONDARY_ONLY ->
                    R.string.settings_mode_secondary to R.string.settings_mode_secondary_desc
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) },
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(descriptionRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** How the readings arrive, and how hard the phone works for them. */
@Composable
private fun UpdatesCard(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.settings_updates), foldId = "settings.updates") {
        SwitchRow(
            label = stringResource(R.string.settings_live_updates),
            description = stringResource(R.string.settings_live_updates_desc),
            checked = settings.liveUpdates,
            onCheckedChange = viewModel::setLiveUpdates,
        )

        // Only ever read when the stream is off: with it open the box decides
        // the cadence, and it is five times a second.
        if (!settings.liveUpdates) {
            Text(
                text = stringResource(R.string.settings_poll_interval),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_poll_interval_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.POLL_INTERVALS.forEach { seconds ->
                    FilterChip(
                        selected = settings.pollIntervalSeconds == seconds,
                        onClick = { viewModel.setPollInterval(seconds) },
                        label = { Text(stringResource(R.string.settings_seconds, seconds)) },
                    )
                }
            }
        }

        SwitchRow(
            label = stringResource(R.string.settings_keep_screen_on),
            description = stringResource(R.string.settings_keep_screen_on_desc),
            checked = settings.keepScreenOn,
            onCheckedChange = viewModel::setKeepScreenOn,
        )
    }
}

@Composable
private fun AppearanceCard(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionCard(
        title = stringResource(R.string.settings_appearance),
        foldId = "settings.appearance",
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                }
                            )
                        )
                    },
                )
            }
        }

        SwitchRow(
            label = stringResource(R.string.settings_artwork),
            description = stringResource(R.string.settings_artwork_desc),
            checked = settings.showArtwork,
            onCheckedChange = viewModel::setShowArtwork,
        )

        // Greyed out rather than hidden where there are no posters to read a
        // colour off: a switch that vanishes leaves the reader wondering what
        // they did, and one that does nothing is worse.
        SwitchRow(
            label = stringResource(R.string.settings_adaptive_color),
            description = stringResource(R.string.settings_adaptive_color_desc),
            checked = settings.adaptiveColor && settings.showArtwork,
            onCheckedChange = viewModel::setAdaptiveColor,
            enabled = settings.showArtwork,
        )
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = stringResource(R.string.settings_about), foldId = "settings.about") {
        InfoRow(
            label = stringResource(R.string.settings_about_version),
            value = BuildConfig.VERSION_NAME,
        )
        InfoRow(
            label = stringResource(R.string.settings_about_addon),
            value = "script.tinyppi",
        )
        InfoRow(
            label = stringResource(R.string.settings_about_default_port),
            value = ServerConfig.DEFAULT_PORT.toString(),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val dim = if (enabled) 1f else DISABLED_ALPHA

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = dim),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** How far a row that cannot be touched is faded. */
private const val DISABLED_ALPHA = 0.38f

/**
 * How much a token field accepts.
 *
 * The add-on's own is eight characters; the limit is looser than that so a
 * reverse proxy that wants a longer secret is not shut out by a field.
 */
private const val TOKEN_FIELD_LIMIT = 128
