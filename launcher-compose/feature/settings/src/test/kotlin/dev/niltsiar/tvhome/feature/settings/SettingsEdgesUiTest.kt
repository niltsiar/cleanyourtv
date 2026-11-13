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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
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
    private val picker = mutableStateOf<OpenOnStartPicker?>(null)

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
                                openOnStart = OptionLabel.Res(R.string.settings_off),
                                wallpaper = OptionLabel.Res(R.string.wallpaper_theme_default),
                                picker = picker.value,
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

    /** The most any pixel of [area], dp by dp, differs from the pixel at [groundX] on its row. */
    private fun PixelMap.peakInk(
        area: DpRect,
        groundX: Dp,
    ): Float {
        var peak = 0f
        var y = area.top
        while (y < area.bottom) {
            var x = area.left
            while (x < area.right) {
                peak = max(peak, distance(pixel(x, y), pixel(groundX, y)))
                x += 1.dp
            }
            y += 1.dp
        }
        return peak
    }

    /**
     * How far the down cue's pill shows in a box [above] the bottom of [bounds]: the pill's half beside the chevron, against the
     * same spot 40 dp further along, where the pill does not reach.
     */
    private fun cueInk(
        bounds: DpRect,
        above: Dp,
    ): Float {
        val middle = (bounds.left + bounds.right) / 2
        val y = bounds.bottom - above
        return shot().ink(middle + 13.dp, y, surfaceX = middle + 53.dp)
    }

    /** The red half way down the option row [id], 8 dp out from each end of its label and chips, where no text is. */
    private fun PixelMap.rowEnds(id: SettingsRowId): List<Float> {
        val row = bounds("setting:${id.name}")
        val middle = (row.top + row.bottom) / 2
        return listOf(pixel(row.left - 8.dp, middle).red, pixel(row.right + 8.dp, middle).red)
    }

    @Test
    fun a_focused_option_row_draws_a_faint_pill_behind_its_label_and_chips_and_an_unfocused_one_does_not() {
        show()
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("setting:Columns"))).assertExists()
        val first = shot()
        val focused = first.rowEnds(SettingsRowId.Columns)
        val unfocused = first.rowEnds(SettingsRowId.RowsBelowDock)

        press(Key.DirectionDown)
        val second = shot()

        // The pill is the page's ink at 0.10: lighter on a dark page, darker on a light one.
        for (end in 0..1) {
            assertTrue("end $end: focused $focused, unfocused $unfocused", abs(focused[end] - unfocused[end]) > 0.05f)
            val left = second.rowEnds(SettingsRowId.Columns)[end]
            assertEquals("end $end of the row focus left", unfocused[end], left, 0.02f)
            assertTrue("end $end of the row focus reached", abs(second.rowEnds(SettingsRowId.RowsBelowDock)[end] - left) > 0.05f)
        }
    }

    @Test
    fun a_selected_row_without_focus_draws_no_fill_only_its_check() {
        show()
        repeat(30) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
        val row = compose.onNodeWithTag("settings:follow_theme")
        row.assertIsSelected()
        val pill = row.getBoundsInRoot()
        val middle = (pill.top + pill.bottom) / 2

        val shot = shot()
        val ground = list().left + 12.dp
        val fill = shot.peakInk(DpRect(pill.left + 400.dp, pill.top + 4.dp, pill.left + 600.dp, pill.bottom - 4.dp), ground)
        val rim = shot.peakInk(DpRect(pill.left + 400.dp, pill.top, pill.left + 600.dp, pill.top + 2.dp), ground)
        val check = shot.peakInk(DpRect(pill.right - 40.dp, middle - 10.dp, pill.right - 12.dp, middle + 10.dp), ground)
        assertTrue("no fill: $fill", fill < 3f)
        assertTrue("no stroke: $rim", rim < 3f)
        assertTrue("the check shows: $check", check > 100f)
    }

    @Test
    fun the_list_shows_the_down_cue_while_rows_lie_below_and_not_at_its_end() {
        show()
        assertTrue("rows lie below", scrolled().let { (value, end) -> value < end })
        val shown = cueInk(list(), above = PanelSideHalf)

        repeat(30) { press(Key.DirectionDown) }
        assertTrue("the list is at its end", scrolled().let { (value, end) -> value == end })
        val hidden = cueInk(list(), above = PanelSideHalf)

        assertTrue("the cue shows with rows below: $shown", shown > 15f)
        assertTrue("no cue at the end: $hidden", hidden < 3f)
    }

    @Test
    fun a_picker_shows_the_down_cue_while_entries_lie_below_and_not_at_the_last_one() {
        show()
        picker.value = OpenOnStartPicker(apps, 0)
        settle()
        compose.onNodeWithTag("picker:0").assertIsFocused()
        val shown = cueInk(bounds("settings:picker"), above = PanelSideHalf)

        repeat(apps.size) { press(Key.DirectionDown) }
        compose.onNodeWithTag("picker:${apps.size}").assertIsFocused()
        val hidden = cueInk(bounds("settings:picker"), above = PanelSideHalf)

        assertTrue("the cue shows with entries below: $shown", shown > 15f)
        assertTrue("no cue at the last entry: $hidden", hidden < 3f)
    }

    @Test
    fun the_page_dims_while_a_picker_is_open_and_not_after_it_closes() {
        show()
        val panel = bounds("settings:panel")
        val ground = panel.left + 8.dp

        fun title(): Float {
            val title = bounds("settings:title")
            return shot().peakInk(DpRect(title.left, title.top, panel.left + 130.dp, title.bottom), ground)
        }
        val before = title()
        picker.value = OpenOnStartPicker(apps, 0)
        settle()
        assertTrue("the picker leaves the title's start in view", bounds("settings:picker").left > panel.left + 150.dp)
        val open = title()
        picker.value = null
        settle()
        val after = title()

        assertTrue("the title shows: $before", before > 100f)
        assertTrue("dimmed while the picker is open: $open against $before", open <= 0.5f * before)
        assertEquals("whole again after it closes", before, after, 2f)
    }

    @Test
    fun at_rest_the_top_edge_is_whole_and_the_bottom_edge_fades() {
        show(reorder = true) { list -> bounds("option:ClockFormat:0").top + 40.dp - list.top }
        val list = list()
        val (value, _) = scrolled()
        assertEquals(0f, value, 0f)

        val shot = shot()
        val first = bounds("settings:reorder")
        val ground = list.left + 12.dp
        val topInBand = shot.ink(first.left + 300.dp, list.top + 10.dp, surfaceX = ground)
        val topBelowBand = shot.ink(first.left + 300.dp, list.top + 30.dp, surfaceX = ground)
        assertTrue("the focused row must show at the top edge: $topBelowBand", topBelowBand > 100f)
        assertTrue("the top edge fades at rest: $topInBand against $topBelowBand", topInBand > 0.9f * topBelowBand)

        val chip = bounds("option:ClockFormat:0")
        val aboveBand = shot.ink(chip.left + 10.dp, chip.top + 10.dp)
        val inBand = shot.ink(chip.left + 20.dp, chip.top + 37.dp)
        assertTrue("the chip must show above the bottom band: $aboveBand", aboveBand > 20f)
        assertTrue("the bottom edge does not fade: $inBand against $aboveBand", inBand * 3f < aboveBand)
    }

    @Test
    fun after_scrolling_down_the_top_edge_fades_and_shows_nothing_in_its_first_12_dp() {
        show { 190.dp }
        repeat(30) { press(Key.DirectionDown) }
        val selected = settingsSections(TvConfig()).flatMap { it.rows }.first { it.id == SettingsRowId.InputArrival }.selected
        val chipTag = "option:InputArrival:$selected"
        // The list grows at its end, so its top rises to 2 dp under the top of the last row's selected chip.
        viewport.value += list().top - compose.onNodeWithTag(chipTag).getUnclippedBoundsInRoot().top - 2.dp
        settle()
        val (value, max) = scrolled()
        assertTrue("the list is scrolled to its end", value > 0f && value == max)
        val list = list()
        val chip = compose.onNodeWithTag(chipTag).getUnclippedBoundsInRoot()
        assertTrue("the selected chip runs under the top edge: $chip in $list", chip.top < list.top && chip.bottom > list.top + 30.dp)

        val shot = shot()
        val inBand = shot.ink(chip.left + 14.dp, list.top + 1.dp, surfaceX = list.left + 12.dp)
        val belowBand = shot.ink(chip.left + 14.dp, list.top + 30.dp, surfaceX = list.left + 12.dp)
        assertTrue("the selected chip must show below the band: $belowBand", belowBand > 20f)
        assertTrue("the top edge fades: $inBand against $belowBand", inBand * 3f < belowBand)

        assertTrue("scrolled past the whole fade: $value", value >= EdgeFade.value * density)
        val clear = shot.peakInk(DpRect(list.left, list.top, list.right, list.top + 12.dp), groundX = list.left + 12.dp)
        assertTrue("nothing shows in the top 12 dp: $clear", clear < 3f)
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

    private fun unclipped(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun centre(tag: String) = unclipped(tag).let { (it.top + it.bottom) / 2 }

    @Test
    fun a_button_row_is_one_option_row_pitch_from_the_rows_around_it_and_its_pill_keeps_a_margin() {
        show(reorder = true)
        val pitch = centre("setting:RowsBelowDock") - centre("setting:Columns")

        for ((above, below) in listOf(
            "setting:ClockFormat" to "settings:wallpaper",
            "settings:wallpaper" to "setting:Animations",
            "setting:Animations" to "settings:open_on_start",
            "settings:follow_theme" to "settings:default_home",
            "settings:default_home" to "settings:tv_settings",
        )) {
            assertEquals("$above to $below", pitch.value, (centre(below) - centre(above)).value, 0.5f)
        }
        val margin = unclipped("settings:default_home").top - unclipped("settings:follow_theme").bottom
        assertTrue("the pills keep a margin: $margin", margin >= 8.dp)
    }

    @Test
    fun every_group_has_a_heading_with_general_over_the_first_options_and_system_over_the_buttons_that_end_the_page() {
        show(reorder = true)
        val headings = compose.onAllNodesWithTag("settings:heading")

        val texts = headings.fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf("General", "Home layout", "App library", "Clock screen", "Motion and hints", "System"), texts)
        val general = headings[0].getUnclippedBoundsInRoot()
        val system = headings[texts.lastIndex].getUnclippedBoundsInRoot()
        assertTrue("General between Reorder and Columns", unclipped("settings:reorder").bottom <= general.top)
        assertTrue("General between Reorder and Columns", general.bottom <= unclipped("setting:Columns").top)
        assertTrue("System between the last option and Follow theme", unclipped("setting:InputArrival").bottom <= system.top)
        assertTrue("System between the last option and Follow theme", system.bottom <= unclipped("settings:follow_theme").top)
    }

    @Test
    fun a_heading_is_48_dp_tall_with_its_baseline_34_dp_under_its_top() {
        show()
        val heading = compose.onAllNodesWithTag("settings:heading")[0]

        assertEquals(48f, heading.getUnclippedBoundsInRoot().height.value, 0.5f)
        assertEquals(34f, heading.getAlignmentLinePosition(FirstBaseline).value, 0.5f)
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

    private companion object {
        /** The cue's middle sits half the 28 dp bottom margin above the bottom of the list's or the picker's box. */
        val PanelSideHalf = 14.dp
        val apps = (1..12).map { OpenOnStartApp("App %02d".format(it), "com.app$it") }
    }
}
