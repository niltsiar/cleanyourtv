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
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

val FocusRingTest by testSuite {
    val density = Density(1f)

    fun grown(
        shape: Shape,
        size: Size,
        delta: Float,
    ) = (shape.createOutline(size, LayoutDirection.Ltr, density).grownBy(delta) as Outline.Rounded).roundRect

    test("a rounded tile grows on every side and each corner by the same amount") {
        val ring = grown(RoundedCornerShape(12.dp), Size(200f, 112f), delta = 5f)

        ring.left shouldBe (-5f plusOrMinus 0f)
        ring.top shouldBe (-5f plusOrMinus 0f)
        ring.right shouldBe (205f plusOrMinus 0f)
        ring.bottom shouldBe (117f plusOrMinus 0f)
        ring.topLeftCornerRadius.x shouldBe (17f plusOrMinus 0f)
        ring.bottomRightCornerRadius.y shouldBe (17f plusOrMinus 0f)
    }

    test("a circle stays a circle") {
        val ring = grown(CircleShape, Size(40f, 40f), delta = 5f)

        ring.width shouldBe (50f plusOrMinus 0f)
        ring.topLeftCornerRadius.x shouldBe (25f plusOrMinus 0f)
    }

    test("a pill stays a pill") {
        val ring = grown(CircleShape, Size(120f, 40f), delta = 5f)

        ring.width shouldBe (130f plusOrMinus 0f)
        ring.height shouldBe (50f plusOrMinus 0f)
        ring.topRightCornerRadius.x shouldBe (25f plusOrMinus 0f)
    }

    test("a square cornered shape gets round corners as wide as the gap") {
        val ring = grown(RectangleShape, Size(100f, 50f), delta = 5f)

        ring.width shouldBe (110f plusOrMinus 0f)
        ring.topLeftCornerRadius.x shouldBe (5f plusOrMinus 0f)
    }
}
