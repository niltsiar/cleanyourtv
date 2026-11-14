package dev.niltsiar.tvhome.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What the header and the top-bar buttons look like over a bright or a dark wallpaper, read from the rendered pixels. */
val HeaderLegibilityUiTest by testSuite {
    robolectricTestSuite<HeaderLegibilityUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HeaderLegibilityUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HeaderLegibilityUiTestFixture() } asContextForEach {
            test("a bright wallpaper gets the stronger scrim behind the title") {
                showHome(level = 1f)
                val at = aboveTheTitle()

                assertNear(scrimmed(255, top = 0.70f, left = 0.60f, at), redAt(at))
            }

            test("a dark wallpaper keeps the fixed scrim behind the title") {
                showHome(level = 0.4f)
                val at = aboveTheTitle()

                assertNear(scrimmed(102, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("header scrim off keeps the fixed scrim over a bright wallpaper") {
                showHome(level = 1f, wallpaperLayout = WallpaperLayout(headerScrim = HeaderScrim.Off))
                val at = aboveTheTitle()

                assertNear(scrimmed(255, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("a bright wallpaper puts the subtitle on a black 045 pill over what the local scrim adds to the page scrim") {
                showHome(level = 1f)
                val at = besideTheSubtitle()

                assertNear(pilled(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at), at)), redAt(at))
            }

            test("the subtitle pill reaches 10 dp left of its text") {
                showHome(level = 1f)
                val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()

                // 8.5 dp out lies inside a pill with 10 dp across, and past the end of one with 8.
                assertNear(
                    pilled(outsideThePill()),
                    redAt(Offset(subtitle.left.value - 8.5f, (subtitle.top.value + subtitle.bottom.value) / 2f)),
                )
            }

            test("the subtitle pill ends in half circles so its corners stay clear") {
                showHome(level = 1f)
                val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()

                // The pill is 26 dp high, 3 dp over the text's box: 3 dp in from each corner of its box is outside a radius of 13 and inside one of 8.
                assertNoPill(outsideThePill(), redAt(Offset(subtitle.left.value - 7f, subtitle.top.value)))
            }

            test("the subtitle pill has a white 012 stroke 1 dp wide inside its edge") {
                showHome(level = 1f)
                val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
                val middle = (subtitle.top.value + subtitle.bottom.value) / 2f
                val fill = pilled(outsideThePill())

                assertNear(Math.round(fill * 0.88f + 255 * 0.12f), redAt(Offset(subtitle.left.value - 9.5f, middle)))
                assertNear(fill, redAt(Offset(subtitle.left.value - 6f, middle)))
            }

            test("a dark wallpaper gets no local scrim and no pill behind the subtitle") {
                showHome(level = 0.3f)
                val at = besideTheSubtitle()

                assertNoPill(scrimmed(77, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("header scrim off draws no local scrim and no pill behind the subtitle") {
                showHome(level = 1f, wallpaperLayout = WallpaperLayout(headerScrim = HeaderScrim.Off))
                val at = besideTheSubtitle()

                assertNoPill(scrimmed(255, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("a busy light picture with a quarter of its pixels bright gets the scrim and the pill behind the subtitle") {
                showHome(wallpapers = flowOf(Wallpaper(picture = grey(1f), thumbnail = stripes(period = 20, white = 5))))
                val at = besideTheSubtitle()

                // A quarter of the thumbnail is white and the rest is grey 0.1: a mean relative luminance of 0.2575.
                val darkening = heroScrimDarkening(0.2575f)
                withClue("the local scrim darkens by $darkening") { darkening shouldBeGreaterThan 0.05f }
                assertNear(pilled(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at), at, darkening)), redAt(at))
            }

            test("a dark picture with a few bright spots gets no local scrim and no pill behind the subtitle") {
                showHome(wallpapers = flowOf(Wallpaper(picture = grey(0.4f), thumbnail = stripes(period = 25, white = 2))))
                val at = besideTheSubtitle()

                assertNoPill(scrimmed(102, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("the picture is sampled once per wallpaper and again for the next one") {
                val picture = grey(1f)
                val thumbnail = grey(1f)
                val wallpapers = MutableStateFlow(Wallpaper(picture = picture, thumbnail = thumbnail))
                showHome(wallpapers = wallpapers)
                val at = besideTheSubtitle()
                val withHeroScrim = pilled(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at), at))
                assertNear(withHeroScrim, redAt(at))

                // The thumbnail is the sample's input: darkening it in place and moving focus around must not make Home sample it again.
                Canvas(thumbnail).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Black })
                press(Key.DirectionDown)
                press(Key.DirectionUp)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                assertNear(withHeroScrim, redAt(at))

                wallpapers.value = Wallpaper(picture = picture, thumbnail = grey(0.3f))
                settle()
                assertNoPill(scrimmed(255, top = 0.55f, left = 0.40f, at), redAt(at))
            }

            test("the hero title has a black 055 shadow 1 dp down blurred 9 dp") {
                showHome(level = 0.3f)

                val shadow = checkNotNull(compose.onNodeWithTag("shelf:title").textStyle().shadow)
                val density = compose.activity.resources.displayMetrics.density

                shadow.color shouldBe Color.Black.copy(alpha = 0.55f)
                shadow.offset shouldBe Offset(0f, density)
                shadow.blurRadius shouldBe (9 * density plusOrMinus 0.01f)
            }

            test("the hero title is medium weight") {
                showHome(level = 0.3f)

                compose.onNodeWithTag("shelf:title").textStyle().fontWeight shouldBe FontWeight.Medium
            }

            test("the local scrim keeps its full strength 24 dp under the subtitle line") {
                showHome(level = 1f)
                val line = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
                val x = line.left.value - 20f

                // 5 dp under the line is below its pill, which reaches 3 dp.
                val inside = redAt(Offset(x, line.bottom.value + 5f))
                val below = redAt(Offset(x, line.bottom.value + 24f))

                assertNear(inside, below, tolerance = 3)
            }

            test("over a white picture the title holds 3 to 1 and the subtitle 4 5 to 1 against what is round them") {
                val wallpapers = MutableStateFlow(Wallpaper(picture = grey(0f), thumbnail = grey(0f)))
                val connected =
                    InputRow.Port(
                        "HDMI 3 (eARC/ARC)",
                        InputIcon.Hdmi,
                        InputConnection.Connected,
                        InputTarget.TvInput("tv/.Hdmi/HW3"),
                    )
                showHome(wallpapers = wallpapers, inputs = flowOf(InputsMenu(available = true, rows = listOf(connected))))
                press(Key.DirectionUp)
                press(Key.DirectionLeft)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                val onBlack = compose.onRoot().captureToImage().toPixelMap()

                wallpapers.value = Wallpaper(picture = grey(1f), thumbnail = grey(1f))
                settle()
                val onPicture = compose.onRoot().captureToImage().toPixelMap()

                val title = worstContrast("shelf:title", onBlack, onPicture)
                val subtitle = worstContrast("shelf:title-meta", onBlack, onPicture)
                withClue("title $title") { title shouldBeGreaterThanOrEqualTo 3f }
                withClue("subtitle $subtitle") { subtitle shouldBeGreaterThanOrEqualTo 4.5f }
            }

            test("over the bright sky a long app name holds 3 to 1 and its subtitle 4 5 to 1") {
                val (title, subtitle) = contrastOver("bright")

                withClue("title $title") { title shouldBeGreaterThanOrEqualTo 3f }
                withClue("subtitle $subtitle") { subtitle shouldBeGreaterThanOrEqualTo 4.5f }
            }

            test("over the busy light forest a long app name holds 3 to 1 and its subtitle 4 5 to 1") {
                val (title, subtitle) = contrastOver("busy")

                withClue("title $title") { title shouldBeGreaterThanOrEqualTo 3f }
                withClue("subtitle $subtitle") { subtitle shouldBeGreaterThanOrEqualTo 4.5f }
            }

            test("the local scrim holds to the end of the title and fades out as a smoothstep over the next 240 dp") {
                showHome(
                    level = 1f,
                    apps = listOf(AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Prime Video")) + NineApps.drop(1),
                )
                val title = compose.onNodeWithTag("shelf:title").getBoundsInRoot()
                val y = title.bottom.value + 3f

                fun at(beyond: Float) = Offset(title.right.value + beyond, y)

                val end = heroAlpha(title.right.value)
                assertNear(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at(-20f)), at(-20f)), redAt(at(-20f)))
                // Half way the smoothstep has taken half of the alpha at the title's end; a quarter of the way it has taken 0.156 of it.
                assertNear(Math.round(scrimmed(255, top = 0.70f, left = 0.60f, at(120f)) * (1f - end * 0.5f)), redAt(at(120f)))
                assertNear(Math.round(scrimmed(255, top = 0.70f, left = 0.60f, at(60f)) * (1f - end * (1f - 0.15625f))), redAt(at(60f)))
                assertNear(scrimmed(255, top = 0.70f, left = 0.60f, at(250f)), redAt(at(250f)))
            }

            test("the subtitle is medium weight with a black 075 shadow blurred 12 dp") {
                showHome(level = 0.4f)

                val style = compose.onNodeWithTag("shelf:title-meta").textStyle()
                val shadow = checkNotNull(style.shadow)
                val density = compose.activity.resources.displayMetrics.density

                style.fontWeight shouldBe FontWeight.Medium
                shadow.color shouldBe Color.Black.copy(alpha = 0.75f)
                shadow.blurRadius shouldBe (12 * density plusOrMinus 0.01f)
            }

            test("a wallpaper between the two brightness marks gets a scrim between the two strengths") {
                // Grey 191 is L* 77.3: 0.865 of the way from the fixed scrim to the strongest.
                showHome(level = 0.75f)
                val at = aboveTheTitle()

                assertNear(scrimmed(191, top = 0.55f + 0.15f * 0.865f, left = 0.40f + 0.20f * 0.865f, at), redAt(at))
            }

            test("the clock pills greeting and date read at 4 5 to 1 on every wallpaper and the date 5 5 to 1 on the busy forest") {
                val ratios =
                    pillContrastOver(
                        true,
                        noFrost,
                        black,
                        white,
                        "bright sky" to picture("bright"),
                        "busy forest" to picture("busy"),
                    )

                val low = ratios.filter { (name, greeting, date) -> greeting < 4.5f || date < if (name == "busy forest") 5.5f else 4.5f }
                withClue("greeting and date contrast on the pill: $ratios") { low.isEmpty().shouldBeTrue() }
            }

            test("without night mode the clock pills greeting and date read at 4 5 to 1 on every wallpaper") {
                val ratios = pillContrastOver(false, noFrost, black, white, "busy forest" to picture("busy"))

                withClue("greeting and date contrast on the pill: $ratios") {
                    (
                        ratios.all { (_, greeting, date) ->
                            greeting >= 4.5f &&
                                date >= 4.5f
                        }
                    ).shouldBeTrue()
                }
            }

            test("the clock pills ink steps down from the clock to the greeting to the date") {
                showHome(wallpapers = flowOf(Wallpaper(picture = grey(0f), thumbnail = grey(0f))))
                val image = compose.onRoot().captureToImage().toPixelMap()

                val clock = ink(pillPixels(image, 12f..44f, 24f, 140f), darkTheme = true).luminance()
                val greeting = ink(pillPixels(image, greetingRows, 160f, pillWidth() - 30f), darkTheme = true).luminance()
                val date = ink(pillPixels(image, dateRows, 160f, pillWidth() - 30f), darkTheme = true).luminance()

                withClue("clock $clock, greeting $greeting, date $date") { (clock > greeting && greeting > date).shouldBeTrue() }
            }

            test("the top bar buttons thicken their tint over a wallpaper brighter than l star 70") {
                showHome(level = 1f)
                withClue("L* ${glass.topBarLightness}") { glass.topBarLightness shouldBeGreaterThan 95f }
                val bright = settingsFill()

                setTopBarLightness(70f)
                val atTheMark = settingsFill()
                setTopBarLightness(0f)
                val dark = settingsFill()

                assertNear(dark, atTheMark, tolerance = 1)
                withClue("bright $bright, dark $dark") { bright shouldBeLessThan dark - 15 }
            }

            test("an unfocused network label draws at three quarters and a focused one in full") {
                showHome(level = 0.4f, config = SixColumns.copy(wifiLabelWidth = 180))
                val label = labelBounds()
                val pill = redAt(Offset(label.left.value - 4f, (label.top.value + label.bottom.value) / 2f))

                // The label's ink is F2F4F8: at 0.75 its stems keep three quarters of their lead over the pill.
                assertNear(pill + (242 - pill) * 3 / 4, redIn(label) { a, b -> max(a, b) }, tolerance = 6)

                press(Key.DirectionUp)
                networkButton().assertIsFocused()
                // The focused pill is light and its label near black.
                redIn(labelBounds()) { a, b -> minOf(a, b) } shouldBeLessThan 60
            }

            test("the network button with a label has no white rim where an icon button keeps it") {
                showHome(level = 0.4f, config = SixColumns.copy(wifiLabelWidth = 180))
                val network = networkButton().getBoundsInRoot()
                val settings = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()

                fun rimLift(button: DpRect): Int {
                    val row = (button.top.value + button.bottom.value) / 2f
                    return redAt(Offset(button.left.value + 0.75f, row)) - redAt(Offset(button.left.value + 4f, row))
                }

                withClue("the icon button's rim lifts its edge by ${rimLift(settings)}") { rimLift(settings) shouldBeGreaterThan 10 }
                assertNear(0, rimLift(network), tolerance = 6)
            }
        }
    })

private class HeaderLegibilityUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    fun grey(level: Float) =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color(level, level, level) })
        }

    /** A 100 by 10 thumbnail, dark except for [white] columns out of every [period]: that share of its pixels is bright, anywhere. */
    fun stripes(
        period: Int,
        white: Int,
    ) = ImageBitmap(100, 10).also { image ->
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, 100f, 10f), Paint().apply { color = Color(0.1f, 0.1f, 0.1f) })
        for (x in 0 until 100) {
            if (x % period < white) canvas.drawRect(Rect(x.toFloat(), 0f, x + 1f, 10f), Paint().apply { color = Color.White })
        }
    }

    lateinit var glass: GlassState

    val ssid = "Living room"

    val wifi = NetworkState(NetworkLink.Wifi, ssid = ssid, validated = true)

    fun showHome(
        level: Float = 1f,
        config: TvConfig = SixColumns,
        wallpaperLayout: WallpaperLayout = WallpaperLayout(),
        wallpapers: Flow<Wallpaper> = grey(level).let { flowOf(Wallpaper(picture = it, thumbnail = it)) },
        inputs: Flow<InputsMenu> = emptyFlow(),
        apps: List<AppEntry> = NineApps,
        lastOpened: Map<String, Long> = emptyMap(),
        darkTheme: Boolean = true,
    ) {
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                wallpaper = wallpapers,
                network = flowOf(wifi),
                inputs = inputs,
                lastOpened = flowOf(lastOpened),
            )
        compose.setContent {
            CompositionLocalProvider(LocalBakeDispatcher provides Dispatchers.Unconfined) {
                TvTheme(darkTheme = darkTheme) {
                    glass = rememberGlassState()
                    val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = config.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = {},
                        topBar = config.topBarLayout(),
                        wallpaperLayout = wallpaperLayout,
                        actions = viewModel.actions,
                        glass = glass,
                    )
                }
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        settle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun screen(): DpRect = compose.onRoot().getBoundsInRoot()

    fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    /** The scrims over a picture of [level], 0 to 255, at [at] in dp: black from the top, then black from the left. */
    fun scrimmed(
        level: Int,
        top: Float,
        left: Float,
        at: Offset,
    ): Int {
        val screen = screen()
        val fromTop = top * max(0f, 1f - at.y / screen.bottom.value / 0.45f)
        val fromLeft = left * max(0f, 1f - at.x / screen.right.value / 0.55f)
        return Math.round(level * (1f - fromTop) * (1f - fromLeft))
    }

    /** Above the title, out of reach of its shadow and of the local scrim's 96 dp rise, at the title's left. */
    fun aboveTheTitle(): Offset {
        val title = compose.onNodeWithTag("shelf:title").getBoundsInRoot()
        return Offset(title.left.value + 20f, title.top.value - 100f)
    }

    /** Left of the subtitle's text, 4 dp inside where its pill starts: the picture there under the page scrim, the local one and the pill. */
    fun besideTheSubtitle(): Offset {
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        return Offset(subtitle.left.value - 4f, (subtitle.top.value + subtitle.bottom.value) / 2f)
    }

    /**
     * The local scrim's alpha at [x] dp over a picture it takes to [darkening] in all, where Home's left scrim is [left] strong at the
     * edge and gone 0.55 of the screen's width in: only what the left scrim lacks.
     */
    fun heroAlpha(
        x: Float,
        left: Float = 0.60f,
        darkening: Float = 0.50f,
    ): Float {
        val home = left * max(0f, 1f - x / screen().right.value / 0.55f)
        return max(0f, 1f - (1f - darkening) / (1f - home))
    }

    /** What the local scrim leaves of a level inside the title block at [at]. */
    fun heroScrimmed(
        level: Int,
        at: Offset,
        darkening: Float = 0.50f,
    ): Int = Math.round(level * (1f - heroAlpha(at.x, darkening = darkening)))

    /** What the subtitle's pill leaves of a level: black 0.45. */
    fun pilled(level: Int): Int = Math.round(level * (1f - 0.45f))

    fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int = 4,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    /** Beside the subtitle its own shadow darkens the picture by up to a tenth; a pill would take it down by nearly half. */
    fun assertNoPill(
        expected: Int,
        actual: Int,
    ) = assertNear(expected, actual, tolerance = 12)

    /** The picture over [at] under the page scrim and the local one: 14 dp left of the subtitle, outside its pill. */
    fun outsideThePill(): Int {
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        return redAt(Offset(subtitle.left.value - 14f, (subtitle.top.value + subtitle.bottom.value) / 2f))
    }

    /** Pixels of [mask] within [reach] pixels of a set one, in a [width] by [height] image. */
    fun spread(
        mask: BooleanArray,
        width: Int,
        height: Int,
        reach: Int,
    ): BooleanArray {
        val across = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) for (k in max(0, x - reach)..minOf(width - 1, x + reach)) across[y * width + k] = true
            }
        }
        val all = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (across[y * width + x]) for (k in max(0, y - reach)..minOf(height - 1, y + reach)) all[k * width + x] = true
            }
        }
        return all
    }

    /**
     * The lowest, over six slices of the text of [tag], contrast ratio of its ink against the pixels 1.5 to 6 dp round
     * it. [onBlack] is the screen over a black picture, where only the text shows, and gives its pixels; [onPicture] is
     * the screen over the picture under test.
     */
    fun worstContrast(
        tag: String,
        onBlack: PixelMap,
        onPicture: PixelMap,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        val box = compose.onNodeWithTag(tag).getBoundsInRoot()
        val margin = 14
        val left = (box.left.value * density).toInt() - margin
        val top = (box.top.value * density).toInt() - margin
        val width = ((box.right.value - box.left.value) * density).toInt() + 2 * margin
        val height = ((box.bottom.value - box.top.value) * density).toInt() + 2 * margin
        val black = { i: Int -> onBlack[left + i % width, top + i / width].luminance() }
        val ink = BooleanArray(width * height) { black(it) > 0.8f }
        val text = BooleanArray(width * height) { black(it) > 0.02f }
        val near = spread(text, width, height, 3)
        val around = spread(text, width, height, 12)
        val inkColumns = ink.indices.filter { ink[it] }.map { it % width }
        val slices = 6
        val first = inkColumns.min()
        val span = inkColumns.max() - first + 1
        return (0 until slices).minOf { slice ->
            val columns = first + span * slice / slices until first + span * (slice + 1) / slices
            val onInk = mutableListOf<Float>()
            val beside = mutableListOf<Float>()
            for (i in ink.indices) {
                if (i % width !in columns) continue
                val level = onPicture[left + i % width, top + i / width].luminance()
                if (ink[i]) {
                    onInk += level
                } else if (around[i] && !near[i]) {
                    beside += level
                }
            }
            (onInk.average().toFloat() + 0.05f) / (beside.average().toFloat() + 0.05f)
        }
    }

    val longName = "TV Control with Smart Speakers"

    val namedApps = listOf(AppEntry(key = "pkg.1", packageName = "pkg.1", label = longName)) + NineApps.drop(1)

    /** The council's wallpaper [name] at half size (960 by 540), with the 120 pixel thumbnail Home samples. */
    fun picture(name: String): Wallpaper {
        val full = checkNotNull(javaClass.getResourceAsStream("/wallpaper/$name.jpg")).use { BitmapFactory.decodeStream(it) }
        val thumbnail = Bitmap.createScaledBitmap(full, 120, 120 * full.height / full.width, true)
        return Wallpaper(picture = full.asImageBitmap(), thumbnail = thumbnail.asImageBitmap())
    }

    /** Home on a black picture, then on [name]: the contrast of the 30-character app name and of its last-opened line, against what is round them. */
    fun contrastOver(name: String): Pair<Float, Float> {
        val wallpapers = MutableStateFlow(Wallpaper(picture = grey(0f), thumbnail = grey(0f)))
        val yesterday = Evening.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 26 * 3_600_000L
        showHome(wallpapers = wallpapers, apps = namedApps, lastOpened = mapOf("pkg.1" to yesterday))
        val onBlack = compose.onRoot().captureToImage().toPixelMap()

        wallpapers.value = picture(name)
        settle()
        val onPicture = compose.onRoot().captureToImage().toPixelMap()

        return worstContrast("shelf:title", onBlack, onPicture) to worstContrast("shelf:title-meta", onBlack, onPicture)
    }

    fun contrast(
        first: Color,
        second: Color,
    ): Float = (max(first.luminance(), second.luminance()) + 0.05f) / (min(first.luminance(), second.luminance()) + 0.05f)

    /** The date's rows under the clock pill's top, in dp, and the greeting's. */
    val dateRows = 31f..44f

    val greetingRows = 11f..22f

    /** The pixels in [rows] under the clock pill's top and from [from] to [to] dp past its left edge. */
    fun pillPixels(
        image: PixelMap,
        rows: ClosedFloatingPointRange<Float>,
        from: Float,
        to: Float,
    ): List<Color> {
        val pill = compose.onNodeWithTag("clock").getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val ys = ((pill.top.value + rows.start) * density).toInt() until ((pill.top.value + rows.endInclusive) * density).toInt()
        val xs = ((pill.left.value + from) * density).toInt() until ((pill.left.value + to) * density).toInt()
        return ys.flatMap { y -> xs.map { x -> image[x, y] } }
    }

    /** The most extreme pixel of [pixels], the one furthest from the pill's surface: the ink of text drawn on it. */
    fun ink(
        pixels: List<Color>,
        darkTheme: Boolean,
    ): Color = if (darkTheme) pixels.maxBy { it.luminance() } else pixels.minBy { it.luminance() }

    /** The clock pill's width in dp, for the strips of its padding at both ends of a line. */
    fun pillWidth(): Float =
        compose
            .onNodeWithTag("clock")
            .getBoundsInRoot()
            .width.value

    /** The lowest contrast of the ink of the pill's line in [rows] against the pill's own surface beside it: its padding at both ends. */
    fun lineContrast(
        image: PixelMap,
        rows: ClosedFloatingPointRange<Float>,
        darkTheme: Boolean,
    ): Float {
        val ink = ink(pillPixels(image, rows, 160f, pillWidth() - 30f), darkTheme)
        val surface = pillPixels(image, rows, 10f, 20f) + pillPixels(image, rows, pillWidth() - 20f, pillWidth() - 9f)
        return surface.minOf { contrast(ink, it) }
    }

    val noFrost = "no frost" to Wallpaper()

    val black = "black" to Wallpaper(picture = grey(0f), thumbnail = grey(0f))

    val white = "white" to Wallpaper(picture = grey(1f), thumbnail = grey(1f))

    /** The contrast of the greeting, then of the date, on the clock pill with each of [surfaces] behind it, by name. */
    fun pillContrastOver(
        darkTheme: Boolean,
        vararg surfaces: Pair<String, Wallpaper>,
    ): List<Triple<String, Float, Float>> {
        val wallpapers = MutableStateFlow(Wallpaper())
        showHome(wallpapers = wallpapers, darkTheme = darkTheme)
        return surfaces.map { (name, wallpaper) ->
            wallpapers.value = wallpaper
            settle()
            val image = compose.onRoot().captureToImage().toPixelMap()
            Triple(name, lineContrast(image, greetingRows, darkTheme), lineContrast(image, dateRows, darkTheme))
        }
    }

    fun settingsFill(): Int {
        val button = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
        return redAt(Offset((button.left.value + button.right.value) / 2f, button.top.value + 4f))
    }

    fun setTopBarLightness(lightness: Float) {
        compose.runOnIdle { glass.topBarLightness = lightness }
        settle()
    }

    fun networkButton() = compose.onNode(hasClickAction() and hasText(ssid))

    fun labelBounds(): DpRect = compose.onNode(hasText(ssid), useUnmergedTree = true).getBoundsInRoot()

    /** The red channel of the pixels inside [box], reduced by [pick]: a glyph's stem shows its colour at full strength. */
    fun redIn(
        box: DpRect,
        pick: (Int, Int) -> Int,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        var found = -1
        for (y in (box.top.value * density).toInt() until (box.bottom.value * density).toInt()) {
            for (x in (box.left.value * density).toInt() until (box.right.value * density).toInt()) {
                val red = Math.round(pixels[x, y].red * 255)
                found = if (found < 0) red else pick(found, red)
            }
        }
        return found
    }
}
