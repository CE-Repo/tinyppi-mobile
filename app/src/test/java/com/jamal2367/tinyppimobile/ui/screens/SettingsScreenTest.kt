package com.jamal2367.tinyppimobile.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.TinyPpiApplication
import com.jamal2367.tinyppimobile.data.prefs.ConnectionMode
import com.jamal2367.tinyppimobile.data.prefs.ServerConfig
import com.jamal2367.tinyppimobile.data.prefs.ThemeMode
import com.jamal2367.tinyppimobile.testing.Robo
import com.jamal2367.tinyppimobile.testing.Screens.show
import com.jamal2367.tinyppimobile.testing.Screens.until
import com.jamal2367.tinyppimobile.ui.settings.SettingsScreen
import com.jamal2367.tinyppimobile.ui.settings.SettingsViewModel
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings screen, against the app's own store. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h6000dp")
class SettingsScreenTest {

    @get:Rule val rule = createComposeRule()

    private val app: TinyPpiApplication = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)
    private val server = MockWebServer()

    @Before fun start() {
        Robo.clearSettings(app)
        server.start()
    }

    @After fun stop() = server.close()

    private fun settings() = app.container.router.settings


    private fun until(condition: () -> Boolean) = rule.until(condition = condition)

    @Test
    fun `the servers are typed in, tested and saved`() {
        val vm = SettingsViewModel(app)
        rule.show { SettingsScreen(vm) }
        until { vm.state.value.loaded }

        // The local box: an address pasted with its scheme and port.
        val host = rule.onAllNodes(hasText(str(R.string.settings_host))).onFirst()
        host.performTextInput("https://box.local:9000/")
        rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst().performTextClearance()
        rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst().performTextInput("8x099")
        rule.onAllNodes(hasText(str(R.string.settings_token))).onFirst().performTextInput("ab12")
        rule.onAllNodesWithContentDescription(str(R.string.settings_token_show)).onFirst().performClick()
        rule.onAllNodesWithContentDescription(str(R.string.settings_token_hide)).onFirst().performClick()
        rule.onAllNodesWithText(str(R.string.settings_https)).onFirst().performClick()
        rule.onAllNodesWithText(str(R.string.settings_unsaved)).onFirst().assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.settings_save)).onFirst().performClick()
        until { settings().primary.host == "box.local" }
        assertEquals(ServerConfig(enabled = true, useHttps = false, host = "box.local", port = 8099, token = "AB12"), settings().primary)

        // A test against a box that answers, one with a refused token and no control, and one that fails.
        rule.onAllNodes(hasText(str(R.string.settings_host))).onFirst().performTextClearance()
        rule.onAllNodes(hasText(str(R.string.settings_host))).onFirst().performTextInput("${server.hostName}:${server.port}")
        server.enqueue(MockResponse.Builder().body("""{"version":"2.15.3","control":true}""").build())
        rule.onAllNodesWithText(str(R.string.settings_test)).onFirst().performClick()
        until { rule.onAllNodesWithText(str(R.string.settings_test_ok, "2.15.3")).fetchSemanticsNodes().isNotEmpty() }
        server.enqueue(MockResponse.Builder().body("""{"version":"1","auth_read":true}""").build())
        server.enqueue(MockResponse.Builder().code(401).build())
        rule.onAllNodesWithText(str(R.string.settings_test)).onFirst().performClick()
        until { rule.onAllNodesWithText(str(R.string.settings_test_token_bad)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.settings_test_no_control)).assertIsDisplayed()
        rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst().performTextClearance()
        rule.onAllNodes(hasText(str(R.string.settings_port))).onFirst().performTextInput("80")
        rule.onAllNodesWithText(str(R.string.settings_test)).onFirst().performClick()
        rule.onNodeWithText(str(R.string.settings_invalid_port)).assertIsDisplayed()

        // The remote one, switched on and saved.
        rule.onAllNodesWithText(str(R.string.settings_server_enabled)).onLast().performClick()
        rule.onAllNodes(hasText(str(R.string.settings_host))).onLast().performTextInput("far.example.com")
        rule.onAllNodesWithText(str(R.string.settings_save)).onLast().performClick()
        until { settings().secondary.host == "far.example.com" && settings().secondary.enabled }
        rule.onAllNodesWithText(str(R.string.settings_test)).onLast().performClick()
        rule.onAllNodes(hasText(str(R.string.settings_host))).onLast().performTextClearance()
        rule.onAllNodesWithText(str(R.string.settings_test)).onLast().performClick()
        rule.onNodeWithText(str(R.string.settings_invalid_host)).assertIsDisplayed()

        // The box that answered is named, as the local or the remote one.
        app.container.router.markReachable(settings().primary)
        until { rule.onAllNodesWithText(str(R.string.server_active_primary) + " · box.local:8099").fetchSemanticsNodes().isNotEmpty() }
        app.container.router.markReachable(settings().secondary)
        until { rule.onAllNodesWithText(str(R.string.server_active_secondary) + " · far.example.com:8099").fetchSemanticsNodes().isNotEmpty() }
        app.container.router.markReachable(ServerConfig(enabled = true, host = "elsewhere", port = 1))
        until { rule.onAllNodesWithText(str(R.string.server_active_secondary) + " · elsewhere:1").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `every switch and choice is written`() {
        val vm = SettingsViewModel(app)
        rule.show { SettingsScreen(vm) }
        until { vm.state.value.loaded }

        rule.onNodeWithText(str(R.string.settings_mode_primary)).performClick()
        until { settings().connectionMode == ConnectionMode.PRIMARY_ONLY }
        rule.onNodeWithText(str(R.string.settings_mode_secondary)).performClick()
        rule.onNodeWithText(str(R.string.settings_mode_auto)).performClick()

        rule.onNodeWithText(str(R.string.settings_live_updates)).performClick()
        until { !settings().liveUpdates }
        rule.onNodeWithText(str(R.string.settings_seconds, 5)).performClick()
        until { settings().pollIntervalSeconds == 5 }
        rule.onNodeWithText(str(R.string.settings_keep_screen_on)).performClick()
        rule.onNodeWithText(str(R.string.settings_volume_keys)).performClick()
        until { settings().keepScreenOn && !settings().volumeKeys }

        rule.onNodeWithText(str(R.string.settings_theme_dark)).performClick()
        until { settings().themeMode == ThemeMode.DARK }
        rule.onNodeWithText(str(R.string.settings_theme_light)).performClick()
        rule.onNodeWithText(str(R.string.settings_theme_system)).performClick()

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
        until { settings().adaptiveColorIntensity == 0.6f }
        rule.onNodeWithText(str(R.string.settings_adaptive_color_reset)).performClick()
        until { settings().adaptiveColorIntensity == 1f }
        rule.onNodeWithText(str(R.string.settings_adaptive_color)).performClick()
        until { !settings().adaptiveColor }
        rule.onNodeWithText(str(R.string.settings_artwork)).performClick()
        until { !settings().showArtwork }
        rule.onNodeWithText(str(R.string.settings_chapters)).performClick()
        until { !settings().showChapters }

        rule.onNodeWithText(str(R.string.library_title)).performClick()
        until { "films" in settings().hiddenTabs }

        Robo.settings(app) { setCardHidden("live", "x", true) }
        until { settings().cardsArranged }
        rule.onNodeWithText(str(R.string.settings_cards_reset)).performClick()
        until { !settings().cardsArranged }

        rule.onNodeWithText(str(R.string.settings_about_version)).assertIsDisplayed()
    }

    @Test
    fun `the about card is there`() {
        rule.show { SettingsScreen(SettingsViewModel(app)) }
        rule.onNodeWithText(str(R.string.settings_about)).assertIsDisplayed()
    }
}
