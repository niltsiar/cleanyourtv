package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * [FlipHole] over a 200 x 100 px page of one colour, with a half-size picture of two bands, the hole about (50, 50) px. The
 * corner farthest from there is 158 px away.
 */
val FlipHoleTest by testSuite {
    robolectricTestSuite<FlipHoleTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

class FlipHoleTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                val hole = FlipHole()
                val page = Color(0xFF20C060)
                val left = Color(0xFF3050A0)
                val right = Color(0xFFA03080)
                val picture = bands(100, 50, listOf(left, right))
                var progress by mutableFloatStateOf(0f)
                var shown by mutableStateOf<ImageBitmap?>(picture)

                fun show() {
                    compose.setContent {
                        Box(
                            Modifier.size(200.dp, 100.dp).testTag("page").drawWithContent {
                                drawRect(page)
                                val held = shown
                                if (held == null) hole.drop() else with(hole) { drawFlip(held, Offset(50f, 50f), progress) }
                            },
                        )
                    }
                }

                fun at(
                    x: Int,
                    y: Int,
                ): Color =
                    compose
                        .onNodeWithTag("page")
                        .captureToImage()
                        .toPixelMap()[x, y]
            }
        } asContextForEach {
            test("at 0 the picture covers the page, each band stretched to its half") {
                show()
                at(50, 50) shouldBe left
                at(80, 99) shouldBe left
                at(120, 0) shouldBe right
                at(199, 99) shouldBe right
            }

            test("half way the page shows inside the hole and the picture stays where it was outside it") {
                progress = 0.5f
                show()
                withClue("the origin") { at(50, 50) shouldBe page }
                withClue("70 px right of the origin, inside the 79 px the hole holds") { at(120, 50) shouldBe page }
                withClue("70 px up and left, past the page's corner") { at(0, 0) shouldBe page }
                withClue("the far corner, 157 px away") { at(199, 99) shouldBe right }
                withClue("the far top corner, 152 px away") { at(199, 0) shouldBe right }
            }

            test("at 1 no pixel of the picture is left, at any corner") {
                progress = 1f
                show()
                for ((x, y) in listOf(0 to 0, 199 to 0, 0 to 99, 199 to 99)) withClue("($x, $y)") { at(x, y) shouldBe page }
            }

            test("dropped, the hole holds no picture") {
                progress = 0.5f
                show()
                at(199, 99) shouldBe right
                hole.shaded shouldBe picture

                shown = null
                at(199, 99) shouldBe page

                hole.shaded.shouldBeNull()
            }
        }
    })
