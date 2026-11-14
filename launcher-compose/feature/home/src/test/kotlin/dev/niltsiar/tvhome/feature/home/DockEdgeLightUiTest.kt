package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.DockEdgeLight
import dev.niltsiar.tvhome.core.designsystem.DockSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlin.math.abs
import kotlin.math.roundToInt

/** The light of the dock edge in isolation: a 400 by 120 dp dock panel with no tint on a black screen, its tile's centre at 200 dp. */
val DockEdgeLightUiTest by testSuite {
    robolectricTestSuite<DockEdgeLightUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class DockEdgeLightUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockEdgeLightUiTestFixture() } asContextForEach {
            test("the floor glint is the light colour at a fifth of its alpha under the tile and gone 100 dp away") {
                showDock()

                // The ellipse is 200 by 20 dp and sits on the panel's bottom edge, so its middle is 10 dp above it.
                assertChannels(listOf(26, 45, 32), at(200f, 110f))
                assertChannels(listOf(0, 0, 0), at(100f, 110f), tolerance = 2)
            }

            test("the floor glint is flat and wide") {
                showDock()

                val side = at(200f + 60f, 110f)
                val up = at(200f, 110f - 8f)

                // 60 dp to the side is 0.6 of the radius across, and 8 dp up is 0.8 of the radius up: the glint is wider than tall.
                side.green shouldBeGreaterThan up.green * 1.5f
            }

            test("the rim highlight is the light at 085 over the panels rim at the centre and gone 60 dp away") {
                showDock()

                val centre = at(200f, 0.75f)
                val away = at(200f + 62f, 0.75f)

                withClue("highlight over the rim") { centre.green shouldBeGreaterThan away.green + 0.3f }
                withClue("the rim itself, white at 0.48 under a 0.16 sheen, is the same grey either side") {
                    abs(away.red - away.blue) shouldBeLessThan 0.02f
                }
            }

            test("a light that is not shown draws nothing") {
                showDock(visible = false)

                assertChannels(listOf(0, 0, 0), at(200f, 110f), tolerance = 2)
            }
        }
    })

private class DockEdgeLightUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val green = Color(0xFF80E0A0)

    val density get() = compose.activity.resources.displayMetrics.density

    fun showDock(visible: Boolean = true) {
        compose.setContent {
            TvTheme {
                val scope = rememberCoroutineScope()
                val edge = remember { DockEdgeLight(scope) }
                LaunchedEffect(edge) {
                    edge.focus(centreX = 200f * density) { green }
                    edge.show(visible)
                }
                Box(Modifier.size(960.dp, 540.dp).background(Color.Black)) {
                    DockSurface(
                        glass = rememberGlassState(),
                        tint = { Color.Transparent },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(400.dp, 120.dp),
                        edgeLight = edge,
                    ) {}
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun at(
        x: Float,
        y: Float,
    ): Color = compose.onRoot().captureToImage().toPixelMap()[(x * density).roundToInt(), (y * density).roundToInt()]

    fun assertChannels(
        expected: List<Int>,
        actual: Color,
        tolerance: Int = 4,
    ) {
        val got = listOf(actual.red, actual.green, actual.blue).map { (it * 255).roundToInt() }
        withClue("expected $expected, got $got") { expected.zip(got).all { (e, g) -> abs(e - g) <= tolerance }.shouldBeTrue() }
    }
}
