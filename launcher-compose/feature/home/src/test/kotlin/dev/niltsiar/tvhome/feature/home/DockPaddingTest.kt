package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val DockPaddingTest by testSuite {
    test("without even padding the sides keep the rings room and the top and bottom a gap") {
        dockPadding(26.8.dp, shrunk = false, even = false) shouldBe DockPadding(side = 26.8.dp, top = 24.dp, bottom = 24.dp)
        dockPadding(29.8.dp, shrunk = false, even = false) shouldBe DockPadding(side = 29.8.dp, top = 24.dp, bottom = 24.dp)
    }

    test("a shrunk panel keeps at least 28 dp at its sides") {
        dockPadding(26.8.dp, shrunk = true, even = false) shouldBe DockPadding(side = 28.dp, top = 24.dp, bottom = 24.dp)
        dockPadding(29.8.dp, shrunk = true, even = false) shouldBe DockPadding(side = 29.8.dp, top = 24.dp, bottom = 24.dp)
    }

    test("even padding is the larger of the side and a gap on all four sides") {
        dockPadding(26.8.dp, shrunk = false, even = true) shouldBe DockPadding(side = 26.8.dp, top = 26.8.dp, bottom = 26.8.dp)
        dockPadding(29.8.dp, shrunk = false, even = true) shouldBe DockPadding(side = 29.8.dp, top = 29.8.dp, bottom = 29.8.dp)
        dockPadding(26.8.dp, shrunk = true, even = true) shouldBe DockPadding(side = 28.dp, top = 28.dp, bottom = 28.dp)
    }
}
