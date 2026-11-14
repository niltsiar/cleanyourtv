package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val InputSourcesTest by testSuite {
    test("maps the last input component") {
        mappedSource(listOf("HW7=25"), "service/.Hdmi/HW7") shouldBe 25
    }

    test("trims mapping whitespace") {
        mappedSource(listOf(" HW7 = 25 "), "service/.Hdmi/HW7") shouldBe 25
    }

    test("a dash hides an input") {
        mappedSource(listOf("HW8=-"), "service/.Hdmi/HW8") shouldBe -1
    }

    test("an unknown or malformed input is not mapped") {
        mappedSource(listOf("HW5=23", "HW7=nope"), "service/.Hdmi/HW6").shouldBeNull()
        mappedSource(listOf("HW7=nope"), "service/.Hdmi/HW7").shouldBeNull()
        mappedSource(emptyList(), "service/.Hdmi/HW7").shouldBeNull()
    }

    test("an activity is a component and an action is not") {
        isComponent("com.xiaomi.mitv.tvplayer/.ExternalSourceActivity").shouldBeTrue()
        isComponent("com.xiaomi.mitv.tvplayer/com.xiaomi.mitv.tvplayer.ExternalSourceActivity").shouldBeTrue()
        isComponent("com.xgimi.action.hdmiPlayer").shouldBeFalse()
        isComponent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").shouldBeFalse()
        isComponent("").shouldBeFalse()
        isComponent(null).shouldBeFalse()
        isComponent("/.Activity").shouldBeFalse()
        isComponent("com.example/").shouldBeFalse()
        isComponent("a/b/c").shouldBeFalse()
    }
}
