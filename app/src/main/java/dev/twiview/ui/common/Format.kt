package dev.twiview.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

private class Notation(
    val thousand: String = "K",
    val million: String = "M",
    val tenThousand: String? = null,
    val minutesAgo: String,
    val hoursAgo: String,
    val daysAgo: String,
    val datePattern: String? = null,
    val dayPattern: String,
    val clock12: String = "h:mm a",
)

private const val NBSP = "\u00A0"

private val english = Notation(minutesAgo = "%dm ago", hoursAgo = "%dh ago", daysAgo = "%dd ago", dayPattern = "EEE, MMM d")

private val notations = mapOf(
    "en" to english,
    "ja" to Notation(tenThousand = "万", minutesAgo = "%d分前", hoursAgo = "%d時間前", daysAgo = "%d日前", datePattern = "yyyy/M/d", dayPattern = "M/d (E)", clock12 = "aK:mm"),
    "ko" to Notation(tenThousand = "만", minutesAgo = "%d분 전", hoursAgo = "%d시간 전", daysAgo = "%d일 전", datePattern = "yyyy. M. d.", dayPattern = "M월 d일 (E)", clock12 = "a h:mm"),
    "de" to Notation("${NBSP}Tsd.", "${NBSP}Mio.", minutesAgo = "vor %d${NBSP}Min.", hoursAgo = "vor %d${NBSP}Std.", daysAgo = "vor %d${NBSP}T.", dayPattern = "EEE, d. MMM"),
    "fr" to Notation("${NBSP}k", "${NBSP}M", minutesAgo = "il y a %d${NBSP}min", hoursAgo = "il y a %d${NBSP}h", daysAgo = "il y a %d${NBSP}j", dayPattern = "EEE d MMM"),
    "es" to Notation("${NBSP}mil", "${NBSP}M", minutesAgo = "hace %d${NBSP}min", hoursAgo = "hace %d${NBSP}h", daysAgo = "hace %d${NBSP}d", dayPattern = "EEE, d MMM"),
    "pt" to Notation("${NBSP}mil", "${NBSP}mi", minutesAgo = "há %d${NBSP}min", hoursAgo = "há %d${NBSP}h", daysAgo = "há %d${NBSP}d", dayPattern = "EEE, d 'de' MMM"),
    "ru" to Notation("${NBSP}тыс.", "${NBSP}млн", minutesAgo = "%d${NBSP}мин назад", hoursAgo = "%d${NBSP}ч назад", daysAgo = "%d${NBSP}дн. назад", dayPattern = "EEE, d MMM"),
    "it" to Notation("K", "${NBSP}Mln", minutesAgo = "%d${NBSP}min fa", hoursAgo = "%d${NBSP}h fa", daysAgo = "%d${NBSP}gg fa", dayPattern = "EEE d MMM"),
    "tr" to Notation("${NBSP}B", "${NBSP}Mn", minutesAgo = "%d${NBSP}dk. önce", hoursAgo = "%d${NBSP}sa. önce", daysAgo = "%d${NBSP}gün önce", dayPattern = "d MMM EEE", clock12 = "a h:mm"),
)

private val Locale.notation get() = notations[language] ?: english

private val Locale.dayPattern
    get() = if (language == "en" && country !in setOf("", "US", "CA", "PH")) "EEE d MMM" else notation.dayPattern

fun formatCount(n: Int, locale: Locale): String {
    val nt = locale.notation
    val man = nt.tenThousand
    return if (man != null) when {
        n >= 100_000 -> "${n / 10_000}$man"
        n >= 10_000 -> String.format(Locale.US, "%.1f", n / 10_000.0) + man
        else -> String.format(Locale.US, "%,d", n)
    } else when {
        n >= 1_000_000 -> oneDecimal(n, 1_000_000, locale) + nt.million
        n >= 10_000 -> "${n / 1_000}" + nt.thousand
        n >= 1_000 -> oneDecimal(n, 1_000, locale) + nt.thousand
        else -> "$n"
    }
}

private fun oneDecimal(n: Int, unit: Int, locale: Locale): String {
    val tenths = n / (unit / 10)
    return if (tenths % 10 == 0) "${tenths / 10}" else String.format(locale, "%.1f", tenths / 10.0)
}

fun formatUptime(start: Instant?, now: Instant = Instant.now(), seconds: Boolean = false): String {
    if (start == null) return ""
    val s = Duration.between(start, now).seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    return when {
        seconds && h > 0 -> "%dh%02dm%02ds".format(h, m, s % 60)
        seconds -> "%dm%02ds".format(m, s % 60)
        h > 0 -> "%dh%02dm".format(h, m)
        else -> "%dm".format(m)
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return when {
        h > 0 -> "%dh%02dm".format(h, m)
        m > 0 -> "%dm%02ds".format(m, seconds % 60)
        else -> "%ds".format(seconds)
    }
}

fun formatLength(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatAgo(t: Instant?, locale: Locale, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (t == null) return ""
    if (Duration.between(t, now).toDays() < 30) return formatAgoDays(t, locale, now)
    val nt = locale.notation
    val f = nt.datePattern?.let { DateTimeFormatter.ofPattern(it, locale) } ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return f.withZone(zone).format(t)
}

fun formatAgoDays(t: Instant, locale: Locale, now: Instant = Instant.now()): String {
    val d = Duration.between(t, now)
    val nt = locale.notation
    return when {
        d.toMinutes() < 60 -> nt.minutesAgo.format(d.toMinutes().coerceAtLeast(1))
        d.toHours() < 24 -> nt.hoursAgo.format(d.toHours())
        else -> nt.daysAgo.format(d.toDays())
    }
}

fun formatDateTime(t: Instant, locale: Locale, use24Hour: Boolean = true, zone: ZoneId = ZoneId.systemDefault()): String {
    val z = t.atZone(zone)
    val clock = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else locale.notation.clock12, locale)
    return formatScheduleDay(z.toLocalDate(), locale) + " " + clock.format(z)
}

@Composable
fun use24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

fun formatScheduleDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(locale.dayPattern, locale).format(date)

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
fun formatClock(epochMs: Long): String = clock.format(Instant.ofEpochMilli(epochMs))
