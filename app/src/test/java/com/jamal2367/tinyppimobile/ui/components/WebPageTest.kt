package com.jamal2367.tinyppimobile.ui.components

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.testing.Screens.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** A web page - a person's on TMDb - opened in the app's own page rather than the browser. */
@RunWith(RobolectricTestRunner::class)
class WebPageTest {

    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val page = "https://www.themoviedb.org/person/2037"

    private fun dialog() = ShadowDialog.getShownDialogs().last { it.isShowing } as ComponentDialog

    private fun webView(): WebView {
        fun find(view: View): WebView? = view as? WebView
            ?: (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
        return find(dialog().window!!.decorView)!!
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun nextStarted(): Intent? = shadowOf(context as Application).nextStartedActivity

    @Test
    fun `a link opens the page over the app, and closing it takes it down`() {
        val browser = InAppBrowser()
        rule.show { InAppBrowserHost(browser) }
        assertTrue(ShadowDialog.getShownDialogs().none { it.isShowing })
        rule.runOnIdle { browser.openUri(page) }
        rule.waitForIdle()
        assertEquals(page, browser.page)
        assertEquals(page, shadowOf(webView()).lastLoadedUrl)
        // Until the page has a title, its address stands in for it.
        rule.onNodeWithText(page).assertExists()
        rule.onNodeWithText("www.themoviedb.org").assertExists()
        // Another link while it is open puts that page up in its place.
        val other = "https://www.themoviedb.org/person/5081"
        rule.runOnIdle { browser.openUri(other) }
        rule.waitForIdle()
        assertEquals(other, shadowOf(webView()).lastLoadedUrl)
        rule.onNodeWithText(other).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.details_close)).performClick()
        rule.waitForIdle()
        assertNull(browser.page)
    }

    @Test
    fun `the bar follows the page as it loads and moves`() {
        rule.show { WebPage(page) {} }
        rule.waitForIdle()
        val view = webView()
        val chrome = shadowOf(view).webChromeClient
        val client = shadowOf(view).webViewClient
        rule.runOnIdle {
            chrome.onProgressChanged(view, 50)
            chrome.onReceivedTitle(view, "Cillian Murphy — The Movie Database (TMDB)")
        }
        rule.onNodeWithText("Cillian Murphy — The Movie Database (TMDB)").assertExists()
        rule.runOnIdle {
            chrome.onProgressChanged(view, 100)
            chrome.onReceivedTitle(view, null)
            client.onPageStarted(view, "about:blank", null)
        }
        // A title gone and an address without a host leave the address alone.
        rule.onNodeWithText("about:blank").assertExists()
    }

    @Test
    fun `web links stay in the page and anything else goes to the phone`() {
        rule.show { WebPage(page) {} }
        rule.waitForIdle()
        val view = webView()
        val client = shadowOf(view).webViewClient
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://www.themoviedb.org/movie/872585")))
        assertFalse(client.shouldOverrideUrlLoading(view, request("http://example.com/")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("mailto:someone@example.com")))
        assertEquals("mailto:someone@example.com", nextStarted()?.dataString)
        // And the page itself into the browser, from the bar.
        rule.onNodeWithContentDescription(context.getString(R.string.web_open_in_browser)).performClick()
        assertEquals(page, nextStarted()?.dataString)
    }

    @Test
    fun `back goes back through the pages followed, and closes it from the first`() {
        var closed = 0
        rule.show { WebPage(page) { closed++ } }
        rule.waitForIdle()
        val view = webView()
        shadowOf(view).setCanGoBack(true)
        rule.runOnIdle { dialog().onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, shadowOf(view).goBackInvocations)
        assertEquals(0, closed)
        shadowOf(view).setCanGoBack(false)
        rule.runOnIdle { dialog().onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, closed)
    }
}
