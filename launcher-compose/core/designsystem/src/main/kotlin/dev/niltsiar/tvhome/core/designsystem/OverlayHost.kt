package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest

private val ScrimColor = Color(0xFF07080C)
private const val SCRIM_ALPHA = 0.5f
private const val SCRIM_IN_MS = 160
private const val SCRIM_OUT_MS = 120
private val SpotRingWidth = 2.dp
private const val SPOT_RING_ALPHA = 0.7f

/**
 * Where a screen's overlays go: it draws over every other child of its parent, whatever their order, and under the
 * overlays it draws a scrim that fades in while [open] is true, darkening by [strength]. A [spotlight] stays undimmed
 * inside a thin ring, so the thing an overlay acts on stays in view. Both are read while [open] and kept while the scrim
 * fades out, except [spotlightOffset], the distance in pixels a [Spotlight.followsOffset] spotlight sits below its
 * rectangle, which is read in every frame so that a spotlight on a moving thing keeps up with it. Put it in the screen's
 * outermost box.
 */
@Composable
public fun OverlayHost(
    open: () -> Boolean,
    modifier: Modifier = Modifier,
    strength: () -> Float = { SCRIM_ALPHA },
    spotlight: () -> Spotlight? = { null },
    spotlightOffset: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val scrim = remember { Animatable(0f) }
    val look = remember { ScrimLook() }
    LaunchedEffect(motion) {
        snapshotFlow(open).collectLatest { shown ->
            scrim.animateTo(if (shown) 1f else 0f, motion.tweenSpec(if (shown) SCRIM_IN_MS else SCRIM_OUT_MS))
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(1f)
                .drawBehind {
                    val shown = scrim.value
                    if (shown <= 0f) return@drawBehind
                    if (open()) {
                        look.strength = strength()
                        look.spot = spotlight()
                    }
                    val spot = look.spot
                    if (spot == null) {
                        drawRect(ScrimColor, alpha = look.strength * shown)
                    } else {
                        val bounds = if (spot.followsOffset) spot.bounds.translate(0f, spotlightOffset()) else spot.bounds
                        drawSpotlit(bounds, spot, look.strength * shown, shown)
                    }
                },
        content = content,
    )
}

/**
 * What an [OverlayHost] leaves undimmed: [bounds] (in the root's coordinates) of [shape], inside a thin ring of [ring] at
 * [ringAlpha] once the scrim is in. A [followsOffset] spotlight is moved by the host's `spotlightOffset`.
 */
@Immutable
public class Spotlight(
    public val bounds: Rect,
    public val shape: Shape = RectangleShape,
    public val ring: Color = Color.White,
    public val ringAlpha: Float = SPOT_RING_ALPHA,
    public val followsOffset: Boolean = true,
)

/** What the scrim draws with, taken while the host is open. */
private class ScrimLook {
    var strength = SCRIM_ALPHA
    var spot: Spotlight? = null
}

/** The scrim at [alpha] around [spot] drawn at [at], which keeps its colours, then a ring hugging its outline at [ringShown]. */
private fun DrawScope.drawSpotlit(
    at: Rect,
    spot: Spotlight,
    alpha: Float,
    ringShown: Float,
) {
    val outline = spot.shape.createOutline(at.size, layoutDirection, this)
    val hole = Path().apply { addOutline(outline) }
    translate(at.left, at.top) {
        clipPath(hole, ClipOp.Difference) {
            translate(-at.left, -at.top) { drawRect(ScrimColor, alpha = alpha) }
        }
        val ring = SpotRingWidth.toPx()
        drawOutline(outline.grownBy(ring / 2f), spot.ring, alpha = spot.ringAlpha * ringShown, style = Stroke(ring))
    }
}
