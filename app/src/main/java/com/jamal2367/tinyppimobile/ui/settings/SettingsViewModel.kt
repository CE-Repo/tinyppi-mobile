package com.jamal2367.tinyppimobile.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.prefs.AppSettings
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.data.remote.ConnectionTestResult
import com.jamal2367.tinyppimobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Which of the two stored addresses an edit is about. */
enum class ServerSlot { PRIMARY, SECONDARY }

/** How the last connection test for one slot went. */
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState

    /**
     * The box answered.
     *
     * [tokenAccepted] is a separate question from being reachable, because on
     * this API it genuinely is one: `/api/hello` needs no token, so a box can
     * be perfectly reachable and still refuse every reading.
     */
    data class Ok(
        val version: String,
        val control: Boolean,
        val tokenAccepted: Boolean,
    ) : TestState

    data class Failed(val message: String) : TestState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val primaryTest: TestState = TestState.Idle,
    val secondaryTest: TestState = TestState.Idle,
    val activeServerLabel: String? = null,
    val activeIsPrimary: Boolean = false,
)

/**
 * The settings, including the two addresses the app can reach a box at.
 *
 * Everything but an address is written through the moment it is changed - a
 * theme has no half-way state worth protecting anyone from. An address does: a
 * host name is wrong for as long as it is being typed, and every keystroke
 * saved would be an address the app tries to reach. That one is edited on the
 * screen and stored when the reader says so.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TinyPpiApplication).container
    private val repository = container.settingsRepository

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        repository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(settings = settings, loaded = true)
            }
            .launchIn(viewModelScope)

        container.router.activeServer
            .onEach { active ->
                _state.value = _state.value.copy(
                    activeServerLabel = active?.config?.label,
                    activeIsPrimary = active?.isPrimary ?: false,
                )
            }
            .launchIn(viewModelScope)
    }

    /** Store an address, which is also what points the app at it. */
    fun saveServer(slot: ServerSlot, config: ServerConfig) {
        val current = _state.value.settings

        // The stored copy is the one that matters, but the screen would flicker
        // back to the old text for a frame while the write lands - so the state
        // is moved first and the write follows.
        _state.value = _state.value.copy(
            settings = if (slot == ServerSlot.PRIMARY) {
                current.copy(primary = config)
            } else {
                current.copy(secondary = config)
            },
        )

        viewModelScope.launch {
            when (slot) {
                ServerSlot.PRIMARY -> repository.setPrimary(config)
                ServerSlot.SECONDARY -> repository.setSecondary(config)
            }
        }
    }

    /**
     * Forget how the last test went.
     *
     * Called as soon as an address is edited: "reachable" said about the
     * address that was there a moment ago is worse than saying nothing.
     */
    fun clearTest(slot: ServerSlot) {
        if (currentTest(slot) != TestState.Idle) setTest(slot, TestState.Idle)
    }

    private fun currentTest(slot: ServerSlot): TestState = when (slot) {
        ServerSlot.PRIMARY -> _state.value.primaryTest
        ServerSlot.SECONDARY -> _state.value.secondaryTest
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch { repository.setConnectionMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setLiveUpdates(enabled: Boolean) {
        viewModelScope.launch { repository.setLiveUpdates(enabled) }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { repository.setPollInterval(seconds) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { repository.setKeepScreenOn(enabled) }
    }

    fun setShowArtwork(enabled: Boolean) {
        viewModelScope.launch { repository.setShowArtwork(enabled) }
    }

    /**
     * Try an address as it stands on the screen.
     *
     * The one being typed, not the one that is stored: testing before saving is
     * the whole point of the button.
     */
    fun testConnection(slot: ServerSlot, server: ServerConfig) {
        val context = getApplication<Application>()
        if (server.host.isBlank()) {
            setTest(slot, TestState.Failed(context.getString(R.string.settings_invalid_host)))
            return
        }
        if (server.port !in ServerConfig.PORT_RANGE) {
            setTest(slot, TestState.Failed(context.getString(R.string.settings_invalid_port)))
            return
        }

        viewModelScope.launch {
            setTest(slot, TestState.Running)
            try {
                // Tested as if it were switched on, so a reader can check an
                // address before committing to it.
                val result = container.connectionTester.test(server.copy(enabled = true))
                setTest(
                    slot,
                    when (result) {
                        is ConnectionTestResult.Reachable -> TestState.Ok(
                            version = result.hello.version.ifBlank { "?" },
                            control = result.hello.control,
                            tokenAccepted = result.tokenAccepted,
                        )

                        is ConnectionTestResult.Refused ->
                            TestState.Failed(result.failure.toUserMessage(context))
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                setTest(slot, TestState.Failed(failure.toUserMessage(context)))
            }
        }
    }

    private fun setTest(slot: ServerSlot, test: TestState) {
        _state.value = when (slot) {
            ServerSlot.PRIMARY -> _state.value.copy(primaryTest = test)
            ServerSlot.SECONDARY -> _state.value.copy(secondaryTest = test)
        }
    }

    /**
     * What a person types is not always what a URL needs: a pasted
     * `http://192.168.1.10:8099/` has to end up as a host on its own.
     */
    fun sanitizeHost(raw: String): String {
        val withoutScheme = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .trim()

        // A single colon followed by digits is a port, and belongs in the port
        // field - an IPv6 literal has several and keeps all of them.
        if (withoutScheme.count { it == ':' } == 1) {
            val port = withoutScheme.substringAfter(':')
            if (port.isNotEmpty() && port.all { it.isDigit() }) {
                return withoutScheme.substringBefore(':')
            }
        }
        return withoutScheme
    }

    /** A port pasted along with the host, so `10.0.0.5:8099` fills both fields. */
    fun portFromHost(raw: String): Int? {
        val hostPart = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        // An IPv6 literal is full of colons and carries no port here.
        if (hostPart.count { it == ':' } != 1) return null
        return hostPart.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 }
    }

    /** Whether a pasted address said https, so the switch can follow it. */
    fun schemeFromHost(raw: String): Boolean? = when {
        raw.trim().startsWith("https://") -> true
        raw.trim().startsWith("http://") -> false
        else -> null
    }
}
