package dev.twiview.ui

import dev.twiview.ui.common.formatAgo
import dev.twiview.ui.common.formatCount
import dev.twiview.ui.common.formatDateTime
import dev.twiview.ui.common.formatDuration
import dev.twiview.ui.common.formatLength
import dev.twiview.ui.common.formatUptime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class FormatTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val ja = Locale.JAPANESE
    private val en = Locale.ENGLISH

    @Test fun `視聴者数は 1万未満はカンマ、1万以上は万表記`() {
        assertEquals("0", formatCount(0, ja))
        assertEquals("9,999", formatCount(9_999, ja))
        assertEquals("1.0万", formatCount(10_000, ja))
        assertEquals("3.3万", formatCount(32_781, ja))
        assertEquals("10万", formatCount(100_000, ja))
        assertEquals("123万", formatCount(1_234_567, ja))
    }

    @Test fun `配信時間は 時間と分`() {
        assertEquals("0m", formatUptime(now, now))
        assertEquals("2h05m", formatUptime(now.minus(Duration.ofMinutes(125)), now))
        assertEquals("12h30m", formatUptime(now.minus(Duration.ofMinutes(750)), now))
        assertEquals("", formatUptime(null, now))
    }

    @Test fun `再生画面では秒まで出す`() {
        assertEquals("2h05m09s", formatUptime(now.minusSeconds(125 * 60 + 9), now, seconds = true))
        assertEquals("0m00s", formatUptime(now.plusSeconds(90), now, seconds = true))
    }

    @Test fun `時計がずれて開始が未来でも負にしない`() {
        assertEquals("0m", formatUptime(now.plusSeconds(90), now))
    }

    @Test fun `動画の長さは 1時間未満なら 分と秒`() {
        assertEquals("0:05", formatLength(5))
        assertEquals("59:59", formatLength(3599))
        assertEquals("1:00:00", formatLength(3600))
        assertEquals("12:16:44", formatLength(44_204))
        assertEquals("54s", formatDuration(54))
        assertEquals("11m54s", formatDuration(714))
        assertEquals("3h17m", formatDuration(11_831))
    }

    @Test fun `経過時間は分・時間・日、30日以上は日付`() {
        assertEquals("1分前", formatAgo(now.minusSeconds(10), ja, now))
        assertEquals("59分前", formatAgo(now.minus(Duration.ofMinutes(59)), ja, now))
        assertEquals("3時間前", formatAgo(now.minus(Duration.ofHours(3)), ja, now))
        assertEquals("29日前", formatAgo(now.minus(Duration.ofDays(29)), ja, now))
        assertEquals(true, formatAgo(now.minus(Duration.ofDays(40)), ja, now).matches(Regex("""\d{4}/\d{1,2}/\d{1,2}""")))
        assertEquals("", formatAgo(null, ja, now))
    }

    @Test fun `英語の視聴者数は K と M、1,000未満はそのまま`() {
        assertEquals("0", formatCount(0, en))
        assertEquals("999", formatCount(999, en))
        assertEquals("1K", formatCount(1_000, en))
        assertEquals("1.2K", formatCount(1_250, en))
        assertEquals("9.9K", formatCount(9_999, en))
        assertEquals("12K", formatCount(12_345, en))
        assertEquals("999K", formatCount(999_999, en))
        assertEquals("1.2M", formatCount(1_234_567, en))
        assertEquals("2M", formatCount(2_000_000, en))
    }

    @Test fun `英語の経過時間は m h d ago、30日以上は月名の日付`() {
        assertEquals("1m ago", formatAgo(now.minusSeconds(10), en, now))
        assertEquals("59m ago", formatAgo(now.minus(Duration.ofMinutes(59)), en, now))
        assertEquals("3h ago", formatAgo(now.minus(Duration.ofHours(3)), en, now))
        assertEquals("29d ago", formatAgo(now.minus(Duration.ofDays(29)), en, now))
        assertEquals("Sep 3, 2026", formatAgo(now.minus(Duration.ofDays(40)), en, now, ZoneOffset.UTC))
        assertEquals("2026/9/3", formatAgo(now.minus(Duration.ofDays(40)), ja, now, ZoneOffset.UTC))
    }

    @Test fun `韓国語は日本語と同じく万単位で数える`() {
        val ko = Locale.KOREAN
        assertEquals("9,999", formatCount(9_999, ko))
        assertEquals("3.3만", formatCount(32_781, ko))
        assertEquals("123만", formatCount(1_234_567, ko))
        assertEquals("3시간 전", formatAgo(now.minus(Duration.ofHours(3)), ko, now))
    }

    @Test fun `欧州の言語は小数点と単位をその言語で書く`() {
        assertEquals("1,2\u00A0Tsd.", formatCount(1_250, Locale.GERMAN))
        assertEquals("1,2\u00A0млн", formatCount(1_234_567, Locale.forLanguageTag("ru")))
        assertEquals("12\u00A0mil", formatCount(12_345, Locale.forLanguageTag("pt-BR")))
        assertEquals("vor 3\u00A0Std.", formatAgo(now.minus(Duration.ofHours(3)), Locale.GERMAN, now))
        assertEquals("hace 5\u00A0min", formatAgo(now.minus(Duration.ofMinutes(5)), Locale.forLanguageTag("es-MX"), now))
    }

    @Test fun `対応していない言語は英語の表記にする`() {
        assertEquals("12K", formatCount(12_345, Locale.forLanguageTag("nl")))
        assertEquals("3h ago", formatAgo(now.minus(Duration.ofHours(3)), Locale.forLanguageTag("nl"), now))
    }

    @Test fun `英語の日付は米国が月から、英国と豪州は日から`() {
        val t = Instant.parse("2026-10-14T21:05:00Z")
        assertEquals("Wed, Oct 14 21:05", formatDateTime(t, Locale.US, zone = ZoneOffset.UTC))
        assertEquals("Wed 14 Oct 21:05", formatDateTime(t, Locale.UK, zone = ZoneOffset.UTC))
        assertEquals("Wed 14 Oct 21:05", formatDateTime(t, Locale.forLanguageTag("en-AU"), zone = ZoneOffset.UTC))
    }

    @Test fun `12時間表記は午前午後の位置も言語に合わせる`() {
        val t = Instant.parse("2026-10-14T09:05:00Z")
        assertEquals("Wed, Oct 14 9:05 AM", formatDateTime(t, Locale.US, use24Hour = false, zone = ZoneOffset.UTC))
        assertEquals("10월 14일 (수) 오전 9:05", formatDateTime(t, Locale.KOREAN, use24Hour = false, zone = ZoneOffset.UTC))
        assertEquals("10/14 (水) 09:05", formatDateTime(t, Locale.JAPAN, zone = ZoneOffset.UTC))
    }
}
