package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Canvas as ImageCanvas

/** Golden images of the design system's surfaces in the Glass and Expressive looks (docs/testing.md, "Golden tests"). */
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
        // Local, as a top-level function of this file would register the suite again while it is being configured.
        // A scene's test title and golden name in a look: Glass's as they are, Expressive's marked.
        fun Look.titled(title: String): String = if (this == Look.Glass) title else "$title, in Expressive"

        fun Look.tagged(scene: String): String = if (this == Look.Glass) scene else "expressive-$scene"

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

            for (look in Look.entries) {
                test(look.titled("a focused pill")) {
                    show(look = look, focused = true) { focus -> Pill(focus) }
                    golden(look.tagged("focus-pill-focused"))
                }
            }

            for (look in Look.entries) {
                test(look.titled("an open menu")) {
                    show(look = look, size = 520) { Menu() }
                    golden(look.tagged("glass-menu-open"))
                }
            }

            for (look in Look.entries) {
                test(look.titled("an open menu with night mode off")) {
                    show(look = look, size = 520, dark = false) { Menu() }
                    golden(look.tagged("glass-menu-open-light"))
                }
            }

            test("a menu 64 ms into its rise") {
                show(size = 520, settle = 48) { Menu() }
                golden("glass-menu-rising")
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

            for (look in Look.entries) {
                test(look.titled("key chips over the wallpaper and in a panel")) {
                    show(look = look) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            KeyChip("OK", "Open")
                            KeyChip(listOf("MENU", "OK"), "Options", look = KeyChipLook.inPanel(TvTheme.colors.onSurface))
                        }
                    }
                    golden(look.tagged("key-chip"))
                }
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

            for (look in Look.entries) {
                test(look.titled("the light overlay card and pill with night mode off")) {
                    show(look = look, dark = false, focused = true) { focus ->
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
                    golden(look.tagged("light-surfaces"))
                }
            }
            for (style in FocusStyle.entries) {
                test("a focus move in ${style.name} at 0, 48, 80 and 160 ms") {
                    val next = FocusRequester()
                    show(style, size = 320, focused = true) { focus ->
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Tile(focus, SmallTile)
                            Tile(Modifier.focusRequester(next), SmallTile)
                        }
                    }
                    compose.runOnUiThread { next.requestFocus() }
                    steps(0, 48, 80, 160) { golden("focus-move-${style.name.lowercase()}-${it}ms") }
                }
            }

            test("a focused surface with a plate and a blended ring 48 ms into a press") {
                val press = Animatable(0f)
                var pressed by mutableStateOf(false)
                show(focused = true) { focus ->
                    val motion = TvTheme.motion
                    LaunchedEffect(pressed) { if (pressed) press.pressDip(motion) }
                    FocusSurface(
                        onClick = {},
                        modifier = focus.size(240.dp, 135.dp),
                        ring = { Mint },
                        ringBlends = true,
                        plate = Mint,
                        press = press,
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF3B5B8C)))
                    }
                }
                compose.runOnUiThread { pressed = true }
                steps(48) { golden("focus-surface-plate-pressed-48ms") }
            }

            test("a pill 64 ms into taking focus") {
                show(focused = true, settle = 64) { focus -> Pill(focus) }
                golden("focus-pill-64ms")
            }

            for (look in Look.entries) {
                test(
                    look.titled("chrome buttons 64, 304 and 704 ms after taking focus, opening a panel and offering a label, and settled"),
                ) {
                    val glass =
                        GlassState().apply {
                            frost = frost()
                            topBarLightness = 95f
                        }
                    val inputs = FocusRequester()
                    var active by mutableStateOf(false)
                    var offer by mutableIntStateOf(0)
                    show(look = look, size = 480) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassSurface(glass, Modifier.size(160.dp, 56.dp), shadow = ChromeShadow.Pill) {
                                BasicText("12:34", style = TvTheme.typography.clock, modifier = Modifier.align(Alignment.Center))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassIconButton(glass, "Settings", {}) { focus, ink -> BarIcon(focus, ink) }
                                GlassIconButton(
                                    glass,
                                    "Apps",
                                    {},
                                    label = { ink -> BasicText("Apps", style = TvTheme.typography.pillLabel, color = ink.content) },
                                ) { focus, ink -> BarIcon(focus, ink) }
                                GlassIconButton(glass, "Inputs", {}, Modifier.focusRequester(inputs), focusLabel = "Inputs") { focus, ink ->
                                    BarIcon(focus, ink)
                                }
                                GlassIconButton(glass, "Wallpaper", {}, active = active) { focus, ink -> BarIcon(focus, ink) }
                                GlassIconButton(
                                    glass,
                                    "Sleep",
                                    {},
                                    focusLabel = "Sleep",
                                    offerToken = offer,
                                ) { focus, ink -> BarIcon(focus, ink) }
                            }
                        }
                    }
                    compose.runOnUiThread {
                        inputs.requestFocus()
                        active = true
                        offer = 1
                    }
                    steps(64, 304, 704, 2_000) { golden(look.tagged("chrome-buttons-${it}ms")) }
                }
            }

            test("a dock panel with an outer hairline, half gloss and shade, as its light fades in, settles and follows a move") {
                var edge: DockEdgeLight? = null
                var density = 1f
                var shown by mutableStateOf(false)
                show {
                    val glass = remember { GlassState().apply { frost = frost() } }
                    val scope = rememberCoroutineScope()
                    val light = remember { DockEdgeLight(scope) }
                    edge = light
                    density = LocalDensity.current.density
                    LaunchedEffect(shown) { light.show(shown) }
                    DockSurface(
                        glass = glass,
                        tint = { Color(0xFF0B0D12).copy(alpha = 0.55f) },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(360.dp, 120.dp),
                        edgeLight = light,
                        outerHairline = true,
                        gloss = { 0.5f },
                        shade = { 0.3f },
                    ) {}
                }
                val light = checkNotNull(edge)
                compose.runOnUiThread {
                    light.focus(light.panelLeft + 120f * density) { Mint }
                    shown = true
                }
                steps(80, 2_000) { golden(if (it == 80L) "dock-surface-light-fading-80ms" else "dock-surface-shaded") }
                compose.runOnUiThread { light.focus(light.panelLeft + 240f * density) { Mint } }
                steps(48) { golden("dock-surface-light-following-48ms") }
            }

            test("an Expressive dock at rest, its light settled on a tile, 48 and 160 ms into a move, and settled again") {
                var edge: DockEdgeLight? = null
                var density = 1f
                var shown by mutableStateOf(false)
                show(look = Look.Expressive) {
                    val glass = remember { GlassState().apply { frost = frost() } }
                    val scope = rememberCoroutineScope()
                    val light = remember { DockEdgeLight(scope) }
                    edge = light
                    density = LocalDensity.current.density
                    LaunchedEffect(shown) { light.show(shown) }
                    DockSurface(
                        glass = glass,
                        tint = { Color(0xFF0B0D12).copy(alpha = 0.55f) },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(360.dp, 120.dp),
                        edgeLight = light,
                    ) {
                        for (centre in listOf(120.dp, 240.dp)) {
                            Box(Modifier.offset(x = centre - 48.dp, y = 33.dp).size(96.dp, 54.dp).background(Color(0xFF3B5B8C)))
                        }
                    }
                }
                golden("expressive-dock-rest")
                val light = checkNotNull(edge)
                compose.runOnUiThread {
                    light.focus(light.panelLeft + 120f * density) { Mint }
                    shown = true
                }
                steps(2_000) { golden("expressive-dock-light-settled") }
                compose.runOnUiThread { light.focus(light.panelLeft + 240f * density) { Mint } }
                steps(48, 160, 2_000) {
                    golden(if (it == 2_000L) "expressive-dock-light-moved" else "expressive-dock-light-moving-${it}ms")
                }
            }

            for (look in Look.entries) {
                test(look.titled("the scrim round a spotlight 80 ms into opening, open, and 64 ms into closing")) {
                    var open by mutableStateOf(false)
                    show(look = look) {
                        OverlayHost(open = { open }, spotlight = { Spotlight(Rect(240f, 110f, 560f, 340f), RoundedCornerShape(16.dp)) }) {}
                    }
                    compose.runOnUiThread { open = true }
                    steps(80, 2_000) { golden(look.tagged(if (it == 80L) "scrim-opening-80ms" else "scrim-open")) }
                    compose.runOnUiThread { open = false }
                    steps(64) { golden(look.tagged("scrim-closing-64ms")) }
                }
            }

            test("a scrolling line 496 ms into its first pass") {
                show(settle = 1_696) {
                    MarqueeLine(
                        "A name far too long for its box",
                        TvTheme.typography.pillLabel,
                        scrolling = { true },
                        modifier = Modifier.size(160.dp, 40.dp),
                        color = { Color.White },
                    )
                }
                golden("marquee-1696ms")
            }

            for (tile in ExpressiveTile.entries) {
                for (style in FocusStyle.entries) {
                    val name = "expressive-${tile.name.lowercase()}-${style.name.lowercase()}"
                    test("an Expressive ${tile.name} tile in ${style.name}, focused") {
                        show(style, focused = true, look = Look.Expressive, tile = tile) { focus -> Tile(focus) }
                        golden("$name-focused")
                    }

                    test("an Expressive ${tile.name} tile in ${style.name}, half way through a held ok") {
                        val hold = OkHold { compose.mainClock.currentTime }
                        show(style, focused = true, look = Look.Expressive, tile = tile) { focus -> Tile(focus.holdOk(hold, {})) }
                        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                        compose.mainClock.advanceTimeBy(HOLD_OK_MS / 2)
                        golden("$name-held")
                    }
                }
            }

            for (tile in ExpressiveTile.entries) {
                test("an Expressive ${tile.name} tile 100, 300 and 540 ms into a held ok") {
                    val hold = OkHold { compose.mainClock.currentTime }
                    show(focused = true, look = Look.Expressive, tile = tile) { focus -> Tile(focus.holdOk(hold, {})) }
                    compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                    steps(100, 300, 540) { golden("expressive-${tile.name.lowercase()}-charge-${it}ms") }
                }
            }

            test("an Expressive round button 300 ms into a held ok") {
                val hold = OkHold { compose.mainClock.currentTime }
                val glass = GlassState().apply { frost = frost() }
                show(focused = true, look = Look.Expressive) { focus ->
                    GlassIconButton(glass, "Settings", {}, focus.holdOk(hold, {})) { progress, ink -> BarIcon(progress, ink) }
                }
                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                steps(300) { golden("expressive-button-charge-300ms") }
            }

            test("an Expressive tile at rest is the same rounded rectangle in every tile shape, for each focus style") {
                val kinds = ExpressiveTile.entries.flatMap { tile -> FocusStyle.entries.map { tile to it } }
                show(size = 480, look = Look.Expressive) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        kinds.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (tile, style) ->
                                    TvTheme(look = Look.Expressive, seed = { Ember }, focusStyle = style, expressiveTile = tile) {
                                        // A flat ground, so the corners compare the tile and not the backdrop behind it.
                                        Box(Modifier.background(Color.Black)) { Tile(Modifier.testTag("rest:$tile:$style"), SmallTile) }
                                    }
                                }
                            }
                        }
                    }
                }
                kinds.forEach { (tile, style) ->
                    val capsule = pixels("rest:${ExpressiveTile.Capsule}:$style")
                    withClue("$tile $style") { pixels("rest:$tile:$style").contentEquals(capsule) shouldBe true }
                }
                golden("expressive-tile-rest")
            }

            test("an Expressive capsule with an app's ring and plate, focused in Glass's style") {
                show(focused = true, look = Look.Expressive) { focus ->
                    FocusSurface(onClick = {}, modifier = focus.size(240.dp, 135.dp), ring = { Mint }, plate = Mint) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF3B5B8C)))
                    }
                }
                golden("expressive-capsule-app-ring-plate")
            }

            test("every type role on the dark ground and on the light surface") {
                show(size = 480, dark = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TypeRoles()
                        OverlaySurface { Box(Modifier.padding(12.dp)) { TypeRoles() } }
                    }
                }
                golden("type-roles")
            }

            for (look in Look.entries) {
                test("a look flip into ${look.name} from a chip at 0, 250 and 500 ms") {
                    val hole = FlipHole()
                    val progress = Animatable(0f)
                    val picture = bands(320, 180, listOf(Color(0xFF2A6F97), Color(0xFF6B4E8C), Color(0xFF3B5B8C), Color(0xFF8C6B3B)))
                    lateinit var scope: CoroutineScope
                    show(look = look) {
                        scope = rememberCoroutineScope()
                        Box(
                            Modifier.size(320.dp, 180.dp).drawWithContent {
                                drawRect(Ember)
                                drawRect(Mint, topLeft = Offset(0f, size.height / 2f), size = Size(size.width / 2f, size.height / 2f))
                                with(hole) { drawFlip(picture, Offset(80.dp.toPx(), 140.dp.toPx()), progress.value) }
                            },
                        )
                    }
                    compose.runOnUiThread { scope.launch { progress.animateTo(1f, Motion().flipSpec(look)) } }
                    steps(0, 250, 500) { golden("look-flip-${look.name.lowercase()}-${it}ms") }
                }
            }
        }
    })

