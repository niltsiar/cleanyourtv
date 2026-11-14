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

    test("a panel that would pass the right margin from the anchors left edge ends on the anchors right edge") {
        left(anchorLeft = 1500) shouldBe 1750 - 550
        left(anchorLeft = 1583, anchorRight = 1834) shouldBe 1834 - 550
    }

    test("a panel with the end alignment ends on the anchors right edge") {
        left(anchorLeft = 1000, anchorRight = 1500, alignEnd = true) shouldBe 950
    }

    test("a panel with the end alignment that would pass the left margin from the anchors right edge starts on the anchors left edge") {
        left(anchorLeft = 100, anchorRight = 350, alignEnd = true) shouldBe 100
    }

    test("a panel too wide for either side of its anchor stops at the margin on the side it grows towards") {
        left(anchorLeft = 700, anchorRight = 950, panel = 1300) shouldBe screen - margin - 1300
        left(anchorLeft = 900, anchorRight = 1150, panel = 1300, alignEnd = true) shouldBe margin
        left(anchorLeft = 700, panel = 1800) shouldBe margin
    }

    test("a panel never starts before the left margin") {
        left(anchorLeft = 20) shouldBe margin
        left(anchorLeft = 0, anchorRight = 300, alignEnd = true) shouldBe margin
    }
}
