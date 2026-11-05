package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 * overlays it draws a scrim that fades in while [open] is true, darkening by [strength]. A [spotlight] (in the root's
 * coordinates, of [spotlightShape]) stays undimmed inside a thin ring of [spotlightRing], so the thing an overlay acts on
 * stays in view. All of them are read while [open] and kept while the scrim fades out, except [spotlightOffset], the
 * distance in pixels the spotlight sits below its rectangle, which is read in every frame so that a spotlight on a moving
 * thing keeps up with it. Put it in the screen's outermost box.
 */
@Composable
public fun OverlayHost(
    open: () -> Boolean,
    modifier: Modifier = Modifier,
    strength: () -> Float = { SCRIM_ALPHA },
    spotlight: () -> Rect? = { null },
    spotlightOffset: () -> Float = { 0f },
    spotlightShape: Shape = RectangleShape,
    spotlightRing: () -> Color = { Color.White },
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
                        look.ring = spotlightRing()
                    }
                    val spot = look.spot?.translate(0f, spotlightOffset())
                    if (spot == null) {
                        drawRect(ScrimColor, alpha = look.strength * shown)
                    } else {
                        drawSpotlit(spot, spotlightShape, look.ring, look.strength * shown, shown)
                    }
                },
        content = content,
    )
}

/** What the scrim draws with, taken while the host is open. */
private class ScrimLook {
    var strength = SCRIM_ALPHA
    var spot: Rect? = null
    var ring = Color.White
}

/** The scrim at [alpha] around [spot], which keeps its colours, then a ring of [color] hugging [spot]'s outline at [ringShown]. */
private fun DrawScope.drawSpotlit(
    spot: Rect,
    shape: Shape,
    color: Color,
    alpha: Float,
    ringShown: Float,
) {
    val outline = shape.createOutline(spot.size, layoutDirection, this)
    val hole = Path().apply { addOutline(outline) }
    translate(spot.left, spot.top) {
        clipPath(hole, ClipOp.Difference) {
            translate(-spot.left, -spot.top) { drawRect(ScrimColor, alpha = alpha) }
        }
        val ring = SpotRingWidth.toPx()
        drawOutline(outline.grownBy(ring / 2f), color, alpha = SPOT_RING_ALPHA * ringShown, style = Stroke(ring))
    }
}
