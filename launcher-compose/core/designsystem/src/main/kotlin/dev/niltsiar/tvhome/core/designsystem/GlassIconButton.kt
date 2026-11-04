package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/** [focusedRotation] turns the icon by that many degrees, with a spring, while the button has focus. */
@Composable
public fun GlassIconButton(
    glass: GlassState,
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusedRotation: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val rotation =
        animateFloatAsState(
            if (focused) focusedRotation else 0f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "iconRotation",
        )
    FocusSurface(onClick = onClick, modifier = modifier.size(40.dp), shape = CircleShape, interactionSource = interactionSource) {
        GlassSurface(glass = glass, modifier = Modifier.matchParentSize(), shape = CircleShape, interactionSource = interactionSource) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center).size(22.dp).graphicsLayer { rotationZ = rotation.value },
                colorFilter = ColorFilter.tint(TvTheme.colors.onSurface),
            )
        }
    }
}
