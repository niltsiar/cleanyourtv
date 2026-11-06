package dev.niltsiar.tvhome.data.apps

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Home opened [packageName] at [atMillis], milliseconds since the epoch. */
public data class LaunchRecord(
    val packageName: String,
    val atMillis: Long,
)

/** How long a launch counts: eight weeks. */
internal const val HABIT_KEEP_MS = 8L * 7 * 24 * 60 * 60 * 1000

private const val WINDOW_MINUTES = 45
private const val DAY_MINUTES = 24 * 60
private const val MIN_LAUNCHES = 4
private const val MIN_DATES = 3

/** The records that still count at [nowMillis]. */
internal fun List<LaunchRecord>.kept(nowMillis: Long): List<LaunchRecord> = filter { it.atMillis > nowMillis - HABIT_KEEP_MS }

/**
 * The package the household usually opens at [nowMillis], or null. It looks at the launches of the last eight weeks on
 * the same kind of day as now (Monday to Friday, or Saturday and Sunday, by the local date of each launch) whose time of
 * day is within 45 minutes of now, midnight included. An app is the habit with at least 4 of those launches, on at
 * least 3 different dates, and at least half of all of them. When two apps both reach half, there is no habit.
 */
public fun habitApp(
    records: List<LaunchRecord>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val weekend = now.dayOfWeek.isWeekend()
    val nowMinute = now.hour * 60 + now.minute
    val inWindow =
        records.kept(nowMillis).filter {
            val at = Instant.ofEpochMilli(it.atMillis).atZone(zone)
            val apart = abs(at.hour * 60 + at.minute - nowMinute)
            at.dayOfWeek.isWeekend() == weekend && minOf(apart, DAY_MINUTES - apart) <= WINDOW_MINUTES
        }
    val habits =
        inWindow
            .groupBy { it.packageName }
            .filter { (_, launches) ->
                launches.size >= MIN_LAUNCHES &&
                    launches.size * 2 >= inWindow.size &&
                    launches.map { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }.distinct().size >= MIN_DATES
            }.keys
    return habits.singleOrNull()
}

private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
