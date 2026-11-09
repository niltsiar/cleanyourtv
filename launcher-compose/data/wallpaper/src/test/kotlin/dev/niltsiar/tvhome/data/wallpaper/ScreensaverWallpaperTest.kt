package dev.niltsiar.tvhome.data.wallpaper

import android.content.SharedPreferences
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.WallpaperOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ScreensaverWallpaperTest {
    @get:Rule
    val themeDir = TemporaryFolder()

    private lateinit var preferences: SharedPreferences
    private lateinit var collection: File
    private val pictures = HashMap<String, ImageBitmap>()
    private val rotating = TvConfig(wallpaperRotationMinutes = 5, wallpaperOrder = WallpaperOrder.Sequential)

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("test", 0)
        collection = themeDir.newFolder("wallpapers")
        listOf("a.jpg", "b.jpg").forEach { File(collection, it).writeBytes(ByteArray(8) { _ -> 1 }) }
    }

    private fun provider(config: TvConfig) =
        WallpaperProvider(
            config = MutableStateFlow(config),
            single = File(themeDir.root, "wallpaper.jpg"),
            folder = collection,
            preferences = preferences,
            decode = { file -> DecodedPicture(pictures.getOrPut(file.name) { ImageBitmap(1, 1) }, null) },
            zone = { ZoneOffset.UTC },
        )

    @Test
    fun the_screensaver_shows_the_picture_home_was_playing() {
        val home = provider(rotating)
        val started = LocalDateTime.now().minusMinutes(8)
        home.advance(started)
        home.advance(started.plusMinutes(5))

        val picture = runBlocking { ScreensaverWallpaper(provider(rotating), Dispatchers.Unconfined).current() }

        assertSame(pictures.getValue("b.jpg"), picture)
    }

    @Test
    fun asked_again_after_the_interval_the_screensaver_carries_the_rotation_on() {
        provider(rotating).advance(LocalDateTime.now().minusMinutes(8))
        val screensaver = ScreensaverWallpaper(provider(rotating), Dispatchers.Unconfined)

        val picture = runBlocking { screensaver.current() }

        assertSame(pictures.getValue("b.jpg"), picture)
    }

    @Test
    fun a_theme_without_a_wallpaper_gives_none() {
        val picture = runBlocking { ScreensaverWallpaper(provider(rotating.copy(wallpaper = false)), Dispatchers.Unconfined).current() }

        assertNull(picture)
    }
}
