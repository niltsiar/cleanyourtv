package dev.niltsiar.tvhome.core.designsystem

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val DropdownLeftTest by testSuite {
    val screen = 1920
    val margin = 80

    fun left(
        anchorLeft: Int,
        anchorRight: Int = anchorLeft + 250,
        panel: Int = 550,
        alignEnd: Boolean = false,
    ) = dropdownLeft(anchorLeft, anchorRight, panel, screen, margin, alignEnd)

    test("the panel starts on the anchors left edge when it fits before the right margin") {
        left(anchorLeft = 700) shouldBe 700
        left(anchorLeft = 1290) shouldBe 1290
    }

    test("a panel that would pass the right margin ends on it with its left edge as near the anchor as that allows") {
        left(anchorLeft = 1500) shouldBe screen - margin - 550
    }

    test("a panel with the end alignment ends on the anchors right edge") {
        left(anchorLeft = 1000, anchorRight = 1500, alignEnd = true) shouldBe 950
    }

    test("a panel never starts before the left margin") {
        left(anchorLeft = 20) shouldBe margin
        left(anchorLeft = 0, anchorRight = 300, alignEnd = true) shouldBe margin
    }
}
