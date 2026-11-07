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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/** What the header and the top-bar buttons look like over a bright or a dark wallpaper, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HeaderLegibilityUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun grey(level: Float) =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color(level, level, level) })
        }

    /** A 100 by 10 thumbnail, dark except for [white] columns out of every [period]: that share of its pixels is bright, anywhere. */
    private fun stripes(
        period: Int,
        white: Int,
    ) = ImageBitmap(100, 10).also { image ->
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, 100f, 10f), Paint().apply { color = Color(0.1f, 0.1f, 0.1f) })
        for (x in 0 until 100) {
            if (x % period < white) canvas.drawRect(Rect(x.toFloat(), 0f, x + 1f, 10f), Paint().apply { color = Color.White })
        }
    }

    private lateinit var glass: GlassState
    private val ssid = "Living room"
    private val wifi = NetworkState(NetworkLink.Wifi, ssid = ssid, validated = true)

    private fun showHome(
        level: Float = 1f,
        config: TvConfig = SixColumns,
        wallpaperLayout: WallpaperLayout = WallpaperLayout(),
        wallpapers: Flow<Wallpaper> = grey(level).let { flowOf(Wallpaper(picture = it, thumbnail = it)) },
        inputs: Flow<InputsMenu> = emptyFlow(),
        apps: List<AppEntry> = NineApps,
        lastOpened: Map<String, Long> = emptyMap(),
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
                TvTheme(darkTheme = true) {
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun screen(): DpRect = compose.onRoot().getBoundsInRoot()

    private fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    /** The scrims over a picture of [level], 0 to 255, at [at] in dp: black from the top, then black from the left. */
    private fun scrimmed(
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
    private fun aboveTheTitle(): Offset {
        val title = compose.onNodeWithTag("shelf:title").getBoundsInRoot()
        return Offset(title.left.value + 20f, title.top.value - 100f)
    }

    /** Left of the subtitle's text, 4 dp inside where its pill starts: the picture there under the page scrim, the local one and the pill. */
    private fun besideTheSubtitle(): Offset {
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        return Offset(subtitle.left.value - 4f, (subtitle.top.value + subtitle.bottom.value) / 2f)
    }

    /**
     * The local scrim's alpha at [x] dp over a picture it takes to [darkening] in all, where Home's left scrim is [left] strong at the
     * edge and gone 0.55 of the screen's width in: only what the left scrim lacks.
     */
    private fun heroAlpha(
        x: Float,
        left: Float = 0.60f,
        darkening: Float = 0.50f,
    ): Float {
        val home = left * max(0f, 1f - x / screen().right.value / 0.55f)
        return max(0f, 1f - (1f - darkening) / (1f - home))
    }

    /** What the local scrim leaves of a level inside the title block at [at]. */
    private fun heroScrimmed(
        level: Int,
        at: Offset,
        darkening: Float = 0.50f,
    ): Int = Math.round(level * (1f - heroAlpha(at.x, darkening = darkening)))

    /** What the subtitle's pill leaves of a level: black 0.45. */
    private fun pilled(level: Int): Int = Math.round(level * (1f - 0.45f))

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int = 4,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    /** Beside the subtitle its own shadow darkens the picture by up to a tenth; a pill would take it down by nearly half. */
    private fun assertNoPill(
        expected: Int,
        actual: Int,
    ) = assertNear(expected, actual, tolerance = 12)

    @Test
    fun a_bright_wallpaper_gets_the_stronger_scrim_behind_the_title() {
        showHome(level = 1f)
        val at = aboveTheTitle()

        assertNear(scrimmed(255, top = 0.70f, left = 0.60f, at), redAt(at))
    }

    @Test
    fun a_dark_wallpaper_keeps_the_fixed_scrim_behind_the_title() {
        showHome(level = 0.4f)
        val at = aboveTheTitle()

        assertNear(scrimmed(102, top = 0.55f, left = 0.40f, at), redAt(at))
    }

    @Test
    fun header_scrim_off_keeps_the_fixed_scrim_over_a_bright_wallpaper() {
        showHome(level = 1f, wallpaperLayout = WallpaperLayout(headerScrim = HeaderScrim.Off))
        val at = aboveTheTitle()

        assertNear(scrimmed(255, top = 0.55f, left = 0.40f, at), redAt(at))
    }

    @Test
    fun a_bright_wallpaper_puts_the_subtitle_on_a_black_045_pill_over_what_the_local_scrim_adds_to_the_page_scrim() {
        showHome(level = 1f)
        val at = besideTheSubtitle()

        assertNear(pilled(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at), at)), redAt(at))
    }

    /** The picture over [at] under the page scrim and the local one: 14 dp left of the subtitle, outside its pill. */
    private fun outsideThePill(): Int {
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        return redAt(Offset(subtitle.left.value - 14f, (subtitle.top.value + subtitle.bottom.value) / 2f))
    }

    @Test
    fun the_subtitle_pill_reaches_10_dp_left_of_its_text() {
        showHome(level = 1f)
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()

        // 8.5 dp out lies inside a pill with 10 dp across, and past the end of one with 8.
        assertNear(
            pilled(outsideThePill()),
            redAt(Offset(subtitle.left.value - 8.5f, (subtitle.top.value + subtitle.bottom.value) / 2f)),
        )
    }

    @Test
    fun the_subtitle_pill_ends_in_half_circles_so_its_corners_stay_clear() {
        showHome(level = 1f)
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()

        // The pill is 26 dp high, 3 dp over the text's box: 3 dp in from each corner of its box is outside a radius of 13 and inside one of 8.
        assertNoPill(outsideThePill(), redAt(Offset(subtitle.left.value - 7f, subtitle.top.value)))
    }

    @Test
    fun the_subtitle_pill_has_a_white_012_stroke_1_dp_wide_inside_its_edge() {
        showHome(level = 1f)
        val subtitle = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        val middle = (subtitle.top.value + subtitle.bottom.value) / 2f
        val fill = pilled(outsideThePill())

        assertNear(Math.round(fill * 0.88f + 255 * 0.12f), redAt(Offset(subtitle.left.value - 9.5f, middle)))
        assertNear(fill, redAt(Offset(subtitle.left.value - 6f, middle)))
    }

    @Test
    fun a_dark_wallpaper_gets_no_local_scrim_and_no_pill_behind_the_subtitle() {
        showHome(level = 0.3f)
        val at = besideTheSubtitle()

        assertNoPill(scrimmed(77, top = 0.55f, left = 0.40f, at), redAt(at))
    }

    @Test
    fun header_scrim_off_draws_no_local_scrim_and_no_pill_behind_the_subtitle() {
        showHome(level = 1f, wallpaperLayout = WallpaperLayout(headerScrim = HeaderScrim.Off))
        val at = besideTheSubtitle()

        assertNoPill(scrimmed(255, top = 0.55f, left = 0.40f, at), redAt(at))
    }

    @Test
    fun a_busy_light_picture_with_a_quarter_of_its_pixels_bright_gets_the_scrim_and_the_pill_behind_the_subtitle() {
        showHome(wallpapers = flowOf(Wallpaper(picture = grey(1f), thumbnail = stripes(period = 20, white = 5))))
        val at = besideTheSubtitle()

        // A quarter of the thumbnail is white and the rest is grey 0.1: a mean relative luminance of 0.2575.
        val darkening = heroScrimDarkening(0.2575f)
        assertTrue("the local scrim darkens by $darkening", darkening > 0.05f)
        assertNear(pilled(heroScrimmed(scrimmed(255, top = 0.70f, left = 0.60f, at), at, darkening)), redAt(at))
    }

    @Test
    fun a_dark_picture_with_a_few_bright_spots_gets_no_local_scrim_and_no_pill_behind_the_subtitle() {
        showHome(wallpapers = flowOf(Wallpaper(picture = grey(0.4f), thumbnail = stripes(period = 25, white = 2))))
        val at = besideTheSubtitle()

        assertNoPill(scrimmed(102, top = 0.55f, left = 0.40f, at), redAt(at))
    }

    @Test
    fun the_picture_is_sampled_once_per_wallpaper_and_again_for_the_next_one() {
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

    @Test
    fun the_hero_title_has_a_black_055_shadow_1_dp_down_blurred_9_dp() {
        showHome(level = 0.3f)

        val shadow = checkNotNull(compose.onNodeWithTag("shelf:title").textStyle().shadow)
        val density = compose.activity.resources.displayMetrics.density

        assertEquals(Color.Black.copy(alpha = 0.55f), shadow.color)
        assertEquals(Offset(0f, density), shadow.offset)
        assertEquals(9 * density, shadow.blurRadius, 0.01f)
    }

    @Test
    fun the_local_scrim_keeps_its_full_strength_24_dp_under_the_subtitle_line() {
        showHome(level = 1f)
        val line = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        val x = line.left.value - 20f

        // 5 dp under the line is below its pill, which reaches 3 dp.
        val inside = redAt(Offset(x, line.bottom.value + 5f))
        val below = redAt(Offset(x, line.bottom.value + 24f))

        assertNear(inside, below, tolerance = 3)
    }

    /** Pixels of [mask] within [reach] pixels of a set one, in a [width] by [height] image. */
    private fun spread(
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
    private fun worstContrast(
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

    @Test
    fun over_a_white_picture_the_title_holds_3_to_1_and_the_subtitle_4_5_to_1_against_what_is_round_them() {
        val wallpapers = MutableStateFlow(Wallpaper(picture = grey(0f), thumbnail = grey(0f)))
        val connected = InputRow.Port("HDMI 3 (eARC/ARC)", InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput("tv/.Hdmi/HW3"))
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
        assertTrue("title $title", title >= 3f)
        assertTrue("subtitle $subtitle", subtitle >= 4.5f)
    }

    private val longName = "TV Control with Smart Speakers"
    private val namedApps = listOf(AppEntry(key = "pkg.1", packageName = "pkg.1", label = longName)) + NineApps.drop(1)

    /** The council's wallpaper [name] at half size (960 by 540), with the 120 pixel thumbnail Home samples. */
    private fun picture(name: String): Wallpaper {
        val full = checkNotNull(javaClass.getResourceAsStream("/wallpaper/$name.jpg")).use { BitmapFactory.decodeStream(it) }
        val thumbnail = Bitmap.createScaledBitmap(full, 120, 120 * full.height / full.width, true)
        return Wallpaper(picture = full.asImageBitmap(), thumbnail = thumbnail.asImageBitmap())
    }

    /** Home on a black picture, then on [name]: the contrast of the 30-character app name and of its last-opened line, against what is round them. */
    private fun contrastOver(name: String): Pair<Float, Float> {
        val wallpapers = MutableStateFlow(Wallpaper(picture = grey(0f), thumbnail = grey(0f)))
        val yesterday = Evening.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 26 * 3_600_000L
        showHome(wallpapers = wallpapers, apps = namedApps, lastOpened = mapOf("pkg.1" to yesterday))
        val onBlack = compose.onRoot().captureToImage().toPixelMap()

        wallpapers.value = picture(name)
        settle()
        val onPicture = compose.onRoot().captureToImage().toPixelMap()

        return worstContrast("shelf:title", onBlack, onPicture) to worstContrast("shelf:title-meta", onBlack, onPicture)
    }

    @Test
    fun over_the_bright_sky_a_long_app_name_holds_3_to_1_and_its_subtitle_4_5_to_1() {
        val (title, subtitle) = contrastOver("bright")

        assertTrue("title $title", title >= 3f)
        assertTrue("subtitle $subtitle", subtitle >= 4.5f)
    }

    @Test
    fun over_the_busy_light_forest_a_long_app_name_holds_3_to_1_and_its_subtitle_4_5_to_1() {
        val (title, subtitle) = contrastOver("busy")

        assertTrue("title $title", title >= 3f)
        assertTrue("subtitle $subtitle", subtitle >= 4.5f)
    }

    @Test
    fun the_local_scrim_holds_to_the_end_of_the_title_and_fades_out_as_a_smoothstep_over_the_next_240_dp() {
        showHome(level = 1f, apps = listOf(AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Prime Video")) + NineApps.drop(1))
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

    @Test
    fun the_subtitle_is_medium_weight_with_a_black_075_shadow_blurred_12_dp() {
        showHome(level = 0.4f)

        val style = compose.onNodeWithTag("shelf:title-meta").textStyle()
        val shadow = checkNotNull(style.shadow)
        val density = compose.activity.resources.displayMetrics.density

        assertEquals(FontWeight.Medium, style.fontWeight)
        assertEquals(Color.Black.copy(alpha = 0.75f), shadow.color)
        assertEquals(12 * density, shadow.blurRadius, 0.01f)
    }

    @Test
    fun a_wallpaper_between_the_two_brightness_marks_gets_a_scrim_between_the_two_strengths() {
        // Grey 191 is L* 77.3: 0.865 of the way from the fixed scrim to the strongest.
        showHome(level = 0.75f)
        val at = aboveTheTitle()

        assertNear(scrimmed(191, top = 0.55f + 0.15f * 0.865f, left = 0.40f + 0.20f * 0.865f, at), redAt(at))
    }

    private fun settingsFill(): Int {
        val button = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
        return redAt(Offset((button.left.value + button.right.value) / 2f, button.top.value + 4f))
    }

    private fun setTopBarLightness(lightness: Float) {
        compose.runOnIdle { glass.topBarLightness = lightness }
        settle()
    }

    @Test
    fun the_top_bar_buttons_thicken_their_tint_over_a_wallpaper_brighter_than_l_star_70() {
        showHome(level = 1f)
        assertTrue("L* ${glass.topBarLightness}", glass.topBarLightness > 95f)
        val bright = settingsFill()

        setTopBarLightness(70f)
        val atTheMark = settingsFill()
        setTopBarLightness(0f)
        val dark = settingsFill()

        assertNear(dark, atTheMark, tolerance = 1)
        assertTrue("bright $bright, dark $dark", bright < dark - 15)
    }

    private fun networkButton() = compose.onNode(hasClickAction() and hasText(ssid))

    private fun labelBounds(): DpRect = compose.onNode(hasText(ssid), useUnmergedTree = true).getBoundsInRoot()

    /** The red channel of the pixels inside [box], reduced by [pick]: a glyph's stem shows its colour at full strength. */
    private fun redIn(
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

    @Test
    fun an_unfocused_network_label_draws_at_three_quarters_and_a_focused_one_in_full() {
        showHome(level = 0.4f, config = SixColumns.copy(wifiLabelWidth = 180))
        val label = labelBounds()
        val pill = redAt(Offset(label.left.value - 4f, (label.top.value + label.bottom.value) / 2f))

        // The label's ink is F2F4F8: at 0.75 its stems keep three quarters of their lead over the pill.
        assertNear(pill + (242 - pill) * 3 / 4, redIn(label) { a, b -> max(a, b) }, tolerance = 6)

        press(Key.DirectionUp)
        networkButton().assertIsFocused()
        // The focused pill is light and its label near black.
        assertTrue(redIn(labelBounds()) { a, b -> minOf(a, b) } < 60)
    }

    @Test
    fun the_network_button_with_a_label_has_no_white_rim_where_an_icon_button_keeps_it() {
        showHome(level = 0.4f, config = SixColumns.copy(wifiLabelWidth = 180))
        val network = networkButton().getBoundsInRoot()
        val settings = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()

        fun rimLift(button: DpRect): Int {
            val row = (button.top.value + button.bottom.value) / 2f
            return redAt(Offset(button.left.value + 0.75f, row)) - redAt(Offset(button.left.value + 4f, row))
        }

        assertTrue("the icon button's rim lifts its edge by ${rimLift(settings)}", rimLift(settings) > 10)
        assertNear(0, rimLift(network), tolerance = 6)
    }
}
