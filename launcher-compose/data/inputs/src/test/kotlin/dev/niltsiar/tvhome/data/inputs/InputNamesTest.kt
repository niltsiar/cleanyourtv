package dev.niltsiar.tvhome.data.inputs

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val InputNamesTest by testSuite {
    test("philips type becomes an emoji") {
        tidyInputName("Xbox&GAME_CONSOLE") shouldBe "🎮 Xbox"
        tidyInputName("Switch &GAME_CONSOLE") shouldBe "🎮 Switch"
        tidyInputName("Ordinateur&COMPUTER") shouldBe "💻 Ordinateur"
    }

    test("an unknown type leaves the name alone") {
        tidyInputName("Box&SOMETHING_NEW") shouldBe "Box"
    }

    test("a type with no name is just its emoji") {
        tidyInputName("&GAME_CONSOLE") shouldBe "🎮"
    }

    test("the text null and blanks are no name") {
        tidyInputName("null").shouldBeNull()
        tidyInputName("  ").shouldBeNull()
        tidyInputName(null).shouldBeNull()
    }

    test("other names are kept") {
        tidyInputName("MacBook Pro") shouldBe "MacBook Pro"
        tidyInputName("Tom & Jerry") shouldBe "Tom & Jerry"
        tidyInputName("AT&T") shouldBe "AT&T"
        tidyInputName("R&D") shouldBe "R&D"
    }
}
