package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** In Expressive a menu grows out of its anchor while springs run, and only fades in with reduced motion. */
val MenuGrowTest by testSuite {
    test("a panel below its anchor grows from its top edge under the anchor's centre") {
        growOrigin(Rect(100f, 50f, 300f, 150f), IntOffset(150, 160), width = 400, height = 200) shouldBe TransformOrigin(0.125f, 0f)
    }

    test("a panel above its anchor grows from its bottom edge") {
        growOrigin(Rect(100f, 400f, 300f, 500f), IntOffset(0, 100), width = 400, height = 200) shouldBe TransformOrigin(0.5f, 1f)
    }

    test("a panel right of its anchor grows from its left edge, level with the anchor's centre") {
        growOrigin(Rect(0f, 100f, 100f, 200f), IntOffset(120, 50), width = 300, height = 400) shouldBe TransformOrigin(0f, 0.25f)
    }

    test("a panel whose anchor's centre lies past its end grows from its nearest corner") {
        growOrigin(Rect(900f, 0f, 1000f, 40f), IntOffset(400, 60), width = 300, height = 200) shouldBe TransformOrigin(1f, 0f)
    }

    robolectricTestSuite<MenuGrowTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

class MenuGrowTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                /**
                 * A menu hanging from an anchor at the top left of a black 480 x 320 dp scene in Expressive with [motion]:
                 * the bounds of what differs from the empty scene [at] ms after it opens, and once it has settled.
                 */
                fun drawnBounds(
                    motion: Motion,
                    at: Long,
                ): Pair<IntRect, IntRect> {
                    compose.mainClock.autoAdvance = false
                    var open by mutableStateOf(false)
                    compose.setContent {
                        TvTheme(look = Look.Expressive, motion = motion) {
                            Box(Modifier.size(480.dp, 320.dp).background(Color.Black).testTag("scene")) {
                                if (open) {
                                    val anchor = remember { mutableStateOf(Rect(40f, 20f, 200f, 60f)) }
                                    GlassMenu(
                                        rows = listOf(android.R.string.ok, android.R.string.cancel, android.R.string.copy),
                                        anchor = anchor,
                                        alignEnd = false,
                                        label = { it },
                                        rowTag = { "row:$it" },
                                        onPick = {},
                                    )
                                }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeBy(100)
                    val empty = scene()
                    compose.runOnUiThread { open = true }
                    compose.waitForIdle()
                    val start = compose.mainClock.currentTime
                    while (compose.mainClock.currentTime - start < at) compose.mainClock.advanceTimeByFrame()
                    val early = differing(empty, scene())
                    compose.mainClock.advanceTimeBy(2_000)
                    return early to differing(empty, scene())
                }

                fun scene(): Array<IntArray> =
                    compose.onNodeWithTag("scene").captureToImage().toPixelMap().let { map ->
                        Array(map.height) { y -> IntArray(map.width) { x -> map[x, y].toArgb() } }
                    }

                fun differing(
                    a: Array<IntArray>,
                    b: Array<IntArray>,
                ): IntRect {
                    var left = Int.MAX_VALUE
                    var top = Int.MAX_VALUE
                    var right = -1
                    var bottom = -1
                    for (y in a.indices) {
                        for (x in a[y].indices) {
                            if (a[y][x] != b[y][x]) {
                                left = minOf(left, x)
                                top = minOf(top, y)
                                right = maxOf(right, x)
                                bottom = maxOf(bottom, y)
                            }
                        }
                    }
                    return IntRect(left, top, right, bottom)
                }
            }
        } asContextForEach {
            test("with springs running the menu is smaller than its settled panel 48 ms in, and grows from its top edge") {
                val (early, settled) = drawnBounds(Motion(look = Look.Expressive), at = 48)

                withClue("early $early, settled $settled") {
                    early shouldNotBe settled
                    early.top shouldBe settled.top
                }
            }

            test("with reduced motion the menu fades in at its full size") {
                val (early, settled) = drawnBounds(Motion(reducedMotion = true, look = Look.Expressive), at = 48)

                withClue("early $early, settled $settled") { early shouldBe settled }
            }
        }
    })
