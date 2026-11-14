package dev.niltsiar.tvhome.feature.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** The pickers' art as drawn at 1920 by 1080 (density 2): a loader that paints one colour stands in for the files and apps. */
val PickerArtUiTest by testSuite {
    robolectricTestSuite<PickerArtUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class PickerArtUiTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                val density get() = compose.activity.resources.displayMetrics.density

                /** Each load asked for: the key, and the width and height in pixels. */
                val asked = CopyOnWriteArrayList<Triple<Any, Int, Int>>()
                val loads = CompletableDeferred<Unit>()

                fun solid(
                    colour: Color,
                    width: Int,
                    height: Int,
                ): ImageBitmap =
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(colour.toArgb()) }.asImageBitmap()

                val art =
                    PickerArt(
                        wallpaper = { picture, width, height ->
                            asked += Triple(picture.fileName, width, height)
                            loads.await()
                            withContext(Dispatchers.Default) {
                                when (picture.fileName) {
                                    "broken.jpg" -> null
                                    "night.jpg" -> solid(Night, width, height)
                                    else -> solid(Thumbnail, width, height)
                                }
                            }
                        },
                        app = { pkg, width, height ->
                            asked += Triple(pkg, width, height)
                            loads.await()
                            withContext(Dispatchers.Default) { solid(Banner, width, height) }
                        },
                    )

                fun show(
                    picker: OpenOnStartPicker? = null,
                    wallpaperPicker: WallpaperPicker? = null,
                ) {
                    compose.setContent {
                        TvTheme(motion = Motion(animations = false)) {
                            SettingsScreen(
                                state = {
                                    SettingsUiState(
                                        settingsSections(TvConfig()),
                                        followsTheme = true,
                                        openOnStart = OptionLabel.Res(R.string.settings_off),
                                        picker = picker,
                                        wallpaper = OptionLabel.Res(R.string.wallpaper_theme_default),
                                        wallpaperPicker = wallpaperPicker,
                                        backdrop = Backdrop,
                                    )
                                },
                                actions = SettingsActions(),
                                onChooseDefaultHome = {},
                                onOpenTvSettings = {},
                                art = art,
                            )
                        }
                    }
                    compose.waitForIdle()
                }

                fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

                fun label(
                    index: Int,
                    text: String,
                ): DpRect =
                    compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("picker:$index")), useUnmergedTree = true).getBoundsInRoot()

                /** The colour drawn in the middle of the art slot of the entry at [index]. */
                fun artColour(index: Int): Color {
                    val box = bounds("picker:$index:art")
                    val image = compose.onRoot().captureToImage().toPixelMap()
                    return image[((box.left + box.right).value / 2 * density).toInt(), ((box.top + box.bottom).value / 2 * density).toInt()]
                }

                /** The colour drawn half a dp inside the start edge of the art slot of the entry at [index], halfway down. */
                fun artEdge(index: Int): Color {
                    val box = bounds("picker:$index:art")
                    val image = compose.onRoot().captureToImage().toPixelMap()
                    return image[((box.left.value + 0.5f) * density).toInt(), ((box.top + box.bottom).value / 2 * density).toInt()]
                }

                /** The slot of the entry at [index] holds the backdrop swatch, with its 1 dp outline of white at 0.20 at the edge. */
                fun assertSwatch(index: Int) {
                    assertColour("entry $index", Backdrop, artColour(index))
                    val outline = Color.White.copy(alpha = 0.20f).compositeOver(Backdrop)
                    assertColour("entry $index outline", outline, artEdge(index))
                }

                fun assertColour(
                    message: String,
                    expected: Color,
                    actual: Color,
                ) {
                    val off = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue))
                    withClue("$message: expected $expected, drew $actual") { off shouldBeLessThanOrEqualTo (2f / 255f) }
                }

                fun arrive(
                    index: Int,
                    colour: Color,
                ) {
                    loads.complete(Unit)
                    compose.waitUntil(30_000) { artColour(index) == colour }
                }
            }
        } asContextForEach {
            test("a picture row draws its thumbnail loaded once at 96 by 54 dp and its label does not move when it arrives") {
                val pictures =
                    listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("broken.jpg"))
                show(wallpaperPicker = wallpaperPicker(pictures, null))
                compose.onNodeWithTag("picker:2").assertTextEquals("Dusk")
                val before = label(2, "Dusk")
                artColour(2) shouldNotBe Thumbnail

                arrive(2, Thumbnail)

                artColour(1) shouldBe Thumbnail
                label(2, "Dusk") shouldBe before
                (asked.sortedBy { it.first.toString() }) shouldBe
                    listOf(Triple("broken.jpg", 192, 108), Triple("dusk.jpg", 192, 108), Triple("wallpaper.jpg", 192, 108))
                compose.onNodeWithTag("picker:3").assertTextEquals("Broken")
                for (index in listOf(0, 3, 4)) withClue("entry $index") { artColour(index) shouldNotBe Thumbnail }
            }

            test("an app row draws its banner and two apps with one label show their packages") {
                val apps =
                    listOf(
                        OpenOnStartApp("DAZN", "com.dazn"),
                        OpenOnStartApp("Inicio", "com.sony.home"),
                        OpenOnStartApp("Inicio", "com.google.home"),
                    )
                show(picker = OpenOnStartPicker(apps, 0))

                arrive(1, Banner)

                artColour(2) shouldBe Banner
                artColour(0) shouldNotBe Banner
                (asked.map { it.first }.toSet()) shouldBe setOf("com.dazn", "com.sony.home", "com.google.home")
                compose.onNodeWithTag("picker:2:detail", useUnmergedTree = true).assertTextEquals("com.sony.home")
                compose.onNodeWithTag("picker:3:detail", useUnmergedTree = true).assertTextEquals("com.google.home")
                for (index in listOf(0, 1)) {
                    withClue("entry $index") {
                        compose.onAllNodesWithTag("picker:$index:detail", useUnmergedTree = true).fetchSemanticsNodes().size shouldBe
                            0
                    }
                }
                val detail = bounds("picker:2:detail")
                val name = label(2, "Inicio")
                withClue("the package line is smaller than the label") {
                    (detail.bottom - detail.top) shouldBeLessThan
                        (name.bottom - name.top)
                }
            }

            test("theme default draws the picture home shows for it and none an outlined swatch of the backdrop") {
                val pictures =
                    listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("night.jpg"))
                show(wallpaperPicker = wallpaperPicker(pictures, null, themeDefault = WallpaperPicture("night.jpg")))

                arrive(0, Night)

                artColour(3) shouldBe Night
                artColour(2) shouldBe Thumbnail
                assertSwatch(4)
            }

            test("theme default with no picture draws the outlined swatch of the backdrop") {
                show(wallpaperPicker = wallpaperPicker(listOf(WallpaperPicture("dusk.jpg")), null))

                arrive(1, Thumbnail)

                assertSwatch(0)
                assertSwatch(2)
            }

            test("off in the open on start picker draws the outlined swatch of the backdrop") {
                show(picker = OpenOnStartPicker(listOf(OpenOnStartApp("DAZN", "com.dazn")), 0))

                arrive(1, Banner)

                assertSwatch(0)
            }
        }
    })

private val Thumbnail = Color(0xFFFF0000)
private val Night = Color(0xFF00FF00)
private val Banner = Color(0xFF0000FF)
private val Backdrop = Color(0xFF203040)
