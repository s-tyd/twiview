package dev.twiview.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.twiview.R
import dev.twiview.ui.common.formatClock
import dev.twiview.ui.common.formatScheduleDay
import dev.twiview.ui.theme.C
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val AXIS_COLUMN = 30.dp
private val MIN_BLOCK = 14.dp
private const val DAY_MIN = 24 * 60

@Composable
fun WeekScheduleView(
    modifier: Modifier,
    table: WeekTable,
    today: LocalDate,
    zone: ZoneId,
    locale: Locale,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenVod: (String) -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.schedule_prev_week), tint = C.Muted) }
            Text(
                "${formatScheduleDay(table.start, locale)} – ${formatScheduleDay(table.end, locale)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.schedule_next_week), tint = C.Muted) }
            TextButton(onClick = onToday, enabled = today !in table.start..table.end) { Text(stringResource(R.string.schedule_this_week)) }
        }
        Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
            Spacer(Modifier.width(AXIS_COLUMN))
            table.rows.forEach { row ->
                val isToday = row.day == today
                Column(Modifier.weight(1f).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(row.day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), fontSize = 10.sp, color = if (isToday) C.Accent else C.Faint)
                    Text(
                        "${row.day.dayOfMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) C.Accent else C.Muted,
                    )
                }
            }
        }
        if (table.isEmpty) {
            Text(
                stringResource(R.string.schedule_week_empty),
                color = C.Faint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(end = 8.dp)) {
            val gridHeight = maxHeight
            val hourHeight = gridHeight / 24
            val ticks = remember(gridHeight) { axisTicks(0, DAY_MIN, maxTicks = (gridHeight.value / 14f).toInt().coerceAtLeast(2)) }
            fun yOf(minute: Int): Dp = hourHeight * (minute / 60f)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(AXIS_COLUMN).fillMaxHeight()) {
                    for (h in ticks) {
                        Text(
                            "%02d".format(h),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = C.Faint,
                            modifier = Modifier.offset(y = yOf(h * 60) - 6.dp).padding(start = 6.dp),
                        )
                    }
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val colW = size.width / 7
                            for (i in 0..7) drawLine(C.Line, Offset(colW * i, 0f), Offset(colW * i, size.height), 1f)
                            for (h in ticks) {
                                val y = yOf(h * 60).toPx()
                                drawLine(C.Line, Offset(0f, y), Offset(size.width, y), 1f)
                            }
                        },
                ) {
                    table.rows.forEachIndexed { i, row ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (row.day == today) Modifier.background(C.Accent.copy(alpha = 0.05f)) else Modifier),
                        ) {
                            table.rows.getOrNull(i - 1)?.items.orEmpty().forEach { item ->
                                val over = minuteOfDay(item.start, zone) + Duration.between(item.start, item.end).toMinutes().toInt() - DAY_MIN
                                if (over > 0) WeekBlock(item, 0.dp, yOf(over.coerceAtMost(DAY_MIN)).coerceAtLeast(MIN_BLOCK), onOpenVod, continued = true)
                            }
                            for (item in row.items) {
                                val lengthMin = Duration.between(item.start, item.end).toMinutes().toInt()
                                val top = yOf(minuteOfDay(item.start, zone))
                                val height = yOf(lengthMin).coerceAtLeast(MIN_BLOCK).coerceAtMost(gridHeight - top).coerceAtLeast(0.dp)
                                WeekBlock(item, top, height, onOpenVod)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun WeekBlock(item: WeekItem, top: Dp, height: Dp, onOpenVod: (String) -> Unit, continued: Boolean = false) {
    val past = item.vodId != null
    Box(
        Modifier
            .offset(y = top)
            .height(height)
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (past) Modifier.background(C.Accent.copy(alpha = 0.3f))
                else Modifier.border(1.dp, C.Accent.copy(alpha = if (item.cancelled) 0.3f else 0.7f), RoundedCornerShape(4.dp)),
            )
            .then(if (past) Modifier.clickable { onOpenVod(item.vodId!!) } else Modifier)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        if (height < 28.dp || continued) return@Box
        Column {
            Text(
                formatClock(item.start.toEpochMilli()),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                color = C.Muted,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            )
            val lines = ((height.value - 18f) / 12f).toInt()
            if (lines >= 1) {
                Text(
                    item.title.ifBlank { item.category ?: "" },
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.cancelled) C.Faint else C.Text,
                    textDecoration = if (item.cancelled) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}
