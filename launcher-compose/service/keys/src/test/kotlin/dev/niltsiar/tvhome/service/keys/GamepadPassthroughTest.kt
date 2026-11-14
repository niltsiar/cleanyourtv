package dev.niltsiar.tvhome.service.keys

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val GamepadPassthroughTest by testSuite {
    // Sources as dumpsys input reports them on a TCL G07.
    test("an xbox controller is a gamepad") {
        isGamepad(0x01000511).shouldBeTrue()
    }

    test("the voice remote with a joystick is not") {
        isGamepad(0x01000311).shouldBeFalse()
    }

    test("the virtual search keyboard is not") {
        isGamepad(0x00000701).shouldBeFalse()
    }

    test("a keypad is not") {
        isGamepad(0x00000101).shouldBeFalse()
    }
}
