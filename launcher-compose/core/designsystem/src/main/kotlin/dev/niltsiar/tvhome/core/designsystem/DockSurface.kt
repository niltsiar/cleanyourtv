package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val DockRimWidth = 1.5.dp
private val TopLineWidth = 1.dp
private val BottomEdgeWidth = 1.dp
private val OuterHairlineWidth = 1.dp
private val DockElevation = 20.dp
private val DockShadowDrop = 6.dp

/**
 * The dock's panel: the frost under a [tint] (read in the draw phase), a sheen over its top, a 1.5 dp rim from
 * [TvElementColors.dockRimTop] at the top to [TvElementColors.dockRimBottom] at the bottom, a 1 dp edge that grows from nothing
 * at mid-height to [TvElementColors.dockBottomEdge] at the bottom (so the sides darken without a step), and a shadow that falls
 * 6 dp lower than the panel. With an [edgeLight] the focused tile lights the rim and the floor. With [outerHairline] a 1 dp
 * line runs round the outside of the panel, so a light panel keeps its edge on a bright picture. [gloss] (read in the draw
 * phase, 0 to 1) scales the sheen and the rim: at less than 1 the top line, [TopLineWidth] thick, takes over the rim's top edge
 * as it fades. [shade] (read in the draw phase, 0 to 1) is the alpha of the shade layer over the whole panel, content included. The panel clips its content to [shape]
 * and marks itself [clipsHalo], so a focused tile's halo inside it ends before that edge.
 *
 * In [Look.Expressive] the panel is the house container, of the dark scheme under a dark [tint] and of the light one under a
 * light tint, opaque, with no frost, sheen, rim, edge or shadow; the [edgeLight] is the only colour of the focused app.
 */
@Composable
public fun DockSurface(
    glass: GlassState,
    tint: () -> Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    edgeLight: DockEdgeLight? = null,
    outerHairline: Boolean = false,
    gloss: () -> Float = { 1f },
    shade: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val colours = TvTheme.elementColors
    val lookState = TvTheme.lookState
    val rimBrush = remember(colours) { Brush.verticalGradient(listOf(colours.dockRimTop, colours.dockRimBottom)) }
    val sheen = remember(colours) { Brush.verticalGradient(0f to colours.dockSheen, 0.35f to Color.Transparent) }
    val bottomEdge = remember(colours) { Brush.verticalGradient(0.5f to Color.Transparent, 1f to colours.dockBottomEdge) }
    Box(
        modifier =
            modifier
                .then(DockShadowElement(shape, colours.dockShadow, lookState))
                .then(if (outerHairline) Modifier.drawBehind { drawOuterHairline(shape, colours.dockOuterHairline) } else Modifier)
                .clipsHalo()
                .clip(shape)
                .glassFrost(glass, dock = true)
                .drawBehind {
                    val expressive = lookState.look == Look.Expressive
                    edgeLight?.look = lookState.look
                    if (expressive) {
                        val tinted = tint()
                        drawRect(lookState.scheme(dark = tinted.luminance() < LIGHT_TINT).surfaceContainer)
                    } else {
                        drawRect(tint())
                        drawRect(sheen, alpha = gloss())
                    }
                    edgeLight?.run { drawFloor(colours.dockGlintAlpha) }
                }.drawWithContent {
                    drawContent()
                    if (lookState.look == Look.Expressive) {
                        edgeLight?.run { drawRim(colours.dockLightAlpha) }
                        val shaded = shade()
                        if (shaded > 0f) drawRect(colours.dockShade, alpha = shaded)
                        return@drawWithContent
                    }
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val glossy = gloss()
                    val rim = DockRimWidth.toPx()
                    drawOutline(outline.grownBy(-rim / 2f), rimBrush, alpha = glossy, style = Stroke(rim))
                    if (glossy < 1f) {
                        val line = TopLineWidth.toPx()
                        val edge = outline.grownBy(-line / 2f)
                        clipRect(bottom = line) { drawOutline(edge, colours.dockTopLine, alpha = 1f - glossy, style = Stroke(line)) }
                    }
                    val bottom = BottomEdgeWidth.toPx()
                    drawOutline(outline.grownBy(-bottom / 2f), bottomEdge, style = Stroke(bottom))
                    edgeLight?.run { drawRim(colours.dockLightAlpha) }
                    val shaded = shade()
                    if (shaded > 0f) drawOutline(outline, colours.dockShade, alpha = shaded)
                }.then(if (edgeLight != null) Modifier.onGloballyPositioned { edgeLight.panelLeft = it.positionInRoot().x } else Modifier),
    ) {
        SurfaceContent { content() }
    }
}

