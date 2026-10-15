package dev.twiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    private fun route(vararg seg: String) = MainActivity.routeForPath(seg.toList())

    @Test fun `チャンネル URL は配信画面へ(小文字に揃える)`() {
        assertEquals("player/stylishnoob4", route("StylishNoob4"))
    }

    @Test fun `アーカイブ一覧 URL はチャンネル画面へ`() {
        assertEquals("channel/kato_junichi0817", route("kato_junichi0817", "videos"))
    }

    @Test fun `動画 URL はアーカイブ再生へ`() {
        assertEquals("vod/2898313736", route("videos", "2898313736"))
    }

    @Test fun `数字でない動画 id は開かない`() {
        assertNull(route("videos", "abc"))
    }

    @Test fun `Twitch の予約パスはチャンネル扱いしない`() {
        for (p in listOf("directory", "settings", "search", "activate", "login", "p")) {
            assertNull(p, route(p))
        }
    }

    @Test fun `ログイン名として不正な形は開かない`() {
        assertNull(route("a"))
        assertNull(route("has-dash"))
        assertNull(route("x".repeat(26)))
        assertNull(route())
    }

    @Test fun `クリップなど深い URL はチャンネルの配信画面へ`() {
        assertEquals("clip/PricklyTenuousLatte", route("kato", "clip", "PricklyTenuousLatte"))
        assertEquals("player/kato", route("kato", "about"))
    }

    @Test fun `動画一覧の URL だけ・大文字の videos は開かない`() {
        assertNull(route("videos"))
        assertNull(route("Videos", "123"))
    }
}
