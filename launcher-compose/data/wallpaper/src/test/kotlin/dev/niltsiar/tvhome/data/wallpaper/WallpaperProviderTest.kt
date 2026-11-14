package dev.niltsiar.tvhome.data.wallpaper

import android.content.SharedPreferences
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Place
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.WallpaperMood
import dev.niltsiar.tvhome.core.config.WallpaperOrder
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

val WallpaperProviderTest by testSuite {
    robolectricTestSuite<WallpaperProviderTestContent>("default sdk")
}

class WallpaperProviderTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val themeDir = TemporaryFolder()

                lateinit var preferences: SharedPreferences
                lateinit var collection: File
                val decoded = mutableListOf<String>()
                val pictures = HashMap<String, ImageBitmap>()
                val unreadable = mutableSetOf<String>()
                val noon = LocalDateTime.of(2026, 11, 4, 12, 0)

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
                    listOf("a.jpg", "b.jpg", "c.jpg").forEach { add(it) }
                }

                fun add(name: String) = File(collection, name).also { it.writeBytes(ByteArray(8) { _ -> 1 }) }

                fun provider(
                    config: TvConfig,
                    choice: StateFlow<String?> = MutableStateFlow(null),
                ) = WallpaperProvider(
                    config = MutableStateFlow(config),
                    wallpaperChoice = choice,
                    single = File(themeDir.root, "wallpaper.jpg"),
                    folder = collection,
                    preferences = preferences,
                    decode = { file ->
                        decoded += file.name
                        if (file.name in unreadable) null else DecodedPicture(pictures.getOrPut(file.name) { ImageBitmap(1, 1) }, null)
                    },
                    zone = { ZoneOffset.UTC },
                )

                fun WallpaperProvider.shows(
                    name: String,
                    at: LocalDateTime,
                ) {
                    val shown = advance(at).picture
                    withClue(name) { shown shouldBeSameInstanceAs pictures.getValue(name) }
                }
            }
        } asContextForEach {
            test("the first tick shows a picture and later ticks keep it until it is due") {
                val provider = provider(rotating)
                provider.shows("a.jpg", noon)
                provider.shows("a.jpg", noon.plusMinutes(4))
                decoded shouldBe listOf("a.jpg")
                provider.shows("b.jpg", noon.plusMinutes(5))
                provider.shows("b.jpg", noon.plusMinutes(6))
            }

            test("a restart resumes the picture that was up") {
                provider(rotating).apply {
                    advance(noon)
                    shows("b.jpg", noon.plusMinutes(5))
                }
                provider(rotating).shows("b.jpg", noon.plusMinutes(6))
            }

            test("a different collection starts from its first picture") {
                provider(rotating).apply {
                    advance(noon)
                    shows("b.jpg", noon.plusMinutes(5))
                }
                add("0.jpg")
                provider(rotating).shows("0.jpg", noon.plusMinutes(6))
            }

            test("an unreadable picture is skipped") {
                unreadable += "a.jpg"
                val provider = provider(rotating)
                provider.shows("b.jpg", noon)
                provider.shows("b.jpg", noon.plusMinutes(1))
                decoded shouldBe listOf("a.jpg", "b.jpg")
            }

            test("a picture outside the time of days group is replaced at once") {
                val moods = mapOf(WallpaperMood.Morning to setOf("a.jpg"), WallpaperMood.Night to setOf("c.jpg"))
                val provider = provider(rotating.copy(wallpaperRotationMinutes = 1440, wallpaperMoods = moods))
                provider.shows("a.jpg", noon.withHour(8))
                provider.shows("c.jpg", noon.withHour(23))
            }

            test("without rotation the single wallpaper shows and the theme can switch it off") {
                val single = File(themeDir.root, "wallpaper.jpg").also { it.writeBytes(ByteArray(8) { _ -> 1 }) }
                provider(TvConfig()).shows(single.name, noon)
                provider(TvConfig(wallpaper = false)).advance(noon).picture.shouldBeNull()
                decoded shouldBe listOf("wallpaper.jpg")
            }

            test("the theme default is the picture up while home plays it and the one home starts it with after a choice") {
                runBlocking {
                    val choice = MutableStateFlow<String?>(null)
                    val provider = provider(rotating, choice)
                    provider.advance(noon)
                    provider.shows("b.jpg", noon.plusMinutes(5))
                    provider.themeDefault(noon.plusMinutes(6)) shouldBe WallpaperPicture("b.jpg")

                    choice.value = "c.jpg"
                    provider.shows("c.jpg", noon.plusMinutes(7))
                    val default = provider.themeDefault(noon.plusMinutes(8))
                    choice.value = null
                    provider.shows(default!!.fileName, noon.plusMinutes(8))
                }
            }

            test("the theme default is the time of days picture when the theme has moods") {
                runBlocking {
                    val moods = mapOf(WallpaperMood.Morning to setOf("a.jpg"), WallpaperMood.Night to setOf("c.jpg"))
                    val provider = provider(rotating.copy(wallpaperRotationMinutes = 1440, wallpaperMoods = moods))
                    val default = provider.themeDefault(noon.withHour(23))

                    provider.shows(default!!.fileName, noon.withHour(23))
                }
            }

            test("the theme default is the single wallpaper without rotation and none with the wallpaper off") {
                runBlocking {
                    File(themeDir.root, "wallpaper.jpg").writeBytes(ByteArray(8) { _ -> 1 })

                    provider(TvConfig()).themeDefault(noon) shouldBe WallpaperPicture("wallpaper.jpg", single = true)
                    provider(TvConfig(wallpaper = false)).themeDefault(noon).shouldBeNull()
                }
            }

            test("captions name the shown picture when the theme asks") {
                File(collection, "captions.txt").writeText("a.jpg\tAlpha\n")
                provider(rotating.copy(wallpaperCaptions = true)).advance(noon).caption shouldBe "Alpha"
                provider(rotating).advance(noon).caption.shouldBeNull()
            }

            test("the sky follows the minute at the place when the theme asks") {
                val madrid = Place(40.4168, -3.7038)
                val sun = Sun.riseAndSet(noon.toLocalDate(), madrid.latitude, madrid.longitude, ZoneOffset.UTC)!!
                val sky = provider(rotating.copy(skyIntensity = 40, place = madrid))
                sky.advance(noon).sky shouldBe Sky.at(12 * 60, sun.sunrise, sun.sunset)
                sky.advance(noon.withHour(21).withMinute(30)).sky shouldBe Sky.at(21 * 60 + 30, sun.sunrise, sun.sunset)
                provider(rotating.copy(skyIntensity = 40)).advance(noon).sky shouldBe Sky.at(12 * 60)
                provider(rotating).advance(noon).sky.shouldBeNull()
            }

            test("nothing runs while stopped") {
                runBlocking {
                    val clock = MutableSharedFlow<LocalDateTime?>()
                    val provider = provider(rotating)
                    provider.wallpaper(clock).test {
                        expectNoEvents()
                        clock.subscriptionCount.value shouldBe 0
                        provider.onStart()
                        withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                        clock.emit(null)
                        clock.emit(noon)
                        val shown = awaitItem().picture
                        shown shouldBeSameInstanceAs pictures.getValue("a.jpg")
                        provider.onStop()
                        withTimeout(5_000) { clock.subscriptionCount.first { it == 0 } }
                        expectNoEvents()
                    }
                }
            }

            test("a trim while stopped drops the picture and the next start decodes it again") {
                runBlocking {
                    val clock = MutableSharedFlow<LocalDateTime?>()
                    val provider = provider(rotating)
                    provider.wallpaper(clock).test {
                        provider.onStart()
                        withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                        clock.emit(noon)
                        val shown = awaitItem().picture
                        shown shouldBeSameInstanceAs pictures.getValue("a.jpg")
                        provider.onStop()
                        withTimeout(5_000) { clock.subscriptionCount.first { it == 0 } }

                        provider.releasePicture()
                        awaitItem().picture.shouldBeNull()

                        provider.onStart()
                        withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                        clock.emit(noon)
                        awaitItem().picture shouldBeSameInstanceAs pictures.getValue("a.jpg")
                        decoded shouldBe listOf("a.jpg", "a.jpg")
                    }
                }
            }

            test("a choice shows one picture with no extra decode and theme default restores the rotation") {
                val choice = MutableStateFlow<String?>(null)
                val provider = provider(rotating, choice)
                provider.shows("a.jpg", noon)

                choice.value = "c.jpg"
                provider.shows("c.jpg", noon.plusMinutes(1))
                provider.shows("c.jpg", noon.plusMinutes(30))
                decoded shouldBe listOf("a.jpg", "c.jpg")

                choice.value = null
                provider.shows("a.jpg", noon.plusMinutes(31))
                provider.shows("a.jpg", noon.plusMinutes(32))
                decoded shouldBe listOf("a.jpg", "c.jpg", "a.jpg")
            }

            test("none shows no picture and decodes nothing until the choice is removed") {
                val choice = MutableStateFlow<String?>("none")
                val provider = provider(rotating, choice)
                provider.advance(noon).picture.shouldBeNull()
                decoded.shouldBeEmpty()

                choice.value = "b.jpg"
                provider.shows("b.jpg", noon.plusMinutes(1))
                choice.value = "none"
                provider.advance(noon.plusMinutes(2)).picture.shouldBeNull()
                choice.value = null
                provider.shows("a.jpg", noon.plusMinutes(3))
            }

            test("a choice of a picture that is gone leaves the theme deciding") {
                provider(rotating, MutableStateFlow("gone.jpg")).shows("a.jpg", noon)
            }

            test("a choice wins over a theme with no wallpaper and can name the single wallpaper") {
                val single = File(themeDir.root, "wallpaper.jpg").also { it.writeBytes(ByteArray(8) { _ -> 1 }) }

                provider(TvConfig(wallpaper = false), MutableStateFlow("b.jpg")).shows("b.jpg", noon)
                provider(rotating, MutableStateFlow(single.name)).shows(single.name, noon)
                provider(TvConfig(wallpaper = false), MutableStateFlow(null)).advance(noon).picture.shouldBeNull()
            }

            test("a choice made while started reaches the flow without a tick") {
                runBlocking {
                    val clock = MutableSharedFlow<LocalDateTime?>()
                    val choice = MutableStateFlow<String?>(null)
                    val provider = provider(rotating, choice)
                    provider.wallpaper(clock).test {
                        provider.onStart()
                        withTimeout(5_000) { clock.subscriptionCount.first { it > 0 } }
                        clock.emit(noon)
                        val first = awaitItem().picture
                        first shouldBeSameInstanceAs pictures.getValue("a.jpg")

                        choice.value = "c.jpg"
                        val chosen = awaitItem().picture
                        chosen shouldBeSameInstanceAs pictures.getValue("c.jpg")

                        choice.value = "none"
                        awaitItem().picture.shouldBeNull()
                    }
                }
            }

            test("captions follow a chosen picture of the folder") {
                File(collection, "captions.txt").writeText("c.jpg\tGamma\n")
                val theme = rotating.copy(wallpaperCaptions = true)

                provider(theme, MutableStateFlow("c.jpg")).advance(noon).caption shouldBe "Gamma"
                provider(theme, MutableStateFlow("none")).advance(noon).caption.shouldBeNull()
            }
        }
    })
