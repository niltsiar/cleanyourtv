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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** The pickers' art as drawn at 1920 by 1080 (density 2): a loader that paints one colour stands in for the files and apps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class PickerArtUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val density get() = compose.activity.resources.displayMetrics.density

    /** Each load asked for: the key, and the width and height in pixels. */
    private val asked = CopyOnWriteArrayList<Triple<Any, Int, Int>>()
    private val loads = CompletableDeferred<Unit>()

    private fun solid(
        colour: Color,
        width: Int,
        height: Int,
    ): ImageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(colour.toArgb()) }.asImageBitmap()

    private val art =
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

    private fun show(
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

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun label(
        index: Int,
        text: String,
    ): DpRect = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("picker:$index")), useUnmergedTree = true).getBoundsInRoot()

    /** The colour drawn in the middle of the art slot of the entry at [index]. */
    private fun artColour(index: Int): Color {
        val box = bounds("picker:$index:art")
        val image = compose.onRoot().captureToImage().toPixelMap()
        return image[((box.left + box.right).value / 2 * density).toInt(), ((box.top + box.bottom).value / 2 * density).toInt()]
    }

    /** The colour drawn half a dp inside the start edge of the art slot of the entry at [index], halfway down. */
    private fun artEdge(index: Int): Color {
        val box = bounds("picker:$index:art")
        val image = compose.onRoot().captureToImage().toPixelMap()
        return image[((box.left.value + 0.5f) * density).toInt(), ((box.top + box.bottom).value / 2 * density).toInt()]
    }

    /** The slot of the entry at [index] holds the backdrop swatch, with its 1 dp outline of white at 0.20 at the edge. */
    private fun assertSwatch(index: Int) {
        assertColour("entry $index", Backdrop, artColour(index))
        val outline = Color.White.copy(alpha = 0.20f).compositeOver(Backdrop)
        assertColour("entry $index outline", outline, artEdge(index))
    }

    private fun assertColour(
        message: String,
        expected: Color,
        actual: Color,
    ) {
        val off = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue))
        assertTrue("$message: expected $expected, drew $actual", off <= 2f / 255f)
    }

    private fun arrive(
        index: Int,
        colour: Color,
    ) {
        loads.complete(Unit)
        compose.waitUntil(5_000) { artColour(index) == colour }
    }

    @Test
    fun a_picture_row_draws_its_thumbnail_loaded_once_at_96_by_54_dp_and_its_label_does_not_move_when_it_arrives() {
        val pictures =
            listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("broken.jpg"))
        show(wallpaperPicker = wallpaperPicker(pictures, null))
        compose.onNodeWithTag("picker:2").assertTextEquals("Dusk")
        val before = label(2, "Dusk")
        assertNotEquals(Thumbnail, artColour(2))

        arrive(2, Thumbnail)

        assertEquals(Thumbnail, artColour(1))
        assertEquals(before, label(2, "Dusk"))
        assertEquals(
            listOf(Triple("broken.jpg", 192, 108), Triple("dusk.jpg", 192, 108), Triple("wallpaper.jpg", 192, 108)),
            asked.sortedBy { it.first.toString() },
        )
        compose.onNodeWithTag("picker:3").assertTextEquals("Broken")
        for (index in listOf(0, 3, 4)) assertNotEquals("entry $index", Thumbnail, artColour(index))
    }

    @Test
    fun an_app_row_draws_its_banner_and_two_apps_with_one_label_show_their_packages() {
        val apps =
            listOf(
                OpenOnStartApp("DAZN", "com.dazn"),
                OpenOnStartApp("Inicio", "com.sony.home"),
                OpenOnStartApp("Inicio", "com.google.home"),
            )
        show(picker = OpenOnStartPicker(apps, 0))

        arrive(1, Banner)

        assertEquals(Banner, artColour(2))
        assertNotEquals(Banner, artColour(0))
        assertEquals(setOf("com.dazn", "com.sony.home", "com.google.home"), asked.map { it.first }.toSet())
        compose.onNodeWithTag("picker:2:detail", useUnmergedTree = true).assertTextEquals("com.sony.home")
        compose.onNodeWithTag("picker:3:detail", useUnmergedTree = true).assertTextEquals("com.google.home")
        for (index in listOf(0, 1)) {
            assertEquals(
                "entry $index",
                0,
                compose.onAllNodesWithTag("picker:$index:detail", useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        }
        val detail = bounds("picker:2:detail")
        val name = label(2, "Inicio")
        assertTrue("the package line is smaller than the label", detail.bottom - detail.top < name.bottom - name.top)
    }

    @Test
    fun theme_default_draws_the_picture_home_shows_for_it_and_none_an_outlined_swatch_of_the_backdrop() {
        val pictures =
            listOf(WallpaperPicture("wallpaper.jpg", single = true), WallpaperPicture("dusk.jpg"), WallpaperPicture("night.jpg"))
        show(wallpaperPicker = wallpaperPicker(pictures, null, themeDefault = WallpaperPicture("night.jpg")))

        arrive(0, Night)

        assertEquals(Night, artColour(3))
        assertEquals(Thumbnail, artColour(2))
        assertSwatch(4)
    }

    @Test
    fun theme_default_with_no_picture_draws_the_outlined_swatch_of_the_backdrop() {
        show(wallpaperPicker = wallpaperPicker(listOf(WallpaperPicture("dusk.jpg")), null))

        arrive(1, Thumbnail)

        assertSwatch(0)
        assertSwatch(2)
    }

    @Test
    fun off_in_the_open_on_start_picker_draws_the_outlined_swatch_of_the_backdrop() {
        show(picker = OpenOnStartPicker(listOf(OpenOnStartApp("DAZN", "com.dazn")), 0))

        arrive(1, Banner)

        assertSwatch(0)
    }

    private companion object {
        val Thumbnail = Color(0xFFFF0000)
        val Night = Color(0xFF00FF00)
        val Banner = Color(0xFF0000FF)
        val Backdrop = Color(0xFF203040)
    }
}
