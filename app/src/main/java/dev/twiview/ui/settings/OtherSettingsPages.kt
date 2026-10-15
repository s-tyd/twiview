package dev.twiview.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.twiview.AppLanguage
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.ChatMessage
import dev.twiview.data.DanmakuSettings
import dev.twiview.data.DoubleTapAction
import dev.twiview.data.GestureKeys
import dev.twiview.data.HoldAction
import dev.twiview.data.LatencyMode
import dev.twiview.data.Prefs
import dev.twiview.data.Settings
import dev.twiview.data.SwipeAction
import dev.twiview.data.TapAction
import dev.twiview.ui.common.SplitTabs
import dev.twiview.ui.player.DanmakuOverlay
import dev.twiview.ui.player.DanmakuSettingsContent
import dev.twiview.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val sampleColors = listOf(0xFFFF7A7A, 0xFF7AB8FF, 0xFF7CE38B, 0xFFFFB86B, 0xFFE38BFF).map { it.toInt() }

@Composable
fun DanmakuSettingsPage(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val d by app.danmaku.settings.collectAsState(initial = DanmakuSettings())
    val sampleLines = stringArrayResource(R.array.danmaku_samples)
    val viewerName = stringResource(R.string.danmaku_viewer)
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    LaunchedEffect(Unit) {
        var i = 0
        while (true) {
            delay((40L..220L).random())
            val m = ChatMessage(
                id = "dm-$i", userId = "", login = "viewer$i", displayName = viewerName.format(i % 7),
                color = sampleColors[i % sampleColors.size], text = sampleLines.random(),
                emotes = emptyList(), badges = emptyList(), timestamp = System.currentTimeMillis(),
            )
            messages = (messages + m).takeLast(120)
            i++
        }
    }
    SettingsPage(
        title = stringResource(R.string.danmaku),
        onBack = onBack,
        pinned = {
            Box(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(C.Surface2),
            ) {
                DanmakuOverlay(messages, Settings(), d.copy(enabled = true))
                if (!d.enabled) {
                    Text(
                        stringResource(R.string.off),
                        color = C.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
        },
    ) {
        DanmakuSettingsContent(d) { next -> scope.launch { app.danmaku.update(next) } }
    }
}

@Composable
fun NgSettingsPage(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val s by prefs.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SettingsPage(
        title = stringResource(R.string.ng),
        onBack = onBack,
        pinned = { SplitTabs(listOf(stringResource(R.string.block_words), stringResource(R.string.block_users)), tab) { tab = it } },
        bottom = {
            val settings = s ?: return@SettingsPage
            BlockAddRow(stringResource(if (tab == 0) R.string.block_word_hint else R.string.block_user_hint)) { v ->
                scope.launch { if (tab == 0) prefs.setNgWords((settings.ngWords + v).distinct().joinToString("\n")) else prefs.addNgUser(v) }
            }
        },
    ) {
        val settings = s ?: return@SettingsPage
        val words = tab == 0
        val items = if (words) settings.ngWords else settings.ngUsers.sorted()
        if (items.isEmpty()) Hint(stringResource(R.string.block_empty), vertical = 12.dp)
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    scope.launch {
                        if (words) prefs.setNgWords((settings.ngWords - item).joinToString("\n"))
                        else prefs.setNgUsers((settings.ngUsers - item).joinToString("\n"))
                    }
                }) { Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = C.Muted) }
            }
            HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun BlockAddRow(hint: String, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        val v = text.trim()
        if (v.isNotEmpty()) { onAdd(v); text = "" }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = ::submit, enabled = text.isNotBlank()) { Text(stringResource(R.string.block_add)) }
    }
}

@Composable
private fun Hint(text: String, vertical: Dp = 4.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = C.Faint,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = vertical),
    )
}

