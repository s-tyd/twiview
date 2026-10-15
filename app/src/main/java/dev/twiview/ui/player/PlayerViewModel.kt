package dev.twiview.ui.player

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dev.twiview.BackgroundPlayService
import dev.twiview.R
import dev.twiview.Strings
import dev.twiview.TwiviewApp
import dev.twiview.data.BackgroundPlay
import dev.twiview.data.ChannelPoints
import dev.twiview.data.ChannelStatus
import dev.twiview.data.ChatBuffer
import dev.twiview.data.ChatConnection
import dev.twiview.data.ChatEvent
import dev.twiview.data.ChatMessage
import dev.twiview.data.Clip
import dev.twiview.data.DanmakuSettings
import dev.twiview.data.HttpStatusException
import dev.twiview.data.LatencyMode
import dev.twiview.data.LowLatencyPlaylistParserFactory
import dev.twiview.data.PrefetchAwareBandwidthMeter
import dev.twiview.data.Settings
import dev.twiview.data.TwitchException
import dev.twiview.data.Video
import dev.twiview.data.VodComment
import dev.twiview.data.WatchHistory
import dev.twiview.data.claimBonusInWeb
import dev.twiview.data.lowLatencyPlaylistTrackerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

sealed interface PlayState {
    data object Loading : PlayState
    data object Playing : PlayState
    data object Offline : PlayState
    data class Error(val message: String) : PlayState
}

class Quality(val label: String, val height: Int, val group: Tracks.Group, val index: Int)

