package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The clock pill's text found by its ink on a black screen, at 2 px per dp as on a 1080p TV. */
val ClockPillLayoutTest by testSuite {
    robolectricTestSuite<ClockPillLayoutTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ClockPillLayoutTestContent :
    RobolectricTestSuiteContent({
        testFixture { ClockPillLayoutFixture() } asContextForEach {
            test("with the date below the clock the greeting ends at the right padding on the clock's baseline over the date alone") {
                show(LocalDateTime.of(2026, 9, 30, 22, 42), Locale.forLanguageTag("es-ES"), "EEEE d 'de' MMMM 'de' yyyy")
                val ink = ink()

                ink.lines shouldHaveSize 2
                val (clock, greeting) = ink.parts(ink.lines[0])
                ink.parts(ink.lines[1]) shouldHaveSize 1
                val date = ink.box(ink.lines[1])
                date.left shouldBeInRange 48..50
                date.right shouldBeInRange (ink.width - 48 - 4 until ink.width - 48)
                greeting.right shouldBeInRange (ink.width - 48 - 8 until ink.width - 48)
                greeting.left - clock.right shouldBeGreaterThanOrEqual 40
                abs(greeting.bottom - clock.bottom) shouldBeInRange 0..1
            }

            test("with the date below the clock and no greeting the pill fits the wider of clock and date") {
                show(LocalDateTime.of(2026, 9, 30, 15, 42), Locale.ENGLISH, "EEEE, MMMM d")
                val withGreeting = ink().width
                show(LocalDateTime.of(2026, 9, 30, 15, 42), Locale.ENGLISH, "EEEE, MMMM d", showGreeting = false)
                val ink = ink()

                ink.lines shouldHaveSize 2
                ink.parts(ink.lines[0]) shouldHaveSize 1
                val clock = ink.box(ink.lines[0])
                val date = ink.box(ink.lines[1])
                ink.width shouldBeLessThan withGreeting
                date.left shouldBeInRange 48..50
                maxOf(clock.right, date.right) shouldBeInRange (ink.width - 48 - 4 until ink.width - 48)
            }

            test("with the date below the clock a 12 hour clock keeps its AM/PM marker and its width") {
                show(LocalDateTime.of(2026, 9, 30, 15, 42), Locale.ENGLISH, "EEEE, MMMM d", hour24 = false)
                val ink = ink()

                val (clock, greeting) = ink.parts(ink.lines[0])
                val (digits, marker) = ink.parts(clock.left..clock.right, ink.lines[0], minGapPx = 10)
                greeting.left - marker.right shouldBeGreaterThanOrEqual 40
                greeting.right shouldBeInRange (ink.width - 48 - 8 until ink.width - 48)
                abs(greeting.bottom - digits.bottom) shouldBeInRange 0..1
                show(LocalDateTime.of(2026, 9, 30, 12, 42), Locale.ENGLISH, "EEEE, MMMM d", hour24 = false)
                ink().width shouldBe ink.width
            }

            test("with the date below the clock the longest Spanish and English dates fit without an ellipsis") {
                for ((locale, pattern, text) in listOf(
                    Triple(Locale.forLanguageTag("es-ES"), "EEEE d 'de' MMMM", "miércoles 30 de septiembre"),
                    Triple(Locale.ENGLISH, "EEEE, MMMM d", "Wednesday, September 30"),
                )) {
                    show(LocalDateTime.of(2026, 9, 30, 15, 42), locale, pattern)
                    state.date shouldBe text
                    val full = measured(text)
                    val ink = ink()

                    ink.width - 96 shouldBeGreaterThanOrEqual full
                    ink.parts(ink.lines[1]).single().let { it.right - it.left + 1 } shouldBeGreaterThanOrEqual full - 4
                }
            }

            test("with the date below the clock the pill keeps its height and puts the lines on their baselines") {
                show(LocalDateTime.of(2026, 9, 30, 11, 11), Locale.ENGLISH, "EEEE, MMMM d", showGreeting = false)
                val below = ink()
                show(LocalDateTime.of(2026, 9, 30, 11, 11), Locale.ENGLISH, "EEEE, MMMM d", showGreeting = false, dateBelowClock = false)

                below.height shouldBe 112
                ink().height shouldBe 112
                below.box(below.lines[0]).bottom shouldBeInRange 57..58
                val date = below.box(below.lines[1])
                below.box(below.lines[1], columns = date.left until date.left + 12).bottom shouldBeInRange 93..94
            }

            test("in a right to left language the clock's digits keep their order in both looks") {
                for (below in listOf(true, false)) {
                    val (ltr, rtl) =
                        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).map {
                            direction = it
                            show(
                                LocalDateTime.of(2026, 9, 30, 15, 42),
                                Locale.ENGLISH,
                                "EEEE, MMMM d",
                                showGreeting = false,
                                dateBelowClock = below,
                            )
                            state = state.copy(date = "")
                            compose.waitForIdle()
                            ink().run { pattern(box(0 until height)) }
                        }
                    val differing = ltr.zip(rtl).sumOf { (a, b) -> a.zip(b).count { (x, y) -> x != y } }
                    // Edge pixels can anti-alias apart; a clock with its digits reversed differs in far more than 1 in 100.
                    withClue(
                        "date below the clock: $below, $differing differ, ${ltr.size} by ${ltr[0].length} px against ${rtl.size} by ${rtl[0].length}",
                    ) {
                        rtl.size shouldBe ltr.size
                        rtl[0].length shouldBe ltr[0].length
                        differing shouldBeLessThan ltr.size * ltr[0].length / 100
                    }
                }
            }

            test("with the date beside the clock the pill keeps its look") {
                show(LocalDateTime.of(2026, 9, 30, 15, 42), Locale.ENGLISH, "EEEE, MMMM d", dateBelowClock = false)
                val ink = ink()

                ink.width shouldBe 834
                ink.lines shouldHaveSize 1
                val parts = ink.parts(ink.lines[0])
                parts shouldHaveSize 3
                parts.zip(listOf(InkBox(58, 32, 82, 77), InkBox(108, 31, 278, 78), InkBox(338, 24, 783, 92))).forEach { (found, pinned) ->
                    listOf(found.left - pinned.left, found.top - pinned.top, found.right - pinned.right, found.bottom - pinned.bottom)
                        .forEach { abs(it) shouldBeInRange 0..1 }
                }
            }
        }
    })

