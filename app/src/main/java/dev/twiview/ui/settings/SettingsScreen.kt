package dev.twiview.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.twiview.AppLanguage
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.DanmakuSettings
import dev.twiview.data.NameMode
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.home.Nav
import dev.twiview.ui.home.streamFilterLabel
import dev.twiview.ui.home.streamFilterOrder
import dev.twiview.ui.theme.C

enum class SettingsPageKey(val key: String) {
    Account("account"), Chat("chat"), Danmaku("danmaku"), Ng("ng"),
    Playback("playback"), Gestures("gestures"), StreamOrder("stream_order"), Language("language"), About("about"),
}

@Composable
fun SettingsScreen(nav: Nav, open: (SettingsPageKey) -> Unit) {
    val app = LocalContext.current.app
    val settings by app.prefs.settings.collectAsState(initial = null)
    val d by app.danmaku.settings.collectAsState(initial = DanmakuSettings())
    val account by app.web.state.collectAsState()

    SettingsPage(title = stringResource(R.string.settings), onBack = nav.back) {
        HubRow(Icons.Filled.AccountCircle, stringResource(R.string.account), account?.login ?: stringResource(R.string.not_logged_in)) {
            open(SettingsPageKey.Account)
        }
        Divider()
        HubRow(
            Icons.AutoMirrored.Filled.Chat,
            stringResource(R.string.chat_display),
            settings?.let {
                listOfNotNull(
                    stringResource(R.string.summary_text_size, it.chatFontSize.toInt()),
                    stringResource(
                        when (it.nameMode) {
                            NameMode.Both -> R.string.name_both; NameMode.DisplayOnly -> R.string.name_display
                            NameMode.LoginOnly -> R.string.name_login; NameMode.Hidden -> R.string.summary_no_names
                        },
                    ),
                    if (it.alignNames) stringResource(R.string.summary_aligned_names) else null,
                ).joinToString(" · ")
            },
        ) { open(SettingsPageKey.Chat) }
        HubRow(
            Icons.Filled.Subtitles,
            stringResource(R.string.danmaku),
            if (d.enabled) stringResource(R.string.summary_danmaku_on, stringResource(speedLabel(d.durationSec)), (d.opacity * 100).toInt())
            else stringResource(R.string.off),
        ) { open(SettingsPageKey.Danmaku) }
        HubRow(
            Icons.Filled.Block,
            stringResource(R.string.ng),
            settings?.let { stringResource(R.string.summary_ng, it.ngWords.size, it.ngUsers.size) },
        ) { open(SettingsPageKey.Ng) }
        Divider()
        HubRow(
            Icons.Filled.PlayCircle,
            stringResource(R.string.playback),
            settings?.let {
                stringResource(
                    R.string.summary_playback,
                    if (it.preferredHeight == 0) stringResource(R.string.auto) else "${it.preferredHeight}p",
                    stringResource(it.latency.label),
                )
            },
        ) { open(SettingsPageKey.Playback) }
        HubRow(
            Icons.Filled.TouchApp,
            stringResource(R.string.gestures),
            settings?.let { stringResource(R.string.summary_gestures, it.skipSeconds, "${it.holdSpeed}".removeSuffix(".0")) },
        ) { open(SettingsPageKey.Gestures) }
        Divider()
        HubRow(
            Icons.Filled.Public,
            stringResource(R.string.stream_order),
            settings?.let { streamFilterOrder(it.streamOrder, AppLanguage.streamDefault(currentLocale())).take(3).map { tag -> stringResource(streamFilterLabel(tag)) }.joinToString(" · ") + " …" },
        ) { open(SettingsPageKey.StreamOrder) }
        HubRow(Icons.Filled.Language, stringResource(R.string.app_language), stringResource(AppLanguage.current().label)) {
            open(SettingsPageKey.Language)
        }
        HubRow(Icons.Filled.Info, stringResource(R.string.about), "v${appVersion(app)}") { open(SettingsPageKey.About) }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 4.dp))

@StringRes
internal fun speedLabel(sec: Float): Int = when {
    sec >= 7f -> R.string.speed_slow
    sec <= 4f -> R.string.speed_fast
    else -> R.string.speed_normal
}

internal fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
fun SettingsSubPage(key: String, onBack: () -> Unit) {
    when (SettingsPageKey.entries.firstOrNull { it.key == key }) {
        SettingsPageKey.Account -> SettingsPage(stringResource(R.string.account), onBack) { AccountSection() }
        SettingsPageKey.Chat -> ChatSettingsPage(onBack)
        SettingsPageKey.Danmaku -> DanmakuSettingsPage(onBack)
        SettingsPageKey.Ng -> NgSettingsPage(onBack)
        SettingsPageKey.Playback -> PlaybackSettingsPage(onBack)
        SettingsPageKey.Gestures -> GesturesSettingsPage(onBack)
        SettingsPageKey.StreamOrder -> StreamOrderPage(onBack)
        SettingsPageKey.Language -> LanguageSettingsPage(onBack)
        SettingsPageKey.About -> AboutPage(onBack)
        null -> onBack()
    }
}