@OptIn(UnstableApi::class)
class PlayerViewModel(private val app: TwiviewApp, login: String, val vodId: String? = null, val clipSlug: String? = null, val multi: Boolean = false) : ViewModel() {
    val isClip = clipSlug != null
    val isVod = vodId != null || isClip
    var clip by mutableStateOf<Clip?>(null); private set
    val hasChat: Boolean get() = !isClip || clip?.videoId != null
    private var replayJob: Job? = null
    var login by mutableStateOf(login); private set
    var video by mutableStateOf<Video?>(null); private set
    private val bandwidth = PrefetchAwareBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(app))
    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(MC.USAGE_MEDIA).setContentType(MC.AUDIO_CONTENT_TYPE_MOVIE).build(),
            !multi,
        )
        .setHandleAudioBecomingNoisy(true)
        .setBandwidthMeter(bandwidth)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    1_000,
                    1_500,
                )
                .build(),
        )
        .setTrackSelector(
            DefaultTrackSelector(
                app,
                AdaptiveTrackSelection.Factory(
                    1_000,
                    AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                    1_000,
                    AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION,
                ),
            ),
        )
        .setLivePlaybackSpeedControl(
            DefaultLivePlaybackSpeedControl.Builder()
                .setTargetLiveOffsetIncrementOnRebufferMs(0)
                .build(),
        )
        .build()

    var status by mutableStateOf<ChannelStatus?>(null); private set
    var state by mutableStateOf<PlayState>(PlayState.Loading); private set
    var buffering by mutableStateOf(true); private set
    var isPlaying by mutableStateOf(false); private set
    var qualities by mutableStateOf<List<Quality>>(emptyList()); private set
    var selectedHeight by mutableStateOf(0); private set
    var currentHeight by mutableStateOf(0); private set
    var holdSpeed by mutableStateOf<Float?>(null); private set
    var inMutedSegment by mutableStateOf(false); private set
    var speed by mutableStateOf(1f); private set
    var audioOnly by mutableStateOf(false); private set
    var sleepMinutes by mutableStateOf<Int?>(null); private set
    var sleepEndsAt by mutableStateOf<Long?>(null); private set
    private var sleepJob: Job? = null
    var settingsTab by mutableStateOf(0)
    val settingsScroll = List(3) { ScrollState(0) }
    private var keptInBackground = false

    var settings by mutableStateOf(Settings()); private set
    var danmaku by mutableStateOf(DanmakuSettings()); private set

    var messages by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var chatConnected by mutableStateOf(false); private set
    var badges by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var canSend by mutableStateOf(false); private set
    var loggedInAs by mutableStateOf<String?>(null); private set
    var points by mutableStateOf<ChannelPoints?>(null); private set
    var bonusGained by mutableStateOf<Long?>(null); private set
    var bonusCount by mutableStateOf(0); private set
    private var startBalance: Long? = null
    val earnedHere: Long
        get() = points?.balance?.let { b -> startBalance?.let { b - it } } ?: 0
    private var me: ChatEvent.UserState? = null
    private var connection: ChatConnection? = null

    private val pending = ConcurrentLinkedQueue<ChatEvent>()
    private val buffer = ChatBuffer(MAX_MESSAGES)
    private var chatJob: Job? = null
    private var retries = 0
    private var appliedPreferred = false
    private var rebuffers = 0
    private var targetOffsetMs = 0L
    private var lastRebufferAt = 0L
    private var lastLearnedRebufferAt = 0L
    private var playlists: LowLatencyPlaylistParserFactory? = null
    private var replayReset = true

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_BUFFERING && state == PlayState.Playing && !isVod) {
                    rebuffers++
                    lastRebufferAt = System.currentTimeMillis()
                    if (playlists?.inAdBreak() != true) {
                        lastLearnedRebufferAt = lastRebufferAt
                        setTarget(maxOf(targetOffsetMs, liveOffsetMs() ?: targetOffsetMs) + 300)
                    }
                }
                buffering = s == Player.STATE_BUFFERING
                when (s) {
                    Player.STATE_READY -> { state = PlayState.Playing; retries = 0 }
                    Player.STATE_ENDED -> if (!isVod) state = PlayState.Offline
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)

            override fun onTracksChanged(tracks: Tracks) = updateQualities(tracks)

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) currentHeight = videoSize.height
            }
        })
        viewModelScope.launch {
            app.prefs.settings.collect { settings = it }
        }
        viewModelScope.launch {
            app.danmaku.settings.collect { danmaku = it }
        }
        if (vodId != null) {
            viewModelScope.launch {
                val (v, owner) = runCatching { app.api.video(vodId) }.getOrNull() ?: return@launch
                video = v
                if (owner != null) {
                    this@PlayerViewModel.login = owner.login
                    status = ChannelStatus(owner, null, null, null)
                    badges = runCatching { app.api.badges(owner.login) }.getOrDefault(emptyMap())
                }
            }
            chatConnected = true
            startReplay(vodId)
            viewModelScope.launch {
                while (isActive) {
                    delay(500)
                    val sec = player.currentPosition / 1000
                    inMutedSegment = video?.mutedSegments?.any { sec in it } == true
                }
            }
            viewModelScope.launch {
                while (isActive) {
                    delay(5_000)
                    if (isPlaying) savePosition()
                }
            }
        } else if (clipSlug == null) {
            viewModelScope.launch {
                badges = runCatching { app.api.badges(login) }.getOrDefault(emptyMap())
            }
            viewModelScope.launch {
                while (isActive) {
                    runCatching { app.api.channel(login) }.getOrNull()?.let { status = it }
                    delay(60_000)
                }
            }
        }
        load()
        viewModelScope.launch {
            app.web.state.map { it?.login }.distinctUntilChanged().collect {
                loggedInAs = it
                if (isVod) return@collect
                chatJob?.cancel(); chatJob = null
                startChat()
                refreshPoints()
            }
        }
        if (!isVod) startLiveLoops()
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                if (pending.isNotEmpty()) applyPending()
            }
        }
    }

    private fun startLiveLoops() {
        viewModelScope.launch {
            var catchingUp = false
            var lastEased = System.currentTimeMillis()
            while (isActive) {
                delay(500)
                val now = System.currentTimeMillis()
                val behind = liveOffsetMs()?.let { it - targetOffsetMs }
                val buffered = player.totalBufferedDuration
                val easeEvery = if (now - lastLearnedRebufferAt > 600_000) 20_000 else 60_000
                if (now - lastRebufferAt > 60_000 && now - lastEased > easeEvery && buffered > 1_000 && behind != null && behind < 500) {
                    setTarget(targetOffsetMs - 100)
                    lastEased = now
                }
                val settled = isPlaying && now - lastRebufferAt > 15_000
                catchingUp = shouldCatchUp(catchingUp, settled, behind, buffered)
                val speed = if (catchingUp) 1.08f else 1f
                if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (!isPlaying) continue
                Log.d(
                    TAG,
                    "遅延 ${liveOffsetMs()}ms 先読み ${player.totalBufferedDuration}ms 速度 ${player.playbackParameters.speed} " +
                        "${currentHeight}p 回線 ${bandwidth.bitrateEstimate / 1000}kbps 途切れ $rebuffers 回 " +
                        "目標 ${targetOffsetMs}ms",
                )
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                val acc = app.web.state.value ?: continue
                val stream = status?.stream ?: continue
                if (!isPlaying) continue
                runCatching { app.api.sendMinuteWatched(stream.channel.id, stream.id, login, acc.userId) }
                refreshPoints()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            state = PlayState.Loading
            buffering = true
            try {
                if (clipSlug != null) {
                    val resumeAt = player.currentPosition.takeIf { player.currentMediaItem != null } ?: 0
                    val (c, url) = app.api.clip(clipSlug) ?: throw TwitchException(R.string.error_vod_unplayable)
                    clip = c
                    c.channel?.let { ch ->
                        login = ch.login
                        status = ChannelStatus(ch, null, null, null)
                    }
                    if (replayJob == null && c.videoId != null) {
                        chatConnected = true
                        replayReset = true
                        replayJob = startReplay(c.videoId, c.videoOffsetSec * 1000L)
                    }
                    player.setMediaItem(MediaItem.fromUri(url), resumeAt)
                    player.prepare()
                    player.setPlaybackSpeed(holdSpeed ?: speed)
                    player.play()
                    return@launch
                }
                if (vodId != null) {
                    val resumeAt = player.currentPosition.takeIf { player.currentMediaItem != null }
                        ?: WatchHistory.resumeFrom(app.history.get(vodId), video?.lengthSeconds ?: 0)
                    val url = playbackUrl(app.api.vodUrl(vodId))
                    player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), resumeAt)
                    player.prepare()
                    player.setPlaybackSpeed(holdSpeed ?: speed)
                    player.play()
                    return@launch
                }
                val url = playbackUrl(app.api.liveUrl(login))
                val latency = app.prefs.settings.first().latency
                if (targetOffsetMs == 0L) targetOffsetMs = startTarget(latency, app.learnedLatency[latency])
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(targetOffsetMs)
                            .setMinPlaybackSpeed(1f)
                            .setMaxPlaybackSpeed(1f)
                            .build(),
                    )
                    .build()
                val parsers = LowLatencyPlaylistParserFactory()
                playlists = parsers
                val source = HlsMediaSource.Factory(DefaultDataSource.Factory(app))
                    .setPlaylistParserFactory(parsers)
                    .setPlaylistTrackerFactory(lowLatencyPlaylistTrackerFactory())
                    .createMediaSource(item)
                player.setMediaSource(source)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = if (e is HttpStatusException && e.code == 404 && !isVod) PlayState.Offline
                else PlayState.Error(e.message ?: Strings.get(R.string.error_load_failed))
                buffering = false
            }
        }
    }

    private suspend fun playbackUrl(base: String): String =
        if (audioOnly) app.api.audioOnlyPlaylist(base) ?: base else base

    private fun handleError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        buffering = false
        if (code == 404 && !isVod) {
            state = PlayState.Offline
            return
        }
        if (retries < 3) {
            retries++
            viewModelScope.launch {
                delay(1_500L * retries)
                load()
            }
        } else {
            state = PlayState.Error(error.errorCodeName)
        }
    }

    private fun updateQualities(tracks: Tracks) {
        val list = ArrayList<Quality>()
        for (g in tracks.groups) {
            if (g.type != MC.TRACK_TYPE_VIDEO) continue
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                if (f.height <= 0) continue
                list += Quality(qualityLabel(f.height, f.frameRate), f.height, g, i)
            }
        }
        qualities = orderQualities(list, { it.height }, { it.label })
        if (!appliedPreferred && qualities.isNotEmpty() && !multi) {
            appliedPreferred = true
            preferredQuality(qualities, settings.preferredHeight) { it.height }?.let { applyQuality(it) }
        }
    }

    fun limitHeight(maxHeight: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, maxHeight).build()
    }

    fun selectQuality(q: Quality?) {
        applyQuality(q)
        viewModelScope.launch { app.prefs.setPreferredHeight(q?.height ?: 0) }
    }

    private fun applyQuality(q: Quality?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (q == null) {
            b.clearOverridesOfType(MC.TRACK_TYPE_VIDEO)
        } else {
            b.setOverrideForType(TrackSelectionOverride(q.group.mediaTrackGroup, q.index))
        }
        player.trackSelectionParameters = b.build()
        selectedHeight = q?.height ?: 0
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (!isVod) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        replayReset = true
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun seekToLive() = player.seekToDefaultPosition()

    fun applyHoldSpeed(s: Float?) {
        if (!isVod) return
        holdSpeed = s
        player.setPlaybackSpeed(s ?: speed)
    }

    fun selectSpeed(v: Float) {
        if (!isVod) return
        speed = v
        if (holdSpeed == null) player.setPlaybackSpeed(v)
    }

    fun selectAudioOnly(v: Boolean) {
        if (audioOnly == v) return
        audioOnly = v
        appliedPreferred = false
        load()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepMinutes = minutes
        if (minutes == null) { sleepEndsAt = null; return }
        val end = System.currentTimeMillis() + minutes * 60_000L
        sleepEndsAt = end
        sleepJob = viewModelScope.launch {
            delay(end - System.currentTimeMillis())
            player.pause()
            sleepEndsAt = null
            sleepMinutes = null
            BackgroundPlayService.stop(app)
        }
    }

    fun skipMutedSegment() {
        val sec = player.currentPosition / 1000
        val seg = video?.mutedSegments?.firstOrNull { sec in it } ?: return
        seekTo((seg.offsetSec + seg.durationSec) * 1000L)
    }

    private suspend fun savePosition() {
        val id = vodId ?: return
        val d = player.duration
        if (d <= 0) return
        app.history.set(id, player.currentPosition, (d / 1000).toInt())
    }

    fun onForeground() {
        BackgroundPlayService.stop(app)
        val kept = keptInBackground
        keptInBackground = false
        if (isVod) return
        if (!player.isPlaying && !kept && (state == PlayState.Playing || state == PlayState.Loading)) {
            player.seekToDefaultPosition()
            player.play()
        }
        startChat()
    }

    fun onBackground(dismissedFromPip: Boolean = false) {
        if (isVod) viewModelScope.launch { savePosition() }
        val mode = settings.backgroundPlay
        keptInBackground = !dismissedFromPip && isPlaying &&
            (mode == BackgroundPlay.Always || (mode == BackgroundPlay.Headphones && hasHeadphones()))
        if (keptInBackground) {
            viewModelScope.launch {
                delay(300)
                if (keptInBackground) startNotification()
            }
        } else {
            player.pause()
            BackgroundPlayService.stop(app)
        }
        if (isVod) return
        chatJob?.cancel()
        chatJob = null
        chatConnected = false
        canSend = false
    }

    fun onEnterPip() {
        if (isPlaying) startNotification()
    }

    fun onExitPip() {
        if (!keptInBackground) BackgroundPlayService.stop(app)
    }

    private fun startNotification() {
        BackgroundPlayService.start(app, player, status?.channel?.displayName ?: login, clip?.title ?: video?.title ?: status?.stream?.title ?: "", seekable = isVod)
    }

    private fun hasHeadphones(): Boolean =
        app.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }

    private fun startChat() {
        if (chatJob != null) return
        val conn = app.chat.connect(login, credentials = { app.web.state.value }, onAuthFailed = { app.web.clear() })
        connection = conn
        chatJob = viewModelScope.launch(Dispatchers.IO) {
            if (messages.isEmpty()) app.chat.recent(login).forEach { pending.add(ChatEvent.Message(it)) }
            conn.events().collect { pending.add(it) }
        }
    }

    fun sendChat(text: String): Boolean {
        val acc = app.web.state.value ?: return false
        val body = text.trim().take(500)
        if (body.isEmpty() || connection?.send(body) != true) return false
        pending.add(
            ChatEvent.Message(
                ChatMessage(
                    id = "local-" + UUID.randomUUID(),
                    userId = acc.userId,
                    login = acc.login,
                    displayName = me?.displayName ?: acc.login,
                    color = me?.color,
                    text = body,
                    emotes = emptyList(),
                    badges = me?.badges.orEmpty(),
                    timestamp = System.currentTimeMillis(),
                ),
            ),
        )
        return true
    }

    private fun applyPending() {
        val events = ArrayList<ChatEvent>()
        while (true) events += pending.poll() ?: break
        val r = buffer.apply(messages, events)
        r.connection?.let {
            chatConnected = it.connected
            canSend = it.connected && it.loggedIn
        }
        r.userState?.let { me = it }
        if (r.changed) messages = r.messages
    }

    fun setLatency(m: LatencyMode) {
        if (m == settings.latency) return
        targetOffsetMs = startTarget(m, app.learnedLatency[m])
        viewModelScope.launch {
            app.prefs.setLatency(m)
            load()
        }
    }

    private fun startReplay(id: String, offsetMs: Long = 0) = viewModelScope.launch {
        val queue = ArrayDeque<VodComment>()
        var lastPageIds = emptySet<String>()
        var nextSec = 0
        var fetching: Job? = null
        var lastPos = 0L
        while (isActive) {
            delay(250)
            val pos = player.currentPosition + offsetMs
            if (replayReset || pos < lastPos - 2_000) {
                replayReset = false
                fetching?.cancel(); fetching = null
                queue.clear(); lastPageIds = emptySet()
                pending.clear(); messages = emptyList()
                nextSec = (pos / 1000).toInt()
            }
            lastPos = pos
            while (queue.isNotEmpty() && queue.first().offsetSec * 1000L <= pos) {
                pending.add(ChatEvent.Message(queue.removeFirst().message))
            }
            if (fetching == null && queue.size < 30 && nextSec != Int.MAX_VALUE) {
                val from = nextSec
                fetching = launch {
                    val got = try {
                        app.api.vodComments(id, from)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (got == null) {
                        delay(3_000)
                    } else {
                        queue.addAll(got.filter { it.message.id !in lastPageIds }.sortedBy { it.offsetSec })
                        lastPageIds = got.mapTo(HashSet()) { it.message.id }
                        nextSec = nextReplayOffset(got, from)
                    }
                    fetching = null
                }
            }
        }
    }

    private fun setTarget(ms: Long) {
        val m = settings.latency
        targetOffsetMs = clampTarget(ms, m)
        app.learnedLatency[m] = targetOffsetMs
    }

    fun liveOffsetMs(): Long? = player.currentLiveOffset.takeIf { it != MC.TIME_UNSET && it >= 0 }

    fun updateDanmaku(s: DanmakuSettings) {
        danmaku = s
        viewModelScope.launch { app.danmaku.update(s) }
    }

    val isFavorite: Boolean get() = login.lowercase() in settings.favorites

    fun toggleFavorite() {
        viewModelScope.launch { app.prefs.toggleFavorite(login) }
    }

    fun addNgUser(login: String) {
        viewModelScope.launch { app.prefs.addNgUser(login) }
    }

    private suspend fun refreshPoints() {
        val token = app.web.token() ?: run { points = null; return }
        val p = runCatching { app.api.channelPoints(login, token) }.getOrElse { return }
        points = p
        if (startBalance == null) startBalance = p?.balance
        if (p?.claimId != null && isPlaying) claimBonus(p)
    }

    private suspend fun claimBonus(before: ChannelPoints) {
        if (!claimBonusInWeb(app, login)) {
            Log.w(TAG, "ボーナスのボタンが見つからず受け取れなかった: $login")
            return
        }
        val token = app.web.token() ?: return
        val after = runCatching { app.api.channelPoints(login, token) }.getOrNull() ?: return
        points = after
        if (after.claimId == null && after.balance > before.balance) {
            bonusGained = after.balance - before.balance
            Log.i(TAG, "ボーナスを受け取った: $login +$bonusGained")
            bonusCount++
            viewModelScope.launch { delay(5_000); bonusGained = null }
        }
    }

    override fun onCleared() {
        BackgroundPlayService.stop(app)
        player.release()
    }

    companion object {
        const val MAX_MESSAGES = 500
        private const val TAG = "twiview"
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