/** A box of ink inside the pill, in pixels from its top left corner; [right] and [bottom] are the last inked pixels. */
private data class InkBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** The pill's ink: pixels well brighter than its dark glass, at least 3 dp inside its rounded border. */
private class PillInk(
    val width: Int,
    val height: Int,
    private val inked: Array<BooleanArray>,
) {
    /** The bands of rows with ink, top to bottom. */
    val lines: List<IntRange> =
        buildList {
            var start = -1
            for (y in 0..height) {
                val any = y < height && inked[y].any { it }
                if (any && start < 0) start = y
                if (!any && start >= 0) {
                    add(start until y)
                    start = -1
                }
            }
        }

    /** The ink of [rows] in [columns], split where at least [minGapPx] empty columns lie between. */
    fun parts(
        columns: IntRange,
        rows: IntRange,
        minGapPx: Int = 24,
    ): List<InkBox> {
        val inkedColumns = columns.filter { x -> rows.any { y -> inked[y][x] } }
        val groups = mutableListOf(mutableListOf(inkedColumns.first()))
        for (x in inkedColumns.drop(1)) {
            if (x - groups.last().last() > minGapPx) groups += mutableListOf(x) else groups.last() += x
        }
        return groups.map { box(rows, it.first()..it.last()) }
    }

    fun parts(rows: IntRange): List<InkBox> = parts(0 until width, rows)

    /** The ink in [box], one string per row with `#` for an inked pixel and `.` for a bare one. */
    fun pattern(box: InkBox): List<String> =
        (box.top..box.bottom).map { y -> (box.left..box.right).joinToString("") { x -> if (inked[y][x]) "#" else "." } }

    fun box(
        rows: IntRange,
        columns: IntRange = 0 until width,
    ): InkBox {
        val points = rows.flatMap { y -> columns.filter { x -> inked[y][x] }.map { x -> x to y } }
        return InkBox(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
    }
}

private class ClockPillLayoutFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    var state by mutableStateOf(HomeUiState())

    var dateBelow by mutableStateOf(true)

    var direction by mutableStateOf(LayoutDirection.Ltr)

    lateinit var measurer: TextMeasurer

    lateinit var dateStyle: TextStyle

    var started = false

    fun show(
        now: LocalDateTime,
        locale: Locale,
        datePattern: String,
        hour24: Boolean = true,
        showGreeting: Boolean = true,
        dateBelowClock: Boolean = true,
    ) {
        state =
            homeUiState(homeApps(), now, null, locale, HeaderStyle(hour24 = hour24, datePattern = datePattern, showGreeting = showGreeting))
        dateBelow = dateBelowClock
        if (!started) {
            started = true
            compose.setContent {
                TvTheme {
                    measurer = rememberTextMeasurer()
                    dateStyle = TvTheme.typography.dateCompact
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Row(Modifier.padding(start = 40.dp, top = TopBarTop).height(TopBarHeight)) {
                                ClockPill(rememberGlassState(), { state }, dateBelow)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The width of [text] in the compact date style, in pixels. */
    fun measured(text: String): Int = compose.runOnIdle { measurer.measure(text, dateStyle).size.width }

    fun ink(): PillInk {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density.density
        val bounds = compose.onNodeWithTag("clock").getBoundsInRoot()
        val left = (bounds.left.value * density).roundToInt()
        val top = (bounds.top.value * density).roundToInt()
        val width = ((bounds.right.value - bounds.left.value) * density).roundToInt()
        val height = ((bounds.bottom.value - bounds.top.value) * density).roundToInt()
        val radius = height / 2f
        val rimClear = 3 * density
        val inked =
            Array(height) { y ->
                BooleanArray(width) { x ->
                    val nearest = (x + 0.5f).coerceIn(radius, width - radius)
                    val inside = radius - hypot(x + 0.5f - nearest, y + 0.5f - radius) >= rimClear
                    inside && image[left + x, top + y].luminance() < 0.15f
                }
            }
        return PillInk(width, height, inked)
    }
}
