package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val FOCUSED_SCALE = 1.1f
private val RingWidth = 2.dp
private val RingGap = 3.dp
private val GlowElevation = 12.dp
private val Gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))

/**
 * A D-pad target: scales up and gets a lit top, an accent ring and an accent glow while focused; how it moves is the
 * theme's [Motion] (a small overshoot by default). Only the content is clipped to [shape]; the ring floats outside it
 * and its corners are concentric to the shape's, so a parent must leave the grown surface plus 5dp times [focusedScale] free around it or it clips the ring.
 * [onFocused] fires on gaining focus. [interactionSource] receives the press and focus events, for content
 * that reacts to them. [accent] colours the ring and the glow; unspecified takes the theme's. [glow] turns the accent
 * glow off: the elevation shadow also shows through a translucent surface, as a dark patch, so glass has none. The
 * animation starts in the focus callback and is read in the layer and draw lambdas, so a focus change neither recomposes
 * nor relays out. [focusedScale] is the size it grows to; a surface that fills its parent grows less than a tile.
 */
@Composable
public fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    glow: Boolean = true,
    accent: Color = Color.Unspecified,
    focusedScale: Float = FOCUSED_SCALE,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(focusedScale)
    val ringColor = accent.takeOrElse { TvTheme.colors.accent }
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val scale = 1f + (grownScale - 1f) * focus.value
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (glow) GlowElevation.toPx() * focus.value.coerceIn(0f, 1f) else 0f
                    this.shape = shape
                    ambientShadowColor = ringColor
                    spotShadowColor = ringColor
                }.drawWithContent {
                    drawContent()
                    val progress = focus.value.coerceIn(0f, 1f)
                    if (progress > 0f) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), Gloss, alpha = progress)
                        val ring = RingWidth.toPx()
                        val ringOutline = shape.createOutline(size, layoutDirection, this).grownBy(RingGap.toPx() + ring / 2f)
                        drawOutline(ringOutline, ringColor, alpha = progress, style = Stroke(ring))
                    }
                }.clip(shape)
                .onFocusChanged {
                    val target = if (it.isFocused) 1f else 0f
                    if (focus.targetValue != target) {
                        // Undispatched, its first frame is the one that shows the new focus, not the frame after.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, motion.focusSpec) }
                    }
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}

/**
 * This outline pushed out by [delta] on every side, with each corner radius grown by [delta] too, so the new curve is
 * concentric with the old one. A shape that is not a rounded rectangle grows as its bounds, with corners of [delta].
 */
internal fun Outline.grownBy(delta: Float): Outline =
    when (this) {
        is Outline.Rounded -> {
            val r = roundRect
            Outline.Rounded(
                RoundRect(
                    left = r.left - delta,
                    top = r.top - delta,
                    right = r.right + delta,
                    bottom = r.bottom + delta,
                    topLeftCornerRadius = r.topLeftCornerRadius.grownBy(delta),
                    topRightCornerRadius = r.topRightCornerRadius.grownBy(delta),
                    bottomRightCornerRadius = r.bottomRightCornerRadius.grownBy(delta),
                    bottomLeftCornerRadius = r.bottomLeftCornerRadius.grownBy(delta),
                ),
            )
        }

        else -> {
            Outline.Rounded(RoundRect(bounds.inflate(delta), CornerRadius(delta)))
        }
    }

private fun CornerRadius.grownBy(delta: Float): CornerRadius = CornerRadius(x + delta, y + delta)
