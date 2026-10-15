package dev.twiview.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import dev.twiview.MainActivity
import dev.twiview.R
import dev.twiview.app
import dev.twiview.ui.common.FavoriteButton
import dev.twiview.ui.common.LiveDot
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.common.formatAgoDays
import dev.twiview.ui.common.formatCount
import dev.twiview.ui.common.formatDateTime
import dev.twiview.ui.common.formatLength
import dev.twiview.ui.common.use24Hour
import dev.twiview.ui.home.Nav
import dev.twiview.ui.theme.C
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun PlayerScreen(login: String, nav: Nav, vodId: String? = null, clipSlug: String? = null) {
    val app = LocalContext.current.app
    val vm = viewModel(key = clipSlug ?: vodId ?: login) { PlayerViewModel(app, login, vodId, clipSlug) }
    val activity = LocalActivity.current as MainActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inPip = activity.inPip
    var chatVisible by rememberSaveable { mutableStateOf(true) }
    val showChat = chatVisible && vm.hasChat
    var landscapeMode by rememberSaveable { mutableIntStateOf(0) }

    BackgroundPauseEffect(onForeground = vm::onForeground, onBackground = { vm.onBackground(SystemClock.uptimeMillis() - activity.pipExitedAt < 1_500) })
    DisposableEffect(vm.isPlaying, vm.audioOnly) {
        activity.pipEnabled = vm.isPlaying && !vm.audioOnly
        onDispose { activity.pipEnabled = false }
    }
    DisposableEffect(vm.isPlaying, vm.isVod) {
        activity.pipControls = object : MainActivity.PipControls {
            override val playing get() = vm.isPlaying
            override val seekable get() = vm.isVod
            override fun togglePlay() = vm.togglePlay()
            override fun seekBy(ms: Long) = vm.seekBy(ms)
        }
        onDispose { activity.pipControls = null }
    }
    LaunchedEffect(inPip) { if (inPip) vm.onEnterPip() else vm.onExitPip() }
    FullscreenEffect(landscape && !inPip)
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    val surface = remember { movableContentOf { VideoSurface(vm.player) } }

    val video: @Composable () -> Unit = { if (vm.audioOnly) AudioOnlyCover(vm) else surface() }
    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { video() }
        return
    }

    val toggleFullscreen = {
        when {
            !landscape -> {
                landscapeMode = 0
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            landscapeMode == 0 -> {
                landscapeMode = 1
                chatVisible = true
            }
            else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    val fullscreenIcon = when {
        !landscape -> Icons.Filled.Fullscreen
        landscapeMode == 0 -> Icons.Filled.AspectRatio
        else -> Icons.Filled.FullscreenExit
    }

    if (landscape) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val fit = landscapeMode == 1 && showChat
            val fitWidth = (maxHeight * 16f / 9f).coerceAtMost(maxWidth - 160.dp)
            Row(Modifier.fillMaxSize()) {
                Box((if (fit) Modifier.width(fitWidth) else Modifier.weight(1f)).fillMaxHeight()) {
                    video()
                    if (vm.danmaku.enabled) DanmakuOverlay(vm.messages, vm.settings, vm.danmaku)
                    VideoOverlay(
                        vm = vm,
                        landscape = true,
                        chatVisible = showChat,
                        onToggleChat = { chatVisible = !chatVisible },
                        onToggleFullscreen = toggleFullscreen,
                        fullscreenIcon = fullscreenIcon,
                        onPip = { activity.enterPip() },
                        nav = nav,
                    )
                }
                if (showChat) {
                    Column(
                        (if (landscapeMode == 1) Modifier.weight(1f) else Modifier.width(320.dp))
                            .background(C.Bg)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                            .fillMaxHeight()
                            .imePadding(),
                    ) {
                        ChatPanel(vm, modifier = Modifier.weight(1f).fillMaxWidth())
                        if (!vm.settings.hideChatInput) ChatInput(vm)
                    }
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Bg)
                .statusBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                video()
                if (vm.danmaku.enabled) DanmakuOverlay(vm.messages, vm.settings, vm.danmaku)
                VideoOverlay(
                    vm = vm,
                    landscape = false,
                    chatVisible = true,
                    onToggleChat = {},
                    onToggleFullscreen = toggleFullscreen,
                    fullscreenIcon = fullscreenIcon,
                    onPip = { activity.enterPip() },
                    nav = nav,
                )
            }
            InfoBar(vm, nav)
            HorizontalDivider(color = C.Line)
            if (vm.hasChat) {
                Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()) {
                    ChatPanel(vm, modifier = Modifier.weight(1f).fillMaxWidth())
                    if (!vm.settings.hideChatInput) ChatInput(vm)
                }
            }
        }
    }
}

