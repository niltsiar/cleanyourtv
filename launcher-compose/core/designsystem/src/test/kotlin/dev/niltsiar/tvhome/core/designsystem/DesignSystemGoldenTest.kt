package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import androidx.compose.ui.graphics.Canvas as ImageCanvas

/** Golden images of the design system's surfaces in the Glass look (docs/testing.md, "Golden tests"). */
val DesignSystemGoldenTest by testSuite {
    robolectricTestSuite<DesignSystemGoldenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class DesignSystemGoldenTestContent :
    RobolectricTestSuiteContent({
        testFixture { DesignSystemGoldenFixture() } asContextForEach {
            for (style in FocusStyle.entries) {
                test("a focused surface in ${style.name}") {
                    show(style, focused = true) { focus -> Tile(focus) }
                    golden("focus-surface-${style.name.lowercase()}-focused")
                }

                test("a surface at rest in ${style.name}") {
                    show(style) { Tile(it) }
                    golden("focus-surface-${style.name.lowercase()}-rest")
                }
            }

            test("a focused surface half way through a held ok") {
                val hold = OkHold { compose.mainClock.currentTime }
                show(focused = true) { focus -> Tile(focus.holdOk(hold, {})) }
                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(HOLD_OK_MS / 2)
                golden("focus-surface-glass-held")
            }

            test("a focused pill") {
                show(focused = true) { focus -> Pill(focus) }
                golden("focus-pill-focused")
            }

            test("an open menu") {
                val anchor = mutableStateOf(Rect(96f, 64f, 496f, 144f))
                show(size = 520) {
                    GlassMenu(
                        rows = listOf(android.R.string.ok, android.R.string.cancel, android.R.string.copy, android.R.string.cut),
                        anchor = anchor,
                        alignEnd = false,
                        label = { it },
                        rowTag = { "row:$it" },
                        onPick = {},
                        title = "Menu",
                        dividerBefore = { it == android.R.string.cut },
                        warn = { it == android.R.string.cut },
                        value = { if (it == android.R.string.cancel) "On" else null },
                    )
                }
                golden("glass-menu-open")
            }

            test("a dock panel with the edge light") {
                show {
                    val glass = remember { GlassState().apply { frost = frost() } }
                    val scope = rememberCoroutineScope()
                    val edge = remember { DockEdgeLight(scope) }
                    val density = LocalDensity.current.density
                    LaunchedEffect(edge) {
                        edge.focus(centreX = 200f * density) { Color(0xFF80E0A0) }
                        edge.show(true)
                    }
                    DockSurface(
                        glass = glass,
                        tint = { Color(0xFF0B0D12).copy(alpha = 0.55f) },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(360.dp, 120.dp),
                        edgeLight = edge,
                    ) {}
                }
                golden("dock-surface-edge-light")
            }

            test("key chips over the wallpaper and in a panel") {
                show {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        KeyChip("OK", "Open")
                        KeyChip(listOf("MENU", "OK"), "Options", look = KeyChipLook.inPanel(TvTheme.colors.onSurface))
                    }
                }
                golden("key-chip")
            }

            test("the scroll cue") {
                show {
                    Canvas(Modifier.size(120.dp, 60.dp)) {
                        drawScrollDownCue(
                            size.height / 2f,
                            chevron = Color.White.copy(alpha = SCROLL_CUE_ALPHA),
                            pill = Color.White.copy(alpha = SCROLL_CUE_PILL_ALPHA),
                        )
                    }
                }
                golden("scroll-cue")
            }

            test("the light overlay card and pill with night mode off") {
                show(dark = false, focused = true) { focus ->
                    OverlaySurface(Modifier.size(320.dp, 200.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(TvTheme.surfaces.card, RoundedCornerShape(16.dp)),
                            ) {
                                BasicText("Card", style = TvTheme.typography.pillLabel, modifier = Modifier.align(Alignment.Center))
                            }
                            Pill(focus)
                        }
                    }
                }
                golden("light-surfaces")
            }
        }
    })

/** The suite's ground: a fixed diagonal gradient, so translucent surfaces show what lies under them. */
private val Ground = Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF6B4E8C)))

@Composable
private fun Tile(modifier: Modifier) {
    FocusSurface(onClick = {}, modifier = modifier.size(240.dp, 135.dp)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF3B5B8C)))
    }
}

@Composable
private fun Pill(modifier: Modifier) {
    FocusPill(onClick = {}, modifier = modifier.size(220.dp, 46.dp)) { ink ->
        BasicText("Settings", style = TvTheme.typography.pillLabel, color = ink.content, modifier = Modifier.align(Alignment.Center))
    }
}

/** A small blurred-looking picture for the glass to frost: a fixed two-colour gradient. */
private fun frost(): ImageBitmap =
    ImageBitmap(32, 18).also { image ->
        val paint = Paint()
        val canvas = ImageCanvas(image)
        for (x in 0 until 32) {
            paint.color = lerp(Color(0xFF2A6F97), Color(0xFFE0A458), x / 31f)
            canvas.drawRect(Rect(x.toFloat(), 0f, x + 1f, 18f), paint)
        }
    }

private class DesignSystemGoldenFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    /**
     * Shows [content] centred on a [size] x [size] * 9 / 16 dp ground, in [style] and with night mode [dark]. With [focused]
     * the node that takes the modifier [content] gets is focused. Every animation then finishes on the test clock.
     */
    fun show(
        style: FocusStyle = FocusStyle.Glass,
        dark: Boolean = true,
        size: Int = 400,
        focused: Boolean = false,
        content: @Composable (Modifier) -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        // The native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        val focus = FocusRequester()
        compose.setContent {
            TvTheme(look = Look.Glass, focusStyle = style, darkTheme = dark) {
                Box(Modifier.size(size.dp, (size * 9 / 16).dp).background(Ground).testTag("scene"), contentAlignment = Alignment.Center) {
                    content(Modifier.focusRequester(focus))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (focused) compose.runOnUiThread { focus.requestFocus() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    fun golden(scene: String) =
        compose
            .onNodeWithTag("scene")
            .captureToImage()
            .assertMatchesGolden("DesignSystemGoldenTest", scene)
}
