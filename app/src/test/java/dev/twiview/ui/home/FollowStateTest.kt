package dev.twiview.ui.home

import dev.twiview.channel
import dev.twiview.data.ChannelStatus
import dev.twiview.offline
import dev.twiview.stream
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowStateTest {
    @Test fun `同期できない理由を順に判定する`() {
        assertEquals(FollowSync.NotLoggedIn, followSyncOf(loggedIn = false, clientId = "cid", appToken = "tok"))
        assertEquals(FollowSync.NeedsClientId, followSyncOf(loggedIn = true, clientId = null, appToken = "tok"))
        assertEquals(FollowSync.NeedsLink, followSyncOf(loggedIn = true, clientId = "cid", appToken = null))
        assertEquals(FollowSync.Synced, followSyncOf(loggedIn = true, clientId = "cid", appToken = "tok"))
    }

    @Test fun `まとめて取った状態をフォローとお気に入りに分け、両方にいる人は両方に出す`() {
        val a = ChannelStatus(channel("a"), stream("a"), null, null)
        val all = listOf(a, offline("b"), offline("c"), offline("d"))
        val (follows, favorites) = splitStatuses(all, follows = listOf("a", "b"), favorites = listOf("b", "c"))
        assertEquals(listOf("a", "b"), follows.map { it.channel.login })
        assertEquals(listOf("b", "c"), favorites.map { it.channel.login })
    }

    @Test fun `どちらにも無い人は捨て、順番は取った順のまま`() {
        val all = listOf(offline("z"), offline("y"), offline("x"))
        val (follows, favorites) = splitStatuses(all, follows = emptyList(), favorites = listOf("x", "z"))
        assertEquals(emptyList<ChannelStatus>(), follows)
        assertEquals(listOf("z", "x"), favorites.map { it.channel.login })
    }
}
