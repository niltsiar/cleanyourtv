package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
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
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

val LookStateTest by testSuite {
    robolectricTestSuite<LookStateTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

private class Counts {
    var compositions = 0
    var draws = 0
    val remembered = mutableListOf<Any>()
}

/** Black in Glass, the seed in Expressive, read in the draw phase. */
@Composable
private fun LookSwatch(counts: Counts) {
    val state = TvTheme.lookState
    SideEffect {
        counts.compositions++
    }
    Box(
        Modifier.size(10.dp).testTag("swatch").drawBehind {
            counts.draws++
            drawRect(if (state.look == Look.Expressive) state.seed else Color.Black)
        },
    )
}

@Composable
private fun RememberOnSurface(counts: Counts) {
    SurfaceContent {
        val token = remember { Any() }
        SideEffect { counts.remembered += token }
    }
}

class LookStateTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())

                fun swatch(): Color = compose.onNodeWithTag("swatch").captureToImage().toPixelMap()[5, 5]
            }
        } asContextForEach {
            test("a look or seed change redraws what reads it in draw and recomposes nothing under the theme") {
                val counts = Counts()
                var look by mutableStateOf(Look.Glass)
                var seed by mutableStateOf(Color.Red)
                compose.setContent { TvTheme(look = look, seed = { seed }) { LookSwatch(counts) } }
                swatch() shouldBe Color.Black
                val draws = counts.draws

                look = Look.Expressive
                swatch() shouldBe Color.Red
                seed = Color.Blue
                swatch() shouldBe Color.Blue

                counts.draws shouldBeGreaterThan draws
                counts.compositions shouldBe 1
            }

            test("a surface that turns light or dark keeps what its content remembers") {
                val counts = Counts()
                var dark by mutableStateOf(true)
                compose.setContent { TvTheme(look = Look.Glass, darkTheme = dark) { RememberOnSurface(counts) } }
                compose.waitForIdle()

                dark = false
                compose.waitForIdle()
                dark = true
                compose.waitForIdle()

                counts.remembered.size shouldBeGreaterThan 1
                counts.remembered.distinct().size shouldBe 1
            }
        }
    })
