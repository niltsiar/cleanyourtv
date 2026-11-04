package dev.niltsiar.tvhome.feature.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Boards without a battery-backed clock boot at 1970; any date before 2025-01-01 is that, not the time. */
internal const val TIME_VALID_AFTER_MS = 1_735_689_600_000L

/**
 * The local time now, then again at the start of every minute and whenever [timeChanges] says the wall clock or the
 * time zone was set. While the time is not valid yet ([TIME_VALID_AFTER_MS]) it emits null and looks again each second,
 * as some TVs set the network time without any broadcast.
 */
public fun minuteClock(
    timeChanges: Flow<Unit> = emptyFlow(),
    currentMillis: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
): Flow<LocalDateTime?> =
    channelFlow {
        timeChanges.onStart { emit(Unit) }.collectLatest {
            while (true) {
                val millis = currentMillis()
                if (millis < TIME_VALID_AFTER_MS) {
                    send(null)
                    delay(MILLIS_PER_SECOND)
                } else {
                    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())
                    send(now)
                    delay(MILLIS_PER_MINUTE - (now.second * MILLIS_PER_SECOND + now.nano / NANOS_PER_MILLI))
                }
            }
        }
    }

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L
