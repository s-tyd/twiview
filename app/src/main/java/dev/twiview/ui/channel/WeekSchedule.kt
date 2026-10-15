package dev.twiview.ui.channel

import dev.twiview.data.ScheduleSegment
import dev.twiview.data.Video
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeekItem(
    val start: Instant,
    val end: Instant,
    val title: String,
    val category: String?,
    val vodId: String?,
    val cancelled: Boolean = false,
)

data class WeekRow(val day: LocalDate, val items: List<WeekItem>)

data class WeekTable(val start: LocalDate, val rows: List<WeekRow>, val axisStartMin: Int, val axisEndMin: Int) {
    val end: LocalDate get() = start.plusDays(6)
    val isEmpty: Boolean get() = rows.all { it.items.isEmpty() }
}

fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val MIN_AXIS_MINUTES = 6 * 60
private const val MAX_AXIS_END = 48 * 60

fun buildWeek(videos: List<Video>, segments: List<ScheduleSegment>, weekStart: LocalDate, zone: ZoneId): WeekTable {
    val items = videos.mapNotNull { v ->
        val start = v.createdAt ?: return@mapNotNull null
        WeekItem(start, start.plusSeconds(v.lengthSeconds.toLong()), v.title, v.game?.displayName, v.id)
    } + segments.map { s ->
        WeekItem(s.startAt, s.endAt ?: s.startAt.plus(Duration.ofHours(1)), s.title, s.categories.firstOrNull(), null, s.cancelled)
    }
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val byDay = items.groupBy { it.start.atZone(zone).toLocalDate() }
    val rows = days.map { d -> WeekRow(d, byDay[d].orEmpty().sortedBy { it.start }) }
    val placed = rows.flatMap { it.items }
    var axisStart = 0
    var axisEnd = 24 * 60
    if (placed.isNotEmpty()) {
        axisStart = placed.minOf { minuteOfDay(it.start, zone) } / 60 * 60
        axisEnd = placed.maxOf { minuteOfDay(it.start, zone) + Duration.between(it.start, it.end).toMinutes().toInt() }
            .let { (it + 59) / 60 * 60 }
            .coerceAtMost(MAX_AXIS_END)
        if (axisEnd - axisStart < MIN_AXIS_MINUTES) axisEnd = (axisStart + MIN_AXIS_MINUTES).coerceAtMost(MAX_AXIS_END)
    }
    return WeekTable(weekStart, rows, axisStart, axisEnd)
}

fun minuteOfDay(t: Instant, zone: ZoneId): Int = t.atZone(zone).let { it.hour * 60 + it.minute }

fun axisTicks(startMin: Int, endMin: Int, maxTicks: Int = 8): List<Int> {
    for (step in listOf(1, 2, 3, 6, 12)) {
        val first = (startMin / 60 + step - 1) / step * step
        val ticks = generateSequence(first) { it + step }.takeWhile { it * 60 <= endMin }.toList()
        if (ticks.size <= maxTicks) return ticks
    }
    return emptyList()
}
