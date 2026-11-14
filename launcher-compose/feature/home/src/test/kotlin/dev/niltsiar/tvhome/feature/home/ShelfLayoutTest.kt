package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.ringColor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

val ShelfLayoutTest by testSuite {
    testFixture {
        object {
            val art = Color(0xFFC81818)

            val plain = Color.Unspecified
        }
    } asContextForEach {
        test("without the cinematic shelf tiles keep the theme accent") {
            val layout = ShelfLayout()

            layout.focusAccent(art, inDock = true) shouldBe plain
            layout.focusAccent(art, inDock = false) shouldBe plain
        }

        test("the cinematic shelf colours the dock and not the grid") {
            val layout = ShelfLayout(cinematic = true)

            layout.focusAccent(art, inDock = true) shouldBe art
            layout.focusAccent(art, inDock = false) shouldBe plain
        }

        test("cinematic white focus keeps the dock white") {
            val layout = ShelfLayout(cinematic = true, whiteFocus = true)

            layout.focusAccent(art, inDock = true) shouldBe Color.White
            layout.focusAccent(art, inDock = false) shouldBe plain
        }

        test("app glow colours every tile even with white focus or without the shelf") {
            ShelfLayout(appGlow = true).focusAccent(art, inDock = false) shouldBe art
            ShelfLayout(appGlow = true).focusAccent(art, inDock = true) shouldBe art
            ShelfLayout(cinematic = true, whiteFocus = true, appGlow = true).focusAccent(art, inDock = true) shouldBe art
        }

        test("the ring takes the app colour in the dock and in the grid only with app glow") {
            val ring = ringColor(art)

            ShelfLayout().ringColour(art, inDock = true) shouldBe ring
            ShelfLayout().ringColour(art, inDock = false) shouldBe Color.White
            ShelfLayout(appGlow = true).ringColour(art, inDock = false) shouldBe ring
        }

        test("the ring of a tile whose edge is its own colour is lifted toward white") {
            val blue = Color(0xFF288BFF)

            ShelfLayout().ringColour(blue, inDock = true, edge = blue) shouldBe ringColor(blue, tile = blue)
            ShelfLayout().ringColour(blue, inDock = true, edge = blue) shouldNotBe ringColor(blue)
        }

        test("cinematic white focus keeps every ring white") {
            val layout = ShelfLayout(whiteFocus = true, appGlow = true)

            layout.ringColour(art, inDock = true) shouldBe Color.White
            layout.ringColour(art, inDock = false) shouldBe Color.White
        }

        test("the theme percentages become fractions and motion follows the switches") {
            val layout = TvConfig(cinematicHome = true, cinematicArt = 20, cinematicScrim = 40, cinematicDots = false).shelfLayout()

            layout.cinematic.shouldBeTrue()
            layout.artAlpha shouldBe (0.2f plusOrMinus 0f)
            layout.scrimAlpha shouldBe (0.4f plusOrMinus 0f)
            layout.dots.shouldBeFalse()
            layout.animated.shouldBeTrue()
            TvConfig(animations = false).shelfLayout().animated.shouldBeFalse()
            TvConfig(reducedMotion = true).shelfLayout().animated.shouldBeFalse()
        }

        test("the glow switches reach the layout") {
            val layout = TvConfig(ambientGlow = false, glowSnap = true, appGlow = true, cinematicWhiteFocus = true).shelfLayout()

            layout.ambientGlow.shouldBeFalse()
            (layout.glowSnap && layout.appGlow && layout.whiteFocus).shouldBeTrue()
        }
    }
}
