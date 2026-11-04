package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private const val FOCUSED_SCALE = 1.1f
private val FocusSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
private val RingWidth = 2.dp
private val GlowElevation = 12.dp
private val Gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))

/**
 * A D-pad target: scales up with a small overshoot and gets a lit top, an accent ring and an accent glow while
 * focused. [onFocused] fires on gaining focus. [interactionSource] receives the press and focus events, for content
 * that reacts to them. [glow] turns the accent glow off: the elevation shadow also shows through a translucent
 * surface, as a dark patch, so glass has none. The animation is read in the layer and draw lambdas, so a focus
 * change never relays out.
 */
@Composable
public fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "focus")
    val accent = TvTheme.colors.accent
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val scale = 1f + (FOCUSED_SCALE - 1f) * focus.value
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (glow) GlowElevation.toPx() * focus.value.coerceIn(0f, 1f) else 0f
                    this.shape = shape
                    clip = true
                    ambientShadowColor = accent
                    spotShadowColor = accent
                }.drawWithContent {
                    drawContent()
                    val progress = focus.value.coerceIn(0f, 1f)
                    if (progress > 0f) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), Gloss, alpha = progress)
                        val ring = RingWidth.toPx()
                        inset(ring / 2f) {
                            drawOutline(shape.createOutline(size, layoutDirection, this), accent, alpha = progress, style = Stroke(ring))
                        }
                    }
                }.onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}
