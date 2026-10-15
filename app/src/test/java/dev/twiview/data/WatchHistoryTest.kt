package dev.twiview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryTest {
    @Test fun `保存形式を往復でき、壊れた行は捨てる`() {
        val m = mapOf("1" to WatchPosition(5000, 100, 7), "2" to WatchPosition(0, 0, 1))
        assertEquals(m, WatchHistory.parse(WatchHistory.encode(m)))
        assertEquals(mapOf("1" to WatchPosition(5000, 100, 7)), WatchHistory.parse("1:5000:100:7\nbroken\nx:y:1:2"))
    }

    @Test fun `件数の上限を超えたら古いものから捨てる`() {
        val m = (1..WatchHistory.MAX + 5).associate { "$it" to WatchPosition(1, 1, it.toLong()) }
        val t = WatchHistory.trim(m)
        assertEquals(WatchHistory.MAX, t.size)
        assertTrue("1" !in t)
        assertTrue("${WatchHistory.MAX + 5}" in t)
    }

    @Test fun `冒頭 10 秒以内と末尾 30 秒以内は最初から再生する`() {
        assertEquals(0L, WatchHistory.resumeFrom(null, 100))
        assertEquals(0L, WatchHistory.resumeFrom(WatchPosition(9_000, 100, 0), 100))
        assertEquals(50_000L, WatchHistory.resumeFrom(WatchPosition(50_000, 100, 0), 100))
        assertEquals(0L, WatchHistory.resumeFrom(WatchPosition(80_000, 100, 0), 100))
        assertEquals(80_000L, WatchHistory.resumeFrom(WatchPosition(80_000, 0, 0), 0))
    }
}
