package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class VolumeKeysTest {
    private val raise = 1
    private val lower = -1

    @Test
    fun volume_up_and_down_are_handled() {
        assertEquals(raise, volumeDirection(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(lower, volumeDirection(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun other_keys_pass_through() {
        // Mute is one press with nothing to repeat, and the firmware did not double it.
        assertEquals(0, volumeDirection(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertEquals(0, volumeDirection(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun only_the_first_down_and_the_up_change_the_press() {
        assertTrue(changesPress(KeyEvent.ACTION_DOWN, 0))
        assertTrue(changesPress(KeyEvent.ACTION_UP, 0))
        // A remote that sends its own repeats must not restart the 500 ms delay on every one.
        assertFalse(changesPress(KeyEvent.ACTION_DOWN, 1))
        assertFalse(changesPress(KeyEvent.ACTION_DOWN, 12))
    }

    private fun key(
        action: Int,
        code: Int,
        repeat: Int = 0,
    ) = KeyEvent(0, 0, action, code, repeat)

    @Test
    fun a_held_volume_key_repeats_after_500_ms_every_60_ms_until_it_is_released() {
        val timer = FakeTimer()
        val steps = mutableListOf<Int>()
        val volume = VolumeRepeat(timer) { steps += it }

        assertTrue(volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        timer.advance(499)
        assertEquals(emptyList<Int>(), steps)
        timer.advance(1)
        assertEquals(listOf(raise), steps)
        timer.advance(120)
        assertEquals(listOf(raise, raise, raise), steps)
        assertTrue(volume.handle(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        timer.advance(1000)
        assertEquals(3, steps.size)
        assertEquals(0, timer.pending)
    }

    @Test
    fun a_remotes_own_repeats_do_not_restart_the_delay_and_other_keys_are_left_alone() {
        val timer = FakeTimer()
        val steps = mutableListOf<Int>()
        val volume = VolumeRepeat(timer) { steps += it }

        volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
        timer.advance(400)
        assertTrue(volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1)))
        timer.advance(100)
        assertEquals(listOf(lower), steps)
        assertFalse(volume.handle(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        volume.stop()
        assertEquals(0, timer.pending)
    }
}
