package dev.niltsiar.tvhome.data.system

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayOnTest {
    @Test
    fun a_display_that_was_off_and_is_now_on_counts() {
        assertTrue(turnedOn(Display.STATE_OFF, Display.STATE_ON))
    }

    @Test
    fun a_display_that_was_dozing_and_is_now_on_counts() {
        assertTrue(turnedOn(Display.STATE_DOZE, Display.STATE_ON))
    }

    @Test
    fun a_display_that_stays_on_does_not_count() {
        assertFalse(turnedOn(Display.STATE_ON, Display.STATE_ON))
    }

    @Test
    fun a_display_going_off_does_not_count() {
        assertFalse(turnedOn(Display.STATE_ON, Display.STATE_OFF))
    }

    @Test
    fun a_display_that_stays_dozing_does_not_count() {
        assertFalse(turnedOn(Display.STATE_DOZE, Display.STATE_DOZE))
    }
}
