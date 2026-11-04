package dev.niltsiar.tvhome.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteClockTest {
    private val zone = { ZoneOffset.UTC }
    private val valid = TIME_VALID_AFTER_MS + 30_000L

    /** The wall clock as [start] plus the test's virtual time, so a delay moves it like real time. */
    private fun TestScope.wallClock(start: Long) = { start + testScheduler.currentTime }

    private fun at(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    @Test
    fun a_time_before_2025_is_hidden_and_looked_at_again_each_second_until_it_is_set() =
        runTest {
            var now = 0L
            minuteClock(currentMillis = { now }, zone = zone).test {
                assertNull(awaitItem())
                advanceTimeBy(1_001)
                assertNull(awaitItem())

                now = valid
                advanceTimeBy(1_000)
                assertEquals(at(valid), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_valid_time_is_emitted_now_and_again_at_the_next_minute_start() =
        runTest {
            minuteClock(currentMillis = wallClock(valid), zone = zone).test {
                assertEquals(at(valid), awaitItem())
                advanceTimeBy(30_001)
                assertEquals(at(valid + 30_000), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_time_change_reads_the_time_again_at_once() =
        runTest {
            var now = valid
            val changes = MutableSharedFlow<Unit>()
            minuteClock(changes, { now }, zone).test {
                assertEquals(at(valid), awaitItem())

                now = valid + 3_600_000L
                changes.emit(Unit)

                assertEquals(at(valid + 3_600_000L), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
