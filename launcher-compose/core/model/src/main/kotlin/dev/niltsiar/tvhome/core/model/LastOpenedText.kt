package dev.niltsiar.tvhome.core.model

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** When Home last opened an app ([openedMs]) and the moment to describe it against ([nowMs]), both epoch milliseconds. */
@Immutable
public data class OpenedAt(
    val openedMs: Long,
    val nowMs: Long,
    val hour24: Boolean,
)

/**
 * How long ago [opened] was, in the TV's language. Within the hour: "5 minutes ago" (under a minute reads as one).
 * Then the day and the time: "today, 11:40 AM", "yesterday, 21:40", "3 days ago, 9:40 PM", and from a week on the date,
 * "Oct 12, 9:40 PM" ("Oct 12, 2025, 9:40 PM" in another year). The wording is Android's own, as in the reference
 * launcher; the time follows [OpenedAt.hour24]. It follows "Last opened" mid-sentence, so the relative words start in
 * lower case, which Android capitalizes.
 */
public fun describeLastOpened(
    context: Context,
    opened: OpenedAt,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    // Under a minute Android says "0 minutes ago".
    val at = minOf(opened.openedMs, opened.nowMs - DateUtils.MINUTE_IN_MILLIS)
    val elapsed = opened.nowMs - at
    if (elapsed < DateUtils.HOUR_IN_MILLIS) {
        return DateUtils.getRelativeTimeSpanString(at, opened.nowMs, DateUtils.MINUTE_IN_MILLIS).toString().midSentence(locale)
    }
    val day =
        if (elapsed < DateUtils.WEEK_IN_MILLIS) {
            DateUtils.getRelativeTimeSpanString(at, opened.nowMs, DateUtils.DAY_IN_MILLIS).toString().midSentence(locale)
        } else {
            val sameYear = Instant.ofEpochMilli(at).atZone(zone).year == Instant.ofEpochMilli(opened.nowMs).atZone(zone).year
            val year = if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
            DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or year)
        }
    val pattern = if (opened.hour24) "HH:mm" else "h:mm a"
    return "$day, ${DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(at).atZone(zone))}"
}

private fun String.midSentence(locale: Locale) = replaceFirstChar { it.lowercase(locale) }
