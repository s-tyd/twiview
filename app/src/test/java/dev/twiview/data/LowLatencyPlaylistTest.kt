package dev.twiview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowLatencyPlaylistTest {
    @Test fun `作成途中の区切りを直前と同じ長さの通常の区切りにし、計測除外の目印を付ける`() {
        val src = """
            #EXTM3U
            #EXT-X-TARGETDURATION:5
            #EXTINF:2.002,live
            https://x/1.ts
            #EXT-X-TWITCH-PREFETCH:https://x/2.ts
            #EXT-X-TWITCH-PREFETCH:https://x/3.ts
        """.trimIndent()
        val out = expandPrefetch(src).lines()
        assertEquals(
            listOf("#EXTINF:2.002,live", "https://x/2.ts#$PREFETCH_MARK", "#EXTINF:2.002,live", "https://x/3.ts#$PREFETCH_MARK"),
            out.subList(4, 8),
        )
        assertEquals("読み直しの間隔を詰めるため区切りの最大秒数を短くする", "#EXT-X-TARGETDURATION:$LOW_LATENCY_TARGET_DURATION", out[1])
    }

    @Test fun `広告は区切りの名前が live 以外で、広告の範囲が示されているときだけ`() {
        val ad = "#EXT-X-DATERANGE:ID=\"stitched-ad-1\",CLASS=\"twitch-stitched-ad\",DURATION=30\n#EXT-X-DISCONTINUITY\n#EXTINF:2.000,Amazon|123\nhttps://x/ad1.ts\n"
        assertTrue(isAdPlaylist(ad))
        assertFalse(isAdPlaylist(ad + "#EXTINF:2.000,live\nhttps://x/1.ts\n#EXT-X-TWITCH-PREFETCH:https://x/2.ts\n"))
        assertFalse(isAdPlaylist("#EXTM3U\n#EXTINF:2.000,live\nhttps://x/1.ts\n"))
    }

    @Test fun `作成途中の区切りが無ければそのまま`() {
        val src = "#EXTM3U\n#EXTINF:2.000,live\nhttps://x/1.ts"
        assertEquals(src, expandPrefetch(src))
    }

    @Test fun `直前の区切りが無ければ 2秒として扱う`() {
        val out = expandPrefetch("#EXTM3U\n#EXT-X-TWITCH-PREFETCH:https://x/1.ts").lines()
        assertEquals("#EXTINF:2.000,live", out[1])
        assertEquals("https://x/1.ts#$PREFETCH_MARK", out[2])
    }

    @Test fun `他の行は変えず、CRLF でも読める`() {
        val src = "#EXTM3U\r\n#EXT-X-DISCONTINUITY\r\n#EXTINF:1.500,live\r\nhttps://x/1.ts\r\n#EXT-X-TWITCH-PREFETCH:https://x/2.ts\r\n"
        val out = expandPrefetch(src)
        assertTrue(out.contains("#EXT-X-DISCONTINUITY\n"))
        assertTrue(out.contains("https://x/1.ts\n"))
        assertTrue(out.contains("#EXTINF:1.500,live\nhttps://x/2.ts#$PREFETCH_MARK\n"))
        assertTrue("変換後に作成途中の印が残っている", "#EXT-X-TWITCH-PREFETCH" !in out)
    }
}
