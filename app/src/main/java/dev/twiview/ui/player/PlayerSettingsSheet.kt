package dev.twiview.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.LatencyMode
import dev.twiview.ui.common.FixedBottomSheet
import dev.twiview.ui.common.formatLength
import dev.twiview.ui.settings.BackgroundPlayChips
import dev.twiview.ui.settings.ChatDisplaySettingsContent
import dev.twiview.ui.settings.ChatPreview
import dev.twiview.ui.settings.ChipRow
import dev.twiview.ui.settings.GesturesSettingsContent
import dev.twiview.ui.settings.GroupLabel
import dev.twiview.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tabs = listOf(R.string.tab_playback, R.string.danmaku, R.string.tab_chat)

@Composable
fun PlayerSettingsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val scope = rememberCoroutineScope()
    val tab = vm.settingsTab
    val sheetHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.6f).toDp() }
    val portrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    FixedBottomSheet(onDismiss = onDismiss) {
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = C.Surface,
            contentColor = C.Text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = C.Accent)
            },
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { vm.settingsTab = i },
                    text = { Text(stringResource(label)) },
                    selectedContentColor = C.Accent,
                    unselectedContentColor = C.Muted,
                )
            }
        }
        val previewHeight = if (tab == 2 && portrait) 84.dp else 0.dp
        if (previewHeight > 0.dp) {
            Box(Modifier.fillMaxWidth().height(previewHeight)) { ChatPreview(vm.settings, maxHeight = previewHeight - 16.dp, count = 2) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(sheetHeight - previewHeight)
                .verticalScroll(vm.settingsScroll[tab])
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            when (tab) {
                0 -> PlaybackSettingsContent(vm)
                1 -> DanmakuSettingsContent(vm.danmaku, vm::updateDanmaku)
                else -> ChatDisplaySettingsContent(vm.settings, prefs, scope)
            }
        }
    }
}

private object AudioOnlyChoice

@Composable
private fun PlaybackSettingsContent(vm: PlayerViewModel) {
    val prefs = LocalContext.current.app.prefs
    val scope = rememberCoroutineScope()
    if (vm.multi) {
        GroupLabel(stringResource(R.string.multi_layout))
        MultiLayoutPicker(vm.settings.multiLayout) { v -> scope.launch { prefs.setMultiLayout(v) } }
    }
    if (vm.isVod) {
        GroupLabel(stringResource(R.string.playback_speed))
        ChipRow(
            options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { it to "$it".removeSuffix(".0") + "×" },
            selected = vm.speed,
            onSelect = vm::selectSpeed,
        )
    } else {
        LatencySettings(vm)
    }
    if (!vm.isClip && !vm.multi && (vm.qualities.isNotEmpty() || vm.audioOnly)) {
        GroupLabel(stringResource(R.string.quality))
        ChipRow(
            options = listOf<Pair<Any?, String>>(null to stringResource(R.string.auto)) +
                vm.qualities.map { it to it.label } +
                (AudioOnlyChoice to stringResource(R.string.audio_only)),
            selected = if (vm.audioOnly) AudioOnlyChoice else vm.qualities.firstOrNull { it.height == vm.selectedHeight && vm.selectedHeight != 0 },
            onSelect = { v ->
                when (v) {
                    AudioOnlyChoice -> vm.selectAudioOnly(true)
                    is Quality -> { vm.selectQuality(v); vm.selectAudioOnly(false) }
                    else -> { vm.selectQuality(null); vm.selectAudioOnly(false) }
                }
            },
        )
    }
    GroupLabel(stringResource(R.string.sleep_timer))
    ChipRow(
        options = listOf<Pair<Int?, String>>(null to stringResource(R.string.off)) + listOf(30, 45, 60, 120, 180).map {
            it to if (it >= 60) stringResource(R.string.hours_short, it / 60) else stringResource(R.string.minutes_short, it)
        },
        selected = vm.sleepMinutes,
        onSelect = vm::setSleepTimer,
    )
    vm.sleepEndsAt?.let { SleepRemaining(it) }
    if (!vm.multi) {
        GroupLabel(stringResource(R.string.background_play))
        BackgroundPlayChips(vm.settings.backgroundPlay) { v -> scope.launch { prefs.setBackgroundPlay(v) } }
    }
    GesturesSettingsContent(vm.settings, prefs, scope, compact = true)
}

@Composable
private fun SleepRemaining(end: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        stringResource(R.string.sleep_remaining, formatLength(((end - now) / 1000).coerceAtLeast(0).toInt())),
        style = MaterialTheme.typography.labelSmall,
        color = C.Faint,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun LatencySettings(vm: PlayerViewModel) {
    GroupLabel(stringResource(R.string.latency))
    ChipRow(
        options = LatencyMode.entries.map { it to stringResource(it.label) },
        selected = vm.settings.latency,
        onSelect = vm::setLatency,
    )
    var offset by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(vm) {
        while (true) {
            offset = vm.liveOffsetMs()
            delay(1_000)
        }
    }
    Text(
        offset?.let { stringResource(R.string.latency_now, it / 1000f) } ?: stringResource(R.string.latency_measuring),
        style = MaterialTheme.typography.bodySmall,
        color = C.Text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
