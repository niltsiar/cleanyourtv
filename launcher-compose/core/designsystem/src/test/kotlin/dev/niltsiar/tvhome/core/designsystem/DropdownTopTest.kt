package dev.niltsiar.tvhome.core.designsystem

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val DropdownTopTest by testSuite {
    val screen = 1080
    val gap = 20
    val topLimit = 160

    fun top(
        anchorTop: Int,
        anchorBottom: Int,
        panel: Int = 400,
        preferAbove: Boolean = false,
    ) = dropdownTop(anchorTop, anchorBottom, panel, screen, gap, topLimit, preferAbove)

    test("the panel hangs below an anchor with room under it") {
        top(anchorTop = 200, anchorBottom = 300) shouldBe 320
    }

    test("the panel goes above an anchor with no room under it but room over it") {
        top(anchorTop = 940, anchorBottom = 1040) shouldBe 520
    }

    test("a panel with room on neither side stays under the top limit and overlaps the anchor") {
        top(anchorTop = 575, anchorBottom = 710) shouldBe topLimit
    }

    test("a panel taller than the room under the top limit starts at the limit") {
        top(anchorTop = 500, anchorBottom = 600, panel = 1000) shouldBe topLimit
    }

    test("a panel that prefers above goes above an anchor even with room under it") {
        top(anchorTop = 600, anchorBottom = 650, preferAbove = true) shouldBe 180
        top(anchorTop = 600, anchorBottom = 650, preferAbove = false) shouldBe 670
    }

    test("a panel that prefers above but has no room over the anchor hangs below it") {
        top(anchorTop = 200, anchorBottom = 300, preferAbove = true) shouldBe 320
    }
}
