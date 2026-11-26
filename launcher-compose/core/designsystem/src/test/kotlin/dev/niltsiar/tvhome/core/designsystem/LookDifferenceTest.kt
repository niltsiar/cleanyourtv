package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * No half-themed component: each public design-system composable draws differently in Glass and in Expressive, switched
 * live, and one that takes the house seed draws differently for two seeds. A switch to the look or seed it already has
 * changes nothing, so a difference comes from the switch.
 */
val LookDifferenceTest by testSuite {
    robolectricTestSuite<LookDifferenceTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LookDifferenceTestContent :
    RobolectricTestSuiteContent({
        testFixture { LookDifferenceFixture() } asContextForEach {
            for ((name, leaf) in Leaves.all) {
                test("$name draws the same after a switch to its own look, and differently in Expressive than in Glass") {
                    show(leaf)
                    val glass = pixels()
                    switch(look = Look.Glass)
                    withClue("after a switch to Glass") { (pixels() contentEquals glass) shouldBe true }
                    switch(look = Look.Expressive)

                    (pixels() contentEquals glass) shouldBe false
                }
                if (leaf.seeded) {
                    test("$name in Expressive draws the same for the same seed again, and differently for another house seed") {
                        show(leaf)
                        switch(look = Look.Expressive)
                        val ember = pixels()
                        switch(seed = Color(0xFFFF6A3D))
                        withClue("after the same seed again") { (pixels() contentEquals ember) shouldBe true }
                        switch(seed = Color(0xFF3050D0))

                        (pixels() contentEquals ember) shouldBe false
                    }
                }
            }

            test("every public composable of the design system has a leaf here, or draws nothing a look changes") {
                val declared =
                    File("src/main/kotlin")
                        .walkTopDown()
                        .filter { it.extension == "kt" }
                        .flatMap { file -> PublicComposable.findAll(file.readText()) }
                        .map { it.groupValues[1] }
                        .toSet()
                declared shouldContain "FocusSurface"
                (
                    declared -
                        Leaves.all.values
                            .map { it.covers }
                            .toSet() - NotDrawnByLook
                ).shouldBeEmpty()
            }
        }
    })

private val PublicComposable = Regex("""@Composable\s+(?:@\w+\s+)*public fun (?:<[^>]+> )?([A-Z]\w*)\(""")

/** Public composables with no leaf here: the theme itself, and a scroll rule that draws nothing. */
private val NotDrawnByLook = setOf("TvTheme", "ScrollsClearOfEdge")

/**
 * A leaf's scene: the public composable it [covers], whether it puts the focus modifier it takes on a node to focus, whether it
 * takes the house seed, and how long it takes to come to rest.
 */
private class Leaf(
    val covers: String,
    val focused: Boolean,
    val seeded: Boolean = true,
    val restMs: Long = 2_000,
    val content: @Composable (Modifier) -> Unit,
)

/** The leaves, in an object of their own, as a top-level value of this file would register the suite again. */
private object Leaves {
    val all: Map<String, Leaf> =
        mapOf(
            "a focused tile" to
                Leaf("FocusSurface", true) { focus ->
                    FocusSurface(onClick = {}, modifier = focus.size(240.dp, 135.dp)) {
                        Box(Modifier.size(240.dp, 135.dp).background(Color(0xFF3B5B8C)))
                    }
                },
            "a focused pill" to
                Leaf("FocusPill", true) { focus ->
                    FocusPill(onClick = {}, modifier = focus.size(220.dp, 46.dp)) { ink ->
                        BasicText("Settings", color = ink.content, modifier = Modifier.align(Alignment.Center))
                    }
                },
            "a chrome panel" to
                Leaf("GlassSurface", false) { _ ->
                    GlassSurface(rememberGlassState(), Modifier.size(160.dp, 56.dp), shadow = ChromeShadow.Pill) {
                        BasicText("12:34", color = { Color.White }, modifier = Modifier.align(Alignment.Center))
                    }
                },
            "an overlay surface" to Leaf("OverlaySurface", false) { _ -> OverlaySurface(Modifier.size(240.dp, 120.dp)) {} },
            "a focused chrome button" to
                Leaf("GlassIconButton", true) { focus ->
                    GlassIconButton(rememberGlassState(), "Inputs", {}, focus, focusLabel = "Inputs") { _, _ -> }
                },
            "an open menu" to
                Leaf("GlassMenu", false) { _ ->
                    val anchor = remember { mutableStateOf(Rect(40f, 40f, 440f, 120f)) }
                    GlassMenu(
                        rows = listOf(android.R.string.ok, android.R.string.cancel),
                        anchor = anchor,
                        alignEnd = false,
                        label = { it },
                        rowTag = { "row:$it" },
                        onPick = {},
                    )
                },
            "a key chip over the wallpaper" to Leaf("KeyChip", false) { _ -> KeyChip("OK", "Open") },
            "a dock with its light" to
                Leaf("DockSurface", false) { _ ->
                    val scope = rememberCoroutineScope()
                    val lookState = TvTheme.lookState
                    val motion = TvTheme.motion
                    val light = remember { DockEdgeLight(scope, lookState) { motion } }
                    LaunchedEffect(light) {
                        light.focus(light.panelLeft + 200f) { Color(0xFF80E0A0) }
                        light.show(true)
                    }
                    DockSurface(
                        glass = rememberGlassState(),
                        tint = { Color(0xFF0B0D12).copy(alpha = 0.55f) },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(360.dp, 120.dp),
                        edgeLight = light,
                    ) {
                        Box(Modifier.offset(x = 52.dp, y = 33.dp).size(96.dp, 54.dp).background(Color(0xFF3B5B8C)))
                    }
                },
            "an open dropdown" to
                Leaf("GlassDropdown", false) { _ ->
                    val anchor = remember { mutableStateOf(Rect(40f, 40f, 440f, 120f)) }
                    GlassDropdown(anchor = anchor, alignEnd = false, firstFocus = remember { FocusRequester() }, minWidth = 200.dp) {
                        BasicText("Wallpaper", color = { Color.White }, modifier = Modifier.size(180.dp, 40.dp))
                    }
                },
            "a menu divider" to Leaf("MenuDivider", false) { _ -> Box(Modifier.size(240.dp, 40.dp)) { MenuDivider() } },
            // The theme's type and scrim take no seed. Three passes of the marquee run out, and it rests at its start.
            "a marquee line in the theme's type" to
                Leaf("MarqueeLine", false, seeded = false, restMs = 20_000) { _ ->
                    MarqueeLine("Settings", TvTheme.typography.tileLabel, scrolling = { true }, Modifier.size(200.dp, 40.dp))
                },
            "an open overlay host's scrim" to Leaf("OverlayHost", false, seeded = false) { _ -> OverlayHost(open = { true }) {} },
        )
}

private class LookDifferenceFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    private var restMs = 0L
    private var look by mutableStateOf(Look.Glass)
    private var seed by mutableStateOf(Color(0xFFFF6A3D))

    /** Shows [leaf] in Glass on a flat ground, focused when it is [Leaf.focused], and lets it settle. */
    fun show(leaf: Leaf) {
        compose.mainClock.autoAdvance = false
        // The native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        restMs = leaf.restMs
        val focus = FocusRequester()
        compose.setContent {
            TvTheme(look = look, seed = { seed }) {
                Box(Modifier.size(480.dp, 270.dp).background(Color(0xFF26304A)).testTag("leaf"), contentAlignment = Alignment.Center) {
                    leaf.content(Modifier.focusRequester(focus))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (leaf.focused) compose.runOnUiThread { focus.requestFocus() }
        settle()
    }

    /** Switches the theme to [look] and [seed] without building the scene again, and lets it settle. */
    fun switch(
        look: Look = this.look,
        seed: Color = this.seed,
    ) {
        compose.runOnUiThread {
            this.look = look
            this.seed = seed
        }
        settle()
    }

    fun pixels(): IntArray =
        compose.onNodeWithTag("leaf").captureToImage().toPixelMap().let { map ->
            IntArray(map.width * map.height) { map[it % map.width, it / map.width].toArgb() }
        }

    /** Lets the window take in what the test just changed, then runs every animation out. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(restMs)
        compose.waitForIdle()
    }
}
