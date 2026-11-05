package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DockRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.10f)))
private val DockSheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.35f to Color.Transparent)
private val InnerRim = Color.White.copy(alpha = 0.12f)
private val InnerRimWidth = 1.dp

/** The inner line's centre: the rim's 1 dp, a 1 dp gap and half its own width in from the edge. */
private val InnerRimInset = 2.5.dp
private val DockShadow = Color.Black.copy(alpha = 0.22f)
private val DockElevation = 20.dp
private val DockShadowDrop = 6.dp

/**
 * The dock's panel: the frost under a [tint] (read in the draw phase), a sheen over its top, a rim with a second line
 * inside its top half, and a shadow that falls 6 dp lower than the panel. With an [edgeLight] the focused tile lights
 * the rim and the floor.
 */
@Composable
public fun DockSurface(
    glass: GlassState,
    tint: () -> Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    edgeLight: DockEdgeLight? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .then(DockShadowElement(shape))
                .clip(shape)
                .glassFrost(glass, dock = true)
                .drawBehind {
                    drawRect(tint())
                    drawRect(DockSheen)
                    edgeLight?.run { drawFloor() }
                }.drawWithContent {
                    drawContent()
                    val outline = shape.createOutline(size, layoutDirection, this)
                    clipRect(bottom = size.height / 2f) {
                        drawOutline(outline.grownBy(-InnerRimInset.toPx()), InnerRim, style = Stroke(InnerRimWidth.toPx()))
                    }
                    edgeLight?.run { drawRim() }
                }.border(1.dp, DockRim, shape)
                .then(if (edgeLight != null) Modifier.onGloballyPositioned { edgeLight.panelLeft = it.positionInRoot().x } else Modifier),
    ) {
        SurfaceContent { content() }
    }
}

/** Draws the dock's shadow: a layer of the panel's shape that casts it from [DockShadowDrop] lower than the panel. */
private data class DockShadowElement(
    val shape: RoundedCornerShape,
) : ModifierNodeElement<DockShadowNode>() {
    override fun create() = DockShadowNode(shape)

    override fun update(node: DockShadowNode) {
        node.shape = shape
        node.invalidateDraw()
    }
}

private class DockShadowNode(
    var shape: RoundedCornerShape,
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
        val panel = IntSize(size.width.roundToInt(), size.height.roundToInt())
        if (layer.size != panel) layer.record(panel) {}
        layer.translationY = DockShadowDrop.toPx()
        layer.shadowElevation = DockElevation.toPx()
        layer.ambientShadowColor = DockShadow
        layer.spotShadowColor = DockShadow
        layer.setRoundRectOutline(Offset.Zero, size, shape.topStart.toPx(size, this))
        drawLayer(layer)
        drawContent()
    }
}

private val LightWidth = 120.dp
private val LightHeight = 1.5.dp
private const val LIGHT_ALPHA = 0.85f
private val GlintWidth = 200.dp
private val GlintHeight = 20.dp
private const val GLINT_ALPHA = 0.20f
private const val SEEN = 0.01f
private const val FADE_MS = 160
private const val FOLLOW_DAMPING = 1f
private const val FOLLOW_STIFFNESS = 500f

/**
 * The light the focused dock tile casts on its panel: a [LightWidth] highlight on the top rim and a [GlintWidth] glint on
 * the floor, centred on the tile. It comes to a newly focused tile with a spring, stays on that tile as [follow] reports
 * it moving, takes [show] to fade in and out, and draws in the colour [focus] was given. Everything is read in the draw phase.
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
                lag.animateTo(0f, spring(FOLLOW_DAMPING, FOLLOW_STIFFNESS))
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
        shown.animateTo(if (visible) 1f else 0f, tween(FADE_MS))
    }

    internal fun DrawScope.drawRim() {
        val seen = shown.value
        if (seen < SEEN) return
        val x = centre() - panelLeft
        val half = LightWidth.toPx() / 2f
        val light = colour()
        val brush =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to light.copy(alpha = light.alpha * LIGHT_ALPHA * seen),
                1f to Color.Transparent,
                startX = x - half,
                endX = x + half,
            )
        drawRect(brush, topLeft = Offset(x - half, 0f), size = Size(half * 2f, LightHeight.toPx()))
    }

    internal fun DrawScope.drawFloor() {
        val seen = shown.value
        if (seen < SEEN) return
        val radius = GlintWidth.toPx() / 2f
        val flatten = GlintHeight.toPx() / 2f / radius
        val middle = Offset(centre() - panelLeft, size.height - GlintHeight.toPx() / 2f)
        val light = colour()
        val brush =
            Brush.radialGradient(
                listOf(light.copy(alpha = light.alpha * GLINT_ALPHA * seen), Color.Transparent),
                center = middle,
                radius = radius,
            )
        withTransform({ scale(1f, flatten, middle) }) { drawCircle(brush, radius, middle) }
    }
}
