package com.jamal2367.tinyppimobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tinyppi_settings")

/**
 * Where the settings live.
 *
 * Everything the app is configured with is one flow, so a screen never reads a
 * half-changed configuration: switching the connection mode and the address it
 * points at arrive together.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setPrimary(config: ServerConfig) = writeServer(PRIMARY, config)

    suspend fun setSecondary(config: ServerConfig) = writeServer(SECONDARY, config)

    suspend fun setConnectionMode(mode: ConnectionMode) = edit { it[KEY_MODE] = mode.name }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setLiveUpdates(enabled: Boolean) = edit { it[KEY_LIVE_UPDATES] = enabled }

    suspend fun setPollInterval(seconds: Int) = edit {
        it[KEY_POLL_SECONDS] = seconds.coerceIn(POLL_RANGE)
    }

    suspend fun setChartRange(range: ChartRange) = edit { it[KEY_CHART_RANGE] = range.name }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[KEY_KEEP_SCREEN_ON] = enabled }

    suspend fun setShowArtwork(enabled: Boolean) = edit { it[KEY_SHOW_ARTWORK] = enabled }

    suspend fun setControlsExpanded(expanded: Boolean) = edit {
        it[KEY_CONTROLS_EXPANDED] = expanded
    }

    private suspend fun writeServer(prefix: String, config: ServerConfig) = edit {
        it[booleanPreferencesKey("$prefix$SUFFIX_ENABLED")] = config.enabled
        it[booleanPreferencesKey("$prefix$SUFFIX_HTTPS")] = config.useHttps
        it[stringPreferencesKey("$prefix$SUFFIX_HOST")] = config.host.trim()
        it[intPreferencesKey("$prefix$SUFFIX_PORT")] = config.port
        // Upper-cased on the way in: the add-on mints tokens out of an
        // upper-case alphabet, and a token typed in lower case off a television
        // is the one mistake worth correcting rather than reporting.
        it[stringPreferencesKey("$prefix$SUFFIX_TOKEN")] = config.token.trim().uppercase()
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        // The local box is on by default: a first launch should only need an
        // address typed in, not a switch found first.
        primary = readServer(PRIMARY, defaultEnabled = true),
        secondary = readServer(SECONDARY, defaultEnabled = false),
        connectionMode = this[KEY_MODE].toEnum(ConnectionMode.AUTO),
        themeMode = this[KEY_THEME].toEnum(ThemeMode.SYSTEM),
        dynamicColor = this[KEY_DYNAMIC_COLOR] ?: true,
        liveUpdates = this[KEY_LIVE_UPDATES] ?: true,
        // Coerced rather than trusted: an interval written by a build that
        // offered a wider choice would otherwise set a cadence this one has no
        // chip to get back from.
        pollIntervalSeconds = (this[KEY_POLL_SECONDS] ?: AppSettings.DEFAULT_POLL_SECONDS)
            .coerceIn(POLL_RANGE),
        chartRange = this[KEY_CHART_RANGE].toEnum(ChartRange.TEN_MINUTES),
        keepScreenOn = this[KEY_KEEP_SCREEN_ON] ?: false,
        showArtwork = this[KEY_SHOW_ARTWORK] ?: true,
        controlsExpanded = this[KEY_CONTROLS_EXPANDED] ?: false,
    )

    private fun Preferences.readServer(prefix: String, defaultEnabled: Boolean) = ServerConfig(
        enabled = this[booleanPreferencesKey("$prefix$SUFFIX_ENABLED")] ?: defaultEnabled,
        useHttps = this[booleanPreferencesKey("$prefix$SUFFIX_HTTPS")] ?: false,
        host = this[stringPreferencesKey("$prefix$SUFFIX_HOST")].orEmpty(),
        port = this[intPreferencesKey("$prefix$SUFFIX_PORT")] ?: ServerConfig.DEFAULT_PORT,
        token = this[stringPreferencesKey("$prefix$SUFFIX_TOKEN")].orEmpty(),
    )

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        val POLL_RANGE = AppSettings.POLL_INTERVALS.min()..AppSettings.POLL_INTERVALS.max()

        const val PRIMARY = "primary"
        const val SECONDARY = "secondary"
        const val SUFFIX_ENABLED = "_enabled"
        const val SUFFIX_HTTPS = "_https"
        const val SUFFIX_HOST = "_host"
        const val SUFFIX_PORT = "_port"
        const val SUFFIX_TOKEN = "_token"

        val KEY_MODE = stringPreferencesKey("connection_mode")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_LIVE_UPDATES = booleanPreferencesKey("live_updates")
        val KEY_POLL_SECONDS = intPreferencesKey("poll_seconds")
        val KEY_CHART_RANGE = stringPreferencesKey("chart_range")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_SHOW_ARTWORK = booleanPreferencesKey("show_artwork")
        val KEY_CONTROLS_EXPANDED = booleanPreferencesKey("controls_expanded")
    }
}
