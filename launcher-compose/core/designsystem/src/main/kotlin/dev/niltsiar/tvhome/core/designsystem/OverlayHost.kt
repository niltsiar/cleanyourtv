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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest

private val ScrimColor = Color(0xFF07080C)
private const val SCRIM_ALPHA = 0.5f
private const val SCRIM_IN_MS = 160
private const val SCRIM_OUT_MS = 120

/**
 * Where a screen's overlays go: it draws over every other child of its parent, whatever their order, and under the
 * overlays it draws a scrim that fades in while [open] is true. Put it in the screen's outermost box.
 */
@Composable
public fun OverlayHost(
    open: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val scrim = remember { Animatable(0f) }
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
                    if (shown > 0f) drawRect(ScrimColor, alpha = SCRIM_ALPHA * shown)
                },
        content = content,
    )
}
