package dev.niltsiar.tvhome.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.EdgeFade
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/**
 * The settings page as rendered at 1920 by 1080: the list fades at the edges it scrolls away from, focus stays out of
 * that fade, and the Wallpaper and Open on start rows read like the others. The state is fixed, so no store is involved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsEdgesUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val density get() = compose.activity.resources.displayMetrics.density
    private val viewport = mutableStateOf(540.dp)

    /** The page with a list [listHeight] tall: the function gets the list's bounds at the full height and says how tall it should be. */
    private fun show(
        reorder: Boolean = false,
        listHeight: (DpRect) -> Dp = { it.height },
    ) {
        compose.setContent {
            TvTheme(motion = Motion(animations = false)) {
                Box(Modifier.fillMaxWidth().height(viewport.value)) {
                    SettingsScreen(
                        state = {
                            SettingsUiState(
                                settingsSections(TvConfig()),
                                followsTheme = true,
                                reorder = reorder,
                                openOnStart = OpenOnStartRow(OptionLabel.Res(R.string.settings_off)),
                                wallpaper = WallpaperRow(OptionLabel.Res(R.string.wallpaper_theme_default)),
                            )
                        },
                        actions = SettingsActions(),
                        onChooseDefaultHome = {},
                        onOpenTvSettings = {},
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
        val full = list()
        viewport.value += listHeight(full) - full.height
        settle()
        assertEquals(listHeight(full).value, list().height.value, 0.5f)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun list() = bounds("settings:list")

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** How far the list is scrolled, and how far it can go. */
    private fun scrolled(): Pair<Float, Float> {
        val range = compose.onNodeWithTag("settings:list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() to range.maxValue()
    }

    private fun shot(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** How far the pixel at ([x], [y]) is from the page's surface at the same height, 0 to 255. By default the surface is the gap between labels and options. */
    private fun PixelMap.ink(
        x: Dp,
        y: Dp,
        surfaceX: Dp = list().left + 300.dp,
    ): Float = distance(pixel(x, y), pixel(surfaceX, y))

    private fun PixelMap.pixel(
        x: Dp,
        y: Dp,
    ) = this[(x.value * density).toInt(), (y.value * density).toInt()]

    private fun distance(
        a: Color,
        b: Color,
    ) = 255 * max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

    @Test
    fun at_rest_the_top_edge_is_whole_and_the_bottom_edge_fades() {
        show { list -> bounds("option:ClockFormat:0").top + 40.dp - list.top }
        val list = list()
        val (value, _) = scrolled()
        assertEquals(0f, value, 0f)

        val shot = shot()
        val first = bounds("option:Columns:0")
        val topInBand = shot.ink(first.left + 20.dp, list.top + 10.dp)
        val topBelowBand = shot.ink(first.left + 20.dp, list.top + 30.dp)
        assertTrue("the focused chip must show at the top edge: $topBelowBand", topBelowBand > 100f)
        assertTrue("the top edge fades at rest: $topInBand against $topBelowBand", topInBand > 0.9f * topBelowBand)

        val chip = bounds("option:ClockFormat:0")
        val aboveBand = shot.ink(chip.left + 10.dp, chip.top + 10.dp)
        val inBand = shot.ink(chip.left + 20.dp, chip.top + 37.dp)
        assertTrue("the chip must show above the bottom band: $aboveBand", aboveBand > 20f)
        assertTrue("the bottom edge does not fade: $inBand against $aboveBand", inBand * 3f < aboveBand)
    }

    @Test
    fun after_scrolling_down_the_top_edge_fades() {
        show { 190.dp }
        repeat(30) { press(Key.DirectionDown) }
        val (value, max) = scrolled()
        assertTrue("the list is scrolled to its end", value > 0f && value == max)
        val list = list()
        val pill = compose.onNodeWithTag("settings:follow_theme").getUnclippedBoundsInRoot()
        assertTrue("the Follow theme pill runs under the top edge", pill.top < list.top && pill.bottom > list.top + 36.dp)

        val shot = shot()
        val inBand = shot.ink(pill.left + 400.dp, list.top + 1.dp, surfaceX = list.left + 12.dp)
        val belowBand = shot.ink(pill.left + 400.dp, list.top + 36.dp, surfaceX = list.left + 12.dp)
        assertTrue("the selected pill must show below the band: $belowBand", belowBand > 20f)
        assertTrue("the top edge is cut straight: $inBand against $belowBand", inBand * 3f < belowBand)
    }

    /** From the first entry down to the last and back up, the focused one stays clear of every edge that fades. */
    private fun assertFocusClearOfTheFade(entries: Int) {
        val steps = List(entries + 2) { Key.DirectionDown } + List(entries + 2) { Key.DirectionUp }
        val seen = mutableSetOf<Pair<Boolean, Boolean>>()
        for ((index, key) in listOf<Key?>(null).plus(steps).withIndex()) {
            if (key != null) press(key)
            val list = list()
            val focused = compose.onNode(isFocused()).getUnclippedBoundsInRoot()
            val (value, end) = scrolled()
            val note = "step $index: focused ${focused.top}..${focused.bottom} in list ${list.top}..${list.bottom}, scrolled $value of $end"
            assertTrue(note, focused.top >= list.top && focused.bottom <= list.bottom)
            if (value > 0f) assertTrue("below the top fade, $note", focused.top >= list.top + EdgeFade)
            if (value < end) assertTrue("above the bottom fade, $note", focused.bottom <= list.bottom - EdgeFade)
            seen += (value > 0f) to (value < end)
        }
        assertTrue("the walk visits the top, the middle and the end of the list: $seen", seen.size == 3)
    }

    @Test
    fun the_focused_row_stays_out_of_the_fade_from_the_first_row_to_the_last_and_back() {
        show()
        assertFocusClearOfTheFade(entries = 23)
    }

    @Test
    fun the_focused_row_stays_out_of_the_fade_with_the_reorder_row_and_a_short_list() {
        show(reorder = true) { 190.dp }
        assertFocusClearOfTheFade(entries = 24)
    }

    /** A text of the row tagged [tag]: a row merges its texts, so the unmerged tree is read. */
    private fun inRow(
        tag: String,
        text: String,
    ) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun chevron(tag: String) = compose.onNodeWithTag("$tag:chevron", useUnmergedTree = true)

    private fun assertPickerRowReadsLikeTheOthers(
        tag: String,
        label: String,
        value: String,
        rtl: Boolean,
        otherLabel: String = "Clock",
    ) {
        show()
        val row = compose.onNodeWithTag(tag)
        val labelBounds = inRow(tag, label).getBoundsInRoot()
        val valueBounds = inRow(tag, value).getBoundsInRoot()
        val chevron = chevron(tag).getBoundsInRoot()
        val rowBounds = row.getBoundsInRoot()
        val other = inRow("setting:ClockFormat", otherLabel).getBoundsInRoot()
        val lastChip = bounds("option:ClockFormat:2")

        if (rtl) {
            assertEquals("the label starts where the other labels start", other.right.value, labelBounds.right.value, 0.5f)
            assertEquals("the chevron ends where the option pills end", lastChip.left.value, chevron.left.value, 0.5f)
            assertTrue(
                "label, value, chevron from the start side",
                labelBounds.left > valueBounds.right && valueBounds.left > chevron.right,
            )
        } else {
            assertEquals("the label starts where the other labels start", other.left.value, labelBounds.left.value, 0.5f)
            assertEquals("the chevron ends where the option pills end", lastChip.right.value, chevron.right.value, 0.5f)
            assertTrue(
                "label, value, chevron from the start side",
                labelBounds.right < valueBounds.left && valueBounds.right < chevron.left,
            )
        }
        assertTrue(
            "the value is on the end side of the row",
            abs(valueBounds.center().value - rowBounds.center().value) > rowBounds.width.value / 4f,
        )
        val description = row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("$label, $value"), description)
    }

    private fun DpRect.center() = (left + right) / 2

    @Test
    fun the_wallpaper_row_has_its_label_on_the_start_side_and_its_value_and_a_chevron_on_the_end_side() =
        assertPickerRowReadsLikeTheOthers("settings:wallpaper", "Wallpaper", "Theme default", rtl = false)

    @Test
    fun the_open_on_start_row_has_its_label_on_the_start_side_and_its_value_and_a_chevron_on_the_end_side() =
        assertPickerRowReadsLikeTheOthers("settings:open_on_start", "Open on start", "Off", rtl = false)

    @Test
    @Config(qualifiers = "ar-rSA-w960dp-h540dp-xhdpi")
    fun in_a_right_to_left_language_the_picker_rows_mirror() {
        val label = stringOf(R.string.wallpaper_label)
        val value = stringOf(R.string.wallpaper_theme_default)
        assertPickerRowReadsLikeTheOthers("settings:wallpaper", label, value, rtl = true, otherLabel = stringOf(R.string.settings_clock))
    }

    private fun stringOf(id: Int) = compose.activity.getString(id)

    /** The side of the chevron's middle row, against its top rows, where the ink sits: 1 for the right, -1 for the left. */
    private fun chevronTip(tag: String): Int {
        val box = chevron(tag).getBoundsInRoot()
        val image = shot()
        val left = (box.left.value * density).toInt()
        val top = (box.top.value * density).toInt()
        val width = (box.width.value * density).toInt()
        val height = (box.height.value * density).toInt()
        val colours = List(width * height) { image[left + it % width, top + it / width] }
        val background =
            colours
                .groupingBy { it }
                .eachCount()
                .maxBy { it.value }
                .key

        fun centroid(rows: IntRange): Float {
            var weight = 0f
            var sum = 0f
            for (y in rows) {
                for (x in 0 until width) {
                    val ink = distance(image[left + x, top + y], background)
                    weight += ink
                    sum += ink * x
                }
            }
            assertTrue("ink in rows $rows", weight > 0f)
            return sum / weight
        }
        val tip = centroid(height * 3 / 8 until height * 5 / 8)
        val wings = centroid(0 until height / 4)
        return if (tip > wings) 1 else -1
    }

    @Test
    fun the_chevron_points_to_the_end_side() {
        show()
        assertEquals(1, chevronTip("settings:wallpaper"))
        assertEquals(1, chevronTip("settings:open_on_start"))
    }

    @Test
    @Config(qualifiers = "ar-rSA-w960dp-h540dp-xhdpi")
    fun in_a_right_to_left_language_the_chevron_points_left() {
        show()
        assertEquals(-1, chevronTip("settings:wallpaper"))
    }
}
