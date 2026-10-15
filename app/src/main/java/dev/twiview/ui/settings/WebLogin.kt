package dev.twiview.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.WebSession
import dev.twiview.ui.theme.C
import dev.twiview.ui.theme.TwiviewTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WebLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TwiviewTheme {
                Surface(color = C.Bg, contentColor = C.Text) {
                    WebLoginContent(onDone = { finish() })
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLoginContent(onDone: () -> Unit) {
    val web = LocalContext.current.app.web
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            val t = WebSession.tokenFromCookies(CookieManager.getInstance().getCookie("https://www.twitch.tv"))
            if (t != null) {
                status = R.string.login_verifying
                if (web.save(t) != null) {
                    val clientId = web.clientId.value ?: return@LaunchedEffect onDone()
                    status = R.string.login_linking_follows
                    webView?.loadUrl(WebSession.appAuthorizeUrl(clientId))
                    return@LaunchedEffect
                }
                status = R.string.login_token_failed
                CookieManager.getInstance().removeAllCookies(null)
            }
        }
    }
    val onRedirect: (String) -> Boolean = { url ->
        if (WebSession.isAppRedirect(url)) {
            scope.launch {
                WebSession.appTokenFromRedirect(url)?.let { web.setAppToken(it) }
                onDone()
            }
            true
        } else {
            false
        }
    }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            Text(stringResource(R.string.login_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            status?.let { Text(stringResource(it), color = C.Muted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.width(12.dp))
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    webView = this
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            onRedirect(request.url.toString())

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (onRedirect(url)) view.stopLoading()
                        }
                    }
                    loadUrl("https://www.twitch.tv/login")
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
