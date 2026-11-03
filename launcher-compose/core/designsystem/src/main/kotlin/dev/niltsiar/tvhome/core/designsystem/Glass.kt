package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * What glass surfaces blur: the content marked with [glassSource] that sits behind them.
 * The blur is Haze's RenderEffect path, real from API 31; below that Haze draws a scrim.
 */
@Stable
public class GlassState internal constructor(
    internal val haze: HazeState,
) {
    internal val input: HazeInput = HazeInput.Sources(haze)
}

@Composable
public fun rememberGlassState(): GlassState {
    val haze = rememberHazeState()
    return remember(haze) { GlassState(haze) }
}

/** Marks this content as something glass blurs. A surface only blurs sources with a lower [zIndex]. */
public fun Modifier.glassSource(
    glass: GlassState,
    zIndex: Float = 0f,
): Modifier = hazeSource(glass.haze, zIndex)

private val GlassBlurStyle =
    HazeBlurStyle {
        blurRadius(20.dp)
        noiseFactor(0.04f)
        colorEffects(listOf(HazeColorEffect.tint(Color(0xFF0B0D12).copy(alpha = 0.38f))))
    }

private val GlassRim =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))

private val GlassPanelShape: Shape = RoundedCornerShape(20.dp)

@Composable
public fun GlassSurface(
    glass: GlassState,
    modifier: Modifier = Modifier,
    shape: Shape = GlassPanelShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .hazeBlur(input = glass.input, style = GlassBlurStyle)
                .border(1.dp, GlassRim, shape),
        content = content,
    )
}
