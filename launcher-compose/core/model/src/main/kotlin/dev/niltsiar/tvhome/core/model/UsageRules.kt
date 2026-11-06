package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When Home last opened each tile ([times], by tile key, milliseconds since the epoch) and since when it has kept that
 * record ([since], null while it keeps none). A tile Home never opened counts from [since], so a record that is days old
 * cannot call an app unused for weeks.
 */
@Immutable
public data class OpenedRecord(
    val times: Map<String, Long> = emptyMap(),
    val since: Long? = null,
)

/** The day the tile [key] counts as idle from: its last open, else the start of the record; null with neither. */
internal fun OpenedRecord.idleFrom(
    key: String,
    zone: ZoneId,
): LocalDate? = (times[key] ?: since)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/**
 * The keys among [keys] that Home last opened more than [days] calendar days before [today] (or, never opened, whose
 * record began that long ago). Nothing for [days] of 0 or less.
 */
public fun OpenedRecord.unusedKeys(
    keys: List<String>,
    days: Int,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Set<String> {
    if (days <= 0) return emptySet()
    return keys
        .filter { key -> idleFrom(key, zone)?.let { ChronoUnit.DAYS.between(it, today) > days } == true }
        .toSet()
}

/** Milliseconds from [nowMillis] to the next local midnight in [zone]. */
public fun millisUntilNextDay(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val tomorrow =
        Instant
            .ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
    return tomorrow.atStartOfDay(zone).toInstant().toEpochMilli() - nowMillis
}

/**
 * The catalogue least used first: apps Home never opened (all as old as the record), then by the time of the last open,
 * oldest first. Ties go by label, then by package.
 */
public fun sortLeastUsed(
    apps: List<LibraryApp>,
    record: OpenedRecord,
    collator: Collator,
): List<LibraryApp> =
    apps.sortedWith(
        compareBy<LibraryApp> { record.times[it.app.key] ?: Long.MIN_VALUE }
            .thenComparator { a, b -> collator.compare(a.app.label, b.app.label) }
            .thenBy { it.app.packageName },
    )
