package dev.niltsiar.tvhome.feature.library

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val FirstCodePointTest by testSuite {
    test("a letter outside the basic plane is not split in two") {
        "𝐀pple".firstCodePoint() shouldBe "𝐀"
    }

    test("an ordinary first letter and an empty label work") {
        "Netflix".firstCodePoint() shouldBe "N"
        "".firstCodePoint() shouldBe ""
    }
}
