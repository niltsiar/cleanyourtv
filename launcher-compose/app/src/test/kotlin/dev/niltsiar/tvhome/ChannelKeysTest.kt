package dev.niltsiar.tvhome

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelKeysTest {
    @Test
    fun channel_up_and_down_are_consumed_with_the_key_on() {
        assertTrue(isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_CHANNEL_UP))
        assertTrue(isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_CHANNEL_DOWN))
    }

    @Test
    fun every_other_key_passes_with_the_key_on() {
        assertFalse(isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_TV))
        assertFalse(isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_PAGE_UP))
    }

    @Test
    fun nothing_is_consumed_with_the_key_off() {
        assertFalse(isIgnoredChannelKey(ignoreChannelKeys = false, keyCode = KeyEvent.KEYCODE_CHANNEL_UP))
        assertFalse(isIgnoredChannelKey(ignoreChannelKeys = false, keyCode = KeyEvent.KEYCODE_CHANNEL_DOWN))
    }
}
