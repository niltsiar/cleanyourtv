package dev.niltsiar.tvhome.service.keys

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadPassthroughTest {
    // Sources as dumpsys input reports them on a TCL G07.
    @Test
    fun an_xbox_controller_is_a_gamepad() {
        assertTrue(isGamepad(0x01000511))
    }

    @Test
    fun the_voice_remote_with_a_joystick_is_not() {
        assertFalse(isGamepad(0x01000311))
    }

    @Test
    fun the_virtual_search_keyboard_is_not() {
        assertFalse(isGamepad(0x00000701))
    }

    @Test
    fun a_keypad_is_not() {
        assertFalse(isGamepad(0x00000101))
    }
}
