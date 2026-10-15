package dev.twiview.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.BadgeMode
import dev.twiview.data.ChatKeys
import dev.twiview.data.ChatMessage
import dev.twiview.data.DeletedMode
import dev.twiview.data.EmoteMode
import dev.twiview.data.EmoteRange
import dev.twiview.data.NameColorMode
import dev.twiview.data.NameMode
import dev.twiview.data.Prefs
import dev.twiview.data.Separator
import dev.twiview.data.Settings
import dev.twiview.ui.player.ChatLine
import dev.twiview.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val previewBadges = mapOf(
    "broadcaster/1" to "https://static-cdn.jtvnw.net/badges/v1/5527c58c-fb7d-422d-b71b-f309dcb85cc1/2",
    "moderator/1" to "https://static-cdn.jtvnw.net/badges/v1/3267646d-33f0-4b17-b3df-f923a41db1d0/2",
    "vip/1" to "https://static-cdn.jtvnw.net/badges/v1/b817aba4-fad8-49e2-b88a-7cc744dfa6ec/2",
)
private const val PREVIEW_ME = "you"

private fun previewMessages(res: Resources): List<ChatMessage> {
    val now = System.currentTimeMillis()
    fun msg(id: String, login: String, name: String, color: Int?, text: String, badges: List<String> = emptyList(),
            emotes: List<EmoteRange> = emptyList(), deleted: Boolean = false, first: Boolean = false, system: String? = null) =
        ChatMessage(id, "", login, name, color, text, emotes, badges, now - id.length * 7_000L,
            deleted = deleted, firstMessage = first, systemText = system)
    val greeting = res.getString(R.string.preview_greeting)
    val kappa = greeting.indexOf("Kappa")
    return listOf(
        msg("p1", "streamer_jp", res.getString(R.string.preview_streamer), 0xFF9146FF.toInt(), greeting, listOf("broadcaster/1"),
            if (kappa >= 0) listOf(EmoteRange("25", kappa, kappa + 4)) else emptyList()),
        msg("p2", "very_long_name_viewer_2026", res.getString(R.string.preview_long_name), 0xFF1E90FF.toInt(),
            res.getString(R.string.preview_long_text), listOf("moderator/1", "vip/1")),
        msg("p3", "dark_blue", "dark_blue", 0xFF0000FF.toInt(), res.getString(R.string.preview_mention, PREVIEW_ME)),
        msg("p4", "newcomer", res.getString(R.string.preview_newcomer), null, res.getString(R.string.preview_first), first = true),
        msg("p5", "troll", "troll", 0xFFFF4500.toInt(), res.getString(R.string.preview_deleted), deleted = true),
        msg("p6", "subber", "subber", 0xFF00FF7F.toInt(), res.getString(R.string.preview_thanks),
            system = res.getString(R.string.preview_sub, "subber")),
    )
}

@Composable
fun ChatSettingsPage(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val settings by prefs.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    SettingsPage(
        title = stringResource(R.string.chat_display),
        onBack = onBack,
        pinned = { ChatPreview(s) },
    ) {
        ChatDisplaySettingsContent(s, prefs, scope)
    }
}

@Composable
fun ChatPreview(s: Settings, maxHeight: Dp = 220.dp, count: Int = Int.MAX_VALUE) {
    val res = LocalContext.current.resources
    val samples = remember(res, count) { previewMessages(res).take(count) }
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(C.Surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        samples.forEachIndexed { i, m ->
            ChatLine(
                m, s, previewBadges, onUser = null, myLogin = PREVIEW_ME,
                shaded = s.separator == Separator.Zebra && i % 2 == 1,
            )
        }
    }
}

