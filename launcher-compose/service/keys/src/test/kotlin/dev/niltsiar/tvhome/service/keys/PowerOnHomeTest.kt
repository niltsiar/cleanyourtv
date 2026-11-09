package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOnHomeTest {
    private val soon = 45_000L
    private val tv = "com.mediatek.wwtv.tvcenter"

    @Test
    fun a_tap_is_a_short_press_and_a_hold_past_the_timeout_is_not() {
        assertTrue(isShortPress(1_000, 1_120, 400))
        assertFalse(isShortPress(1_000, 1_400, 400))
        assertFalse(isShortPress(1_000, 3_000, 400))
    }

    @Test
    fun the_first_bind_of_a_new_boot_probes() {
        assertTrue(firstBindAfterBoot(12, 11, soon))
        assertTrue(firstBindAfterBoot(1, -1, soon))
    }

    @Test
    fun a_rebind_in_the_same_boot_a_late_bind_or_no_boot_count_does_not() {
        assertFalse(firstBindAfterBoot(12, 12, soon))
        assertFalse(firstBindAfterBoot(12, 11, BOOT_WINDOW_MS))
        assertFalse(firstBindAfterBoot(-1, -1, soon))
    }

    @Test
    fun live_tv_before_any_key_goes_home() {
        assertTrue(powerOnRedirect(tv, tv, soon, BOOT_WINDOW_MS))
    }

    @Test
    fun other_apps_and_inputs_are_left_alone() {
        assertFalse(powerOnRedirect(tv, "com.netflix.ninja", soon, BOOT_WINDOW_MS))
        assertFalse(powerOnRedirect(tv, null, soon, BOOT_WINDOW_MS))
        assertFalse(powerOnRedirect("", tv, soon, BOOT_WINDOW_MS))
    }

    @Test
    fun after_a_key_or_the_window_live_tv_stays() {
        assertFalse(powerOnRedirect(tv, tv, soon, 0))
        assertFalse(powerOnRedirect(tv, tv, BOOT_WINDOW_MS, BOOT_WINDOW_MS))
    }

    @Test
    fun still_armed_until_the_window_ends() {
        assertTrue(stillArmed(soon, soon + SCREEN_ON_WINDOW_MS))
        assertFalse(stillArmed(soon, 0))
    }

    @Test
    fun boot_to_live_tv_switches_the_power_on_keys_off() {
        val both = TvConfig(powerOnHome = true, powerOnHomeAlways = true)
        assertEquals(PowerOnPlan(tv, always = true), powerOnPlan(both, ""))
        assertEquals(PowerOnPlan("", always = false), powerOnPlan(both.copy(bootToLiveTv = true), ""))
        assertFalse(powerOnPlan(TvConfig(), "").enabled)
        assertFalse(powerOnPlan(both.copy(liveTvPackage = null, powerOnHomeAlways = false), "").enabled)
    }

    private val picking = TvConfig(openOnStartChoice = true)

    @Test
    fun live_tv_picked_as_the_open_on_start_app_is_not_sent_home() {
        val config = picking.copy(powerOnHome = true)
        assertEquals(PowerOnPlan("", always = false), powerOnPlan(config, tv))
    }

    @Test
    fun another_app_picked_leaves_the_live_tv_redirect() {
        val config = picking.copy(powerOnHome = true)
        assertEquals(PowerOnPlan(tv, always = false), powerOnPlan(config, "com.netflix.ninja"))
    }

    @Test
    fun any_pick_turns_power_on_home_always_off_and_no_pick_leaves_it_on() {
        val config = picking.copy(powerOnHomeAlways = true)
        assertEquals(PowerOnPlan("", always = false), powerOnPlan(config, "com.netflix.ninja"))
        assertEquals(PowerOnPlan("", always = false), powerOnPlan(config, tv))
        assertEquals(PowerOnPlan("", always = true), powerOnPlan(config, ""))
    }

    @Test
    fun a_stored_pick_is_ignored_while_open_on_start_choice_is_off() {
        val config = TvConfig(openOnStartChoice = false, powerOnHome = true, powerOnHomeAlways = true)
        assertEquals(PowerOnPlan(tv, always = true), powerOnPlan(config, tv))
        assertEquals(PowerOnPlan(tv, always = true), powerOnPlan(config, "com.netflix.ninja"))
    }

    /** The unit on a clock and a timer that move together. */
    private class Rig(
        plan: PowerOnPlan,
        bootedMs: Long,
    ) {
        val timer = FakeTimer()
        val homes = mutableListOf<Long>()
        val power = PowerOnHome(plan, { bootedMs + timer.now }, timer) { homes += timer.now }
    }

    @Test
    fun always_opens_home_800_ms_after_the_screen_comes_on() {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.armAtBind()
        assertEquals(0, rig.timer.pending) // bound long after boot: nothing to do until the screen turns on

        rig.power.onScreenOn()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS - 1)
        assertEquals(emptyList<Long>(), rig.homes)
        rig.timer.advance(1)
        assertEquals(listOf(POWER_ON_ALWAYS_DELAY_MS), rig.homes)
    }

    @Test
    fun a_key_pressed_before_the_delay_keeps_home_from_opening() {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(300)
        rig.power.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER)
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        assertEquals(emptyList<Long>(), rig.homes)
    }

    @Test
    fun the_power_and_wake_keys_do_not_count_as_a_key() {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.power.onKeyDown(KeyEvent.KEYCODE_POWER)
        rig.power.onKeyDown(KeyEvent.KEYCODE_WAKEUP)
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        assertEquals(1, rig.homes.size)
    }

    @Test
    fun a_bind_during_the_boot_opens_home_too() {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 30_000L)
        rig.power.armAtBind()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        assertEquals(1, rig.homes.size)
    }

    @Test
    fun without_always_the_screen_coming_on_opens_nothing_by_itself() {
        val rig = Rig(PowerOnPlan(tv, always = false), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS * 10)
        assertEquals(emptyList<Long>(), rig.homes)
    }

    @Test
    fun live_tv_is_sent_home_for_60_seconds_after_the_screen_comes_on_until_a_key() {
        val rig = Rig(PowerOnPlan(tv, always = false), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(SCREEN_ON_WINDOW_MS - 1)
        assertTrue(rig.power.redirects(tv))
        assertFalse(rig.power.redirects("com.netflix.ninja"))
        rig.power.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN)
        assertFalse(rig.power.redirects(tv))

        rig.power.onScreenOn()
        rig.timer.advance(SCREEN_ON_WINDOW_MS)
        assertFalse(rig.power.redirects(tv))
    }
}
