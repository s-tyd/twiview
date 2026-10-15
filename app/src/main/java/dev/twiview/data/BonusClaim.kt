package dev.twiview.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
suspend fun claimBonusInWeb(context: Context, login: String, timeoutMs: Long = 30_000): Boolean = withContext(Dispatchers.Main) {
    val view = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        webViewClient = WebViewClient()
        loadUrl("https://www.twitch.tv/popout/${login.lowercase()}/chat")
    }
    try {
        withTimeoutOrNull(timeoutMs) {
            while (view.eval(CLICK_BONUS) != "\"clicked\"") delay(1_000)
            delay(3_000)
            true
        } ?: false
    } finally {
        view.stopLoading()
        view.destroy()
    }
}

private suspend fun WebView.eval(js: String): String? = suspendCoroutine { c -> evaluateJavascript(js) { c.resume(it) } }

private const val CLICK_BONUS = """
(function () {
  var icon = document.querySelector('.claimable-bonus__icon');
  var button = icon && icon.closest('button');
  if (!button) return 'waiting';
  button.click();
  return 'clicked';
})()
"""
