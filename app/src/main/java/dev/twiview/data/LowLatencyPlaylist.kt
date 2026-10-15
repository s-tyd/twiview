package dev.twiview.data

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.io.InputStream

@UnstableApi
class LowLatencyPlaylistParserFactory : HlsPlaylistParserFactory {
    private val base = DefaultHlsPlaylistParserFactory()

    @Volatile private var adPlaying = false
    @Volatile private var adEndedAt = 0L

    fun inAdBreak(now: Long = System.currentTimeMillis()): Boolean = adPlaying || now - adEndedAt < AD_GRACE_MS

    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> =
        wrap(base.createPlaylistParser())

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?,
    ): ParsingLoadable.Parser<HlsPlaylist> = wrap(base.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))

    private fun wrap(parser: ParsingLoadable.Parser<HlsPlaylist>) =
        ParsingLoadable.Parser<HlsPlaylist> { uri: Uri, input: InputStream ->
            val text = input.readBytes().decodeToString()
            if ("#EXTINF" in text) {
                val ad = isAdPlaylist(text)
                if (adPlaying && !ad) adEndedAt = System.currentTimeMillis()
                adPlaying = ad
            }
            parser.parse(uri, expandPrefetch(text).byteInputStream())
        }

    private companion object {
        const val AD_GRACE_MS = 15_000L
    }
}

internal fun isAdPlaylist(playlist: String): Boolean {
    if ("twitch-stitched-ad" !in playlist) return false
    val lastTitle = playlist.lines().lastOrNull { it.startsWith("#EXTINF:") }?.substringAfter(',') ?: return false
    return lastTitle != "live"
}

private const val PREFETCH = "#EXT-X-TWITCH-PREFETCH:"
private const val TARGET_DURATION = "#EXT-X-TARGETDURATION:"

internal const val LOW_LATENCY_TARGET_DURATION = 2

private const val STUCK_TARGET_DURATION_COEFFICIENT = 10.0

@UnstableApi
fun lowLatencyPlaylistTrackerFactory() = HlsPlaylistTracker.Factory { dataSourceFactory, loadErrorHandlingPolicy, parserFactory, cmcd ->
    DefaultHlsPlaylistTracker(dataSourceFactory, loadErrorHandlingPolicy, parserFactory, cmcd, STUCK_TARGET_DURATION_COEFFICIENT)
}

internal const val PREFETCH_MARK = "twiview-prefetch"

internal fun expandPrefetch(playlist: String): String {
    if (PREFETCH !in playlist) return playlist
    val lines = playlist.lines()
    val duration = lines.lastOrNull { it.startsWith("#EXTINF:") }?.substringBefore(',') ?: "#EXTINF:2.000"
    return buildString {
        for (line in lines) {
            if (line.startsWith(PREFETCH)) {
                append(duration).append(",live\n")
                append(line.removePrefix(PREFETCH)).append('#').append(PREFETCH_MARK).append('\n')
            } else if (line.startsWith(TARGET_DURATION)) {
                append(TARGET_DURATION).append(LOW_LATENCY_TARGET_DURATION).append('\n')
            } else {
                append(line).append('\n')
            }
        }
    }
}

@UnstableApi
class PrefetchAwareBandwidthMeter(private val base: DefaultBandwidthMeter) : BandwidthMeter by base {
    private val listener = object : TransferListener {
        private fun skip(spec: DataSpec) = spec.uri.fragment == PREFETCH_MARK
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (!skip(dataSpec)) base.onTransferInitializing(source, dataSpec, isNetwork)
        }
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (!skip(dataSpec)) base.onTransferStart(source, dataSpec, isNetwork)
        }
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (!skip(dataSpec)) base.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred)
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (!skip(dataSpec)) base.onTransferEnd(source, dataSpec, isNetwork)
        }
    }

    override fun getTransferListener(): TransferListener = listener
}
