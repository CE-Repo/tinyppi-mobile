package com.jamal2367.tinyppimobile.util

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jamal2367.tinyppimobile.data.model.CastMember
import com.jamal2367.tinyppimobile.data.model.MarkTarget
import com.jamal2367.tinyppimobile.data.model.TitleDetails
import com.jamal2367.tinyppimobile.ui.live.DetailsDialog
import com.jamal2367.tinyppimobile.ui.live.DetailsUiState
import com.jamal2367.tinyppimobile.ui.theme.TinyPpiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A web page opens as a Custom Tab over the app, titled and shareable. */
@RunWith(RobolectricTestRunner::class)
class InAppBrowserTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val page = "https://www.themoviedb.org/person/2037"

    private fun assertCustomTab(started: Intent) {
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(page, started.dataString)
        assertTrue(started.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.SHOW_PAGE_TITLE, started.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, -1))
        assertEquals(CustomTabsIntent.SHARE_STATE_ON, started.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
    }

    @Test
    fun `a page opens as a custom tab with its title and a share key`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        InAppBrowser(activity).openUri(page)
        assertCustomTab(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a tap on a cast card on the details page opens a custom tab, not the plain browser`() {
        val film = MarkTarget(MarkTarget.Kind.MOVIE, 2, "Oppenheimer")
        val details = TitleDetails(cast = listOf(CastMember("Cillian Murphy", "J. Robert Oppenheimer", tmdb = 2037)))
        rule.setContent {
            TinyPpiTheme {
                CompositionLocalProvider(LocalUriHandler provides InAppBrowser(rule.activity)) {
                    DetailsDialog(DetailsUiState(film, details), null, false, null) {}
                }
            }
        }
        rule.onNodeWithText("Cillian Murphy").performClick()
        rule.waitForIdle()
        // The details' own window would have opened it as a bare link.
        assertCustomTab(shadowOf(rule.activity).nextStartedActivity)
    }
}
