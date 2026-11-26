package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.LookColors
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import dev.niltsiar.tvhome.core.model.ShelfPoster
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A focused Watch Next card in Expressive against what it holds, in both tiles, read from the pixels: no glyph box of a
 * text card's title and no part of a watched card's footer line lies on the focus band.
 */
val ShelfCardShapeUiTest by testSuite {
    robolectricTestSuite<ShelfCardShapeUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ShelfCardShapeUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { ShelfCardShapeUiTestFixture() } asContextForEach {
            for (tile in ExpressiveTile.entries) {
                val named = tile.name.lowercase()

                test("in the $named no glyph box of a focused text card's title lies on its band, the j of a second line included") {
                    showHome(tile)
                    val title = title(UNWATCHED, screen())
                    withClue("the second line starts with a j") { title.secondLine shouldBe 'j' }

                    focus(UNWATCHED)
                    withClue("glyph boxes on the band") { title.onBand(screen()).shouldBeEmpty() }
                }

                test("in the $named a part watched text card keeps its title as far above the footer as in from its side") {
                    showHome(tile)
                    val rest = screen()
                    val title = title(WATCHED, rest)
                    withClue("clearance over the footer against the side inset") {
                        title.overFooter(rest) shouldBeGreaterThanOrEqualTo title.sideInset - 1f
                    }

                    focus(WATCHED)
                    withClue("glyph boxes on the band") { title.onBand(screen()).shouldBeEmpty() }
                }

                test("in the $named a watched card's footer line is the same whole line at rest and focused, none of it on the band") {
                    showHome(tile)
                    val restCard = card(POSTER)
                    val line = line(restCard, screen())

                    focus(POSTER)
                    val card = card(POSTER)
                    val shown = screen()
                    val mapped = card.scaled(line.translate(-restCard.topLeft))
                    withClue("line pixels on the band") { onBand(card, mapped, shown) shouldBe 0 }
                    val focusedLine = line(mapped.inflate(4f), shown)
                    withClue("focused line $focusedLine against the line at rest $mapped") {
                        maxOf(
                            abs(focusedLine.left - mapped.left),
                            abs(focusedLine.right - mapped.right),
                            abs(focusedLine.top - mapped.top),
                            abs(focusedLine.bottom - mapped.bottom),
                        ) shouldBeLessThanOrEqualTo 2f
                    }
                }
            }
        }
    })

private const val UNWATCHED = "Silo: The Complete journey, a quiet gripping saga"
private const val WATCHED = "Wool: The Complete journey, a quiet gripping saga"
private const val POSTER = "Severance"

private val Items =
    listOf(
        ShelfItem("pkg.1", UNWATCHED, ShelfKind.Next),
        ShelfItem("pkg.1", WATCHED, ShelfKind.Continue, positionMs = 2_520_000, durationMs = 3_600_000),
        ShelfItem("pkg.1", POSTER, ShelfKind.Continue, positionMs = 1_440_000, durationMs = 3_600_000, posterUri = "content://pkg.1/1"),
    )

private val ShelfExpressive =
    SixColumns.copy(look = Look.Expressive, appShelf = true, appShelfSources = listOf("pkg.1"), ambientGlow = false)

/**
 * A card's title as the layout sets it: the box of each letter in root pixels at rest, and how far up the card draws the
 * title from there, measured at the foot of the T of "The" against the first line's baseline.
 */
private class Title(
    val name: String,
    val card: Rect,
    val boxes: List<Rect>,
    val lift: Float,
    val secondLine: Char,
    val fixture: ShelfCardShapeUiTestFixture,
) {
    val sideInset: Float get() = boxes.minOf { it.left } - card.left

    /** From the lowest glyph box, as drawn, to the top of the footer, in pixels, read from [pixels] at rest. */
    fun overFooter(pixels: PixelMap): Float = fixture.footerTop(card, pixels) - boxes.maxOf { it.bottom - lift }

    /** The glyph boxes, as drawn on the focused card, with a pixel of the focus gap inside them. */
    fun onBand(pixels: PixelMap): List<Rect> {
        val focused = fixture.card(name)
        return with(fixture) {
            boxes
                .map { focused.scaled(it.translate(Offset(-card.left, -card.top - lift))) }
                .filter { onBand(focused, it, pixels) > 0 }
        }
    }
}

