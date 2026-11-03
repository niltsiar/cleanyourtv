package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private const val FOCUSED_SCALE = 1.1f
private const val FOCUS_ANIMATION_MS = 120

/** A D-pad target: scales up and gets an accent border and glow while focused. [onFocused] fires on gaining focus. */
@Composable
public fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, tween(FOCUS_ANIMATION_MS), label = "focusScale")
    val accent = TvTheme.colors.accent
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.shadow(if (focused) 12.dp else 0.dp, shape, ambientColor = accent, spotColor = accent)
                .border(2.dp, if (focused) accent else Color.Transparent, shape)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}
