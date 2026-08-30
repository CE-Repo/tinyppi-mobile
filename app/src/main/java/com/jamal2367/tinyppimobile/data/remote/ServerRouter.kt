package com.jamal2367.tinyppimobile.data.remote

import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the two configured boxes a request goes to.
 *
 * The interesting case is a phone that leaves the flat: the local address stops
 * resolving and every call would fail, so in [ConnectionMode.AUTO] the router
 * hands out both and lets the caller walk down the list. The one that last
 * answered is tried first, so the fallback costs one failed connection rather
 * than one per request.
 *
 * That is remembered past the end of the session too, through [memory]: a cold
 * start outside the flat would otherwise open by asking the local address, and
 * an address that is not on this network answers nothing at all - the request
 * sits there for the whole connect timeout, with an empty screen behind it,
 * before the one that works is even tried.
 */
class ServerRouter(private val memory: ReachabilityMemory = ReachabilityMemory.None) {

    private val _settings = MutableStateFlow(AppSettings())

    private val _activeServer = MutableStateFlow<ActiveServer?>(null)

    /** Which server the last successful call went to, for the status line. */
    val activeServer: StateFlow<ActiveServer?> = _activeServer.asStateFlow()

    val settings: AppSettings get() = _settings.value

    /**
     * The same configuration, as something that can be waited on.
     *
     * Read by the live session, which has to restart when the addresses or the
     * way it fetches change - not merely see the new values on its next pass.
     * A stream that is already open would otherwise go on streaming for as long
     * as it lasted after live updates were switched off.
     */
    val settingsFlow: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * The address the note on disk names, once it has been read.
     *
     * Read on the first request rather than at construction, because that
     * happens on the main thread during startup and this is a file. Volatile
     * because the reader is whichever OkHttp thread got there first; two of
     * them reading the same note is a wasted read, not a wrong answer.
     */
    @Volatile
    private var remembered: String? = null

    @Volatile
    private var rememberedRead = false

    /**
     * Whether a stored configuration has arrived yet.
     *
     * The first one to come out of the settings is not somebody changing an
     * address - it is the addresses being read for the first time, and the
     * defaults it replaces were never pointed at anything. Told apart because
     * everything below it throws away what was learned about the old server,
     * and at startup that is the note the first request was meant to follow.
     */
    private var configured = false

    fun update(settings: AppSettings) {
        val previous = _settings.value
        val established = configured
        configured = true
        _settings.value = settings

        val addressesChanged = previous.primary != settings.primary ||
            previous.secondary != settings.secondary ||
            previous.connectionMode != settings.connectionMode

        // A changed address is a different server: what answered before says
        // nothing about what will answer now.
        if (established && addressesChanged) {
            _activeServer.value = null
            // The note was about the addresses that are being replaced, and
            // says nothing about the ones taking their place.
            remembered = null
            rememberedRead = true
            memory.forget()
        }
    }

    /**
     * The servers to try, best guess first.
     *
     * Empty when nothing is configured - the caller turns that into
     * [ApiFailure.NotConfigured] rather than a connection error, because the
     * two want completely different words on screen.
     */
    fun candidates(): List<ServerConfig> {
        val servers = _settings.value.servers()
        val lastGood = lastGoodBaseUrl() ?: return servers
        val index = servers.indexOfFirst { it.baseUrl == lastGood }
        if (index <= 0) return servers
        return listOf(servers[index]) + servers.filterIndexed { i, _ -> i != index }
    }

    /**
     * The address to try first: what answered in this session, or failing that
     * what the note from the last one says.
     */
    private fun lastGoodBaseUrl(): String? {
        _activeServer.value?.let { return it.config.baseUrl }
        if (!rememberedRead) {
            rememberedRead = true
            remembered = memory.lastReachable()
        }
        return remembered
    }

    fun markReachable(config: ServerConfig) {
        _activeServer.value = ActiveServer(config, isPrimary(config))
        remembered = config.baseUrl
        rememberedRead = true
        memory.remember(config.baseUrl)
    }

    /**
     * Nothing answered.
     *
     * The note is left alone: it is only ever an order to try things in, and
     * when neither address answers there is no better one to fall back to.
     */
    fun markUnreachable() {
        _activeServer.value = null
    }

    private fun isPrimary(config: ServerConfig): Boolean =
        _settings.value.primary.isUsable && _settings.value.primary.baseUrl == config.baseUrl

    data class ActiveServer(val config: ServerConfig, val isPrimary: Boolean)
}
