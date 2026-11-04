package dev.niltsiar.tvhome.feature.home

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Week = Duration.ofDays(7)

/**
 * How long ago [opened] was at [now], in English as the reference launcher words it. Within the hour: "5 minutes ago"
 * (under a minute reads as one). Then the day and the time: "today, 11:40 AM", "yesterday, 21:40", "3 days ago, 9:40 PM",
 * and from a week on the date, "Oct 12, 9:40 PM" ("Oct 12, 2025, 9:40 PM" in another year). It follows "Last opened"
 * mid-sentence, so the day words are lower case.
 */
internal fun describeLastOpened(
    opened: LocalDateTime,
    now: LocalDateTime,
    hour24: Boolean,
): String {
    val at = minOf(opened, now.minusMinutes(1))
    val elapsed = Duration.between(at, now)
    if (elapsed < Duration.ofHours(1)) {
        val minutes = elapsed.toMinutes().toInt()
        return if (minutes == 1) "1 minute ago" else "$minutes minutes ago"
    }
    val days = ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate()).toInt()
    val day =
        when {
            elapsed >= Week -> date(at, now)
            days == 0 -> "today"
            days == 1 -> "yesterday"
            else -> "$days days ago"
        }
    return "$day, ${time(at, hour24)}"
}

private fun date(
    at: LocalDateTime,
    now: LocalDateTime,
): String {
    val month = at.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val year = if (at.year == now.year) "" else ", ${at.year}"
    return "$month ${at.dayOfMonth}$year"
}

private fun time(
    at: LocalDateTime,
    hour24: Boolean,
): String {
    val minute = at.minute.toString().padStart(2, '0')
    if (hour24) return "${at.hour.toString().padStart(2, '0')}:$minute"
    val hour = (at.hour + 11) % 12 + 1
    return "$hour:$minute ${if (at.hour < 12) "AM" else "PM"}"
}
