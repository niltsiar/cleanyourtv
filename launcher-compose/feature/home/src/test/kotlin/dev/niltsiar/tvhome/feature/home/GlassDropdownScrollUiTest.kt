package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.GlassDropdown
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ranges.shouldBeIn
import kotlin.math.abs

private const val ROWS = 10
private val Row = 46.dp

/** Inside a row, 4 dp from the bare surface that [GlassDropdownScrollUiTest.through] reads: the sheen varies across the panel. */
private val HiddenX = 10.dp

/** A scrolling [GlassDropdown] of ten black rows, read from the rendered pixels: where it ends and where its content fades. */
val GlassDropdownScrollUiTest by testSuite {
    robolectricTestSuite<GlassDropdownScrollUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class GlassDropdownScrollUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { GlassDropdownScrollUiTestFixture() } asContextForEach {
            test("a panel taller than its room ends between two rows") {
                show()
                val panel = panelEdge

                val inside = (0 until ROWS).filter { compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot().top < panel.bottom }
                withClue("rows inside: $inside") { inside.size shouldBeIn (1 until ROWS) }
                inside.forEach {
                    withClue("row $it") {
                        compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot().bottom shouldBeLessThanOrEqualTo
                            panel.bottom
                    }
                }
            }

            test("the edge with hidden rows hides its first 12 dp and fades over 36 dp and the edge without stays whole") {
                show()

                // Row 0 starts 14 dp under the top edge; nothing is hidden above it.
                assertNear(0, redUnderTop(15.dp), 2)
                // Rows lie below: hidden in the 12 dp band at the edge, half shown in the middle of the ramp, whole past it.
                listOf(6.dp, 24.dp, 40.dp).forEach { assertRowAt(panelEdge.bottom - it) }
                assertNear(through(0f, panelEdge.bottom - 6.dp), redAt(panelEdge.bottom - 6.dp, x = HiddenX), 2)
                assertNear(through(0.5f, panelEdge.bottom - 24.dp), redAboveBottom(24.dp), 14)
                assertNear(0, redAboveBottom(40.dp), 2)
            }

            test("at the end of the list the top edge fades and the bottom edge does not") {
                show()

                down(ROWS - 1)

                compose.onNodeWithTag("row:${ROWS - 1}").assertIsFocused()
                // The last row ends 14 dp over the edge; nothing is hidden below it.
                assertNear(0, redAboveBottom(15.dp), 2)
                // Rows lie in the band at the top edge, where they are hidden, and in the middle of the ramp, where they show half.
                listOf(6.dp, 24.dp).forEach { assertRowAt(panelEdge.top + it) }
                assertNear(through(0f, panelEdge.top + 6.dp), redAt(panelEdge.top + 6.dp, x = HiddenX), 2)
                assertNear(through(0.5f, panelEdge.top + 24.dp), redUnderTop(24.dp), 14)
            }

            test("a focused row clears the fade at the edge it moved towards") {
                show()

                // The third row is the last whole one, at the edge.
                down(2)

                compose.onNodeWithTag("row:2").assertIsFocused()
                val row = compose.onNodeWithTag("row:2").getUnclippedBoundsInRoot()
                assertNear(0, redAt(row.bottom - 1.dp), 2)
                assertNear(0, redAt(row.top + 1.dp), 2)
            }
        }
    })

private class GlassDropdownScrollUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val density get() = compose.activity.resources.displayMetrics.density

    fun show() {
        compose.setContent {
            TvTheme {
                val anchor = remember { mutableStateOf(Rect(100f, 100f, 400f, 160f)) }
                val first = remember { FocusRequester() }
                GlassDropdown(
                    anchor = anchor,
                    alignEnd = false,
                    firstFocus = first,
                    minWidth = 200.dp,
                    bottomLimit = 300.dp,
                    childHeights = List(ROWS) { Row },
                ) {
                    repeat(ROWS) { row ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(Row)
                                .testTag("row:$row")
                                .then(if (row == 0) Modifier.focusRequester(first) else Modifier)
                                .focusable()
                                .background(Color.Black),
                        )
                    }
                }
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        settle()
        compose.onNodeWithTag("row:0").assertIsFocused()
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun down(times: Int) =
        repeat(times) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            settle()
        }

    val panelEdge get() = compose.onNodeWithTag("menu").getBoundsInRoot()

    /** The red channel, 0 to 255, [dp] dp over the panel's bottom edge. */
    fun redAboveBottom(dp: Dp): Int = redAt(panelEdge.bottom - dp)

    fun redUnderTop(dp: Dp): Int = redAt(panelEdge.top + dp)

    /** The red at [y], in the middle of the panel or at [x] dp from its left edge. */
    fun redAt(
        y: Dp,
        x: Dp? = null,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val at = x?.let { panelEdge.left + it } ?: ((panelEdge.left + panelEdge.right) / 2)
        return Math.round(pixels[(at.value * density).toInt(), (y.value * density).toInt()].red * 255)
    }

    fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    /** The red at [y] of a black row that [alpha] of it shows through: the panel's bare surface, in its padding at the side, at [y]. */
    fun through(
        alpha: Float,
        y: Dp,
    ) = Math.round((1 - alpha) * redAt(y, x = 6.dp))

    /** Fails unless a row, not a gap, lies at [y]. */
    fun assertRowAt(y: Dp) {
        val rows = (0 until ROWS).map { compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot() }
        withClue("a row at $y") { (rows.any { it.top < y && it.bottom > y }).shouldBeTrue() }
    }
}
