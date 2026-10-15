package dev.twiview.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.twiview.AppLanguage
import dev.twiview.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

enum class FollowSort(@StringRes val label: Int) {
    Viewers(R.string.sort_viewers),
    Newest(R.string.sort_newest),
    Longest(R.string.sort_longest),
    Name(R.string.sort_name),
    Added(R.string.sort_followed),
}

data class Settings(
    val favorites: List<String> = emptyList(),
    val ngWords: List<String> = emptyList(),
    val ngUsers: Set<String> = emptySet(),
    val chatFontSize: Float = 14f,
    val showTimestamps: Boolean = false,
    val language: String? = AppLanguage.streamDefault(),
    val streamOrder: List<String> = emptyList(),
    val preferredHeight: Int = 0,
    val followSort: FollowSort = FollowSort.Viewers,
    val latency: LatencyMode = LatencyMode.Normal,
    val nameMode: NameMode = NameMode.Both,
    val nameColorMode: NameColorMode = NameColorMode.Twitch,
    val brightenNames: Boolean = true,
    val alignNames: Boolean = false,
    val nameWidth: Int = 6,
    val badgeMode: BadgeMode = BadgeMode.All,
    val emoteMode: EmoteMode = EmoteMode.Image,
    val emoteSize: Int = 1,
    val timestampSeconds: Boolean = false,
    val lineSpacing: Int = 1,
    val separator: Separator = Separator.None,
    val deletedMode: DeletedMode = DeletedMode.Strike,
    val showSystemNotices: Boolean = true,
    val highlightFirstMessage: Boolean = false,
    val highlightMentions: Boolean = true,
    val hideChatInput: Boolean = false,
    val tapAction: TapAction = TapAction.Controls,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.Skip,
    val skipSeconds: Int = 10,
    val holdAction: HoldAction = HoldAction.Speed,
    val holdSpeed: Float = 2f,
    val swipeLeft: SwipeAction = SwipeAction.Brightness,
    val swipeRight: SwipeAction = SwipeAction.Volume,
    val swipeSeek: Boolean = true,
    val backgroundPlay: BackgroundPlay = BackgroundPlay.Off,
    val multiLayout: MultiLayout = MultiLayout.Focus,
    val multiNoticeDone: Boolean = false,
    val multiTutorialDone: Boolean = false,
) {
    fun isNg(m: ChatMessage): Boolean =
        m.login in ngUsers || ngWords.any { m.text.contains(it, ignoreCase = true) }
}

enum class LatencyMode(@StringRes val label: Int, val targetOffsetMs: Long) {
    Low(R.string.latency_low, 1_500),
    Normal(R.string.latency_normal, 4_000),
    Stable(R.string.latency_stable, 8_000),
}

enum class NameMode { DisplayOnly, Both, LoginOnly, Hidden }
enum class NameColorMode { Twitch, Uniform, Auto }
enum class BadgeMode { None, All, First }
enum class EmoteMode { Image, Text }
enum class Separator { None, Zebra, Line }
enum class DeletedMode { Strike, Hide, Placeholder }
enum class TapAction { Controls, PlayPause, None }
enum class DoubleTapAction { Skip, Fullscreen, PlayPause, None }
enum class HoldAction { Speed, None }
enum class SwipeAction { Brightness, Volume, None }
enum class MultiLayout(@StringRes val label: Int) { Focus(R.string.multi_layout_focus), Grid(R.string.multi_layout_grid), Line(R.string.multi_layout_line) }

enum class BackgroundPlay(@StringRes val label: Int) { Off(R.string.off), Always(R.string.background_always), Headphones(R.string.background_headphones) }

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

object ChatKeys {
    val nameMode = stringPreferencesKey("chat_name_mode")
    val nameColorMode = stringPreferencesKey("chat_name_color")
    val brightenNames = booleanPreferencesKey("chat_name_brighten")
    val alignNames = booleanPreferencesKey("chat_align_names")
    val nameWidth = intPreferencesKey("chat_name_width")
    val badgeMode = stringPreferencesKey("chat_badge_mode")
    val emoteMode = stringPreferencesKey("chat_emote_mode")
    val emoteSize = intPreferencesKey("chat_emote_size")
    val timestampSeconds = booleanPreferencesKey("chat_ts_seconds")
    val lineSpacing = intPreferencesKey("chat_line_spacing")
    val separator = stringPreferencesKey("chat_separator")
    val deletedMode = stringPreferencesKey("chat_deleted_mode")
    val showSystem = booleanPreferencesKey("chat_show_system")
    val highlightFirst = booleanPreferencesKey("chat_hl_first")
    val highlightMentions = booleanPreferencesKey("chat_hl_mentions")
    val hideInput = booleanPreferencesKey("chat_hide_input")
}

object GestureKeys {
    val tap = stringPreferencesKey("gesture_tap")
    val doubleTap = stringPreferencesKey("gesture_double_tap")
    val skipSeconds = intPreferencesKey("gesture_skip_seconds")
    val hold = stringPreferencesKey("gesture_hold")
    val holdSpeed = floatPreferencesKey("gesture_hold_speed")
    val swipeLeft = stringPreferencesKey("gesture_swipe_left")
    val swipeRight = stringPreferencesKey("gesture_swipe_right")
    val swipeSeek = booleanPreferencesKey("gesture_swipe_seek")
}

