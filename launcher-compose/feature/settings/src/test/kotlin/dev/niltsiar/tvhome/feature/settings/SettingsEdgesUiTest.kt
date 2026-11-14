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
import androidx.compose.ui.semantics.SemanticsNode
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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.EdgeFade
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.max

/**
 * The settings page as rendered at 1920 by 1080: the list fades at the edges it scrolls away from, focus stays out of
 * that fade, and the Wallpaper and Open on start rows read like the others. The state is fixed, so no store is involved.
 */
val SettingsEdgesUiTest by testSuite {
    robolectricTestSuite<SettingsEdgesUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<SettingsEdgesUiArabicTestContent>(
        "sdk 31, ar-rSA, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "ar-rSA-w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsEdgesUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsEdgesUiFixture() } asContextForEach {
            test("a focused option row draws a faint pill behind its label and chips and an unfocused one does not") {
                show()
                compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("setting:Columns"))).assertExists()
                val first = shot()
                val focused = first.rowEnds(SettingsRowId.Columns)
                val unfocused = first.rowEnds(SettingsRowId.RowsBelowDock)

                press(Key.DirectionDown)
                val second = shot()

                // The pill is the page's ink at 0.10: lighter on a dark page, darker on a light one.
                for (end in 0..1) {
                    withClue("end $end: focused $focused, unfocused $unfocused") {
                        abs(focused[end] - unfocused[end]) shouldBeGreaterThan
                            0.05f
                    }
                    val left = second.rowEnds(SettingsRowId.Columns)[end]
                    withClue("end $end of the row focus left") { left shouldBe (unfocused[end] plusOrMinus 0.02f) }
                    withClue("end $end of the row focus reached") {
                        abs(second.rowEnds(SettingsRowId.RowsBelowDock)[end] - left) shouldBeGreaterThan
                            0.05f
                    }
                }
            }

            test("a selected row without focus draws no fill only its check") {
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
                withClue("no fill: $fill") { fill shouldBeLessThan 3f }
                withClue("no stroke: $rim") { rim shouldBeLessThan 3f }
                withClue("the check shows: $check") { check shouldBeGreaterThan 100f }
            }

            test("the list shows the down cue while rows lie below and not at its end") {
                show()
                withClue("rows lie below") { (scrolled().let { (value, end) -> value < end }).shouldBeTrue() }
                val shown = cueInk(list(), above = PanelSideHalf)

                repeat(30) { press(Key.DirectionDown) }
                withClue("the list is at its end") { (scrolled().let { (value, end) -> value == end }).shouldBeTrue() }
                val hidden = cueInk(list(), above = PanelSideHalf)

                withClue("the cue shows with rows below: $shown") { shown shouldBeGreaterThan 15f }
                withClue("no cue at the end: $hidden") { hidden shouldBeLessThan 3f }
            }

            test("a picker shows the down cue while entries lie below and not at the last one") {
                show()
                picker.value = OpenOnStartPicker(apps, 0)
                settle()
                compose.onNodeWithTag("picker:0").assertIsFocused()
                val shown = cueInk(bounds("settings:picker"), above = PanelSideHalf)

                repeat(apps.size) { press(Key.DirectionDown) }
                compose.onNodeWithTag("picker:${apps.size}").assertIsFocused()
                val hidden = cueInk(bounds("settings:picker"), above = PanelSideHalf)

                withClue("the cue shows with entries below: $shown") { shown shouldBeGreaterThan 15f }
                withClue("no cue at the last entry: $hidden") { hidden shouldBeLessThan 3f }
            }

            test("the page dims while a picker is open and not after it closes") {
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
                withClue("the picker leaves the title's start in view") {
                    bounds("settings:picker").left shouldBeGreaterThan
                        (panel.left + 150.dp)
                }
                val open = title()
                picker.value = null
                settle()
                val after = title()

                withClue("the title shows: $before") { before shouldBeGreaterThan 100f }
                withClue("dimmed while the picker is open: $open against $before") { open shouldBeLessThanOrEqualTo (0.5f * before) }
                withClue("whole again after it closes") { after shouldBe (before plusOrMinus 2f) }
            }

            test("at rest the top edge is whole and the bottom edge fades") {
                show(reorder = true) { list -> bounds("option:ClockFormat:0").top + 40.dp - list.top }
                val list = list()
                val (value, _) = scrolled()
                value shouldBe (0f plusOrMinus 0f)

                val shot = shot()
                val first = bounds("settings:reorder")
                val ground = list.left + 12.dp
                val topInBand = shot.ink(first.left + 300.dp, list.top + 10.dp, surfaceX = ground)
                val topBelowBand = shot.ink(first.left + 300.dp, list.top + 30.dp, surfaceX = ground)
                withClue("the focused row must show at the top edge: $topBelowBand") { topBelowBand shouldBeGreaterThan 100f }
                withClue("the top edge fades at rest: $topInBand against $topBelowBand") {
                    topInBand shouldBeGreaterThan
                        (0.9f * topBelowBand)
                }

                val chip = bounds("option:ClockFormat:0")
                val aboveBand = shot.ink(chip.left + 10.dp, chip.top + 10.dp)
                val inBand = shot.ink(chip.left + 20.dp, chip.top + 37.dp)
                withClue("the chip must show above the bottom band: $aboveBand") { aboveBand shouldBeGreaterThan 10f }
                withClue("the bottom edge does not fade: $inBand against $aboveBand") { (inBand * 3f) shouldBeLessThan aboveBand }
            }

            test("after scrolling down the top edge fades and shows nothing in its first 12 dp") {
                show { 190.dp }
                repeat(30) { press(Key.DirectionDown) }
                val selected = settingsSections(TvConfig()).flatMap { it.rows }.first { it.id == SettingsRowId.InputArrival }.selected
                val chipTag = "option:InputArrival:$selected"
                // The list grows at its end, so its top rises to 2 dp under the top of the last row's selected chip.
                viewport.value += list().top - compose.onNodeWithTag(chipTag).getUnclippedBoundsInRoot().top - 2.dp
                settle()
                val (value, max) = scrolled()
                withClue("the list is scrolled to its end") {
                    value shouldBeGreaterThan 0f
                    value shouldBe max
                }
                val list = list()
                val chip = compose.onNodeWithTag(chipTag).getUnclippedBoundsInRoot()
                withClue("the selected chip runs under the top edge: $chip in $list") {
                    chip.top shouldBeLessThan list.top
                    chip.bottom shouldBeGreaterThan (list.top + 30.dp)
                }

                val shot = shot()
                val inBand = shot.ink(chip.left + 14.dp, list.top + 1.dp, surfaceX = list.left + 12.dp)
                val belowBand = shot.ink(chip.left + 14.dp, list.top + 30.dp, surfaceX = list.left + 12.dp)
                withClue("the selected chip must show below the band: $belowBand") { belowBand shouldBeGreaterThan 10f }
                withClue("the top edge fades: $inBand against $belowBand") { (inBand * 3f) shouldBeLessThan belowBand }

                withClue("scrolled past the whole fade: $value") { value shouldBeGreaterThanOrEqualTo (EdgeFade.value * density) }
                val clear = shot.peakInk(DpRect(list.left, list.top, list.right, list.top + 12.dp), groundX = list.left + 12.dp)
                withClue("nothing shows in the top 12 dp: $clear") { clear shouldBeLessThan 3f }
            }

            test("the focused row stays out of the fade from the first row to the last and back") {
                show()
                assertFocusClearOfTheFade(entries = 23)
            }

            test("the focused row stays out of the fade with the reorder row and a short list") {
                show(reorder = true) { 190.dp }
                assertFocusClearOfTheFade(entries = 24)
            }

            test("a button row is one option row pitch from the rows around it and its pill keeps a margin") {
                show(reorder = true)
                val pitch = centre("setting:RowsBelowDock") - centre("setting:Columns")

                for ((above, below) in listOf(
                    "setting:ClockFormat" to "settings:wallpaper",
                    "settings:wallpaper" to "setting:Animations",
                    "setting:Animations" to "settings:open_on_start",
                    "settings:follow_theme" to "settings:default_home",
                    "settings:default_home" to "settings:tv_settings",
                )) {
                    withClue("$above to $below") { (centre(below) - centre(above)).value shouldBe (pitch.value plusOrMinus 0.5f) }
                }
                val margin = unclipped("settings:default_home").top - unclipped("settings:follow_theme").bottom
                withClue("the pills keep a margin: $margin") { margin shouldBeGreaterThanOrEqualTo 8.dp }
            }

            test("every group has a heading with general over the first options and system over the buttons that end the page") {
                show(reorder = true)
                val headings = compose.onAllNodesWithTag("settings:heading")

                val texts = headings.fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
                texts shouldBe listOf("General", "Home layout", "App library", "Clock screen", "Motion and hints", "System")
                val general = headings[0].getUnclippedBoundsInRoot()
                val system = headings[texts.lastIndex].getUnclippedBoundsInRoot()
                withClue(
                    "General between Reorder and Columns",
                ) { unclipped("settings:reorder").bottom shouldBeLessThanOrEqualTo general.top }
                withClue(
                    "General between Reorder and Columns",
                ) { general.bottom shouldBeLessThanOrEqualTo unclipped("setting:Columns").top }
                withClue("System between the last option and Follow theme") {
                    unclipped("setting:InputArrival").bottom shouldBeLessThanOrEqualTo
                        system.top
                }
                withClue("System between the last option and Follow theme") {
                    system.bottom shouldBeLessThanOrEqualTo
                        unclipped("settings:follow_theme").top
                }
            }

            test("a heading is 56 dp with the divider 16 dp under its top and its baseline 12 dp over its group's first row") {
                show(reorder = true)
                val headings = compose.onAllNodesWithTag("settings:heading")
                val dividers = compose.onAllNodesWithTag("settings:divider")
                dividers.fetchSemanticsNodes().size shouldBe groupFirstRows.size
                for ((at, row) in groupFirstRows.withIndex()) {
                    val heading = headings[at].getUnclippedBoundsInRoot()
                    val divider = dividers[at].getUnclippedBoundsInRoot()
                    val baseline = heading.top + headings[at].getAlignmentLinePosition(FirstBaseline)
                    withClue("heading over $row: $heading, divider $divider") {
                        heading.height.value shouldBe (56f plusOrMinus 0.5f)
                        (divider.top - heading.top).value shouldBe (16f plusOrMinus 0.5f)
                        divider.height.value shouldBe (1f plusOrMinus 0.1f)
                        (divider.left - heading.left).value shouldBe (12f plusOrMinus 0.5f)
                        (heading.right - divider.right).value shouldBe (12f plusOrMinus 0.5f)
                        (heading.bottom - baseline).value shouldBe (12f plusOrMinus 0.5f)
                        if (row.startsWith("setting:")) unclipped(row).top.value shouldBe (heading.bottom.value plusOrMinus 0.5f)
                    }
                }
            }

            test("a heading that opens the list has no divider and no divider follows the last row") {
                show()
                val headings = compose.onAllNodesWithTag("settings:heading")
                val dividers = compose.onAllNodesWithTag("settings:divider")
                val count = headings.fetchSemanticsNodes().size
                dividers.fetchSemanticsNodes().size shouldBe count - 1
                for (at in 1 until count) {
                    val divider = dividers[at - 1].getUnclippedBoundsInRoot()
                    withClue("divider $at") {
                        (divider.top - headings[at].getUnclippedBoundsInRoot().top).value shouldBe (16f plusOrMinus 0.5f)
                        divider.bottom shouldBeLessThan unclipped("settings:tv_settings").top
                    }
                }
            }

            test("a divider stays 38 dp or more over a focused first row with its heading between them and never meets a focused row") {
                show(reorder = true)
                val steps = List(26) { Key.DirectionDown } + List(26) { Key.DirectionUp }
                val firstsSeen = mutableSetOf<String>()
                for ((index, key) in listOf<Key?>(null).plus(steps).withIndex()) {
                    if (key != null) press(key)
                    val (tag, row) = focusedRow()
                    val nodes = compose.onAllNodesWithTag("settings:divider")
                    val dividers = nodes.fetchSemanticsNodes().indices.map { nodes[it].getUnclippedBoundsInRoot() }
                    for (divider in dividers) {
                        withClue("step $index: $tag at $row, divider at $divider") {
                            (divider.bottom < row.top || divider.top > row.bottom).shouldBeTrue()
                        }
                    }
                    val group = groupFirstRows.indexOf(tag)
                    if (group >= 0) {
                        firstsSeen += tag
                        val divider = dividers.filter { it.bottom <= row.top }.maxBy { it.bottom }
                        val heading = compose.onAllNodesWithTag("settings:heading")[group]
                        val baseline = heading.getUnclippedBoundsInRoot().top + heading.getAlignmentLinePosition(FirstBaseline)
                        withClue("step $index: $tag at $row, divider at $divider, heading baseline $baseline") {
                            (row.top - divider.bottom) shouldBeGreaterThanOrEqualTo 38.dp
                            baseline shouldBeGreaterThan divider.bottom
                            baseline shouldBeLessThan row.top
                        }
                    }
                }
                firstsSeen shouldBe groupFirstRows.toSet()
            }

            test("no row heading or divider moves in the list while focus moves within and across groups") {
                show(reorder = true)
                val before = placesInList()
                val steps = List(26) { Key.DirectionDown } + List(26) { Key.DirectionUp }
                for ((index, key) in steps.withIndex()) {
                    press(key)
                    val now = placesInList()
                    for ((name, place) in before) {
                        withClue("step $index, $name") {
                            now.getValue(name).first.value shouldBe (place.first.value plusOrMinus 0.5f)
                            now.getValue(name).second.value shouldBe (place.second.value plusOrMinus 0.5f)
                        }
                    }
                }
            }

            test("the wallpaper row has its label on the start side and its value and a chevron on the end side") {

                assertPickerRowReadsLikeTheOthers("settings:wallpaper", "Wallpaper", "Theme default", rtl = false)
            }

            test("the open on start row has its label on the start side and its value and a chevron on the end side") {

                assertPickerRowReadsLikeTheOthers("settings:open_on_start", "Open on start", "Off", rtl = false)
            }

            test("the chevron points to the end side") {
                show()
                chevronTip("settings:wallpaper") shouldBe 1
                chevronTip("settings:open_on_start") shouldBe 1
            }
        }
    })

class SettingsEdgesUiArabicTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsEdgesUiFixture() } asContextForEach {
            test("in a right to left language the picker rows mirror") {
                val label = stringOf(R.string.wallpaper_label)
                val value = stringOf(R.string.wallpaper_theme_default)
                assertPickerRowReadsLikeTheOthers(
                    "settings:wallpaper",
                    label,
                    value,
                    rtl = true,
                    otherLabel = stringOf(R.string.settings_clock),
                )
            }

            test("in a right to left language the chevron points left") {
                show()
                chevronTip("settings:wallpaper") shouldBe -1
            }
        }
    })

private class SettingsEdgesUiFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val density get() = compose.activity.resources.displayMetrics.density
    val viewport = mutableStateOf(540.dp)
    val picker = mutableStateOf<OpenOnStartPicker?>(null)

    /** The page with a list [listHeight] tall: the function gets the list's bounds at the full height and says how tall it should be. */
    fun show(
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
        list().height.value shouldBe (listHeight(full).value plusOrMinus 0.5f)
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun list() = bounds("settings:list")

    fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** How far the list is scrolled, and how far it can go. */
    fun scrolled(): Pair<Float, Float> {
        val range = compose.onNodeWithTag("settings:list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() to range.maxValue()
    }

    fun shot(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** How far the pixel at ([x], [y]) is from the page's surface at the same height, 0 to 255. By default the surface is the gap between labels and options. */
    fun PixelMap.ink(
        x: Dp,
        y: Dp,
        surfaceX: Dp = list().left + 300.dp,
    ): Float = distance(pixel(x, y), pixel(surfaceX, y))

    fun PixelMap.pixel(
        x: Dp,
        y: Dp,
    ) = this[(x.value * density).toInt(), (y.value * density).toInt()]

    fun distance(
        a: Color,
        b: Color,
    ) = 255 * max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

    /** The most any pixel of [area], dp by dp, differs from the pixel at [groundX] on its row. */
    fun PixelMap.peakInk(
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
    fun cueInk(
        bounds: DpRect,
        above: Dp,
    ): Float {
        val middle = (bounds.left + bounds.right) / 2
        val y = bounds.bottom - above
        return shot().ink(middle + 13.dp, y, surfaceX = middle + 53.dp)
    }

    /** The red half way down the option row [id], 8 dp out from each end of its label and chips, where no text is. */
    fun PixelMap.rowEnds(id: SettingsRowId): List<Float> {
        val row = bounds("setting:${id.name}")
        val middle = (row.top + row.bottom) / 2
        return listOf(pixel(row.left - 8.dp, middle).red, pixel(row.right + 8.dp, middle).red)
    }

    /** From the first entry down to the last and back up, the focused one stays clear of every edge that fades. */
    fun assertFocusClearOfTheFade(entries: Int) {
        val steps = List(entries + 2) { Key.DirectionDown } + List(entries + 2) { Key.DirectionUp }
        val seen = mutableSetOf<Pair<Boolean, Boolean>>()
        for ((index, key) in listOf<Key?>(null).plus(steps).withIndex()) {
            if (key != null) press(key)
            val list = list()
            val focused = compose.onNode(isFocused()).getUnclippedBoundsInRoot()
            val (value, end) = scrolled()
            val note = "step $index: focused ${focused.top}..${focused.bottom} in list ${list.top}..${list.bottom}, scrolled $value of $end"
            withClue(note) {
                focused.top shouldBeGreaterThanOrEqualTo list.top
                focused.bottom shouldBeLessThanOrEqualTo list.bottom
            }
            if (value > 0f) withClue("below the top fade, $note") { focused.top shouldBeGreaterThanOrEqualTo (list.top + EdgeFade) }
            if (value < end) withClue("above the bottom fade, $note") { focused.bottom shouldBeLessThanOrEqualTo (list.bottom - EdgeFade) }
            seen += (value > 0f) to (value < end)
        }
        withClue("the walk visits the top, the middle and the end of the list: $seen") { seen.size shouldBe 3 }
    }

    fun unclipped(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** The tag and bounds of the row that holds focus: a button row itself, or the option row around a focused option. */
    fun focusedRow(): Pair<String, DpRect> {
        var node: SemanticsNode? = compose.onNode(isFocused()).fetchSemanticsNode()
        while (node != null) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null && (tag.startsWith("setting:") || tag.startsWith("settings:"))) return tag to unclipped(tag)
            node = node.parent
        }
        error("no row holds focus")
    }

    /** Where each row, heading and divider sits in the list's content, its top and its height, whatever the scroll. */
    fun placesInList(): Map<String, Pair<Dp, Dp>> {
        val origin = list().top - (scrolled().first / density).dp
        val rows =
            settingsSections(TvConfig()).flatMap { it.rows }.map { "setting:${it.id.name}" } +
                listOf(
                    "settings:reorder",
                    "settings:wallpaper",
                    "settings:open_on_start",
                    "settings:follow_theme",
                    "settings:default_home",
                    "settings:tv_settings",
                )
        val places = rows.associateWith { unclipped(it) }.toMutableMap()
        for (tag in listOf("settings:heading", "settings:divider")) {
            val nodes = compose.onAllNodesWithTag(tag)
            for (at in nodes.fetchSemanticsNodes().indices) places["$tag $at"] = nodes[at].getUnclippedBoundsInRoot()
        }
        return places.mapValues { (_, bounds) -> (bounds.top - origin) to bounds.height }
    }

    fun centre(tag: String) = unclipped(tag).let { (it.top + it.bottom) / 2 }

    /** A text of the row tagged [tag]: a row merges its texts, so the unmerged tree is read. */
    fun inRow(
        tag: String,
        text: String,
    ) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    fun chevron(tag: String) = compose.onNodeWithTag("$tag:chevron", useUnmergedTree = true)

    fun assertPickerRowReadsLikeTheOthers(
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
            withClue("the label starts where the other labels start") {
                labelBounds.right.value shouldBe
                    (other.right.value plusOrMinus 0.5f)
            }
            withClue("the chevron ends where the option pills end") { chevron.left.value shouldBe (lastChip.left.value plusOrMinus 0.5f) }
            withClue("label, value, chevron from the start side") {
                labelBounds.left shouldBeGreaterThan valueBounds.right
                valueBounds.left shouldBeGreaterThan chevron.right
            }
        } else {
            withClue(
                "the label starts where the other labels start",
            ) { labelBounds.left.value shouldBe (other.left.value plusOrMinus 0.5f) }
            withClue("the chevron ends where the option pills end") { chevron.right.value shouldBe (lastChip.right.value plusOrMinus 0.5f) }
            withClue("label, value, chevron from the start side") {
                labelBounds.right shouldBeLessThan valueBounds.left
                valueBounds.right shouldBeLessThan chevron.left
            }
        }
        withClue("the value is on the end side of the row") {
            abs(valueBounds.center().value - rowBounds.center().value) shouldBeGreaterThan
                (rowBounds.width.value / 4f)
        }
        val description = row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        description shouldBe listOf("$label, $value")
    }

    fun DpRect.center() = (left + right) / 2

    fun stringOf(id: Int) = compose.activity.getString(id)

    /** The side of the chevron's middle row, against its top rows, where the ink sits: 1 for the right, -1 for the left. */
    fun chevronTip(tag: String): Int {
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
            withClue("ink in rows $rows") { weight shouldBeGreaterThan 0f }
            return sum / weight
        }
        val tip = centroid(height * 3 / 8 until height * 5 / 8)
        val wings = centroid(0 until height / 4)
        return if (tip > wings) 1 else -1
    }
}

/** The cue's middle sits half the 28 dp bottom margin above the bottom of the list's or the picker's box. */
private val PanelSideHalf = 14.dp

/** The first row of each group that has a heading, top to bottom. */
private val groupFirstRows =
    listOf(
        "setting:Columns",
        "setting:AlwaysShowNames",
        "setting:AppLibrary",
        "setting:HomeIdleClockMinutes",
        "setting:ReducedMotion",
        "settings:follow_theme",
    )
private val apps = (1..12).map { OpenOnStartApp("App %02d".format(it), "com.app$it") }
