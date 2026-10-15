package dev.twiview.ui.channel

import dev.twiview.data.ScheduleSegment
import dev.twiview.data.Video
import dev.twiview.ui.common.formatScheduleDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class WeekScheduleTest {
    private val jst = ZoneId.of("Asia/Tokyo")
    private val monday = LocalDate.of(2026, 10, 12)
    private fun video(id: String, start: String, hours: Int) =
        Video(id, "t$id", hours * 3600, 100, Instant.parse(start), null, null)

    @Test fun `週の始まりは月曜(日曜なら前の月曜)`() {
        assertEquals(monday, weekStartOf(LocalDate.of(2026, 10, 12)))
        assertEquals(monday, weekStartOf(LocalDate.of(2026, 10, 18)))
        assertEquals(monday, weekStartOf(LocalDate.of(2026, 10, 14)))
    }

    @Test fun `配信は端末の時間帯で始まった日の行に置き、週の外は入れない`() {
        val table = buildWeek(
            listOf(
                video("a", "2026-10-12T10:00:00Z", 2),
                video("b", "2026-10-11T16:00:00Z", 1),
                video("c", "2026-10-11T14:00:00Z", 1),
                video("d", "2026-10-19T00:00:00Z", 1),
            ),
            emptyList(), monday, jst,
        )
        assertEquals(7, table.rows.size)
        assertEquals(listOf("b", "a"), table.rows[0].items.map { it.vodId })
        assertTrue(table.rows.drop(1).all { it.items.isEmpty() })
    }

    @Test fun `横軸は配信の最初と最後に合わせ、日をまたぐ配信は 24時 を超える`() {
        val table = buildWeek(listOf(video("a", "2026-10-13T12:30:00Z", 6)), emptyList(), monday, jst)
        assertEquals(21 * 60, table.axisStartMin)
        assertEquals(28 * 60, table.axisEndMin)
    }

    @Test fun `配信が無い週は 0時〜24時、短い配信だけでも最低 6時間の幅`() {
        val empty = buildWeek(emptyList(), emptyList(), monday, jst)
        assertEquals(0, empty.axisStartMin)
        assertEquals(24 * 60, empty.axisEndMin)
        assertTrue(empty.isEmpty)
        val short = buildWeek(listOf(video("a", "2026-10-13T01:00:00Z", 1)), emptyList(), monday, jst)
        assertEquals(10 * 60, short.axisStartMin)
        assertEquals(16 * 60, short.axisEndMin)
    }

    @Test fun `予定は再生できない枠として置き、終了が無ければ 1時間`() {
        val seg = ScheduleSegment("s", Instant.parse("2026-10-16T10:00:00Z"), null, "予定", listOf("Just Chatting"), cancelled = true)
        val table = buildWeek(emptyList(), listOf(seg), monday, jst)
        val item = table.rows[4].items.single()
        assertNull(item.vodId)
        assertEquals(Instant.parse("2026-10-16T11:00:00Z"), item.end)
        assertEquals("Just Chatting", item.category)
        assertTrue(item.cancelled)
    }

    @Test fun `目盛りは多すぎない刻みで、軸の範囲に収める`() {
        assertEquals(listOf(12, 15, 18, 21, 24, 27), axisTicks(11 * 60, 28 * 60))
        assertEquals(listOf(0, 6, 12, 18, 24), axisTicks(0, 24 * 60))
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 16), axisTicks(10 * 60, 16 * 60))
    }

    @Test fun `日付の見出しは言語で形が変わる`() {
        val d = LocalDate.of(2026, 10, 14)
        assertEquals("10/14 (水)", formatScheduleDay(d, Locale.JAPAN))
        assertEquals("Wed, Oct 14", formatScheduleDay(d, Locale.US))
    }
}
