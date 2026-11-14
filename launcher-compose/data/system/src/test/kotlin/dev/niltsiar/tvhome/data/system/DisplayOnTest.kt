package dev.niltsiar.tvhome.data.system

import android.view.Display
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val DisplayOnTest by testSuite {
    test("a display that was off and is now on counts") {
        turnedOn(Display.STATE_OFF, Display.STATE_ON).shouldBeTrue()
    }

    test("a display that was dozing and is now on counts") {
        turnedOn(Display.STATE_DOZE, Display.STATE_ON).shouldBeTrue()
    }

    test("a display that stays on does not count") {
        turnedOn(Display.STATE_ON, Display.STATE_ON).shouldBeFalse()
    }

    test("a display going off does not count") {
        turnedOn(Display.STATE_ON, Display.STATE_OFF).shouldBeFalse()
    }

    test("a display that stays dozing does not count") {
        turnedOn(Display.STATE_DOZE, Display.STATE_DOZE).shouldBeFalse()
    }
}
