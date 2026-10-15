package dev.twiview.ui.player

import dev.twiview.data.LatencyMode
import dev.twiview.data.VodComment
import dev.twiview.msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {
    private data class Q(val height: Int, val label: String)
    private fun q(height: Int, fps: Float) = Q(height, qualityLabel(height, fps))

    @Test fun `画質の表示名は 50fps 以上を 60 と付ける`() {
        assertEquals("1080p60", qualityLabel(1080, 60f))
        assertEquals("720p60", qualityLabel(720, 59.94f))
        assertEquals("480p", qualityLabel(480, 30f))
    }

    @Test fun `画質は高い順、同じ高さは 60fps が先、同名は1つ`() {
        val list = listOf(q(480, 30f), q(1080, 60f), q(720, 30f), q(720, 60f), q(1080, 60f), q(160, 30f))
        assertEquals(
            listOf("1080p60", "720p60", "720p", "480p", "160p"),
            orderQualities(list, { it.height }, { it.label }).map { it.label },
        )
    }

    @Test fun `既定の画質は希望以下で一番高いもの。0 や低すぎる希望は自動`() {
        val ordered = orderQualities(listOf(q(1080, 60f), q(720, 60f), q(480, 30f)), { it.height }, { it.label })
        assertEquals("720p60", preferredQuality(ordered, 720) { it.height }?.label)
        assertEquals("720p60", preferredQuality(ordered, 900) { it.height }?.label)
        assertEquals("1080p60", preferredQuality(ordered, 1080) { it.height }?.label)
        assertNull(preferredQuality(ordered, 0) { it.height })
        assertNull(preferredQuality(ordered, 100) { it.height })
    }

    @Test fun `遅延の目標は設定値から +4秒 の範囲に収める`() {
        assertEquals(1_500L, clampTarget(500, LatencyMode.Low))
        assertEquals(3_000L, clampTarget(3_000, LatencyMode.Low))
        assertEquals(5_500L, clampTarget(9_000, LatencyMode.Low))
        assertEquals(8_000L, clampTarget(1_000, LatencyMode.Stable))
    }

    @Test fun `最初の目標は覚えた値か、無ければ設定値と3秒の大きい方`() {
        assertEquals(2_200L, startTarget(LatencyMode.Low, learned = 2_200))
        assertEquals(3_000L, startTarget(LatencyMode.Low, learned = null))
        assertEquals(4_000L, startTarget(LatencyMode.Normal, learned = null))
        assertEquals(8_000L, startTarget(LatencyMode.Stable, learned = null))
    }

    @Test fun `追いつきは余裕があるとき始め、追いついたらやめる`() {
        assertFalse(shouldCatchUp(catchingUp = false, playing = false, behindMs = 1_000, bufferedMs = 5_000))
        assertFalse(shouldCatchUp(catchingUp = false, playing = true, behindMs = null, bufferedMs = 5_000))
        assertFalse(shouldCatchUp(catchingUp = false, playing = true, behindMs = 300, bufferedMs = 5_000))
        assertFalse(shouldCatchUp(catchingUp = false, playing = true, behindMs = 1_000, bufferedMs = 1_200))
        assertTrue(shouldCatchUp(catchingUp = false, playing = true, behindMs = 500, bufferedMs = 2_000))
        assertTrue(shouldCatchUp(catchingUp = true, playing = true, behindMs = 200, bufferedMs = 900))
        assertFalse(shouldCatchUp(catchingUp = true, playing = true, behindMs = 100, bufferedMs = 5_000))
        assertFalse(shouldCatchUp(catchingUp = true, playing = true, behindMs = 500, bufferedMs = 700))
    }

    @Test fun `過去配信コメントの次の取得位置`() {
        fun c(sec: Int) = VodComment(sec, msg("m$sec"))
        assertEquals(Int.MAX_VALUE, nextReplayOffset(emptyList(), 100))
        assertEquals(150, nextReplayOffset(listOf(c(120), c(150), c(130)), 100))
        assertEquals(101, nextReplayOffset(listOf(c(100), c(100)), 100))
        assertEquals(101, nextReplayOffset(listOf(c(90)), 100))
    }
}