private fun DrawScope.drawOuterHairline(
    shape: RoundedCornerShape,
    colour: Color,
) {
    val width = OuterHairlineWidth.toPx()
    drawOutline(shape.createOutline(size, layoutDirection, this).grownBy(width / 2f), colour, style = Stroke(width))
}

/** Draws the dock's shadow in [colour]: a layer of the panel's shape that casts it from [DockShadowDrop] lower than the panel. */
private data class DockShadowElement(
    val shape: RoundedCornerShape,
    val colour: Color,
    val look: LookState,
) : ModifierNodeElement<DockShadowNode>() {
    override fun create() = DockShadowNode(shape, colour, look)

    override fun update(node: DockShadowNode) {
        node.shape = shape
        node.colour = colour
        node.look = look
        node.invalidateDraw()
    }
}

/** Casts no shadow in [Look.Expressive], read in the draw phase. */
private class DockShadowNode(
    var shape: RoundedCornerShape,
    var colour: Color,
    var look: LookState,
) : Modifier.Node(),
    DrawModifierNode {
    private lateinit var layer: GraphicsLayer

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        requireGraphicsContext().releaseGraphicsLayer(layer)
    }

    override fun ContentDrawScope.draw() {
        if (look.look == Look.Expressive) {
            drawContent()
            return
        }
        val panel = IntSize(size.width.roundToInt(), size.height.roundToInt())
        if (layer.size != panel) layer.record(panel) {}
        layer.translationY = DockShadowDrop.toPx()
        layer.shadowElevation = DockElevation.toPx()
        layer.ambientShadowColor = colour
        layer.spotShadowColor = colour
        layer.setRoundRectOutline(Offset.Zero, size, shape.topStart.toPx(size, this))
        drawLayer(layer)
        drawContent()
    }
}

private val LightWidth = 120.dp
private val LightHeight = 1.5.dp
private val GlintWidth = 200.dp
private val GlintHeight = 20.dp
private const val SEEN = 0.01f

/** A tint lighter than this luminance takes the light house scheme. */
private const val LIGHT_TINT = 0.5f

/**
 * The Expressive light: a cushion [CushionInset] inside the panel's top and bottom, [LightWidth] wide with [CushionCorner]
 * corners, that stretches by [STRETCH] of its lag behind the tile, up to [MostStretch], towards the tile while it moves;
 * and a crest [CrestHeight] tall on the lip above it.
 */
private val CushionInset = 8.dp
private val CushionCorner = 28.dp
private val MostStretch = 48.dp
private const val STRETCH = 0.35f
private val CrestHeight = 3.dp

/**
 * The light the focused dock tile casts on its panel: a [LightWidth] highlight on the top rim and a [GlintWidth] glint on
 * the floor, centred on the tile. It comes to a newly focused tile with a spring, stays on that tile as [follow] reports
 * it moving, takes [show] to fade in and out, and draws in the colour [focus] was given. Everything is read in the draw phase.
 * In [Look.Expressive] it is a tonal cushion behind the tile and a crest on the lip above it, both stretched towards the
 * tile while the light catches up, on the look's spring.
 */
