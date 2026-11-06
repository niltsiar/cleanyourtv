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
import dev.niltsiar.tvhome.core.designsystem.GlassDropdown
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val ROWS = 10
private val Row = 46.dp

/** A scrolling [GlassDropdown] of ten black rows, read from the rendered pixels: where it ends and where its content fades. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class GlassDropdownScrollUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val density get() = compose.activity.resources.displayMetrics.density

    private fun show() {
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun down(times: Int) =
        repeat(times) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            settle()
        }

    private val panelEdge get() = compose.onNodeWithTag("menu").getBoundsInRoot()

    /** The red channel, 0 to 255, [dp] dp over the panel's bottom edge. */
    private fun redAboveBottom(dp: Dp): Int = redAt(panelEdge.bottom - dp)

    private fun redUnderTop(dp: Dp): Int = redAt(panelEdge.top + dp)

    /** The red at [y], in the middle of the panel or at [x] dp from its left edge. */
    private fun redAt(
        y: Dp,
        x: Dp? = null,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val at = x?.let { panelEdge.left + it } ?: ((panelEdge.left + panelEdge.right) / 2)
        return Math.round(pixels[(at.value * density).toInt(), (y.value * density).toInt()].red * 255)
    }

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    /** The red at [y] of a black row that [alpha] of it shows through: the panel's bare surface, in its padding at the side, at [y]. */
    private fun through(
        alpha: Float,
        y: Dp,
    ) = Math.round((1 - alpha) * redAt(y, x = 6.dp))

    @Test
    fun a_panel_taller_than_its_room_ends_between_two_rows() {
        show()
        val panel = panelEdge

        val inside = (0 until ROWS).filter { compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot().top < panel.bottom }
        assertTrue("rows inside: $inside", inside.size in 1 until ROWS)
        inside.forEach { assertTrue("row $it", compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot().bottom <= panel.bottom) }
    }

    @Test
    fun the_edge_with_hidden_rows_fades_over_24_dp_and_the_edge_without_stays_whole() {
        show()

        // Row 0 starts 14 dp under the top edge; nothing is hidden above it.
        assertNear(0, redUnderTop(15.dp), 2)
        // Rows lie below, and the last whole row ends 4 dp over the edge: its bottom 20 dp are in the fade.
        assertNear(through(10 / 24f, panelEdge.bottom - 10.dp), redAboveBottom(10.dp), 14)
        assertNear(through(20 / 24f, panelEdge.bottom - 20.dp), redAboveBottom(20.dp), 14)
        assertNear(0, redAboveBottom(28.dp), 2)
    }

    @Test
    fun at_the_end_of_the_list_the_top_edge_fades_and_the_bottom_edge_does_not() {
        show()

        down(ROWS - 1)

        compose.onNodeWithTag("row:${ROWS - 1}").assertIsFocused()
        // The last row ends 14 dp over the edge; nothing is hidden below it.
        assertNear(0, redAboveBottom(15.dp), 2)
        // A row, not a gap, lies 12 dp under the top edge: half the fade.
        val rows = (0 until ROWS).map { compose.onNodeWithTag("row:$it").getUnclippedBoundsInRoot() }
        assertTrue("no row at the top edge", rows.any { it.top < panelEdge.top + 12.dp && it.bottom > panelEdge.top + 12.dp })
        assertNear(through(0.5f, panelEdge.top + 12.dp), redUnderTop(12.dp), 14)
    }

    @Test
    fun a_focused_row_clears_the_fade_at_the_edge_it_moved_towards() {
        show()

        // The third row is the last whole one, at the edge.
        down(2)

        compose.onNodeWithTag("row:2").assertIsFocused()
        val row = compose.onNodeWithTag("row:2").getUnclippedBoundsInRoot()
        assertNear(0, redAt(row.bottom - 1.dp), 2)
        assertNear(0, redAt(row.top + 1.dp), 2)
    }
}
