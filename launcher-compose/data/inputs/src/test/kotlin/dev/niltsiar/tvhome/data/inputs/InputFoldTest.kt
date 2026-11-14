package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val InputFoldTest by testSuite {
    test("a connected hdmi port stays") {
        inputFolds(hdmi = true, PortState.Connected).shouldBeFalse()
    }

    test("an empty port reporting standby folds") {
        inputFolds(hdmi = true, PortState.Standby).shouldBeTrue()
    }

    test("a disconnected port folds") {
        inputFolds(hdmi = true, PortState.Disconnected).shouldBeTrue()
    }

    test("an hdmi row with no state stays") {
        inputFolds(hdmi = true, PortState.None).shouldBeFalse()
    }

    test("an av jack always folds") {
        inputFolds(hdmi = false, PortState.None).shouldBeTrue()
        inputFolds(hdmi = false, PortState.Connected).shouldBeTrue()
    }
}
