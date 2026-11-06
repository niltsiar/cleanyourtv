package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** What the chrome surfaces draw from: the [frost] behind them, stretched over the whole screen; with none they draw their tint alone. */
@Stable
public class GlassState internal constructor() {
    /** A small, blurred picture of what is behind the screen's surfaces. */
    public var frost: ImageBitmap? by mutableStateOf(null)

    /** The [frost] with its saturation held down, for the dock's glass; null when the dock does not use glass. */
    public var dockFrost: ImageBitmap? by mutableStateOf(null)

    /** The mean CIE L* (0 to 100) of the [frost] under the dock: how bright the wallpaper is where the dock sits. */
    public var dockLightness: Float by mutableStateOf(0f)

    /** The 90th-percentile CIE L* of the wallpaper, before Home's scrim, where the header's text can sit. */
    public var headerLightness: Float by mutableStateOf(0f)

    /** The same under the top-bar buttons. */
    public var topBarLightness: Float by mutableStateOf(0f)

    /** The mean relative luminance (0 to 1) of the wallpaper, before Home's scrim, in the box of the hero block's title and subtitle. */
    public var heroLuminance: Float by mutableStateOf(0f)
}

@Composable
public fun rememberGlassState(): GlassState = remember { GlassState() }

private val Rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f)))
private val ChromeSheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
private val OverlaySheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.4f to Color.Transparent)
private val OverlayShadow = Color.Black.copy(alpha = 0.50f)
private val OverlayElevation = 24.dp

private val GlassPanelShape = RoundedCornerShape(20.dp)
internal val OverlayCorner = 28.dp
private val OverlayShape = RoundedCornerShape(OverlayCorner)

/** The shadow under a chrome surface: none, or a pill's. */
public enum class ChromeShadow(
    internal val elevation: Dp,
    internal val alpha: Float,
) {
    None(0.dp, 0f),
    Pill(8.dp, 0.30f),
}

/**
 * A chrome panel (the clock pill, the top-bar buttons, the library pill): the [GlassState.frost] under a tint, a
 * sheen and, with [rim], a rim. It casts [shadow].
 */
@Composable
public fun GlassSurface(
    glass: GlassState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassPanelShape,
    shadow: ChromeShadow = ChromeShadow.None,
    rim: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shadowColor = Color.Black.copy(alpha = shadow.alpha)
    val shadowed = if (shadow == ChromeShadow.None) modifier else modifier.shadow(shadow.elevation, shape, false, shadowColor, shadowColor)
    Box(
        modifier =
            shadowed
                .clip(shape)
                .glassFrost(glass)
                .background(TvTheme.surfaces.chromeTint)
                .background(ChromeSheen)
                .then(if (rim) Modifier.border(1.dp, Rim, shape) else Modifier),
    ) {
        SurfaceContent { content() }
    }
}

/**
 * The surface of an overlay: a menu, the Inputs list, the App library. Opaque, so nothing behind it shows through; a
 * sheen over its top and the rim keep the glass look. Its text turns dark on the light one.
 */
@Composable
public fun OverlaySurface(
    modifier: Modifier = Modifier,
    shape: Shape = OverlayShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(OverlayElevation, shape, false, OverlayShadow, OverlayShadow)
                .clip(shape)
                .background(TvTheme.surfaces.overlay)
                .background(OverlaySheen)
                .border(1.dp, Rim, shape),
    ) {
        SurfaceContent { content() }
    }
}

/**
 * Draws the state's frost as it lies behind this node on screen, or with [dock] its [GlassState.dockFrost] when it has
 * one; a move of the node redraws it, nothing recomposes.
 */
public fun Modifier.glassFrost(
    glass: GlassState,
    dock: Boolean = false,
): Modifier = this then FrostElement(glass, dock)

private data class FrostElement(
    val glass: GlassState,
    val dock: Boolean,
) : ModifierNodeElement<FrostNode>() {
    override fun create() = FrostNode(glass, dock)

    override fun update(node: FrostNode) {
        node.glass = glass
        node.dock = dock
        node.invalidateDraw()
    }
}

private class FrostNode(
    var glass: GlassState,
    var dock: Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    private var origin = Offset.Zero
    private var screen = IntSize.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val at = coordinates.positionInRoot()
        val root = coordinates.findRootCoordinates().size
        if (at != origin || root != screen) {
            origin = at
            screen = root
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val frost = (if (dock) glass.dockFrost else null) ?: glass.frost
        if (frost != null && screen != IntSize.Zero) {
            drawImage(
                image = frost,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(frost.width, frost.height),
                dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                dstSize = screen,
                filterQuality = FilterQuality.Low,
            )
        }
        drawContent()
    }
}
