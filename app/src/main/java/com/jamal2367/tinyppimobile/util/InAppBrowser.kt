package com.jamal2367.tinyppimobile.util

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * Opens a web page over the app instead of leaving it: a Custom Tab of the
 * phone's browser, with the page's title, a way to share it and a key back
 * to the app. A phone whose browser has no Custom Tabs opens it in the
 * browser as before.
 */
class InAppBrowser(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .build()
            .launchUrl(context, uri.toUri())
    }
}
