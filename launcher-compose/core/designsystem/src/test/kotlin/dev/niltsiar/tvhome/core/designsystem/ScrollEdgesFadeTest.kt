package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * A white column 1000 dp tall in a black 300 dp viewport, scrolled 400 dp, 2 px to a dp: content lies past both edges, so
 * each edge fades in full, and the red of a pixel is the alpha the fade leaves there.
 */
val ScrollEdgesFadeTest by testSuite {
    robolectricTestSuite<ScrollEdgesFadeTestContent>(
        "sdk 31, xhdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "xhdpi"
            },
    )
}

class ScrollEdgesFadeTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())

                fun shot(): PixelMap {
                    compose.setContent {
                        val scroll = rememberScrollState(with(LocalDensity.current) { 400.dp.roundToPx() })
                        Box(Modifier.size(100.dp, 300.dp).background(Color.Black).testTag("viewport")) {
                            Column(Modifier.fillMaxSize().fadeScrolledEdges(scroll).verticalScroll(scroll)) {
                                Box(Modifier.fillMaxWidth().height(1000.dp).background(Color.White))
                            }
                        }
                    }
                    return compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
                }
            }
        } asContextForEach {
            test("each edge hides its first 12 dp then eases in to half at 32 dp and whole at 52 dp below a straight ramp") {
                val pixels = shot()
                // The pixel row whose centre lies `dp` dp in from the top edge, and from the bottom edge.
                val edges =
                    mapOf<String, (Int) -> Float>(
                        "top" to { dp -> pixels[pixels.width / 2, dp * 2].red },
                        "bottom" to { dp -> pixels[pixels.width / 2, pixels.height - 1 - dp * 2].red },
                    )
                edges.forEach { (edge, alpha) ->
                    withClue("$edge, 6 dp in") { alpha(6) shouldBe (0f plusOrMinus 0.02f) }
                    withClue("$edge, 12 dp in") { alpha(12) shouldBe (0f plusOrMinus 0.02f) }
                    withClue("$edge, 32 dp in") { alpha(32) shouldBe (0.5f plusOrMinus 0.02f) }
                    withClue("$edge, 52 dp in") { alpha(52) shouldBe (1f plusOrMinus 0.02f) }
                    // A quarter along the ramp a straight one shows 0.25 and the eased one about 0.16.
                    withClue("$edge, 22 dp in") { alpha(22) shouldBeLessThan 0.2f }
                }
            }
        }
    })
