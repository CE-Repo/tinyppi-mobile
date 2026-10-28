package com.jamal2367.tinyppimobile.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.jamal2367.tinyppimobile.R
import com.jamal2367.tinyppimobile.ui.live.DisableDialogWindowAnimations

/**
 * Opens web pages - a person's on TMDb - in a page of the app's own rather
 * than in the browser: [openUri] puts the page up, [close] takes it down, and
 * [InAppBrowserHost] draws it over whatever is showing.
 */
class InAppBrowser : UriHandler {
    var page: String? by mutableStateOf(null)
        private set

    override fun openUri(uri: String) {
        page = uri
    }

    fun close() {
        page = null
    }
}

/** The page [browser] has open, if any. */
@Composable
fun InAppBrowserHost(browser: InAppBrowser) {
    val page = browser.page ?: return
    WebPage(page, onClose = browser::close)
}

/**
 * One web page over the app, edge to edge: a bar with a key to close it, the
 * page's title over its address and a key to open it in the browser after
 * all; a line of how far it has loaded; and the page. Back goes back through
 * the pages followed from it, and closes it from the first.
 */
@Composable
internal fun WebPage(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var title by remember(url) { mutableStateOf("") }
    var address by remember(url) { mutableStateOf(url) }
    var progress by remember(url) { mutableIntStateOf(0) }
    val view = remember(url) {
        pageView(context, onAddress = { address = it }, onTitle = { title = it }, onProgress = { progress = it })
            .also { it.loadUrl(url) }
    }
    Dialog(
        onDismissRequest = { if (view.canGoBack()) view.goBack() else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DisableDialogWindowAnimations()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.details_close))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = title.ifEmpty { address },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = address.toUri().host.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { openOutside(context, address) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.web_open_in_browser),
                        )
                    }
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                AndroidView(
                    factory = { view },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** A web view that keeps web links in itself and hands anything else to the phone. */
@SuppressLint("SetJavaScriptEnabled")
internal fun pageView(
    context: Context,
    onAddress: (String) -> Unit,
    onTitle: (String) -> Unit,
    onProgress: (Int) -> Unit,
) = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val web = request.url.scheme == "http" || request.url.scheme == "https"
            if (!web) openOutside(view.context, request.url.toString())
            return !web
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onAddress(url)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) = onTitle(title.orEmpty())

        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
}

/** The page in the phone's browser, or whatever app takes the link; nothing where none does. */
private fun openOutside(context: Context, address: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
