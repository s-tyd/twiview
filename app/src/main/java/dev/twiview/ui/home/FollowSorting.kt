package dev.twiview.ui.home

import dev.twiview.data.ChannelStatus
import dev.twiview.data.FollowSort
import dev.twiview.data.Stream

fun sortLive(list: List<Stream>, sort: FollowSort, followOrder: List<String>): List<Stream> = when (sort) {
    FollowSort.Viewers -> list.sortedByDescending { it.viewers }
    FollowSort.Newest -> list.sortedByDescending { it.startedAt }
    FollowSort.Longest -> list.sortedBy { it.startedAt }
    FollowSort.Name -> list.sortedBy { it.channel.displayName.lowercase() }
    FollowSort.Added -> indexOf(followOrder.asReversed()).let { o -> list.sortedBy { o[it.channel.login] ?: Int.MAX_VALUE } }
}

fun sortOffline(list: List<ChannelStatus>, sort: FollowSort, followOrder: List<String>): List<ChannelStatus> = when (sort) {
    FollowSort.Name -> list.sortedBy { it.channel.displayName.lowercase() }
    FollowSort.Added -> indexOf(followOrder.asReversed()).let { o -> list.sortedBy { o[it.channel.login] ?: Int.MAX_VALUE } }
    else -> list.sortedByDescending { it.lastBroadcastAt }
}

private fun indexOf(order: List<String>): Map<String, Int> = order.withIndex().associate { (i, l) -> l to i }

enum class FollowSync { NotLoggedIn, NeedsClientId, NeedsLink, Synced }

fun followSyncOf(loggedIn: Boolean, clientId: String?, appToken: String?): FollowSync = when {
    !loggedIn -> FollowSync.NotLoggedIn
    clientId == null -> FollowSync.NeedsClientId
    appToken == null -> FollowSync.NeedsLink
    else -> FollowSync.Synced
}

fun splitStatuses(
    all: List<ChannelStatus>,
    follows: List<String>,
    favorites: List<String>,
): Pair<List<ChannelStatus>, List<ChannelStatus>> {
    val f = follows.toSet()
    val v = favorites.toSet()
    return all.filter { it.channel.login in f } to all.filter { it.channel.login in v }
}
