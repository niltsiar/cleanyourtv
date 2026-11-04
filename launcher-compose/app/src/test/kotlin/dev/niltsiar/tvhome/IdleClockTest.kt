package dev.niltsiar.tvhome

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IdleClockTest {
    private var resumed = true
    private var shown = 0
    private val minute = 60_000L

    private fun idleClock(idleMs: Long) = IdleClock(idleMs, { resumed }, { shown++ })

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun the_clock_starts_after_the_idle_time_without_input() {
        val idle = idleClock(2 * minute)
        idle.restart()

        pass(2 * minute - 1)
        assertEquals(0, shown)
        pass(1)
        assertEquals(1, shown)
    }

    @Test
    fun input_restarts_the_count() {
        val idle = idleClock(2 * minute)
        idle.restart()
        pass(minute + 30_000)
        idle.restart()
        pass(minute + 30_000)
        assertEquals(0, shown)

        pass(30_000)
        assertEquals(1, shown)
    }

    @Test
    fun a_key_that_reaches_a_paused_home_does_not_start_the_clock() {
        val idle = idleClock(minute)
        idle.restart()
        resumed = false
        idle.cancel() // onPause
        idle.restart() // the UP of the key that opened another app
        pass(10 * minute)

        assertEquals(0, shown)
    }

    @Test
    fun zero_minutes_is_off() {
        idleClock(0).restart()
        pass(60 * minute)

        assertEquals(0, shown)
    }
}
