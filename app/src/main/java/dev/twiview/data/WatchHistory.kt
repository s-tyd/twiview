package dev.twiview.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.watchStore by preferencesDataStore("watch_history")

data class WatchPosition(val positionMs: Long, val lengthSec: Int, val updatedAt: Long)

class WatchHistory(private val context: Context) {
    private val key = stringPreferencesKey("positions")

    val positions: Flow<Map<String, WatchPosition>> = context.watchStore.data.map { parse(it[key]) }

    suspend fun get(id: String): WatchPosition? = positions.first()[id]

    suspend fun set(id: String, positionMs: Long, lengthSec: Int) {
        context.watchStore.edit { p ->
            p[key] = encode(trim(parse(p[key]) + (id to WatchPosition(positionMs, lengthSec, System.currentTimeMillis()))))
        }
    }

    companion object {
        const val MAX = 300

        fun parse(s: String?): Map<String, WatchPosition> = s.orEmpty().lineSequence().mapNotNull { line ->
            val f = line.split(':')
            if (f.size != 4) return@mapNotNull null
            val pos = f[1].toLongOrNull() ?: return@mapNotNull null
            f[0] to WatchPosition(pos, f[2].toIntOrNull() ?: 0, f[3].toLongOrNull() ?: 0)
        }.toMap()

        fun encode(m: Map<String, WatchPosition>): String =
            m.entries.joinToString("\n") { "${it.key}:${it.value.positionMs}:${it.value.lengthSec}:${it.value.updatedAt}" }

        fun trim(m: Map<String, WatchPosition>): Map<String, WatchPosition> =
            if (m.size <= MAX) m else m.entries.sortedByDescending { it.value.updatedAt }.take(MAX).associate { it.key to it.value }

        fun resumeFrom(saved: WatchPosition?, lengthSec: Int): Long {
            val p = saved?.positionMs ?: return 0
            if (p < 10_000) return 0
            if (lengthSec > 0 && p > lengthSec * 1000L - 30_000) return 0
            return p
        }
    }
}
