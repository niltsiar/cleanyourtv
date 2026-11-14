package dev.niltsiar.tvhome.data.wallpaper

import android.content.SharedPreferences
import androidx.compose.ui.graphics.ImageBitmap
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.WallpaperOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

val ScreensaverWallpaperTest by testSuite {
    robolectricTestSuite<ScreensaverWallpaperTestContent>("default sdk")
}

class ScreensaverWallpaperTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val themeDir = TemporaryFolder()

                lateinit var preferences: SharedPreferences
                lateinit var collection: File
                val pictures = HashMap<String, ImageBitmap>()
                val rotating = TvConfig(wallpaperRotationMinutes = 5, wallpaperOrder = WallpaperOrder.Sequential)

                val rules: RuleChain =
                    rule(
                        RuleChain
                            .outerRule(themeDir)
                            .around(
                                object : ExternalResource() {
                                    override fun before() = setUp()
                                },
                            ),
                    )

                fun setUp() {
                    preferences = RuntimeEnvironment.getApplication().getSharedPreferences("test", 0)
                    collection = themeDir.newFolder("wallpapers")
                    listOf("a.jpg", "b.jpg").forEach { File(collection, it).writeBytes(ByteArray(8) { _ -> 1 }) }
                }

                fun provider(
                    config: TvConfig,
                    choice: StateFlow<String?> = MutableStateFlow(null),
                ) = WallpaperProvider(
                    config = MutableStateFlow(config),
                    wallpaperChoice = choice,
                    single = File(themeDir.root, "wallpaper.jpg"),
                    folder = collection,
                    preferences = preferences,
                    decode = { file -> DecodedPicture(pictures.getOrPut(file.name) { ImageBitmap(1, 1) }, null) },
                    zone = { ZoneOffset.UTC },
                )
            }
        } asContextForEach {
            test("the screensaver shows the picture home was playing") {
                val home = provider(rotating)
                val started = LocalDateTime.now().minusMinutes(8)
                home.advance(started)
                home.advance(started.plusMinutes(5))

                val picture = runBlocking { ScreensaverWallpaper(provider(rotating), Dispatchers.Unconfined).current() }

                picture shouldBeSameInstanceAs pictures.getValue("b.jpg")
            }

            test("asked again after the interval the screensaver carries the rotation on") {
                provider(rotating).advance(LocalDateTime.now().minusMinutes(8))
                val screensaver = ScreensaverWallpaper(provider(rotating), Dispatchers.Unconfined)

                val picture = runBlocking { screensaver.current() }

                picture shouldBeSameInstanceAs pictures.getValue("b.jpg")
            }

            test("a theme without a wallpaper gives none") {
                val picture =
                    runBlocking { ScreensaverWallpaper(provider(rotating.copy(wallpaper = false)), Dispatchers.Unconfined).current() }

                picture.shouldBeNull()
            }

            test("the screensaver follows the on tv wallpaper choice") {
                val choice = MutableStateFlow<String?>("b.jpg")
                val screensaver = ScreensaverWallpaper(provider(rotating, choice), Dispatchers.Unconfined)

                val chosen = runBlocking { screensaver.current() }
                chosen shouldBeSameInstanceAs pictures.getValue("b.jpg")

                choice.value = "none"
                runBlocking { screensaver.current() }.shouldBeNull()
            }
        }
    })
