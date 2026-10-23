package com.jamal2367.tinyppimobile.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.ui.navigation.TopLevelDestination
import com.jamal2367.tinyppimobile.ui.settings.ServerSlot
import com.jamal2367.tinyppimobile.ui.settings.SettingsViewModel
import com.jamal2367.tinyppimobile.ui.settings.TestState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The settings screen's view model, against the app's own store and a made-up box. */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    @Before fun start() {
        Robo.clearSettings(app)
        server.start()
    }

    @After fun stop() = server.close()

    private fun box() = ServerConfig(enabled = false, host = server.hostName, port = server.port)

    @Test
    fun `settings are written and read back`() {
        val vm = SettingsViewModel(app)
        Robo.until { vm.state.value.loaded }

        vm.saveServer(ServerSlot.PRIMARY, ServerConfig(enabled = true, host = "a", port = 8099))
        vm.saveServer(ServerSlot.SECONDARY, ServerConfig(enabled = true, host = "b", port = 443))
        assertEquals("a", vm.state.value.settings.primary.host)
        vm.setConnectionMode(ConnectionMode.PRIMARY_ONLY)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setLiveUpdates(false)
        vm.setPollInterval(5)
        vm.setKeepScreenOn(true)
        vm.setVolumeKeys(false)
        vm.setShowArtwork(false)
        vm.setShowChapters(false)
        vm.setColourfulRatings(false)
        vm.setAdaptiveColor(false)
        vm.setAdaptiveColorIntensity(0.7f)
        vm.setTabHidden(TopLevelDestination.entries.first { it.canHide }, true)
        vm.setTabHidden(TopLevelDestination.entries.first { !it.canHide }, true)
        Robo.until {
            val s = vm.state.value.settings
            s.secondary.host == "b" && s.connectionMode == ConnectionMode.PRIMARY_ONLY && s.themeMode == ThemeMode.DARK &&
                !s.liveUpdates && s.pollIntervalSeconds == 5 && s.keepScreenOn && !s.volumeKeys && !s.showArtwork && !s.showChapters && !s.colourfulRatings &&
                !s.adaptiveColor && s.adaptiveColorIntensity == 0.7f && s.hiddenTabs.size == 1
        }

        Robo.settings(app) { setCardHidden("live", "x", true) }
        Robo.until { vm.state.value.settings.cardsArranged }
        vm.resetCards()
        Robo.until { !vm.state.value.settings.cardsArranged }

        app.container.router.update(vm.state.value.settings)
        app.container.router.markReachable(ServerConfig(enabled = true, host = "a", port = 8099))
        Robo.until { vm.state.value.activeServerLabel == "a:8099" }
        assertTrue(vm.state.value.activeIsPrimary)
        app.container.router.markUnreachable()
        Robo.until { vm.state.value.activeServerLabel == null }
        assertFalse(vm.state.value.activeIsPrimary)
    }

    @Test
    fun `a test says what the box answered`() {
        val vm = SettingsViewModel(app)

        vm.testConnection(ServerSlot.PRIMARY, box().copy(host = " "))
        assertEquals(TestState.Failed(app.getString(R.string.settings_invalid_host)), vm.state.value.primaryTest)
        vm.testConnection(ServerSlot.SECONDARY, box().copy(port = 80))
        assertEquals(TestState.Failed(app.getString(R.string.settings_invalid_port)), vm.state.value.secondaryTest)
        vm.clearTest(ServerSlot.SECONDARY)
        vm.testConnection(ServerSlot.SECONDARY, box().copy(port = 70000))
        assertEquals(TestState.Failed(app.getString(R.string.settings_invalid_port)), vm.state.value.secondaryTest)

        server.enqueue(MockResponse.Builder().body("""{"version":"2.15.3","control":true}""").build())
        vm.testConnection(ServerSlot.PRIMARY, box())
        Robo.until { vm.state.value.primaryTest is TestState.Ok }
        assertEquals(TestState.Ok("2.15.3", control = true, tokenAccepted = true), vm.state.value.primaryTest)

        server.enqueue(MockResponse.Builder().body("{}").build())
        vm.testConnection(ServerSlot.SECONDARY, box())
        Robo.until { vm.state.value.secondaryTest is TestState.Ok }
        assertEquals(TestState.Ok("?", control = false, tokenAccepted = true), vm.state.value.secondaryTest)

        server.enqueue(MockResponse.Builder().body("""{"version":"1","auth_read":true}""").build())
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        vm.testConnection(ServerSlot.SECONDARY, box())
        Robo.until { (vm.state.value.secondaryTest as? TestState.Ok)?.version == "1" }
        assertFalse((vm.state.value.secondaryTest as TestState.Ok).tokenAccepted)

        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        vm.testConnection(ServerSlot.PRIMARY, box())
        Robo.until { vm.state.value.primaryTest is TestState.Failed }
        assertEquals(TestState.Failed(app.getString(R.string.api_error_unauthorized)), vm.state.value.primaryTest)

        // A host no address can be made of fails before anything is sent.
        vm.testConnection(ServerSlot.PRIMARY, box().copy(host = "bad host"))
        Robo.until { (vm.state.value.primaryTest as? TestState.Failed)?.message?.isNotBlank() == true && vm.state.value.primaryTest != TestState.Failed(app.getString(R.string.api_error_unauthorized)) }

        vm.clearTest(ServerSlot.PRIMARY)
        vm.clearTest(ServerSlot.PRIMARY)
        vm.clearTest(ServerSlot.SECONDARY)
        assertEquals(TestState.Idle, vm.state.value.primaryTest)
        assertEquals(TestState.Idle, vm.state.value.secondaryTest)

        // A test still running when the screen goes is dropped.
        server.enqueue(MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("{}").build())
        vm.testConnection(ServerSlot.PRIMARY, box())
        Robo.until { vm.state.value.primaryTest == TestState.Running }
        vm.viewModelScope.cancel()
        // The answer still arrives, and is dropped rather than shown.
        val settle = System.currentTimeMillis() + 2_000
        Robo.until(5_000) { System.currentTimeMillis() > settle }
        assertEquals(TestState.Running, vm.state.value.primaryTest)
    }

    @Test
    fun `an address typed with its scheme and port is taken apart`() {
        val vm = SettingsViewModel(app)
        assertEquals("box", vm.sanitizeHost(" https://box:8099/path "))
        assertEquals("box", vm.sanitizeHost("http://box?x=1"))
        assertEquals("box:", vm.sanitizeHost("box:"))
        assertEquals("box:ab", vm.sanitizeHost("box:ab"))
        assertEquals("fe80::1", vm.sanitizeHost("fe80::1"))
        assertEquals(8099, vm.portFromHost("http://box:8099/"))
        assertNull(vm.portFromHost("box"))
        assertNull(vm.portFromHost("fe80::1"))
        assertNull(vm.portFromHost("box:x"))
        assertNull(vm.portFromHost("box:0"))
        assertNull(vm.portFromHost("box:70000"))
        assertEquals(true, vm.schemeFromHost(" https://box"))
        assertEquals(false, vm.schemeFromHost("http://box"))
        assertNull(vm.schemeFromHost("box"))
    }
}
