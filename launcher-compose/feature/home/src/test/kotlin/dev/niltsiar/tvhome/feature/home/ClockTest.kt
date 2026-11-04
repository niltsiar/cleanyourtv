package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.config.ClockFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {
    @Test
    fun a_chosen_format_wins_without_asking_the_tv() {
        val neverAsked = { error("the TV is not asked") }

        assertTrue(ClockFormat.Hour24.is24Hour(neverAsked))
        assertFalse(ClockFormat.Hour12.is24Hour(neverAsked))
    }

    @Test
    fun system_follows_the_tv() {
        assertTrue(ClockFormat.System.is24Hour { true })
        assertFalse(ClockFormat.System.is24Hour { false })
    }
}
