package com.jamal2367.tinyppimobile.util

import android.app.Activity
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A web page opens as a Custom Tab over the app, titled and shareable. */
@RunWith(RobolectricTestRunner::class)
class InAppBrowserTest {

    @Test
    fun `a page opens as a custom tab with its title and a share key`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        InAppBrowser(activity).openUri("https://www.themoviedb.org/person/2037")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.themoviedb.org/person/2037", started.dataString)
        assertTrue(started.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.SHOW_PAGE_TITLE, started.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, -1))
        assertEquals(CustomTabsIntent.SHARE_STATE_ON, started.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
    }
}