@Composable
fun ChatDisplaySettingsContent(s: Settings, prefs: Prefs, scope: CoroutineScope) {
    fun <T> put(key: Preferences.Key<T>, v: T) = scope.launch { prefs.put(key, v) }
    fun <E : Enum<E>> putE(key: Preferences.Key<String>, v: E) = scope.launch { prefs.putEnum(key, v) }
    Column(Modifier.fillMaxWidth()) {
        ToggleRow(stringResource(R.string.hide_chat_input), s.hideChatInput) { put(ChatKeys.hideInput, it) }
        GroupLabel(stringResource(R.string.names))
        ChipRow(
            listOf(
                NameMode.Both to stringResource(R.string.name_both), NameMode.DisplayOnly to stringResource(R.string.name_display),
                NameMode.LoginOnly to stringResource(R.string.name_login), NameMode.Hidden to stringResource(R.string.name_hidden),
            ),
            s.nameMode,
        ) { putE(ChatKeys.nameMode, it) }
        SubLabel(stringResource(R.string.name_color))
        ChipRow(
            listOf(
                NameColorMode.Twitch to stringResource(R.string.name_color_twitch),
                NameColorMode.Auto to stringResource(R.string.name_color_auto),
                NameColorMode.Uniform to stringResource(R.string.name_color_uniform),
            ),
            s.nameColorMode,
        ) { putE(ChatKeys.nameColorMode, it) }
        if (s.nameColorMode == NameColorMode.Twitch) {
            ToggleRow(stringResource(R.string.brighten_names), s.brightenNames) { put(ChatKeys.brightenNames, it) }
        }
        ToggleRow(stringResource(R.string.align_names), s.alignNames) { put(ChatKeys.alignNames, it) }
        if (s.alignNames) {
            SliderRow(stringResource(R.string.name_width), stringResource(R.string.name_width_value, s.nameWidth), s.nameWidth.toFloat(), 3f..12f, 8) { put(ChatKeys.nameWidth, it.toInt()) }
        }
        SubLabel(stringResource(R.string.badges))
        ChipRow(
            listOf(BadgeMode.All to stringResource(R.string.all), BadgeMode.First to stringResource(R.string.badges_first), BadgeMode.None to stringResource(R.string.badges_none)),
            s.badgeMode,
        ) { putE(ChatKeys.badgeMode, it) }

        GroupLabel(stringResource(R.string.appearance))
        SliderRow(stringResource(R.string.text_size), "${s.chatFontSize.toInt()}", s.chatFontSize, 10f..22f, 11) { v -> scope.launch { prefs.setFontSize(v) } }
        SubLabel(stringResource(R.string.line_spacing))
        ChipRow(listOf(0 to stringResource(R.string.spacing_compact), 1 to stringResource(R.string.spacing_standard), 2 to stringResource(R.string.spacing_relaxed)), s.lineSpacing) { put(ChatKeys.lineSpacing, it) }
        SubLabel(stringResource(R.string.dividers))
        ChipRow(
            listOf(Separator.None to stringResource(R.string.none), Separator.Zebra to stringResource(R.string.dividers_zebra), Separator.Line to stringResource(R.string.dividers_line)),
            s.separator,
        ) { putE(ChatKeys.separator, it) }
        SubLabel(stringResource(R.string.emotes))
        ChipRow(listOf(EmoteMode.Image to stringResource(R.string.emotes_image), EmoteMode.Text to stringResource(R.string.emotes_text)), s.emoteMode) { putE(ChatKeys.emoteMode, it) }
        if (s.emoteMode == EmoteMode.Image) {
            ChipRow(listOf(0 to stringResource(R.string.size_small), 1 to stringResource(R.string.size_medium), 2 to stringResource(R.string.size_large)), s.emoteSize) { put(ChatKeys.emoteSize, it) }
        }

        GroupLabel(stringResource(R.string.content))
        SubLabel(stringResource(R.string.timestamps))
        ChipRow(
            listOf(0 to stringResource(R.string.none), 1 to "12:34", 2 to "12:34:56"),
            when { !s.showTimestamps -> 0; s.timestampSeconds -> 2; else -> 1 },
        ) {
            scope.launch {
                prefs.setTimestamps(it != 0)
                if (it != 0) prefs.put(ChatKeys.timestampSeconds, it == 2)
            }
        }
        SubLabel(stringResource(R.string.deleted_messages))
        ChipRow(
            listOf(DeletedMode.Strike to stringResource(R.string.deleted_strike), DeletedMode.Placeholder to stringResource(R.string.chat_deleted), DeletedMode.Hide to stringResource(R.string.deleted_hide)),
            s.deletedMode,
        ) { putE(ChatKeys.deletedMode, it) }
        ToggleRow(stringResource(R.string.system_notices), s.showSystemNotices) { put(ChatKeys.showSystem, it) }

        GroupLabel(stringResource(R.string.highlights))
        ToggleRow(stringResource(R.string.highlight_first), s.highlightFirstMessage) { put(ChatKeys.highlightFirst, it) }
        ToggleRow(stringResource(R.string.highlight_mentions), s.highlightMentions) { put(ChatKeys.highlightMentions, it) }
    }
}