@Stable
public class DockEdgeLight(
    private val scope: CoroutineScope,
) {
    private var tile by mutableFloatStateOf(0f)
    private val lag = Animatable(0f)
    private val shown = Animatable(0f)
    private var colour: () -> Color = { Color.White }

    /** The panel's left edge in the root, set by the panel when it is laid out. */
    internal var panelLeft = 0f

    /** The panel's look, set by the panel as it draws. */
    internal var look: Look = Look.Glass

    /** The tile centred at [centreX] (in the root) took focus; the light takes the colour [colour] gives. */
    public fun focus(
        centreX: Float,
        colour: () -> Color,
    ) {
        this.colour = colour
        val drawn = centre()
        tile = centreX
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (shown.value < SEEN) {
                lag.snapTo(0f)
            } else {
                lag.snapTo(drawn - centreX)
                lag.animateTo(0f, Motion.dockLightFollowSpec(look))
            }
        }
    }

    /** The focused tile is now centred at [centreX] (in the root), because the row scrolled or its tiles moved. */
    public fun follow(centreX: Float) {
        tile = centreX
    }

    private fun centre(): Float = tile + lag.value

    /** Fades the light in or out. */
    public suspend fun show(visible: Boolean) {
        shown.animateTo(if (visible) 1f else 0f, Motion.dockLightFadeSpec())
    }

    /** The light on the rim, at [alpha] of the tile's colour at its middle. */
    internal fun DrawScope.drawRim(alpha: Float) {
        val seen = shown.value
        if (seen < SEEN) return
        if (look == Look.Expressive) {
            val stretch = stretch()
            val width = LightWidth.toPx() + stretch
            val left = lightLeft(stretch, width)
            val crest = CrestHeight.toPx()
            val light = colour()
            drawRoundRect(
                light,
                topLeft = Offset(left, 0f),
                size = Size(width, crest),
                cornerRadius = CornerRadius(crest / 2f),
                alpha = light.alpha * alpha * seen,
            )
            return
        }
        val x = centre() - panelLeft
        val half = LightWidth.toPx() / 2f
        val light = colour()
        val brush =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to light.copy(alpha = light.alpha * alpha * seen),
                1f to Color.Transparent,
                startX = x - half,
                endX = x + half,
            )
        drawRect(brush, topLeft = Offset(x - half, 0f), size = Size(half * 2f, LightHeight.toPx()))
    }

    /** The glint on the floor, at [alpha] of the tile's colour at its middle. */
    internal fun DrawScope.drawFloor(alpha: Float) {
        val seen = shown.value
        if (seen < SEEN) return
        if (look == Look.Expressive) {
            val stretch = stretch()
            val width = LightWidth.toPx() + stretch
            val left = lightLeft(stretch, width)
            val inset = CushionInset.toPx()
            val light = colour()
            drawRoundRect(
                light,
                topLeft = Offset(left, inset),
                size = Size(width, size.height - 2f * inset),
                cornerRadius = CornerRadius(CushionCorner.toPx()),
                alpha = light.alpha * alpha * seen,
            )
            return
        }
        val radius = GlintWidth.toPx() / 2f
        val flatten = GlintHeight.toPx() / 2f / radius
        val middle = Offset(centre() - panelLeft, size.height - GlintHeight.toPx() / 2f)
        val light = colour()
        val brush =
            Brush.radialGradient(
                listOf(light.copy(alpha = light.alpha * alpha * seen), Color.Transparent),
                center = middle,
                radius = radius,
            )
        withTransform({ scale(1f, flatten, middle) }) { drawCircle(brush, radius, middle) }
    }

    /** How far the Expressive light stretches towards the tile: [STRETCH] of its lag, up to [MostStretch]. */
    private fun Density.stretch(): Float = (abs(lag.value) * STRETCH).coerceAtMost(MostStretch.toPx())

    /** The left edge in the panel of an Expressive light [width] wide, [stretch]ed towards the tile, which lies opposite its lag. */
    private fun lightLeft(
        stretch: Float,
        width: Float,
    ): Float = centre() - panelLeft - sign(lag.value) * stretch / 2f - width / 2f
}
