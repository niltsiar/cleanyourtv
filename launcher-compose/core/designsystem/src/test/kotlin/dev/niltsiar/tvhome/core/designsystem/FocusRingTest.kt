package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRingTest {
    private val density = Density(1f)

    private fun grown(
        shape: Shape,
        size: Size,
        delta: Float,
    ) = (shape.createOutline(size, LayoutDirection.Ltr, density).grownBy(delta) as Outline.Rounded).roundRect

    @Test
    fun a_rounded_tile_grows_on_every_side_and_each_corner_by_the_same_amount() {
        val ring = grown(RoundedCornerShape(12.dp), Size(200f, 112f), delta = 5f)

        assertEquals(-5f, ring.left, 0f)
        assertEquals(-5f, ring.top, 0f)
        assertEquals(205f, ring.right, 0f)
        assertEquals(117f, ring.bottom, 0f)
        assertEquals(17f, ring.topLeftCornerRadius.x, 0f)
        assertEquals(17f, ring.bottomRightCornerRadius.y, 0f)
    }

    @Test
    fun a_circle_stays_a_circle() {
        val ring = grown(CircleShape, Size(40f, 40f), delta = 5f)

        assertEquals(50f, ring.width, 0f)
        assertEquals(25f, ring.topLeftCornerRadius.x, 0f)
    }

    @Test
    fun a_pill_stays_a_pill() {
        val ring = grown(CircleShape, Size(120f, 40f), delta = 5f)

        assertEquals(130f, ring.width, 0f)
        assertEquals(50f, ring.height, 0f)
        assertEquals(25f, ring.topRightCornerRadius.x, 0f)
    }

    @Test
    fun a_square_cornered_shape_gets_round_corners_as_wide_as_the_gap() {
        val ring = grown(RectangleShape, Size(100f, 50f), delta = 5f)

        assertEquals(110f, ring.width, 0f)
        assertEquals(5f, ring.topLeftCornerRadius.x, 0f)
    }
}
