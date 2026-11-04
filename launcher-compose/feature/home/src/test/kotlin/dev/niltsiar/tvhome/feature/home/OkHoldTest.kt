package dev.niltsiar.tvhome.feature.home

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHoldTest {
    private val hold = OkHold(holdMs = 550)

    @Test
    fun the_first_down_opens_nothing() {
        assertFalse(hold.down(repeatCount = 0, downTime = 1_000, eventTime = 1_000))
    }

    @Test
    fun a_repeat_opens_the_menu_from_550_ms_after_the_down_and_only_once() {
        hold.down(0, downTime = 1_000, eventTime = 1_000)

        assertFalse(hold.down(1, downTime = 1_000, eventTime = 1_549))
        assertTrue(hold.down(2, downTime = 1_000, eventTime = 1_550))
        assertFalse(hold.down(3, downTime = 1_000, eventTime = 1_600))
        assertTrue(hold.up())
        assertFalse(hold.up())
    }

    @Test
    fun a_short_press_is_not_eaten() {
        hold.down(0, 1_000, 1_000)

        assertFalse(hold.up())
    }

    @Test
    fun the_timer_opens_the_menu_once_for_a_remote_that_never_repeats() {
        hold.down(0, 1_000, 1_000)

        assertTrue(hold.timeUp())
        assertFalse(hold.timeUp())
        assertTrue(hold.up())
    }

    @Test
    fun the_timer_of_a_press_that_ended_opens_nothing() {
        hold.down(0, 1_000, 1_000)
        hold.up()

        assertFalse(hold.timeUp())
    }

    @Test
    fun a_new_press_starts_over_after_a_hold() {
        hold.down(0, 1_000, 1_000)
        hold.timeUp()
        hold.up()

        assertFalse(hold.down(0, 5_000, 5_000))
        assertFalse(hold.up())
    }

    @Test
    fun a_press_cancelled_midway_opens_nothing_and_its_up_is_eaten() {
        hold.down(0, 1_000, 1_000)

        hold.cancel()

        assertFalse(hold.timeUp())
        assertTrue(hold.up())
    }

    @Test
    fun menu_opens_on_a_normal_release_and_on_xiaomis_injected_one() {
        val cancelled = KeyEvent.FLAG_CANCELED
        assertTrue(menuReleased(isUp = true, flags = 0))
        assertTrue(menuReleased(isUp = true, flags = KeyEvent.FLAG_FROM_SYSTEM))
        assertTrue(menuReleased(isUp = true, flags = XIAOMI_SHORT_MENU_FLAGS))
        assertFalse(menuReleased(isUp = false, flags = 0))
        assertFalse(menuReleased(isUp = false, flags = XIAOMI_SHORT_MENU_FLAGS))
        assertFalse(menuReleased(isUp = true, flags = cancelled))
        assertFalse(menuReleased(isUp = true, flags = cancelled or KeyEvent.FLAG_CANCELED_LONG_PRESS or KeyEvent.FLAG_FROM_SYSTEM))
        assertFalse(menuReleased(isUp = true, flags = XIAOMI_SHORT_MENU_FLAGS and 0x4000.inv()))
    }
}
