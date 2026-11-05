package dev.niltsiar.tvhome.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
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
import androidx.compose.ui.graphics.lerp
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

/** How glass surfaces draw. */
public enum class GlassKind {
    /** Glass that refracts what is behind it. It needs API 33; below that it draws as [Flat]. */
    Refractive,

    /** Frosted: the [GlassState.frost] under a tint, with no capture of the content behind. */
    Flat,
}

/** The kind surfaces draw as on API level [sdk] when the theme asks for [requested]. */
internal fun glassKindOn(
    requested: GlassKind,
    sdk: Int,
): GlassKind = if (requested == GlassKind.Refractive && sdk >= Build.VERSION_CODES.TIRAMISU) GlassKind.Refractive else GlassKind.Flat

/**
 * What glass surfaces draw from: the content marked with [glassSource] that sits behind them, drawn as [kind]. A
 * [GlassKind.Flat] surface draws the [frost] behind it, stretched over the whole screen; with none it draws its tint alone.
 */
@Stable
public class GlassState internal constructor(
    public val kind: GlassKind,
    internal val haze: HazeState,
) {
    internal val input: HazeInput = HazeInput.Sources(haze)

    /** A small, blurred picture of what is behind the screen's surfaces. */
    public var frost: ImageBitmap? by mutableStateOf(null)
}

@Composable
public fun rememberGlassState(): GlassState {
    val kind = glassKindOn(LocalGlassKind.current, Build.VERSION.SDK_INT)
    val haze = rememberHazeState()
    return remember(kind, haze) { GlassState(kind, haze) }
}

/** Marks this content as something glass draws from. A surface only uses sources with a lower [zIndex]. */
public fun Modifier.glassSource(
    glass: GlassState,
    zIndex: Float = 0f,
): Modifier =
    when (glass.kind) {
        GlassKind.Refractive -> hazeSource(glass.haze, zIndex)
        GlassKind.Flat -> this
    }

/**
 * Glass surfaces inside merge when they come near each other. The container draws their glass behind all its
 * children, so keep it on a layout that holds only the surfaces and their content.
 */
public fun Modifier.glassContainer(glass: GlassState): Modifier =
    when (glass.kind) {
        GlassKind.Refractive -> hazeGlassContainer()
        GlassKind.Flat -> this
    }

private const val GLASS_VEIL_ALPHA = 0.14f

/** Refraction keeps the dark material response whatever the system appearance is. */
private val GlassLook =
    GlassStyle {
        contrast(0.08f)
        whitePoint(-0.22f)
        chromaMultiplier(1.1f)
        lightPosition(BiasAlignment(-0.7f, -1f))
        specularIntensity(0.8f)
    }

private val GlassInteractions =
    GlassStyle {
        focused { lightingIntensity(0.9f) }
        pressed {
            lightingIntensity(1f)
            refractionMultiplier(1.08f)
            whitePointDelta(0.04f)
        }
    }

private val Rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f)))
private val ChromeSheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
private val OverlaySheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.4f to Color.Transparent)
private val OverlayShadow = Color.Black.copy(alpha = 0.50f)
private val OverlayElevation = 24.dp

private val GlassPanelShape = RoundedCornerShape(20.dp)
private val OverlayShape = RoundedCornerShape(28.dp)

/** The shadow under a chrome surface: none, the dock's, or a pill's. */
public enum class ChromeShadow(
    internal val elevation: Dp,
    internal val alpha: Float,
) {
    None(0.dp, 0f),
    Dock(16.dp, 0.35f),
    Pill(8.dp, 0.30f),
}

/** Haze reads the shape from the style, and only rounded corners. */
@Composable
private fun rememberRefractiveGlassStyle(shape: RoundedCornerShape): GlassStyle {
    val colors = TvTheme.colors
    val veil = lerp(colors.onSurface, colors.accent, 0.35f).copy(alpha = GLASS_VEIL_ALPHA)
    val base = GlassStyle.regular.material3(containerColor = colors.surface, tint = veil)
    return remember(base, shape) { base.then(GlassLook).then(GlassInteractions).then { shape(shape) } }
}

/**
 * A chrome panel (the clock pill, the top-bar buttons, the dock, the library pill) drawn as the state's [GlassKind].
 * [interactionSource] drives the refractive glass's response to press and D-pad focus; share it with the clickable that
 * owns the surface. A flat panel draws the frost under a tint, a sheen and a rim, and casts [shadow]; refractive glass
 * casts none, as a shadow would show through it as a dark patch.
 */
@Composable
public fun GlassSurface(
    glass: GlassState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassPanelShape,
    interactionSource: InteractionSource? = null,
    shadow: ChromeShadow = ChromeShadow.None,
    content: @Composable BoxScope.() -> Unit,
) {
    if (glass.kind == GlassKind.Refractive) {
        val surface =
            modifier
                .clip(shape)
                .hazeGlass(input = glass.input, style = rememberRefractiveGlassStyle(shape), interactionSource = interactionSource)
        Box(modifier = surface, content = content)
        return
    }
    val shadowColor = Color.Black.copy(alpha = shadow.alpha)
    val shadowed = if (shadow == ChromeShadow.None) modifier else modifier.shadow(shadow.elevation, shape, false, shadowColor, shadowColor)
    Box(
        modifier =
            shadowed
                .clip(shape)
                .then(FrostElement(glass))
                .background(TvTheme.surfaces.chromeTint)
                .background(ChromeSheen)
                .border(1.dp, Rim, shape),
    ) {
        SurfaceContent { content() }
    }
}

/**
 * The surface of an overlay: a menu, the Inputs list, the App library. Nearly opaque in either kind, so what is behind
 * never competes with it; a sheen over its top and the rim keep the glass look. Its text turns dark on the light one.
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

/** Draws the state's frost as it lies behind this node on screen; a move of the node redraws it, nothing recomposes. */
private data class FrostElement(
    val glass: GlassState,
) : ModifierNodeElement<FrostNode>() {
    override fun create() = FrostNode(glass)

    override fun update(node: FrostNode) {
        node.glass = glass
        node.invalidateDraw()
    }
}

private class FrostNode(
    var glass: GlassState,
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
        val frost = glass.frost
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
