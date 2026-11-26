package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.BandReach
import dev.niltsiar.tvhome.core.designsystem.expressive.cinchArtRadius
import dev.niltsiar.tvhome.core.designsystem.expressive.cinchOutline
import dev.niltsiar.tvhome.core.designsystem.expressive.restRadius
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** The tiles' art: a green that neither the band, the gap, the keyline nor the black ground has. */
private val Art = Color(0xFF00C000)

/** Whether [pixel] carries the art: green well above its red and blue, as the art is, alone or under a dark keyline. */
private fun showsArt(pixel: Color): Boolean = pixel.green > pixel.red + 0.05f && pixel.green > pixel.blue + 0.05f

val ExpressiveTileArtTest by testSuite {
    robolectricTestSuite<ExpressiveTileArtTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ExpressiveTileArtTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                /** A focused cinch tile the size of a dock tile on a 1080p TV, 125 by 70 dp and not grown, on a black ground, in [style]. */
                fun showCinch(style: FocusStyle) {
                    compose.mainClock.autoAdvance = false
                    // The native graphics mode starts in touch mode, where nothing takes key focus.
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    val focus = FocusRequester()
                    compose.setContent {
                        TvTheme(look = Look.Expressive, focusStyle = style, expressiveTile = ExpressiveTile.Cinch) {
                            Box(
                                Modifier.size(400.dp, 225.dp).background(Color.Black).testTag("scene"),
                                contentAlignment = Alignment.Center,
                            ) {
                                FocusSurface(
                                    onClick = {},
                                    modifier = Modifier.focusRequester(focus).size(125.dp, 70.dp).testTag("tile"),
                                    focusedScale = 1f,
                                ) { Box(Modifier.fillMaxSize().background(Art)) }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnUiThread { focus.requestFocus() }
                    settle()
                }

                fun settle() {
                    compose.mainClock.advanceTimeBy(2_000)
                    compose.waitForIdle()
                }

                fun shot(): PixelMap = compose.onNodeWithTag("scene").captureToImage().toPixelMap()

                /** The tile's bounds in the scene, in pixels. */
                fun tile(): Rect {
                    val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
                    return compose
                        .onNodeWithTag("tile")
                        .fetchSemanticsNode()
                        .boundsInRoot
                        .translate(-scene.left, -scene.top)
                }
            }
        } asContextForEach {
            for (style in FocusStyle.entries) {
                test("a focused cinch tile in ${style.name} shows no art past its band, through its keyline included") {
                    showCinch(style)
                    val shot = shot()
                    val tile = tile()
                    val edge =
                        with(compose.density) { cinchOutline(tile.width, tile.height, BandReach.toPx()) }
                    val inside =
                        ImageBitmap(shot.width, shot.height)
                            .also { mask ->
                                Canvas(mask).apply {
                                    translate(tile.left, tile.top)
                                    drawPath(edge, Paint())
                                }
                            }.toPixelMap()

                    val outside =
                        (0 until shot.height).flatMap { y ->
                            (0 until shot.width).filter { x -> inside[x, y].alpha == 0f && showsArt(shot[x, y]) }.map { x -> x to y }
                        }
                    withClue("art pixels outside the band, first of them") { outside.take(8).shouldBeEmpty() }
                }
            }

            test("a focused cinch tile's art outline follows the rest corner of the base shape it is asked for") {
                val shape = FocusedShape(LookState(Look.Expressive) { Color.Red }.apply { tile = ExpressiveTile.Cinch })
                val size = Size(250f, 140f)
                val density = Density(2f)

                fun corner(base: Shape) =
                    (shape.outline(base, size, LayoutDirection.Ltr, density) as Outline.Rounded).roundRect.topLeftCornerRadius.x

                fun expected(base: Shape) = with(density) { cinchArtRadius(size.width, size.height, restRadius(base, size)) }
                val (soft, round) = RoundedCornerShape(20.dp) to RoundedCornerShape(30.dp)
                expected(soft) shouldNotBe expected(round)

                corner(soft) shouldBe expected(soft)
                corner(round) shouldBe expected(round)
            }

            test("a focused cinch tile's halo starts at the outer edge of its band, so its waist shows no ground inside the glow") {
                showCinch(FocusStyle.Halo)
                val shot = shot()
                val tile = tile()
                val waist = shot[(tile.left - with(compose.density) { 5.dp.toPx() }).toInt(), tile.center.y.toInt()]
                val ground = shot[4, 4]

                withClue("waist $waist, ground $ground") {
                    (waist.red + waist.green + waist.blue) shouldBeGreaterThan
                        ground.red + ground.green + ground.blue + 0.1f
                }
            }
        }
    })
