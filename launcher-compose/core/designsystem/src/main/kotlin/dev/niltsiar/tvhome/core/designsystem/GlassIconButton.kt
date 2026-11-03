package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@Composable
public fun GlassIconButton(
    glass: GlassState,
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(onClick = onClick, modifier = modifier.size(40.dp), shape = CircleShape) {
        GlassSurface(glass = glass, modifier = Modifier.matchParentSize(), shape = CircleShape) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
                colorFilter = ColorFilter.tint(TvTheme.colors.onSurface),
            )
        }
    }
}
