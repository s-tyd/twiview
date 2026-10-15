package dev.twiview.data

import dev.twiview.MockServerTest
import dev.twiview.jsonNode
import dev.twiview.jsonResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScheduleTest : MockServerTest() {
    @Test fun `枠の項目を読み、中止と無題・終了時刻なしも扱う`() {
        val s = parseSegment(jsonNode("""{"id":"a","startAt":"2026-10-14T10:00:00Z","endAt":"2026-10-14T12:00:00Z","title":"雑談","isCancelled":true,"categories":[{"displayName":"Just Chatting"}]}"""))!!
        assertEquals("a", s.id)
        assertEquals(Instant.parse("2026-10-14T10:00:00Z"), s.startAt)
        assertEquals(Instant.parse("2026-10-14T12:00:00Z"), s.endAt)
        assertEquals("雑談", s.title)
        assertEquals(listOf("Just Chatting"), s.categories)
        assertTrue(s.cancelled)
        val bare = parseSegment(jsonNode("""{"id":"b","startAt":"2026-10-14T10:00:00Z","endAt":null,"title":"","isCancelled":false,"categories":[]}"""))!!
        assertNull(bare.endAt)
        assertFalse(bare.cancelled)
    }

    @Test fun `id か開始時刻が無い枠は捨てる`() {
        assertNull(parseSegment(jsonNode("""{"startAt":"2026-10-14T10:00:00Z"}""")))
        assertNull(parseSegment(jsonNode("""{"id":"a","startAt":"not a time"}""")))
    }

    private fun seg(id: String, day: Int) =
        """{"id":"$id","startAt":"2026-10-${"%02d".format(day)}T10:00:00Z","endAt":null,"title":"","isCancelled":false,"categories":[]}"""

    @Test fun `4日刻みで6回取り、重なりを除いて始まる順に返す`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"schedule":{"segments":[${seg("b", 15)},${seg("a", 14)}]}}}}}"""))
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"schedule":{"segments":[${seg("b", 15)},${seg("c", 22)}]}}}}}"""))
        repeat(4) { server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"schedule":{"segments":null}}}}}""")) }
        val s = twitchApi().schedule("shroud", now = Instant.parse("2026-10-14T09:30:00Z"))
        assertEquals(listOf("a", "b", "c"), s.map { it.id })
        assertEquals(6, server.requestCount)
        val q0 = server.takeRequest().body.readUtf8()
        assertTrue(q0, q0.contains("""relativeDate: \"2026-10-14T00:00:00Z\""""))
        assertTrue(q0, q0.contains("""startingWeekday: \"MONDAY\""""))
        assertTrue(server.takeRequest().body.readUtf8().contains("""relativeDate: \"2026-10-18T00:00:00Z\""""))
    }

    @Test fun `スケジュールを設定していないチャンネルは空で、それ以上は問い合わせない`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"schedule":null}}}}"""))
        assertEquals(emptyList<ScheduleSegment>(), twitchApi().schedule("kato", now = Instant.parse("2026-10-14T00:00:00Z")))
        assertEquals(1, server.requestCount)
    }
}