@Composable
private fun AudioOnlyCover(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = vm.video?.thumbnailUrl ?: vm.status?.stream?.previewUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
        )
        Pill(Icons.Filled.Headphones, stringResource(R.string.audio_only), Modifier)
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun VideoSurface(player: Player) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setEnableComposeSurfaceSyncWorkaround(true)
                this.player = player
            }
        },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun VideoOverlay(
    vm: PlayerViewModel,
    landscape: Boolean,
    chatVisible: Boolean,
    onToggleChat: () -> Unit,
    onToggleFullscreen: () -> Unit,
    fullscreenIcon: ImageVector,
    onPip: () -> Unit,
    nav: Nav,
) {
    var controls by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    var settingsSheet by remember { mutableStateOf(false) }
    LaunchedEffect(controls, poke) {
        if (controls) {
            delay(3_500); controls = false
        }
    }
    val hints = rememberGestureHints()
    val activity = LocalActivity.current!!
    val focus = LocalFocusManager.current
    DisposableEffect(vm) { onDispose { vm.applyHoldSpeed(null) } }

    Box(
        Modifier
            .fillMaxSize()
            .playerGestures(vm, vm.settings, hints, activity, onToggleControls = { controls = !controls }, onToggleFullscreen = onToggleFullscreen, onAnyTap = { focus.clearFocus() }),
    ) {
        when (val s = vm.state) {
            PlayState.Offline -> StateCard(stringResource(R.string.offline)) {
                OutlinedButton(onClick = { nav.channel(vm.login) }) { Text(stringResource(R.string.videos)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = vm::load) { Text(stringResource(R.string.reload)) }
            }
            is PlayState.Error -> StateCard(s.message) {
                Button(onClick = vm::load) { Text(stringResource(R.string.reload)) }
            }
            else -> if (vm.buffering) {
                CircularProgressIndicator(
                    Modifier.size(36.dp).align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }

        GestureHintOverlay(hints.current)
        var skipMuted by remember { mutableStateOf(false) }
        if (vm.inMutedSegment && hints.current == null) {
            Pill(
                Icons.AutoMirrored.Filled.VolumeOff,
                stringResource(R.string.muted_segment),
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).clickable { skipMuted = true },
                color = mutedColor,
            )
        }
        if (skipMuted) {
            AlertDialog(
                onDismissRequest = { skipMuted = false },
                text = { Text(stringResource(R.string.muted_skip_question)) },
                confirmButton = { TextButton(onClick = { vm.skipMutedSegment(); skipMuted = false }) { Text(stringResource(R.string.skip)) } },
                dismissButton = { TextButton(onClick = { skipMuted = false }) { Text(stringResource(R.string.cancel)) } },
                containerColor = C.Surface,
            )
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (vm.isVod) 112.dp else 64.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
                )
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OverlayButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.back, onClick = nav.back)
                    if (landscape) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.status?.channel?.displayName ?: vm.login,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            (vm.video?.title ?: vm.status?.stream?.title)?.let {
                                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    OverlayButton(Icons.Filled.Settings, R.string.settings) { settingsSheet = true }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.isVod) {
                        OverlayButton(Icons.Filled.Replay10, R.string.rewind_10, Modifier.size(48.dp), iconSize = 32.dp) { vm.seekBy(-10_000); poke++ }
                        Spacer(Modifier.width(24.dp))
                    }
                    OverlayButton(
                        if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (vm.isPlaying) R.string.pause else R.string.play,
                        Modifier.size(56.dp),
                        iconSize = 40.dp,
                    ) { vm.togglePlay(); poke++ }
                    if (vm.isVod) {
                        Spacer(Modifier.width(24.dp))
                        OverlayButton(Icons.Filled.Forward10, R.string.forward_10, Modifier.size(48.dp), iconSize = 32.dp) { vm.seekBy(10_000); poke++ }
                    }
                }
                if (vm.isVod) {
                    SeekBar(
                        vm,
                        onInteract = { poke++ },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 48.dp),
                    )
                }
                OverlayButton(Icons.Filled.Refresh, R.string.reload, Modifier.align(Alignment.BottomStart).padding(4.dp)) { vm.load(); poke++ }
                Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OverlayButton(
                        if (vm.danmaku.enabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                        R.string.danmaku,
                        tint = if (vm.danmaku.enabled) C.Accent else Color.White,
                    ) { vm.updateDanmaku(vm.danmaku.copy(enabled = !vm.danmaku.enabled)); poke++ }
                    if (landscape && vm.hasChat) {
                        OverlayButton(Icons.AutoMirrored.Filled.Chat, R.string.chat, tint = if (chatVisible) C.Accent else Color.White) { onToggleChat(); poke++ }
                    }
                    OverlayButton(Icons.Filled.PictureInPictureAlt, R.string.pip, onClick = onPip)
                    OverlayButton(fullscreenIcon, R.string.toggle_fullscreen, onClick = onToggleFullscreen)
                }
            }
        }
    }
    if (settingsSheet) {
        PlayerSettingsSheet(vm, onDismiss = { settingsSheet = false })
    }
}

@Composable
internal fun OverlayButton(
    icon: ImageVector,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp? = null,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, stringResource(label), tint = tint, modifier = iconSize?.let { Modifier.size(it) } ?: Modifier)
    }
}

