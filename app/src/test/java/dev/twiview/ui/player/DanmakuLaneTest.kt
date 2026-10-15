package dev.twiview.ui.player

import dev.twiview.data.EmoteRange
import dev.twiview.msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuLaneTest {
    private data class B(override val lane: Int, override val startNs: Long, override val width: Float) : LaneOccupant

    private val w = 1000f
    private val d = 1_000_000_000L

    private fun startedSoTailIsAt(tailX: Float, pw: Float, now: Long): Long {
        val v = (w + pw) / d
        val elapsed = ((w + pw - tailX) / v).toLong()
        return now - elapsed
    }

    @Test fun `誰もいなければ一番上の段`() {
        assertEquals(0, freeLane(emptyList(), lanes = 5, now = 0, durationNs = d, width = w, newW = 100f))
    }

    @Test fun `入口がまだ塞がっている段は飛ばす`() {
        val now = 10_000_000_000L
        val justStarted = B(lane = 0, startNs = now, width = 200f)
        assertEquals(1, freeLane(listOf(justStarted), 5, now, d, w, 100f))
    }

    @Test fun `全段塞がっていれば null`() {
        val now = 10_000_000_000L
        val all = (0 until 3).map { B(it, now, 200f) }
        assertNull(freeLane(all, 3, now, d, w, 100f))
    }

    @Test fun `前の弾の尾が十分入っていれば同じ段に入れる`() {
        val now = 10_000_000_000L
        val prev = B(0, startedSoTailIsAt(tailX = 500f, pw = 100f, now = now), 100f)
        assertEquals(0, freeLane(listOf(prev), 5, now, d, w, newW = 100f))
    }

    @Test fun `長い弾は速いので、前の短い弾に追いつくなら入れない`() {
        val now = 10_000_000_000L
        val prev = B(0, startedSoTailIsAt(tailX = 900f, pw = 100f, now = now), 100f)
        assertEquals(0, freeLane(listOf(prev), 2, now, d, w, newW = 100f))
        assertEquals(1, freeLane(listOf(prev), 2, now, d, w, newW = 2000f))
    }

    @Test fun `同じ段の判定は一番新しい弾で行う`() {
        val now = 10_000_000_000L
        val old = B(0, startedSoTailIsAt(-500f, 100f, now), 100f)
        val fresh = B(0, now, 300f)
        assertEquals(1, freeLane(listOf(old, fresh), 2, now, d, w, 100f))
    }

    @Test fun `範囲外の段にいる弾は数えない(流す範囲を狭めた直後)`() {
        val now = 10_000_000_000L
        val outside = B(lane = 7, startNs = now, width = 200f)
        assertEquals(0, freeLane(listOf(outside), 3, now, d, w, 100f))
    }

    @Test fun `無制限時は一番進んでいる段に重ねる`() {
        val now = 10_000_000_000L
        val bullets = listOf(
            B(0, startedSoTailIsAt(950f, 100f, now), 100f),
            B(1, startedSoTailIsAt(400f, 100f, now), 100f),
            B(2, startedSoTailIsAt(990f, 100f, now), 100f),
        )
        assertEquals(1, leastBusyLane(bullets, 3, now, d, w))
    }

    @Test fun `エモートか絵文字が1つでもあれば絵文字入り`() {
        assertTrue(containsEmoji(msg("1", text = "Kappa 草", emotes = listOf(EmoteRange("25", 0, 4)))))
        assertTrue(containsEmoji(msg("2", text = "うおお🔥")))
        assertTrue(containsEmoji(msg("3", text = "👍🏻")))
        assertTrue(containsEmoji(msg("4", text = "いいね❤")))
    }

    @Test fun `文字だけなら絵文字入りではない`() {
        assertFalse(containsEmoji(msg("1", text = "wwwwww")))
        assertFalse(containsEmoji(msg("2", text = "88888888 ナイス！")))
        assertFalse(containsEmoji(msg("3", text = "今の見た？(笑)")))
    }
}
