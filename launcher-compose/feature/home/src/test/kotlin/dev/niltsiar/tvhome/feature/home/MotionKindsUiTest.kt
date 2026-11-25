package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

/**
 * What moves on Expressive Home during a dock move and a held OK, read frame by frame from the pixels: the focus band, its
 * colour and the charge round the two tiles, the light on the dock's rim, and the dock title's swap of names, which both
 * looks have. Nothing else on the screen changes.
 */
val MotionKindsUiTest by testSuite {
    robolectricTestSuite<MotionKindsUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class MotionKindsUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { MotionKindsUiTestFixture() } asContextForEach {
            test("a dock move and a held ok change only the two tiles with their bands and charge, the dock's rim and the dock title") {
                showHome()
                val rest = screen()
                val moving = mutableSetOf<String>()
                val elsewhere = mutableListOf<String>()

                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                var previous = rest
                while (compose.mainClock.currentTime < start + 500) {
                    compose.mainClock.advanceTimeByFrame()
                    val frame = screen()
                    for (y in 0 until frame.height) {
                        for (x in 0 until frame.width) {
                            if (frame[x, y] == previous[x, y]) continue
                            val source = sourceAt(x / density, y / density)
                            if (source == null) elsewhere += "($x, $y) at ${compose.mainClock.currentTime - start} ms" else moving += source
                        }
                    }
                    previous = frame
                }
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }

                withClue("pixels outside the tiles, the rim and the title that changed") { elsewhere.take(10).shouldBeEmpty() }
                moving shouldBe setOf("tile 1", "tile 2", "dock rim", "dock title")
                compose.onAllNodesWithTag("menu").fetchSemanticsNodes().size shouldBe 0
            }
        }
    })

private class MotionKindsUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val config = SixColumns.copy(look = Look.Expressive)

    val apps =
        listOf(Color(0xFFE0453A), Color(0xFF2F9FD8)).mapIndexed {
            i,
            accent,
            ->
            AppEntry("pkg.${i + 1}", "pkg.${i + 1}", "App ${i + 1}", accent = accent)
        } +
            (3..9).map { AppEntry("pkg.$it", "pkg.$it", "App $it") }

    val density: Float get() = compose.activity.resources.displayMetrics.density

    var start = 0L

    private lateinit var tiles: List<Pair<String, DpRect>>
    private lateinit var rim: DpRect
    private lateinit var title: DpRect

    fun showHome() {
        val sources = sources(apps = flowOf(AppList(apps)))
        compose.mainClock.autoAdvance = false
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme(look = config.look, seed = { Color(0xFFFF6A3D) }, motion = config.motion()) {
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    wallpaperLayout = WallpaperLayout(background = Color.Black),
                    shelfLayout = config.shelfLayout(),
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                )
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        // Each tile's own box, out past its grown band and the charge round it.
        tiles =
            (1..2).map { n ->
                val box = compose.onNodeWithTag("tile:pkg.$n").getBoundsInRoot()
                val reach = (box.bottom - box.top) * 0.1f + 12.dp
                "tile $n" to DpRect(box.left - reach, box.top - reach, box.right + reach, box.bottom + reach)
            }
        val dock = compose.onNodeWithTag("dock").getBoundsInRoot()
        rim = DpRect(dock.left, dock.top - 4.dp, dock.right, dock.top + 4.dp)
        // The title and the line under it, with room for the new name's rise and a longer name.
        val name = compose.onNodeWithTag("shelf:title").getBoundsInRoot()
        val meta = compose.onNodeWithTag("shelf:title-meta").getBoundsInRoot()
        val lift = (name.bottom - name.top) / 2
        title = DpRect(name.left, name.top - lift, maxOf(name.right, meta.right) * 1.5f, meta.bottom + lift)
        start = compose.mainClock.currentTime
    }

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** What may draw a pixel at [x], [y] dp: the dock's rim, a tile with its band and charge, the dock title, or nothing that moves. */
    fun sourceAt(
        x: Float,
        y: Float,
    ): String? {
        fun DpRect.has() = x >= left.value && x < right.value && y >= top.value && y < bottom.value
        return when {
            rim.has() -> "dock rim"
            title.has() -> "dock title"
            else -> tiles.firstOrNull { (_, area) -> area.has() }?.first
        }
    }
}
