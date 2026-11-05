package dev.niltsiar.tvhome.feature.home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The time as `HH:mm`, or `h:mm` in the 12-hour clock. */
internal fun clockText(
    time: LocalDateTime,
    hour24: Boolean,
    locale: Locale,
): String = time.format(DateTimeFormatter.ofPattern(if (hour24) "HH:mm" else "h:mm", locale))

/** The AM/PM marker, empty on the 24-hour clock. */
internal fun amPmText(
    time: LocalDateTime,
    hour24: Boolean,
    locale: Locale,
): String = if (hour24) "" else time.format(DateTimeFormatter.ofPattern("a", locale)).uppercase(locale)
