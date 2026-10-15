package dev.twiview.ui.home

import dev.twiview.data.FollowSort
import dev.twiview.offline
import dev.twiview.stream
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FollowSortingTest {
    private val t0 = Instant.parse("2026-10-13T00:00:00Z")
    private val live = listOf(
        stream("b", viewers = 50, startedAt = t0.plusSeconds(3600), display = "Bravo"),
        stream("a", viewers = 900, startedAt = t0, display = "alpha"),
        stream("c", viewers = 300, startedAt = t0.plusSeconds(7200), display = "Charlie"),
    )
    private val follows = listOf("c", "a", "b")

    private fun liveOrder(s: FollowSort) = sortLive(live, s, follows).map { it.channel.login }

    @Test fun `視聴者が多い順`() = assertEquals(listOf("a", "c", "b"), liveOrder(FollowSort.Viewers))

    @Test fun `配信開始が新しい順`() = assertEquals(listOf("c", "b", "a"), liveOrder(FollowSort.Newest))

    @Test fun `配信時間が長い順`() = assertEquals(listOf("a", "b", "c"), liveOrder(FollowSort.Longest))

    @Test fun `名前順は大文字小文字を区別しない`() = assertEquals(listOf("a", "b", "c"), liveOrder(FollowSort.Name))

    @Test fun `フォローした順は古い順`() = assertEquals(listOf("b", "a", "c"), liveOrder(FollowSort.Added))

    @Test fun `フォロー一覧に無い人は最後`() {
        val r = sortLive(live, FollowSort.Added, listOf("b")).map { it.channel.login }
        assertEquals("b", r.first())
    }

    @Test fun `オフラインは名前順・フォロー順以外は最後に配信した順`() {
        val off = listOf(
            offline("x", last = t0, display = "Xray"),
            offline("y", last = t0.plusSeconds(100), display = "yankee"),
            offline("z", last = null, display = "Zulu"),
        )
        for (s in listOf(FollowSort.Viewers, FollowSort.Newest, FollowSort.Longest)) {
            assertEquals(s.name, listOf("y", "x", "z"), sortOffline(off, s, emptyList()).map { it.channel.login })
        }
        assertEquals(listOf("x", "y", "z"), sortOffline(off, FollowSort.Name, emptyList()).map { it.channel.login })
        assertEquals(listOf("y", "x", "z"), sortOffline(off, FollowSort.Added, listOf("z", "x", "y")).map { it.channel.login })
    }
}
