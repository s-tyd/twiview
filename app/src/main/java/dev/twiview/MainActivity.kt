package dev.twiview

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.twiview.ui.AppNav
import dev.twiview.ui.theme.TwiviewTheme

class MainActivity : AppCompatActivity() {
    var inPip by mutableStateOf(false)
        private set

    var pipEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            setPictureInPictureParams(pipParams())
        }

    private val deepLink = mutableStateOf<String?>(null)

    interface PipControls {
        val playing: Boolean
        val seekable: Boolean
        fun togglePlay()
        fun seekBy(ms: Long)
    }

    var pipControls: PipControls? = null
        set(value) {
            field = value
            if (pipEnabled || inPip) setPictureInPictureParams(pipParams())
        }

    var pipExitedAt = 0L
        private set

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val p = pipControls ?: return
            when (intent?.getStringExtra(PIP_OP)) {
                "toggle" -> p.togglePlay()
                "back" -> p.seekBy(-10_000)
                "fwd" -> p.seekBy(10_000)
            }
            setPictureInPictureParams(pipParams())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (savedInstanceState == null) deepLink.value = routeFor(intent?.data)
        setContent {
            TwiviewTheme { AppNav(deepLink) }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent.data)?.let { deepLink.value = it }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode) pipExitedAt = SystemClock.uptimeMillis()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
    }

    fun enterPip() {
        enterPictureInPictureMode(pipParams())
    }

    private fun pipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(pipActions())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(pipEnabled)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

    private fun pipActions(): List<RemoteAction> {
        val p = pipControls ?: return emptyList()
        return buildList {
            if (p.seekable) add(pipAction("back", android.R.drawable.ic_media_rew, R.string.rewind_10))
            if (p.playing) add(pipAction("toggle", android.R.drawable.ic_media_pause, R.string.pause))
            else add(pipAction("toggle", android.R.drawable.ic_media_play, R.string.play))
            if (p.seekable) add(pipAction("fwd", android.R.drawable.ic_media_ff, R.string.forward_10))
        }
    }

    private fun pipAction(op: String, icon: Int, label: Int): RemoteAction {
        val intent = Intent(PIP_ACTION).setPackage(packageName).putExtra(PIP_OP, op)
        val pending = PendingIntent.getBroadcast(this, op.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(this, icon), getString(label), getString(label), pending)
    }

    fun setSystemBarsHidden(hidden: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val PIP_ACTION = "dev.twiview.PIP"
        private const val PIP_OP = "op"
        private val reserved = setOf(
            "directory", "settings", "videos", "search", "downloads", "jobs", "p", "subscriptions",
            "inventory", "wallet", "drops", "friends", "messages", "turbo", "login", "signup", "activate",
        )
        private val loginRe = Regex("^[a-zA-Z0-9_]{2,25}$")

        fun routeFor(uri: Uri?): String? {
            val seg = uri?.pathSegments ?: return null
            if (uri.host == "clips.twitch.tv") return seg.firstOrNull()?.let { "clip/$it" }
            return routeForPath(seg)
        }

        fun routeForPath(seg: List<String>): String? {
            if (seg.size >= 2 && seg[0] == "videos") return seg[1].takeIf { it.all(Char::isDigit) }?.let { "vod/$it" }
            val first = seg.firstOrNull() ?: return null
            if (first.lowercase() in reserved || !loginRe.matches(first)) return null
            if (seg.size >= 2 && seg[1] == "videos") return "channel/${first.lowercase()}"
            if (seg.size >= 3 && seg[1] == "clip") return "clip/${seg[2]}"
            return "player/${first.lowercase()}"
        }
    }
}
