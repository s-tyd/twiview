package dev.twiview.ui.player

import dev.twiview.data.LatencyMode
import dev.twiview.data.VodComment

internal fun qualityLabel(height: Int, frameRate: Float): String = "${height}p${if (frameRate >= 50f) "60" else ""}"

internal fun <T> orderQualities(list: List<T>, height: (T) -> Int, label: (T) -> String): List<T> =
    list.sortedWith(compareByDescending<T> { height(it) }.thenByDescending { label(it) }).distinctBy(label)

internal fun <T> preferredQuality(ordered: List<T>, preferredHeight: Int, height: (T) -> Int): T? =
    if (preferredHeight <= 0) null else ordered.firstOrNull { height(it) <= preferredHeight }

internal fun clampTarget(ms: Long, mode: LatencyMode): Long =
    ms.coerceIn(mode.targetOffsetMs, mode.targetOffsetMs + 4_000)

internal fun startTarget(mode: LatencyMode, learned: Long?): Long = learned ?: maxOf(mode.targetOffsetMs, 3_000)

internal fun shouldCatchUp(catchingUp: Boolean, playing: Boolean, behindMs: Long?, bufferedMs: Long): Boolean = when {
    !playing || behindMs == null -> false
    catchingUp -> behindMs > 150 && bufferedMs > 800
    else -> behindMs > 400 && bufferedMs > 1_200
}

internal fun nextReplayOffset(fetched: List<VodComment>, from: Int): Int {
    val last = fetched.maxOfOrNull { it.offsetSec } ?: return Int.MAX_VALUE
    return if (last <= from) from + 1 else last
}