private class ShelfCardShapeUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    lateinit var colours: LookColors

    val density: Float get() = compose.activity.resources.displayMetrics.density

    fun showHome(tile: ExpressiveTile) {
        val config = ShelfExpressive.copy(expressiveTile = tile)
        val poster = ShelfPoster(bands(18, 27, listOf(Color(0xFF5A2E6E), Color(0xFFD08A3A))))
        val banner = AppEntry("pkg.1", "pkg.1", "App 1", image = bands(32, 18, listOf(Color(0xFF3A7BD5), Color(0xFFE0A030))))
        val sources =
            sources(
                apps = flowOf(AppList(listOf(banner) + NineApps.drop(1))),
                shelfItems = { Items },
                shelfPoster = { _, _, _ -> poster },
            )
        compose.setContent {
            TvTheme(look = config.look, seed = { Color(0xFFFF6A3D) }, motion = config.motion(), expressiveTile = tile) {
                colours = TvTheme.lookColors
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = viewModel::onClick,
                    topBar = config.topBarLayout(),
                    wallpaperLayout = WallpaperLayout(background = Color.Black),
                    shelfLayout = config.shelfLayout(),
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                    onOpenShelfItem = viewModel::onOpenShelfItem,
                    shelfPoster = sources.shelfPoster,
                    onShelfRoom = viewModel::onShelfRoom,
                )
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
        compose.mainClock.advanceTimeBy(SHELF_HOLD_MS + 1_000)
        compose.waitForIdle()
    }

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** Moves focus up from the dock onto the shelf and along it to the card titled [title]. */
    fun focus(title: String) {
        press(Key.DirectionUp)
        repeat(Items.size) {
            if (index(title) == focusedCard()) return
            press(Key.DirectionRight)
        }
        compose.onNodeWithTag("shelf:card:${index(title)}").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun cards(): List<Pair<Int, String>> =
        Items.indices.mapNotNull { n ->
            compose
                .onAllNodesWithTag("shelf:card:$n")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.config
                ?.getOrNull(SemanticsProperties.ContentDescription)
                ?.let { n to it.single() }
        }

    private fun index(title: String): Int = cards().single { it.second == title }.first

    private fun focusedCard(): Int? =
        Items.indices.firstOrNull { n ->
            compose
                .onAllNodesWithTag("shelf:card:$n")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.config
                ?.getOrNull(SemanticsProperties.Focused) == true
        }

    /** The layout bounds of the card titled [title], in root pixels. */
    fun card(title: String): Rect = compose.onNodeWithTag("shelf:card:${index(title)}").fetchSemanticsNode().boundsInRoot

    /** The title of the card titled [title], read at rest from [pixels]. */
    fun title(
        title: String,
        pixels: PixelMap,
    ): Title {
        val card = card(title)
        val node =
            compose
                .onAllNodesWithTag("shelf:card-title", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single { card.contains(it.boundsInRoot.center) }
        val layouts = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        val layout = layouts.single()
        val origin = node.boundsInRoot.topLeft
        val text = layout.layoutInput.text.text
        val boxes =
            (0 until layout.lineCount).flatMap { line ->
                val end = layout.getLineEnd(line, visibleEnd = true)
                val letters =
                    (layout.getLineStart(line) until end)
                        .filterNot { text[it].isWhitespace() }
                        .map { layout.getBoundingBox(it).translate(origin) }
                val ellipsis =
                    if (layout.isLineEllipsized(line)) {
                        val box = layout.getBoundingBox(end - 1).translate(origin)
                        listOf(Rect(box.right, box.top, origin.x + layout.getLineRight(line), box.bottom))
                    } else {
                        emptyList()
                    }
                letters + ellipsis
            }
        val t = layout.getBoundingBox(text.indexOf("The")).translate(origin)
        val foot = inkFoot(t.center.x.roundToInt(), card, pixels)
        val lift = origin.y + layout.getLineBaseline(0) - foot
        return Title(title, card, boxes, lift.roundToInt().toFloat(), text[layout.getLineStart(1)], this)
    }

    /** The row under the first run of title ink down column [x] of [card] from its top: the foot of what stands there. */
    private fun inkFoot(
        x: Int,
        card: Rect,
        pixels: PixelMap,
    ): Int {
        val ink = colours.tileLabel()
        val ground = pixels[x, (card.top + 3 * density).toInt()]

        fun inked(y: Int) = distance(pixels[x, y], ink) < distance(pixels[x, y], ground)
        var y = card.top.toInt()
        while (!inked(y)) y++
        while (inked(y)) y++
        return y
    }

    /** The top row of the footer strip of [card], from the corner side of its right edge up. */
    fun footerTop(
        card: Rect,
        pixels: PixelMap,
    ): Float {
        val footer = colours.cardFooter()
        val x = (card.right - 4 * density).toInt()
        var y = (card.bottom - 4 * density).toInt()
        withClue("footer colour at the card's foot") { distance(pixels[x, y], footer) shouldBeLessThanOrEqualTo 2f }
        while (distance(pixels[x, y - 1], footer) <= 2f) y--
        return y.toFloat()
    }

    /** The box round the pixels of the footer's line, the wave and its track, in the bottom of [area] as drawn in [pixels]. */
    fun line(
        area: Rect,
        pixels: PixelMap,
    ): Rect {
        val wave = colours.cardProgress()
        val track = colours.cardProgressTrack()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -1f
        var bottom = -1f
        val rows = (area.bottom - minOf(area.height, area.width * 0.2f)).toInt() until area.bottom.toInt()
        for (y in rows) {
            for (x in area.left.toInt() until area.right.toInt()) {
                val p = pixels[x, y]
                if (distance(p, wave) <= 6f || distance(p, track) <= 6f) {
                    left = minOf(left, x.toFloat())
                    top = minOf(top, y.toFloat())
                    right = maxOf(right, x + 1f)
                    bottom = maxOf(bottom, y + 1f)
                }
            }
        }
        withClue("a footer line in $area") { (right > left) shouldBe true }
        return Rect(left, top, right, bottom)
    }

    /** [local], a box in a card's own pixels, as the focused [this] card draws it, grown about its centre. */
    fun Rect.scaled(local: Rect): Rect {
        val c = center

        fun x(v: Float) = c.x + (left + v - c.x) * TILE_FOCUSED_SCALE

        fun y(v: Float) = c.y + (top + v - c.y) * TILE_FOCUSED_SCALE
        return Rect(x(local.left), y(local.top), x(local.right), y(local.bottom))
    }

    /**
     * How many pixels of [box], a pixel in from its edges, are the focus gap of the focused [card]: the house surface
     * just outside the focused shape, read as the second still run up the card's middle from inside its top edge.
     */
    fun onBand(
        card: Rect,
        box: Rect,
        pixels: PixelMap,
    ): Int {
        val gap = gapColour(card, pixels)
        var count = 0
        for (y in (box.top + 1).toInt() until (box.bottom - 1).toInt()) {
            for (x in (box.left + 1).toInt() until (box.right - 1).toInt()) {
                if (distance(pixels[x, y], gap) <= 2f) count++
            }
        }
        return count
    }

    private fun gapColour(
        card: Rect,
        pixels: PixelMap,
    ): Color {
        val x = card.center.x.toInt()
        val inside = card.center.y - (card.height / 2f - 10 * density) * TILE_FOCUSED_SCALE
        val runs = mutableListOf<Pair<Color, Int>>()
        for (y in inside.toInt() downTo (card.top - 30 * density).toInt()) {
            val p = pixels[x, y]
            val last = runs.lastOrNull()
            if (last != null && distance(last.first, p) <= 2f) runs[runs.lastIndex] = last.first to last.second + 1 else runs += p to 1
        }
        return runs.filter { it.second >= 3 }[1].first
    }

    /** The largest gap between two colours' channels, in 8-bit steps. */
    private fun distance(
        a: Color,
        b: Color,
    ): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f
}
