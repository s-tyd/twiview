package dev.twiview.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.twiview.R
import dev.twiview.data.ChannelStatus
import dev.twiview.data.Clip
import dev.twiview.data.Game
import dev.twiview.data.Stream
import dev.twiview.data.Video
import dev.twiview.ui.theme.C

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamRow(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showGame: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumbnail(stream.freshPreviewUrl, formatUptime(stream.startedAt))
        Column(Modifier.weight(1f)) {
            Text(
                stream.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(stream.channel.avatarUrl, 16.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    stream.channel.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = C.Text,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(4.dp))
                Text(
                    formatCount(stream.viewers, currentLocale()),
                    style = MaterialTheme.typography.bodySmall,
                    color = C.Text,
                )
                if (showGame && stream.game != null) {
                    Text(
                        "  ${stream.game.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = C.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineRow(status: ChannelStatus, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(status.channel.avatarUrl, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                status.channel.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = C.Muted,
                maxLines = 1,
            )
            status.lastTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = C.Faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatAgo(status.lastBroadcastAt, currentLocale()),
            style = MaterialTheme.typography.labelSmall,
            color = C.Faint,
        )
    }
}

@Composable
fun VideoRow(video: Video, watched: Float? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumbnail(video.thumbnailUrl, formatDuration(video.lengthSeconds)) {
            if (watched != null && watched > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(watched.coerceIn(0.02f, 1f)).height(3.dp).background(C.Accent))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val locale = currentLocale()
            Text(
                listOfNotNull(
                    video.createdAt?.let { formatDateTime(it, locale, use24Hour()) },
                    pluralStringResource(R.plurals.views_short, video.views, formatCount(video.views, locale)),
                    video.game?.displayName,
                ).joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = C.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            video.createdAt?.let {
                Text(formatAgoDays(it, locale), style = MaterialTheme.typography.bodySmall, color = C.Faint, maxLines = 1)
            }
        }
    }
}

@Composable
fun GameCell(game: Game, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = game.boxArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(4.dp))
                .background(C.Surface2),
        )
        Text(
            game.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = C.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (game.viewers > 0) {
            Text(formatCount(game.viewers, currentLocale()), style = MaterialTheme.typography.labelSmall, color = C.Muted)
        }
    }
}

@Composable
fun ClipRow(clip: Clip, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumbnail(clip.thumbnailUrl, formatDuration(clip.durationSec))
        Column(Modifier.weight(1f)) {
            Text(clip.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val locale = currentLocale()
            Text(
                listOfNotNull(
                    clip.createdAt?.let { formatDateTime(it, locale, use24Hour()) },
                    pluralStringResource(R.plurals.views_short, clip.views, formatCount(clip.views, locale)),
                    clip.game?.displayName,
                ).joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = C.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(clip.createdAt?.let { formatAgoDays(it, locale) }, clip.curator?.let { stringResource(R.string.clip_by, it) }).joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = C.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Thumbnail(model: String?, badge: String, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(
        Modifier
            .width(136.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(4.dp))
            .background(C.Surface2),
    ) {
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Text(
            badge,
            color = C.Text,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(C.Bg.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
                .padding(horizontal = 3.dp),
        )
        overlay()
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(size).clip(CircleShape).background(C.Surface2),
    )
}

@Composable
fun LiveDot(size: Int = 7) {
    Box(
        Modifier
            .size(size.dp)
            .background(C.Live, CircleShape),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = C.Faint,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
fun CenterMessage(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun FooterLoading() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorFooter(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = C.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(36.dp)) {
        Icon(
            if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (favorite) C.Accent else C.Muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SplitTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SecondaryTabRow(
        selectedTabIndex = selected,
        containerColor = C.Bg,
        contentColor = C.Text,
        indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(selected), color = C.Accent) },
        divider = { HorizontalDivider(color = C.Line) },
    ) {
        labels.forEachIndexed { i, label ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                selectedContentColor = C.Accent,
                unselectedContentColor = C.Muted,
            )
        }
    }
}