/** The suite's ground: a fixed diagonal gradient, so translucent surfaces show what lies under them. */
private val Ground = Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF6B4E8C)))

/** The house seed of the Expressive scenes. */
private val Ember = Color(0xFFFF6A3D)

/** A light green for the coloured rings, plates and dock lights of the scenes. */
private val Mint = Color(0xFF80E0A0)

private val SmallTile = DpSize(112.dp, 63.dp)

@Composable
private fun Tile(
    modifier: Modifier,
    size: DpSize = DpSize(240.dp, 135.dp),
) {
    FocusSurface(onClick = {}, modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF3B5B8C)))
    }
}

@Composable
private fun Menu() {
    val anchor = remember { mutableStateOf(Rect(96f, 64f, 496f, 144f)) }
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

/** An icon that draws its focus progress: a bar from 30 % of the box's width at rest to all of it focused. */
@Composable
private fun BoxScope.BarIcon(
    focus: () -> Float,
    ink: ColorProducer,
) {
    Canvas(Modifier.matchParentSize()) {
        drawRect(ink(), size = Size(size.width * (0.3f + 0.7f * focus()), size.height / 3f))
    }
}

/** Every role of [TvTheme.typography] in its own colour, family and axes, at one small size so that they fit one scene. */
@Composable
private fun TypeRoles() {
    val type = TvTheme.typography
    val roles =
        listOf(
            type.clock,
            type.clockCompact,
            type.date,
            type.dateCompact,
            type.amPm,
            type.greeting,
            type.pillLabel,
            type.shelfTitle,
            type.heroTitle,
            type.chromeTitle,
            type.cinematicTitle,
            type.cinematicMeta,
            type.tileLabel,
            type.tileCaption,
            type.chipKey,
            type.chipLabel,
            type.caption,
            type.screensaverClock,
            type.screensaverAmPm,
            type.screensaverDate,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.chunked(10).forEach { column ->
            Column {
                column.forEach { BasicText("Ag 12", style = it.merge(TextStyle(fontSize = 11.sp, lineHeight = 13.sp))) }
            }
        }
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
     * Shows [content] centred on a [size] x [size] * 9 / 16 dp ground, in [look] with [tile] tiles and the house [seed], in
     * [style] and with night mode [dark]. With [focused]
     * the node that takes the modifier [content] gets is focused. The test clock then runs [settle] ms, by default long
     * enough for every animation to finish.
     */
    fun show(
        style: FocusStyle = FocusStyle.Glass,
        dark: Boolean = true,
        size: Int = 400,
        focused: Boolean = false,
        settle: Long = 2_000,
        look: Look = Look.Glass,
        tile: ExpressiveTile = ExpressiveTile.Capsule,
        seed: Color = Ember,
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
            TvTheme(look = look, seed = { seed }, focusStyle = style, expressiveTile = tile, darkTheme = dark) {
                Box(Modifier.size(size.dp, (size * 9 / 16).dp).background(Ground).testTag("scene"), contentAlignment = Alignment.Center) {
                    content(Modifier.focusRequester(focus))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (focused) compose.runOnUiThread { focus.requestFocus() }
        compose.mainClock.advanceTimeBy(settle)
        compose.waitForIdle()
    }

    /**
     * Lets the window take in what the test just changed, then runs the test clock frame by frame to each of [at] ms from
     * now in turn, a whole number of frames, and calls [capture] there.
     */
    fun steps(
        vararg at: Long,
        capture: (Long) -> Unit,
    ) {
        compose.waitForIdle()
        val start = compose.mainClock.currentTime
        for (ms in at) {
            while (compose.mainClock.currentTime - start < ms) compose.mainClock.advanceTimeByFrame()
            capture(ms)
        }
    }

    /** The pixels of the node tagged [tag], row by row. */
    fun pixels(tag: String): IntArray =
        compose.onNodeWithTag(tag).captureToImage().toPixelMap().let { map ->
            IntArray(map.width * map.height) { map[it % map.width, it / map.width].toArgb() }
        }

    fun golden(scene: String) =
        compose
            .onNodeWithTag("scene")
            .captureToImage()
            .assertMatchesGolden("DesignSystemGoldenTest", scene)
}
