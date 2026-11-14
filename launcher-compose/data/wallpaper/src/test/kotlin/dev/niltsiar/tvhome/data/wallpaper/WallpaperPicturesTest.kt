package dev.niltsiar.tvhome.data.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import java.io.File

val WallpaperPicturesTest by testSuite {
    robolectricTestSuite<WallpaperPicturesTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class WallpaperPicturesTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val theme = ThemeDirectory(RuntimeEnvironment.getApplication())
                val root = theme.dir!!.also { it.mkdirs() }
                val folder = File(root, "wallpapers").also { it.mkdirs() }
                val pictures = WallpaperPictures(theme, Dispatchers.Unconfined)

                /** A [width] x [height] PNG: [centre] in the middle, as wide as the height allows at 16:9, and [sides] left and right of it. */
                fun png(
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
            }
        } asContextForEach {
            test("the theme picture comes first and each folder picture carries its caption from captions txt") {
                png(File(root, "wallpaper.jpg"), 16, 9, Color.RED)
                png(File(folder, "a.png"), 16, 9, Color.RED)
                png(File(folder, "b.png"), 16, 9, Color.RED)
                File(folder, "captions.txt").writeText("a.png\tDusk over the peaks\nwallpaper.jpg\tNot for the single picture\n")

                runBlocking { pictures.pictures() } shouldBe
                    listOf(
                        WallpaperPicture("wallpaper.jpg", single = true),
                        WallpaperPicture("a.png", "Dusk over the peaks"),
                        WallpaperPicture("b.png"),
                    )
            }

            test("a thumbnail is the centre of the picture decoded at the size asked for") {
                png(File(folder, "pano.png"), 3200, 900, centre = Color.RED, sides = Color.BLUE)

                val thumbnail = runBlocking { pictures.thumbnail(WallpaperPicture("pano.png"), 192, 108) }!!.asAndroidBitmap()

                thumbnail.width shouldBe 192
                thumbnail.height shouldBe 108
                for ((x, y) in listOf(0 to 0, 191 to 0, 96 to 54, 0 to 107, 191 to 107)) {
                    withClue("pixel $x,$y") { thumbnail.getPixel(x, y) shouldBe Color.RED }
                }
            }

            test("the theme picture thumbnail comes from wallpaper jpg and not from a folder file of that name") {
                png(File(root, "wallpaper.jpg"), 320, 180, Color.GREEN)
                png(File(folder, "wallpaper.jpg"), 320, 180, Color.BLUE)

                val single = runBlocking { pictures.thumbnail(WallpaperPicture("wallpaper.jpg", single = true), 32, 18) }
                val inFolder = runBlocking { pictures.thumbnail(WallpaperPicture("wallpaper.jpg"), 32, 18) }

                single!!.asAndroidBitmap().getPixel(16, 9) shouldBe Color.GREEN
                inFolder!!.asAndroidBitmap().getPixel(16, 9) shouldBe Color.BLUE
            }

            test("a file that does not decode or is gone has no thumbnail") {
                File(folder, "broken.jpg").writeBytes(ByteArray(64) { 7 })

                runBlocking { pictures.thumbnail(WallpaperPicture("broken.jpg"), 192, 108) }.shouldBeNull()
                runBlocking { pictures.thumbnail(WallpaperPicture("gone.jpg"), 192, 108) }.shouldBeNull()
            }
        }
    })