class Prefs(private val context: Context) {
    private object K {
        val favorites = stringPreferencesKey("favorites")
        val ngWords = stringPreferencesKey("ng_words")
        val ngUsers = stringPreferencesKey("ng_users")
        val fontSize = floatPreferencesKey("chat_font_size")
        val timestamps = booleanPreferencesKey("chat_timestamps")
        val badges = booleanPreferencesKey("chat_badges")
        val language = stringPreferencesKey("language")
        val streamOrder = stringPreferencesKey("stream_order")
        val height = intPreferencesKey("preferred_height")
        val followSort = stringPreferencesKey("follow_sort")
        val latency = stringPreferencesKey("latency_mode")
        val backgroundPlay = stringPreferencesKey("background_play")
        val multiLayout = stringPreferencesKey("multi_layout")
        val multiNotice = booleanPreferencesKey("multi_notice_done")
        val multiTutorial = booleanPreferencesKey("multi_tutorial_hidden")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            favorites = p[K.favorites].lines(),
            ngWords = p[K.ngWords].lines(),
            ngUsers = p[K.ngUsers].lines().toSet(),
            chatFontSize = p[K.fontSize] ?: 14f,
            showTimestamps = p[K.timestamps] ?: false,
            language = p[K.language].let { if (it == null) AppLanguage.streamDefault() else it.ifEmpty { null } },
            streamOrder = p[K.streamOrder].lines(),
            preferredHeight = p[K.height] ?: 0,
            followSort = enumOr(p[K.followSort], FollowSort.Viewers),
            latency = enumOr(p[K.latency], LatencyMode.Normal),
            nameMode = enumOr(p[ChatKeys.nameMode], NameMode.Both),
            nameColorMode = enumOr(p[ChatKeys.nameColorMode], NameColorMode.Twitch),
            brightenNames = p[ChatKeys.brightenNames] ?: true,
            alignNames = p[ChatKeys.alignNames] ?: false,
            nameWidth = p[ChatKeys.nameWidth] ?: 6,
            badgeMode = enumOr(p[ChatKeys.badgeMode], if (p[K.badges] == false) BadgeMode.None else BadgeMode.All),
            emoteMode = enumOr(p[ChatKeys.emoteMode], EmoteMode.Image),
            emoteSize = p[ChatKeys.emoteSize] ?: 1,
            timestampSeconds = p[ChatKeys.timestampSeconds] ?: false,
            lineSpacing = p[ChatKeys.lineSpacing] ?: 1,
            separator = enumOr(p[ChatKeys.separator], Separator.None),
            deletedMode = enumOr(p[ChatKeys.deletedMode], DeletedMode.Strike),
            showSystemNotices = p[ChatKeys.showSystem] ?: true,
            highlightFirstMessage = p[ChatKeys.highlightFirst] ?: false,
            highlightMentions = p[ChatKeys.highlightMentions] ?: true,
            hideChatInput = p[ChatKeys.hideInput] ?: false,
            tapAction = enumOr(p[GestureKeys.tap], TapAction.Controls),
            doubleTapAction = enumOr(p[GestureKeys.doubleTap], DoubleTapAction.Skip),
            skipSeconds = p[GestureKeys.skipSeconds] ?: 10,
            holdAction = enumOr(p[GestureKeys.hold], HoldAction.Speed),
            holdSpeed = p[GestureKeys.holdSpeed] ?: 2f,
            swipeLeft = enumOr(p[GestureKeys.swipeLeft], SwipeAction.Brightness),
            swipeRight = enumOr(p[GestureKeys.swipeRight], SwipeAction.Volume),
            swipeSeek = p[GestureKeys.swipeSeek] ?: true,
            backgroundPlay = enumOr(p[K.backgroundPlay], BackgroundPlay.Off),
            multiLayout = enumOr(p[K.multiLayout], MultiLayout.Focus),
            multiNoticeDone = p[K.multiNotice] ?: false,
            multiTutorialDone = p[K.multiTutorial] ?: false,
        )
    }

    suspend fun toggleFavorite(login: String) = edit { p ->
        val cur = p[K.favorites].lines()
        val l = login.lowercase()
        p[K.favorites] = (if (l in cur) cur - l else cur + l).joinToString("\n")
    }
    suspend fun setNgWords(text: String) = edit { it[K.ngWords] = text.lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n") }
    suspend fun setNgUsers(text: String) = edit { it[K.ngUsers] = text.lines().map { l -> l.trim().lowercase() }.filter(String::isNotEmpty).joinToString("\n") }
    suspend fun addNgUser(login: String) = edit { p ->
        p[K.ngUsers] = (p[K.ngUsers].lines() + login.lowercase()).distinct().joinToString("\n")
    }
    suspend fun setFontSize(v: Float) = edit { it[K.fontSize] = v }
    suspend fun setTimestamps(v: Boolean) = edit { it[K.timestamps] = v }
    suspend fun <T> put(key: Preferences.Key<T>, value: T) = edit { it[key] = value }
    suspend fun <E : Enum<E>> putEnum(key: Preferences.Key<String>, value: E) = edit { it[key] = value.name }
    suspend fun setLanguage(v: String?) = edit { it[K.language] = v ?: "" }
    suspend fun setStreamOrder(v: List<String>) = edit { it[K.streamOrder] = v.joinToString("\n") }
    suspend fun setPreferredHeight(v: Int) = edit { it[K.height] = v }
    suspend fun setFollowSort(v: FollowSort) = edit { it[K.followSort] = v.name }
    suspend fun setLatency(v: LatencyMode) = edit { it[K.latency] = v.name }
    suspend fun setBackgroundPlay(v: BackgroundPlay) = edit { it[K.backgroundPlay] = v.name }
    suspend fun setMultiLayout(v: MultiLayout) = edit { it[K.multiLayout] = v.name }
    suspend fun setMultiNoticeDone(v: Boolean) = edit { it[K.multiNotice] = v }
    suspend fun setMultiTutorialDone(v: Boolean) = edit { it[K.multiTutorial] = v }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    private fun String?.lines(): List<String> =
        this?.split('\n')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
}