@Composable
fun PlaybackSettingsPage(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val s by prefs.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    SettingsPage(title = stringResource(R.string.playback), onBack = onBack) {
        val settings = s ?: return@SettingsPage
        GroupLabel(stringResource(R.string.default_quality))
        ChipRow(
            options = listOf(0 to stringResource(R.string.auto), 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", 160 to "160p"),
            selected = settings.preferredHeight,
            onSelect = { v -> scope.launch { prefs.setPreferredHeight(v) } },
        )
        Hint(stringResource(R.string.default_quality_hint))
        GroupLabel(stringResource(R.string.latency))
        ChipRow(
            options = LatencyMode.entries.map { it to stringResource(it.label) },
            selected = settings.latency,
            onSelect = { v -> scope.launch { prefs.setLatency(v) } },
        )
        Hint(stringResource(R.string.latency_hint))
        GroupLabel(stringResource(R.string.background_play))
        BackgroundPlayChips(settings.backgroundPlay) { v -> scope.launch { prefs.setBackgroundPlay(v) } }
    }
}

@Composable
fun GesturesSettingsPage(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val s by prefs.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    SettingsPage(title = stringResource(R.string.gestures), onBack = onBack) {
        val settings = s ?: return@SettingsPage
        GesturesSettingsContent(settings, prefs, scope)
    }
}

@Composable
internal fun GesturesSettingsContent(settings: Settings, prefs: Prefs, scope: CoroutineScope, compact: Boolean = false) {
    val section: @Composable (String) -> Unit = if (compact) { t -> SubLabel(t) } else { t -> GroupLabel(t) }
    if (compact) GroupLabel(stringResource(R.string.gestures))
    Column {
        section(stringResource(R.string.gesture_tap))
        ChipRow(
            options = listOf(
                TapAction.Controls to stringResource(R.string.gesture_controls),
                TapAction.PlayPause to stringResource(R.string.gesture_play_pause),
                TapAction.None to stringResource(R.string.off),
            ),
            selected = settings.tapAction,
            onSelect = { v -> scope.launch { prefs.putEnum(GestureKeys.tap, v) } },
        )
        section(stringResource(R.string.gesture_double_tap))
        ChipRow(
            options = listOf(
                DoubleTapAction.Skip to stringResource(R.string.gesture_skip),
                DoubleTapAction.Fullscreen to stringResource(R.string.gesture_fullscreen),
                DoubleTapAction.PlayPause to stringResource(R.string.gesture_play_pause),
                DoubleTapAction.None to stringResource(R.string.off),
            ),
            selected = settings.doubleTapAction,
            onSelect = { v -> scope.launch { prefs.putEnum(GestureKeys.doubleTap, v) } },
        )
        if (settings.doubleTapAction == DoubleTapAction.Skip) {
            SubLabel(stringResource(R.string.gesture_skip_seconds))
            ChipRow(
                options = listOf(5, 10, 15, 30).map { it to stringResource(R.string.seconds_short, it) },
                selected = settings.skipSeconds,
                onSelect = { v -> scope.launch { prefs.put(GestureKeys.skipSeconds, v) } },
            )
        }
        section(stringResource(R.string.gesture_hold))
        ChipRow(
            options = listOf(
                HoldAction.Speed to stringResource(R.string.gesture_hold_speed_action),
                HoldAction.None to stringResource(R.string.off),
            ),
            selected = settings.holdAction,
            onSelect = { v -> scope.launch { prefs.putEnum(GestureKeys.hold, v) } },
        )
        if (settings.holdAction == HoldAction.Speed) {
            SubLabel(stringResource(R.string.gesture_hold_speed))
            ChipRow(
                options = listOf(1.5f, 2f, 3f).map { it to "$it".removeSuffix(".0") + "×" },
                selected = settings.holdSpeed,
                onSelect = { v -> scope.launch { prefs.put(GestureKeys.holdSpeed, v) } },
            )
            Hint(stringResource(R.string.gesture_hold_vod_only))
        }
        section(stringResource(R.string.gesture_swipe))
        val swipeOptions = listOf(
            SwipeAction.Brightness to stringResource(R.string.brightness),
            SwipeAction.Volume to stringResource(R.string.volume),
            SwipeAction.None to stringResource(R.string.off),
        )
        SubLabel(stringResource(R.string.gesture_swipe_left))
        ChipRow(options = swipeOptions, selected = settings.swipeLeft, onSelect = { v -> scope.launch { prefs.putEnum(GestureKeys.swipeLeft, v) } })
        SubLabel(stringResource(R.string.gesture_swipe_right))
        ChipRow(options = swipeOptions, selected = settings.swipeRight, onSelect = { v -> scope.launch { prefs.putEnum(GestureKeys.swipeRight, v) } })
        ToggleRow(stringResource(R.string.gesture_swipe_seek), settings.swipeSeek) { v -> scope.launch { prefs.put(GestureKeys.swipeSeek, v) } }
    }
}

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.about), onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Text("twiview  v${appVersion(context)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.about_unofficial),
            style = MaterialTheme.typography.bodySmall,
            color = C.Muted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun LanguageSettingsPage(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(AppLanguage.current()) }
    SettingsPage(title = stringResource(R.string.app_language), onBack = onBack) {
        Spacer(Modifier.height(4.dp))
        AppLanguage.entries.forEach { lang ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        if (selected != lang) {
                            selected = lang
                            AppLanguage.set(lang)
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = selected == lang,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = C.Accent, unselectedColor = C.Faint),
                )
                Text(stringResource(lang.label), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Hint(stringResource(R.string.app_language_hint), vertical = 8.dp)
    }
}
