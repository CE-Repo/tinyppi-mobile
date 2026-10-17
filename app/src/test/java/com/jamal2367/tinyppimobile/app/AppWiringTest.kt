package com.jamal2367.tinyppimobile.app

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.remote.ApiFailure
import com.jamal2367.tinyppimobile.util.LocalNetworkAccess
import com.jamal2367.tinyppimobile.util.toUserMessage
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** The application object, its graph, and the words failures are put into. */
@RunWith(RobolectricTestRunner::class)
class AppWiringTest {

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private val sdk = Build.VERSION.SDK_INT

    @After fun restoreSdk() = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    @Test
    fun `the graph is built once and wired to the stored settings`() {
        val container = app.container
        assertSame(container.imageLoader, app.newImageLoader(app))
        assertNotNull(container.repository)
        assertNotNull(container.connectionTester)
        assertNotNull(container.updateChecker)
        assertNotNull(container.liveState)
        container.reconnect()
        container.setVisible(false)
        container.setVisible(true)

        runBlocking { container.settingsRepository.setPrimary(ServerConfig(enabled = true, host = "box", port = 8099)) }
        val deadline = System.currentTimeMillis() + 5_000
        while (container.router.settings.primary.host != "box" && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals("box", container.router.settings.primary.host)
    }

    private fun message(failure: Throwable) = failure.toUserMessage(app)

    @Test
    fun `every failure is put into words`() {
        assertEquals(app.getString(R.string.error_not_configured), message(ApiFailure.NotConfigured))
        assertEquals(app.getString(R.string.error_unreachable), message(ApiFailure.Unreachable(null, false, null)))
        assertEquals(app.getString(R.string.error_unreachable), message(ApiFailure.Unreachable(" ", false, null)))
        assertEquals(app.getString(R.string.api_error_timeout, "box"), message(ApiFailure.Unreachable("box", true, null)))
        assertEquals(app.getString(R.string.api_error_network, "box"), message(ApiFailure.Unreachable("box", false, null)))
        assertEquals(app.getString(R.string.api_error_unauthorized), message(ApiFailure.Api(401, null)))
        assertEquals(app.getString(R.string.api_error_control_disabled), message(ApiFailure.Api(403, null)))
        assertEquals(app.getString(R.string.api_error_busy), message(ApiFailure.Api(503, null)))
        assertEquals(app.getString(R.string.api_error_not_found), message(ApiFailure.Api(404, null)))
        assertEquals(app.getString(R.string.api_error_bad_request), message(ApiFailure.Api(400, null)))
        assertEquals("bad mode", message(ApiFailure.Api(400, "bad mode")))
        assertEquals(app.getString(R.string.error_title), message(ApiFailure.Api(500, null)))
        assertEquals("broken", message(ApiFailure.Api(500, "broken")))
        assertEquals(app.getString(R.string.api_error_parse), message(ApiFailure.Malformed(null)))
        assertEquals("plain", message(IOException("plain")))
        assertEquals(app.getString(R.string.error_title), message(IOException()))
    }

    @Test
    fun `the local network permission is asked about only where it exists`() {
        assertFalse(LocalNetworkAccess.isRequired)
        assertTrue(LocalNetworkAccess.isGranted(app))

        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 37)
        assertTrue(LocalNetworkAccess.isRequired)
        assertFalse(LocalNetworkAccess.isGranted(app))
        assertEquals(app.getString(R.string.error_local_network_denied), message(ApiFailure.Unreachable("box", false, null)))

        shadowOf(app as Application).grantPermissions(LocalNetworkAccess.PERMISSION)
        assertTrue(LocalNetworkAccess.isGranted(app))
    }
}
