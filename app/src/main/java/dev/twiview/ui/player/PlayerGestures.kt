package dev.twiview.ui.player

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings as SysSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.twiview.R
import dev.twiview.data.DoubleTapAction
import dev.twiview.data.HoldAction
import dev.twiview.data.Settings
import dev.twiview.data.SwipeAction
import dev.twiview.data.TapAction
import dev.twiview.ui.common.formatLength
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal sealed interface GestureHint {
    data class Skip(val seconds: Int) : GestureHint
    data class Speed(val speed: Float) : GestureHint
    data class Volume(val percent: Int) : GestureHint
    data class Brightness(val percent: Int) : GestureHint
    data class Seek(val positionMs: Long, val deltaMs: Long) : GestureHint
}

internal class GestureHints {
    var current by mutableStateOf<GestureHint?>(null)
    var skipAccum = 0
    var lastSkipAt = 0L
}

@Composable
internal fun rememberGestureHints(): GestureHints {
    val hints = remember { GestureHints() }
    val h = hints.current
    LaunchedEffect(h) {
        if (h == null || h is GestureHint.Speed || h is GestureHint.Seek) return@LaunchedEffect
        delay(700)
        if (hints.current === h) hints.current = null
    }
    return hints
}

internal fun Modifier.playerGestures(
    vm: PlayerViewModel,
    settings: Settings,
    hints: GestureHints,
    activity: Activity,
    onToggleControls: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onAnyTap: () -> Unit = {},
): Modifier = this
    .pointerInput(settings.tapAction, settings.doubleTapAction, settings.skipSeconds, settings.holdAction, settings.holdSpeed, vm.isVod) {
        var holding = false
        detectTapGestures(
            onTap = {
                onAnyTap()
                when (settings.tapAction) {
                    TapAction.Controls -> onToggleControls()
                    TapAction.PlayPause -> vm.togglePlay()
                    TapAction.None -> Unit
                }
            },
            onDoubleTap = { pos ->
                onAnyTap()
                when (settings.doubleTapAction) {
                    DoubleTapAction.Skip -> {
                        val forward = pos.x >= size.width / 2
                        val sec = if (forward) settings.skipSeconds else -settings.skipSeconds
                        if (forward && !vm.isVod) vm.seekToLive() else vm.seekBy(sec * 1_000L)
                        val now = System.currentTimeMillis()
                        hints.skipAccum = if (now - hints.lastSkipAt < 800 && (hints.skipAccum > 0) == forward) hints.skipAccum + sec else sec
                        hints.lastSkipAt = now
                        hints.current = GestureHint.Skip(hints.skipAccum)
                    }
                    DoubleTapAction.Fullscreen -> onToggleFullscreen()
                    DoubleTapAction.PlayPause -> vm.togglePlay()
                    DoubleTapAction.None -> Unit
                }
            },
            onLongPress = {
                if (settings.holdAction == HoldAction.Speed && vm.isVod) {
                    holding = true
                    vm.applyHoldSpeed(settings.holdSpeed)
                    hints.current = GestureHint.Speed(settings.holdSpeed)
                }
            },
            onPress = {
                tryAwaitRelease()
                if (holding) {
                    holding = false
                    vm.applyHoldSpeed(null)
                    if (hints.current is GestureHint.Speed) hints.current = null
                }
            },
        )
    }
    .pointerInput(settings.swipeLeft, settings.swipeRight, settings.swipeSeek, vm.isVod) {
        val seekEnabled = settings.swipeSeek && vm.isVod
        if (settings.swipeLeft == SwipeAction.None && settings.swipeRight == SwipeAction.None && !seekEnabled) return@pointerInput
        val slop = viewConfiguration.touchSlop
        var axis = AXIS_NONE
        var startX = 0f
        var action = SwipeAction.None
        var level = 0f
        var seekFrom = 0L
        var seekDelta = 0L
        var acc = Offset.Zero
        detectDragGestures(
            onDragStart = { p ->
                axis = AXIS_NONE; startX = p.x; acc = Offset.Zero
                seekFrom = vm.player.currentPosition; seekDelta = 0
            },
            onDragEnd = {
                if (axis == AXIS_SEEK) vm.seekTo(seekFrom + seekDelta)
                if (hints.current is GestureHint.Seek) hints.current = null
            },
            onDragCancel = { if (hints.current is GestureHint.Seek) hints.current = null },
        ) { change, drag ->
            acc += drag
            if (axis == AXIS_NONE) {
                if (abs(acc.x) < slop && abs(acc.y) < slop) return@detectDragGestures
                action = if (startX < size.width / 2) settings.swipeLeft else settings.swipeRight
                axis = when {
                    abs(acc.y) >= abs(acc.x) -> if (action != SwipeAction.None) AXIS_LEVEL else AXIS_IGNORE
                    else -> if (seekEnabled) AXIS_SEEK else AXIS_IGNORE
                }
                if (axis == AXIS_LEVEL) level = if (action == SwipeAction.Brightness) activity.brightness() else activity.volume()
            }
            when (axis) {
                AXIS_LEVEL -> {
                    change.consume()
                    val v = (level - acc.y / size.height).coerceIn(0f, 1f)
                    hints.current = if (action == SwipeAction.Brightness) {
                        activity.setBrightness(v); GestureHint.Brightness((v * 100).roundToInt())
                    } else {
                        activity.setVolume(v); GestureHint.Volume((v * 100).roundToInt())
                    }
                }
                AXIS_SEEK -> {
                    change.consume()
                    seekDelta = (acc.x / size.width * SEEK_FULL_WIDTH_MS).toLong()
                    val dur = vm.player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    seekDelta = (seekFrom + seekDelta).coerceIn(0, dur) - seekFrom
                    hints.current = GestureHint.Seek(seekFrom + seekDelta, seekDelta)
                }
            }
        }
    }

