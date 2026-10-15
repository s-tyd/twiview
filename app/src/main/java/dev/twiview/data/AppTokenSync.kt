package dev.twiview.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("SetJavaScriptEnabled")
suspend fun silentAppAuthorize(context: Context, clientId: String, timeoutMs: Long = 15_000): String? = withContext(Dispatchers.Main) {
    val result = CompletableDeferred<String?>()
    val view = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        webViewClient = object : WebViewClient() {
            private fun check(url: String): Boolean {
                if (!WebSession.isAppRedirect(url)) return false
                result.complete(WebSession.appTokenFromRedirect(url))
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                check(request.url.toString())

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (check(url)) view.stopLoading()
            }
        }
        loadUrl(WebSession.appAuthorizeUrl(clientId))
    }
    try {
        withTimeoutOrNull(timeoutMs) { result.await() }
    } finally {
        view.stopLoading()
        view.destroy()
    }
}
