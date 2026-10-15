package dev.twiview.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFiltersTest {
    @Test fun `未設定なら自分の言語、すべて、ユーザー数の多い順`() {
        assertEquals(listOf("ja", "all", "en", "pt", "de", "fr", "ru", "es", "it", "tr", "ko"), streamFilterOrder(emptyList(), "ja"))
        assertEquals(listOf("all", "en", "pt", "de", "fr", "ru", "es", "it", "tr", "ko", "ja"), streamFilterOrder(emptyList(), null))
    }

    @Test fun `保存した並びを使い、知らない値は捨て、足りない言語は末尾に足す`() {
        assertEquals(
            listOf("ko", "ja", "all", "en", "pt", "de", "fr", "ru", "es", "it", "tr"),
            streamFilterOrder(listOf("ko", "ja", "nl", "all", "ja"), "ja"),
        )
    }
}
