package dev.niltsiar.tvhome.feature.clock

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The screensaver's clock as text. The colon between [hours] and [minutes] is drawn apart so it can breathe. [amPm] is
 * empty on the 24-hour clock.
 */
@Immutable
public data class ClockFace(
    val hours: String,
    val minutes: String,
    val amPm: String,
    val date: String,
)

/** [time] as `HH:mm` or `h:mm a`, and the date in [datePattern] (a java.time pattern) in capitals. */
public fun clockFace(
    time: LocalDateTime,
    hour24: Boolean,
    datePattern: String,
    locale: Locale,
): ClockFace {
    fun format(pattern: String) = time.format(DateTimeFormatter.ofPattern(pattern, locale))
    return ClockFace(
        hours = format(if (hour24) "HH" else "h"),
        minutes = format("mm"),
        amPm = if (hour24) "" else format("a").uppercase(locale),
        date = format(datePattern).uppercase(locale),
    )
}
