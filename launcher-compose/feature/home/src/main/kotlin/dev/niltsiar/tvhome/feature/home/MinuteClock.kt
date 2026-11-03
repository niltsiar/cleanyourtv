package dev.niltsiar.tvhome.feature.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

/** The current local time now, then again at the start of every minute. */
public fun minuteClock(): Flow<LocalDateTime> =
    flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay(MILLIS_PER_MINUTE - (now.second * MILLIS_PER_SECOND + now.nano / NANOS_PER_MILLI))
        }
    }

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L
