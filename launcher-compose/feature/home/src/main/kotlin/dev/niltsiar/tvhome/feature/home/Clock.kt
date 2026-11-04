package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.config.ClockFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** clock_format: a chosen 12 or 24 wins without asking the TV; "system" asks [system] (the TV's own setting). */
public fun ClockFormat.is24Hour(system: () -> Boolean): Boolean =
    when (this) {
        ClockFormat.Hour24 -> true
        ClockFormat.Hour12 -> false
        ClockFormat.System -> system()
    }

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
