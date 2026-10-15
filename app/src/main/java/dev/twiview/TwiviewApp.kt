package dev.twiview

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.twiview.data.ChatClient
import dev.twiview.data.DanmakuPrefs
import dev.twiview.data.LatencyMode
import dev.twiview.data.Prefs
import dev.twiview.data.TwitchApi
import dev.twiview.data.WatchHistory
import dev.twiview.data.WebSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TwiviewApp : Application(), SingletonImageLoader.Factory {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(60, TimeUnit.SECONDS)
            .build()
    }
    val web by lazy { WebSession(this, http) }
    val api by lazy { TwitchApi(http, webToken = { web.token() }, onWebTokenRejected = { web.clear() }) }
    val chat by lazy { ChatClient(http) }
    val prefs by lazy { Prefs(this) }
    val history by lazy { WatchHistory(this) }
    val danmaku by lazy { DanmakuPrefs(this) }
    val learnedLatency = HashMap<LatencyMode, Long>()

    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()
}

val Context.app: TwiviewApp get() = applicationContext as TwiviewApp
