package com.jamal2367.tinyppimobile.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.prefs.ChartRange
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.demo.DemoServer
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.ui.history.HistoryViewModel
import java.net.ServerSocket
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The history screen's view model, against the demo box and against nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryViewModelTest {

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()

    @Before fun reset() {
        Robo.clearSettings(app)
    }

    @Test
    fun `the history follows the playing title and the chosen range`() {
        DemoServer.start(app)
        Robo.settings(app) { setPrimary(ServerConfig(enabled = true, host = "127.0.0.1", port = 8099)) }
        val vm = HistoryViewModel(app)
        Robo.until { vm.state.value.history != null && vm.state.value.snapshot != null }
        assertTrue(vm.state.value.isConfigured)
        assertNull(vm.state.value.error)

        vm.setRange(ChartRange.ONE_MINUTE)
        Robo.until { vm.state.value.range == ChartRange.ONE_MINUTE }

        // Asked again while the first answer is still on its way: one fetch.
        vm.refresh()
        vm.refresh()
        Robo.until { !vm.state.value.loading }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `a box that does not answer is said, and a fetch is dropped with the screen`() {
        val port = ServerSocket(0).use { it.localPort }
        Robo.settings(app) { setPrimary(ServerConfig(enabled = true, host = "127.0.0.1", port = port)) }
        val vm = HistoryViewModel(app)
        Robo.until { vm.state.value.isConfigured }
        vm.refresh()
        Robo.until { vm.state.value.error != null }
        assertNotNull(vm.state.value.error)

        // A box that holds the request open until the screen has gone.
        ServerSocket(0).use { silent ->
            Robo.settings(app) { setPrimary(ServerConfig(enabled = true, host = "127.0.0.1", port = silent.localPort)) }
            Robo.until { app.container.router.settings.primary.port == silent.localPort }
            vm.refresh()
            Robo.until { vm.state.value.loading }
            vm.viewModelScope.cancel()
            Robo.until(3_000) { true }
        }
        assertEquals(true, vm.state.value.loading)
    }
}
