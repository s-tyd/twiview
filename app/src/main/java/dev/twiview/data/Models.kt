package dev.twiview.data

import androidx.annotation.StringRes
import dev.twiview.R
import java.time.Instant

data class Channel(
    val id: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class Game(
    val id: String,
    val name: String,
    val displayName: String,
    val viewers: Int,
    val boxArtUrl: String?,
)

data class Stream(
    val id: String,
    val title: String,
    val viewers: Int,
    val startedAt: Instant?,
    val previewUrl: String?,
    val channel: Channel,
    val game: Game?,
) {
    val freshPreviewUrl: String?
        get() = previewUrl?.let { "$it?t=${System.currentTimeMillis() / 300_000}" }
}

data class ChannelStatus(
    val channel: Channel,
    val stream: Stream?,
    val lastBroadcastAt: Instant?,
    val lastTitle: String?,
    val description: String? = null,
)

data class Video(
    val id: String,
    val title: String,
    val lengthSeconds: Int,
    val views: Int,
    val createdAt: Instant?,
    val thumbnailUrl: String?,
    val game: Game?,
    val mutedSegments: List<MutedSegment> = emptyList(),
)

enum class ClipPeriod(val gql: String, @StringRes val label: Int) {
    Day("LAST_DAY", R.string.clip_period_day),
    Week("LAST_WEEK", R.string.clip_period_week),
    Month("LAST_MONTH", R.string.clip_period_month),
    All("ALL_TIME", R.string.clip_period_all),
}

data class Clip(
    val slug: String,
    val title: String,
    val views: Int,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val createdAt: Instant?,
    val game: Game?,
    val curator: String?,
    val channel: Channel?,
    val videoId: String?,
    val videoOffsetSec: Int,
)

data class MutedSegment(val offsetSec: Int, val durationSec: Int) {
    operator fun contains(sec: Long) = sec >= offsetSec && sec < offsetSec + durationSec
}

data class ChannelPoints(
    val balance: Long,
    val claimId: String?,
    val name: String? = null,
    val iconUrl: String? = null,
)

data class VodComment(val offsetSec: Int, val message: ChatMessage)

data class Page<T>(val items: List<T>, val cursor: String?)

data class SearchResult(val channels: List<ChannelStatus>, val games: List<Game>)

data class ScheduleSegment(
    val id: String,
    val startAt: Instant,
    val endAt: Instant?,
    val title: String,
    val categories: List<String>,
    val cancelled: Boolean,
)
