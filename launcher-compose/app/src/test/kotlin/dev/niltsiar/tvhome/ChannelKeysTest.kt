package dev.niltsiar.tvhome

import android.view.KeyEvent
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val ChannelKeysTest by testSuite {
    test("channel up and down are consumed with the key on") {
        isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_CHANNEL_UP).shouldBeTrue()
        isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_CHANNEL_DOWN).shouldBeTrue()
    }

    test("every other key passes with the key on") {
        isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_DPAD_UP).shouldBeFalse()
        isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_TV).shouldBeFalse()
        isIgnoredChannelKey(ignoreChannelKeys = true, keyCode = KeyEvent.KEYCODE_PAGE_UP).shouldBeFalse()
    }

    test("nothing is consumed with the key off") {
        isIgnoredChannelKey(ignoreChannelKeys = false, keyCode = KeyEvent.KEYCODE_CHANNEL_UP).shouldBeFalse()
        isIgnoredChannelKey(ignoreChannelKeys = false, keyCode = KeyEvent.KEYCODE_CHANNEL_DOWN).shouldBeFalse()
    }
}
