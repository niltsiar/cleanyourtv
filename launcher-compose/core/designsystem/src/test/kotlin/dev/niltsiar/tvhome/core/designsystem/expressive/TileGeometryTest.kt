package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.math.abs

val TileGeometryTest by testSuite {
    robolectricTestSuite<TileGeometryTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

/** A 240 by 135 dp tile with 12 dp corners at density 2, and the 9 dp focus reach in pixels. */
private val TileSize = Size(480f, 270f)
private val Twice = Density(2f)
private const val REACH = 18f

private val Base = RoundedCornerShape(24f)

private fun band(
    tile: ExpressiveTile,
    rim: Boolean,
): BandGeometry = TilePainter().band(tile, rim, Base, TileSize, Twice)

class TileGeometryTestContent :
    RobolectricTestSuiteContent({
        for (tile in listOf(ExpressiveTile.Wave, ExpressiveTile.Cinch)) {
            for (rim in listOf(false, true)) {
                test("the ${tile.name} band${if (rim) " in rim" else ""} stays inside the focus reach and reaches past the tile") {
                    val outer = checkNotNull(band(tile, rim).rings[2]).getBounds()

                    withClue("keyline bounds $outer") {
                        outer.left shouldBeGreaterThanOrEqual -REACH
                        outer.top shouldBeGreaterThanOrEqual -REACH
                        outer.right shouldBeLessThanOrEqual TileSize.width + REACH
                        outer.bottom shouldBeLessThanOrEqual TileSize.height + REACH
                        outer.top shouldBeLessThan -REACH / 2f
                    }
                }
            }
        }

        test("the cinch band dips in at the middle of the top, by more than 4 dp against its highest point") {
            val measure = band(ExpressiveTile.Cinch, rim = false).holdMeasure
            val top =
                (0 until measure.length.toInt()).map { measure.getPosition(it.toFloat()) }.filter { it.y < TileSize.height / 2f }
            val middle = top.minBy { abs(it.x - TileSize.width / 2f) }
            val highest = top.minBy { it.y }

            withClue("middle $middle, highest $highest") { middle.y - highest.y shouldBeGreaterThan 8f }
        }

        test("the wave band's hold line starts at the middle of the top") {
            val start = band(ExpressiveTile.Wave, rim = false).holdMeasure.getPosition(0f)

            withClue("start $start") { abs(start.x - TileSize.width / 2f) shouldBeLessThan 24f }
        }

        test("tiles of one size and style share one band, built once") {
            val first = TilePainter().band(ExpressiveTile.Wave, rim = false, base = Base, size = Size(300f, 170f), density = Twice)

            TilePainter().band(
                ExpressiveTile.Wave,
                rim = false,
                base = Base,
                size = Size(300f, 170f),
                density = Twice,
            ) shouldBeSameInstanceAs
                first
            (first.masks?.count { it != null } ?: 0) shouldBe 2
        }

        test("a capsule's corner steps from its rest radius to half its height on whole pixels") {
            val steps = CapsuleSteps(TileSize, rest = 24f)

            steps.radius(0f) shouldBe 24f
            steps.radius(1f) shouldBe 135f
            steps.radius(1.8f) shouldBe 135f
            steps.radius(0.5f) shouldBe 79f
            steps.shape(79f) shouldBeSameInstanceAs steps.shape(79f)
        }
    })
