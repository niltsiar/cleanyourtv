package dev.niltsiar.tvhome.data.system

import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
val MinuteClockTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    val zone = { ZoneOffset.UTC }
    val valid = TIME_VALID_AFTER_MS + 30_000L

    /** The wall clock as [start] plus the test's virtual time, so a delay moves it like real time. */
    fun TestScope.wallClock(start: Long) = { start + testScheduler.currentTime }

    fun at(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    test("a time before 2025 is hidden and looked at again each second until it is set") {
        with(testScope) {
            var now = 0L
            minuteClock(emptyFlow(), emptyFlow(), { now }, zone).test {
                awaitItem().shouldBeNull()
                advanceTimeBy(1_001)
                awaitItem().shouldBeNull()

                now = valid
                advanceTimeBy(1_000)
                awaitItem() shouldBe at(valid)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("a valid time is emitted now and again at the next minute start") {
        with(testScope) {
            minuteClock(emptyFlow(), emptyFlow(), wallClock(valid), zone).test {
                awaitItem() shouldBe at(valid)
                advanceTimeBy(30_001)
                awaitItem() shouldBe at(valid + 30_000)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("a time change reads the time again at once") {
        with(testScope) {
            var now = valid
            val changes = MutableSharedFlow<Unit>()
            minuteClock(changes, emptyFlow(), { now }, zone).test {
                awaitItem() shouldBe at(valid)

                now = valid + 3_600_000L
                changes.emit(Unit)

                awaitItem() shouldBe at(valid + 3_600_000L)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("the display coming on reads the time again without waiting for the pending minute") {
        with(testScope) {
            var now = valid
            val displayOn = MutableSharedFlow<Unit>()
            minuteClock(emptyFlow(), displayOn, { now }, zone).test {
                awaitItem() shouldBe at(valid)

                // The set slept: the wall clock moved on while the pending delay stood still.
                now = valid + 600_000L
                displayOn.emit(Unit)

                awaitItem() shouldBe at(valid + 600_000L)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("a time change updates the shared time at once without waiting for the next minute") {
        with(testScope) {
            var now = valid
            val changes = MutableSharedFlow<Unit>()
            val clock = MinuteClock(changes, emptyFlow(), backgroundScope, { now }, zone)
            backgroundScope.launch { clock.now.collect {} }
            runCurrent()
            clock.now.value shouldBe at(valid)

            now = valid + 3_600_000L
            changes.emit(Unit)
            runCurrent()

            clock.now.value shouldBe at(valid + 3_600_000L)
        }
    }

    test("every collector shares one timer") {
        with(testScope) {
            var reads = 0
            val clock =
                MinuteClock(emptyFlow(), emptyFlow(), backgroundScope, {
                    reads++
                    valid
                }, zone)
            repeat(3) { backgroundScope.launch { clock.now.collect {} } }
            runCurrent()

            reads shouldBe 1
        }
    }
}
