package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** How glass surfaces draw. */
public enum class GlassKind {
    /** Haze glass with refraction on API 33+, and Haze's own fallback below. */
    HazeGlass,

    /** A translucent scrim, with no capture of the content behind. */
    Flat,
}

/** What glass surfaces draw from: the content marked with [glassSource] that sits behind them, drawn as [kind]. */
@Stable
public class GlassState internal constructor(
    internal val kind: GlassKind,
    internal val haze: HazeState,
) {
    internal val input: HazeInput = HazeInput.Sources(haze)
}

@Composable
public fun rememberGlassState(): GlassState {
    val kind = LocalGlassKind.current
    val haze = rememberHazeState()
    return remember(kind, haze) { GlassState(kind, haze) }
}

/** Marks this content as something glass draws from. A surface only uses sources with a lower [zIndex]. */
public fun Modifier.glassSource(
    glass: GlassState,
    zIndex: Float = 0f,
): Modifier =
    when (glass.kind) {
        GlassKind.HazeGlass -> hazeSource(glass.haze, zIndex)
        GlassKind.Flat -> this
    }

/**
 * Glass surfaces inside merge when they come near each other. The container draws their glass behind all its
 * children, so keep it on a layout that holds only the surfaces and their content.
 */
public fun Modifier.glassContainer(glass: GlassState): Modifier =
    when (glass.kind) {
        GlassKind.HazeGlass -> hazeGlassContainer()
        GlassKind.Flat -> this
    }

private const val GLASS_TINT_ALPHA = 0.38f
private const val FLAT_SCRIM_ALPHA = 0.62f

private val GlassInteractions =
    GlassStyle {
        focused { lightingIntensity(0.35f) }
        pressed {
            lightingIntensity(1f)
            refractionMultiplier(1.08f)
            whitePointDelta(0.04f)
        }
    }

private val FlatRim =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))

private val GlassPanelShape = RoundedCornerShape(20.dp)

/** Haze reads the shape from the style, and only rounded corners. */
@Composable
private fun rememberHazeGlassStyle(shape: RoundedCornerShape): GlassStyle {
    val colors = TvTheme.colors
    val base = GlassStyle.regular.material3(containerColor = colors.surface, tint = colors.background.copy(alpha = GLASS_TINT_ALPHA))
    return remember(base, shape) { base.then(GlassInteractions).then { shape(shape) } }
}

/**
 * A panel drawn as the state's [GlassKind]. [interactionSource] drives the glass response to press and D-pad focus; share it with the
 * clickable that owns the surface.
 */
@Composable
public fun GlassSurface(
    glass: GlassState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassPanelShape,
    interactionSource: InteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface =
        when (glass.kind) {
            GlassKind.HazeGlass -> {
                modifier
                    .clip(shape)
                    .hazeGlass(input = glass.input, style = rememberHazeGlassStyle(shape), interactionSource = interactionSource)
            }

            GlassKind.Flat -> {
                modifier
                    .clip(shape)
                    .background(TvTheme.colors.background.copy(alpha = FLAT_SCRIM_ALPHA))
                    .border(1.dp, FlatRim, shape)
            }
        }
    Box(modifier = surface, content = content)
}
