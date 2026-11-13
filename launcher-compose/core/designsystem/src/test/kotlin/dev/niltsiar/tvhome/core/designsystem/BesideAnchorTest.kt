package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val BesideAnchorTest by testSuite {
    val screenWidth = 1920
    val gap = 32
    val top = 512
    val fallbackTop = 184
    val bottom = 984

    fun place(
        anchor: IntRect,
        width: Int = 400,
        height: Int = 300,
    ) = besideAnchor(anchor, width, height, screenWidth, gap, top, fallbackTop, bottom)

    test("the panel sits right of its tile centred on it") {
        place(IntRect(left = 280, top = 680, right = 580, bottom = 840)) shouldBe IntOffset(612, 610)
    }

    test("a tile with no room on its right gets the panel on its left") {
        place(IntRect(left = 1480, top = 680, right = 1780, bottom = 840)) shouldBe IntOffset(1048, 610)
    }

    test("a panel centred above the top limit comes down to it") {
        place(IntRect(left = 80, top = 560, right = 380, bottom = 720)).y shouldBe top
    }

    test("a panel centred past the bottom limit goes up to end on it") {
        place(IntRect(left = 80, top = 900, right = 380, bottom = 1060)).y shouldBe bottom - 300
    }

    test("a panel taller than the room under the top limit may rise to the fallback") {
        place(IntRect(left = 80, top = 700, right = 380, bottom = 860), height = 600).y shouldBe bottom - 600
    }

    test("a panel too tall for the screen starts at the fallback") {
        place(IntRect(left = 80, top = 700, right = 380, bottom = 860), height = 900).y shouldBe fallbackTop
    }
}
