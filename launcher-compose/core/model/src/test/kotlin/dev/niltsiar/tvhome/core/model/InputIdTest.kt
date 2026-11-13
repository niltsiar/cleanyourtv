package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val InputIdTest by testSuite {
    test("an input value gives the input id without the prefix and trimmed") {
        inputIdOf("input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5") shouldBe
            "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
        inputIdOf("input: HW5 ") shouldBe "HW5"
    }

    test("every other value is no input") {
        inputIdOf(null).shouldBeNull()
        inputIdOf("FEATURED_LAUNCH").shouldBeNull()
        inputIdOf("INPUT:HW5").shouldBeNull()
        inputIdOf("not-input:HW5").shouldBeNull()
    }

    test("an empty input id is no input") {
        inputIdOf("input:").shouldBeNull()
        inputIdOf("input:   ").shouldBeNull()
    }
}