private const val AXIS_NONE = 0
private const val AXIS_LEVEL = 1
private const val AXIS_SEEK = 2
private const val AXIS_IGNORE = 3
private const val SEEK_FULL_WIDTH_MS = 90_000f

private fun Activity.brightness(): Float {
    val w = window.attributes.screenBrightness
    if (w >= 0f) return w
    val sys = runCatching { SysSettings.System.getInt(contentResolver, SysSettings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    return sys / 255f
}

private fun Activity.setBrightness(v: Float) {
    window.attributes = window.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
}

private fun Activity.volume(): Float {
    val am = getSystemService(AudioManager::class.java)
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}

private fun Activity.setVolume(v: Float) {
    val am = getSystemService(AudioManager::class.java)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
}

@Composable
internal fun BoxScope.GestureHintOverlay(hint: GestureHint?) {
    when (hint) {
        null -> Unit
        is GestureHint.Skip -> {
            val back = hint.seconds < 0
            Pill(
                if (back) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                (if (back) "−" else "+") + stringResource(R.string.seconds_short, abs(hint.seconds)),
                Modifier.align(if (back) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 32.dp),
            )
        }
        is GestureHint.Speed -> Pill(Icons.Filled.FastForward, "${hint.speed}".removeSuffix(".0") + "×", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        is GestureHint.Volume -> Pill(Icons.AutoMirrored.Filled.VolumeUp, "${hint.percent}%", Modifier.align(Alignment.Center))
        is GestureHint.Brightness -> Pill(Icons.Filled.BrightnessMedium, "${hint.percent}%", Modifier.align(Alignment.Center))
        is GestureHint.Seek -> {
            val d = hint.deltaMs / 1000
            Pill(null, formatLength((hint.positionMs / 1000).toInt()) + "  (" + (if (d < 0) "−" else "+") + formatLength(abs(d).toInt()) + ")", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
internal fun Pill(icon: ImageVector?, text: String, modifier: Modifier, color: Color = Color.White) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
