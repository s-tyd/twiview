package dev.twiview.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.danmakuStore by preferencesDataStore("danmaku")

data class DanmakuSettings(
    val enabled: Boolean = false,
    val opacity: Float = 0.9f,
    val durationSec: Float = 5f,
    val fontPx: Float = 48f,
    val area: Float = 1f,
    val showName: Boolean = false,
    val useNameColor: Boolean = false,
    val hideWithEmoji: Boolean = false,
    val maxItems: Int = FIT,
) {
    companion object {
        const val FIT = -1
        const val UNLIMITED = -2
    }
}

class DanmakuPrefs(private val context: Context) {
    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val opacity = floatPreferencesKey("opacity")
        val duration = floatPreferencesKey("duration")
        val fontPx = floatPreferencesKey("font_px")
        val area = floatPreferencesKey("area")
        val showName = booleanPreferencesKey("show_name")
        val nameColor = booleanPreferencesKey("name_color")
        val hideEmoji = booleanPreferencesKey("hide_with_emoji")
        val max = intPreferencesKey("max_items_v2")
    }

    val settings: Flow<DanmakuSettings> = context.danmakuStore.data.map { p ->
        val d = DanmakuSettings()
        DanmakuSettings(
            enabled = p[K.enabled] ?: d.enabled,
            opacity = p[K.opacity] ?: d.opacity,
            durationSec = p[K.duration] ?: d.durationSec,
            fontPx = p[K.fontPx] ?: d.fontPx,
            area = p[K.area] ?: d.area,
            showName = p[K.showName] ?: d.showName,
            useNameColor = p[K.nameColor] ?: d.useNameColor,
            hideWithEmoji = p[K.hideEmoji] ?: d.hideWithEmoji,
            maxItems = p[K.max] ?: d.maxItems,
        )
    }

    suspend fun update(s: DanmakuSettings) {
        context.danmakuStore.edit {
            it[K.enabled] = s.enabled
            it[K.opacity] = s.opacity
            it[K.duration] = s.durationSec
            it[K.fontPx] = s.fontPx
            it[K.area] = s.area
            it[K.showName] = s.showName
            it[K.nameColor] = s.useNameColor
            it[K.hideEmoji] = s.hideWithEmoji
            it[K.max] = s.maxItems
        }
    }
}
