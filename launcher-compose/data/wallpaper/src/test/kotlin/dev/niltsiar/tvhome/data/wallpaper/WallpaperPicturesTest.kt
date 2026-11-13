package dev.niltsiar.tvhome.data.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperPicturesTest {
    private val theme = ThemeDirectory(RuntimeEnvironment.getApplication())
    private val root = theme.dir!!.also { it.mkdirs() }
    private val folder = File(root, "wallpapers").also { it.mkdirs() }
    private val pictures = WallpaperPictures(theme, Dispatchers.Unconfined)

    /** A [width] x [height] PNG: [centre] in the middle, as wide as the height allows at 16:9, and [sides] left and right of it. */
    private fun png(
        file: File,
        width: Int,
        height: Int,
        centre: Int,
        sides: Int = centre,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(sides)
        val middle = height * 16 / 9
        val left = (width - middle) / 2f
        canvas.drawRect(left, 0f, left + middle, height.toFloat(), Paint().apply { color = centre })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun the_theme_picture_comes_first_and_each_folder_picture_carries_its_caption_from_captions_txt() {
        png(File(root, "wallpaper.jpg"), 16, 9, Color.RED)
        png(File(folder, "a.png"), 16, 9, Color.RED)
        png(File(folder, "b.png"), 16, 9, Color.RED)
        File(folder, "captions.txt").writeText("a.png\tDusk over the peaks\nwallpaper.jpg\tNot for the single picture\n")

        assertEquals(
            listOf(
                WallpaperPicture("wallpaper.jpg", single = true),
                WallpaperPicture("a.png", "Dusk over the peaks"),
                WallpaperPicture("b.png"),
            ),
            runBlocking { pictures.pictures() },
        )
    }

    @Test
    fun a_thumbnail_is_the_centre_of_the_picture_decoded_at_the_size_asked_for() {
        png(File(folder, "pano.png"), 3200, 900, centre = Color.RED, sides = Color.BLUE)

        val thumbnail = runBlocking { pictures.thumbnail(WallpaperPicture("pano.png"), 192, 108) }!!.asAndroidBitmap()

        assertEquals(192, thumbnail.width)
        assertEquals(108, thumbnail.height)
        for ((x, y) in listOf(0 to 0, 191 to 0, 96 to 54, 0 to 107, 191 to 107)) {
            assertEquals("pixel $x,$y", Color.RED, thumbnail.getPixel(x, y))
        }
    }

    @Test
    fun the_theme_picture_thumbnail_comes_from_wallpaper_jpg_and_not_from_a_folder_file_of_that_name() {
        png(File(root, "wallpaper.jpg"), 320, 180, Color.GREEN)
        png(File(folder, "wallpaper.jpg"), 320, 180, Color.BLUE)

        val single = runBlocking { pictures.thumbnail(WallpaperPicture("wallpaper.jpg", single = true), 32, 18) }
        val inFolder = runBlocking { pictures.thumbnail(WallpaperPicture("wallpaper.jpg"), 32, 18) }

        assertEquals(Color.GREEN, single!!.asAndroidBitmap().getPixel(16, 9))
        assertEquals(Color.BLUE, inFolder!!.asAndroidBitmap().getPixel(16, 9))
    }

    @Test
    fun a_file_that_does_not_decode_or_is_gone_has_no_thumbnail() {
        File(folder, "broken.jpg").writeBytes(ByteArray(64) { 7 })

        assertNull(runBlocking { pictures.thumbnail(WallpaperPicture("broken.jpg"), 192, 108) })
        assertNull(runBlocking { pictures.thumbnail(WallpaperPicture("gone.jpg"), 192, 108) })
    }
}