@Composable
private fun StateCard(title: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row { actions() }
    }
}

@Composable
private fun InfoBar(vm: PlayerViewModel, nav: Nav) {
    val st = vm.status
    val stream = st?.stream
    val video = vm.video
    val clip = vm.clip
    val locale = currentLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = st?.channel?.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(C.Surface2)
                .clickable { nav.channel(vm.login) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { nav.channel(vm.login) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        st?.channel?.displayName ?: vm.login,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (stream != null) {
                        LiveDot(6)
                        Spacer(Modifier.width(3.dp))
                        Text(formatCount(stream.viewers, locale), style = MaterialTheme.typography.bodySmall)
                    } else if (video != null) {
                        Text(pluralStringResource(R.plurals.views, video.views, formatCount(video.views, locale)), style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1)
                    } else if (clip != null) {
                        Text(pluralStringResource(R.plurals.views, clip.views, formatCount(clip.views, locale)), style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(8.dp))
                val trailing = stream?.startedAt?.let { formatLength(Duration.between(it, rememberSecondTicker()).seconds.coerceAtLeast(0).toInt()) }
                    ?: (video?.createdAt ?: clip?.createdAt)?.let { formatDateTime(it, locale, use24Hour()) }
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = C.Muted, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                video?.let { v ->
                    Text(
                        listOfNotNull(v.createdAt?.let { formatAgoDays(it, locale) }, v.game?.displayName).joinToString("  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = C.Muted,
                    )
                    Text(
                        v.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = C.Text.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                clip?.let { c ->
                    Text(
                        listOfNotNull(c.createdAt?.let { formatAgoDays(it, locale) }, c.game?.displayName, c.curator?.let { stringResource(R.string.clip_by, it) }).joinToString("  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = C.Muted,
                    )
                    Text(
                        c.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = C.Text.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                stream?.let {
                    Text(
                        it.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = C.Text.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    it.game?.let { g ->
                        Text(g.displayName, style = MaterialTheme.typography.labelSmall, color = C.Accent, modifier = Modifier.clickable { nav.category(g.name) })
                    }
                }
                }
                Spacer(Modifier.width(8.dp))
                FavoriteButton(vm.isFavorite, vm::toggleFavorite)
            }
        }
    }
}

@Composable
internal fun BackgroundPauseEffect(onForeground: () -> Unit, onBackground: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var started = true
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> if (!started) { started = true; onForeground() }
                Lifecycle.Event.ON_STOP -> { started = false; onBackground() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
internal fun FullscreenEffect(fullscreen: Boolean) {
    val activity = LocalActivity.current as MainActivity
    DisposableEffect(fullscreen) {
        activity.setSystemBarsHidden(fullscreen)
        onDispose { activity.setSystemBarsHidden(false) }
    }
}

@Composable
private fun rememberSecondTicker(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            value = Instant.now()
        }
    }
    return now
}

@Composable
private fun SeekBar(vm: PlayerViewModel, onInteract: () -> Unit, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(vm) {
        while (true) {
            position = vm.player.currentPosition
            duration = vm.player.duration.takeIf { it > 0 } ?: 0
            delay(500)
        }
    }
    val shown = dragging?.let { (it * duration).toLong() } ?: position
    val fraction = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
    val muted = vm.video?.mutedSegments.orEmpty()
    val timeStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatLength((shown / 1000).toInt()), color = Color.White, style = timeStyle)
        Canvas(
            Modifier
                .weight(1f)
                .height(28.dp)
                .padding(horizontal = 10.dp)
                .blockParentGestures()
                .pointerInput(duration) {
                    detectTapGestures { pos ->
                        vm.seekTo((pos.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                        onInteract()
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> dragging = (pos.x / size.width).coerceIn(0f, 1f); onInteract() },
                        onDragEnd = { dragging?.let { vm.seekTo((it * duration).toLong()) }; dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f); onInteract() }
                },
        ) {
            val cy = size.height / 2
            val stroke = 3.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
            drawLine(C.Accent, Offset(0f, cy), Offset(size.width * fraction, cy), stroke, StrokeCap.Round)
            if (duration > 0) {
                for (m in muted) {
                    val x0 = (size.width * m.offsetSec * 1000f / duration).coerceIn(0f, size.width)
                    val x1 = (size.width * (m.offsetSec + m.durationSec) * 1000f / duration).coerceIn(0f, size.width)
                    drawLine(mutedColor, Offset(x0, cy), Offset(maxOf(x1, x0 + 3.dp.toPx()).coerceAtMost(size.width), cy), stroke)
                }
            }
            drawCircle(C.Accent, (if (dragging != null) 8.dp else 6.dp).toPx(), Offset(size.width * fraction, cy))
        }
        Text(formatLength((duration / 1000).toInt()), color = Color.White, style = timeStyle)
    }
}

private val mutedColor = Color(0xFFFF5252)

private fun Modifier.blockParentGestures() = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
