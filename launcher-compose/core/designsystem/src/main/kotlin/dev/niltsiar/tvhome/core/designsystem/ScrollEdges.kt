package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How far a scrolling panel fades its content out at an edge with more content past it. */
public val EdgeFade: Dp = 24.dp

/**
 * Fades the content over [EdgeFade] at the top and at the bottom as far as [scroll] has content hidden past that edge. Put it
 * before the `verticalScroll` it belongs to, so that it fades the viewport.
 */
public fun Modifier.fadeScrolledEdges(scroll: ScrollState): Modifier =
    graphicsLayer {
        val hidden = scroll.value > 0 || scroll.value < scroll.maxValue
        compositingStrategy = if (hidden) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val fade = EdgeFade.toPx()
        val above = (scroll.value / fade).coerceIn(0f, 1f)
        val below = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
        val mask = Size(size.width, fade)
        if (above > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - above), Color.Black), 0f, fade),
                size = mask,
                blendMode = BlendMode.DstIn,
            )
        }
        if (below > 0f) {
            translate(top = size.height - fade) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - below)), 0f, fade),
                    size = mask,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

/**
 * [content] in which a focused child scrolls at least [clear] clear of the edge it moves towards, [EdgeFade] by default so
 * that focus stays out of the fade of [fadeScrolledEdges]; a scroll that cannot go further leaves it nearer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun ScrollsClearOfEdge(
    clear: Dp = EdgeFade,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val spec = remember(density, clear) { ClearOfEdge(with(density) { clear.toPx() }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
private class ClearOfEdge(
    private val clear: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val margin = minOf(clear, (containerSize - size).coerceAtLeast(0f) / 2f)
        val before = offset - margin
        val after = offset + size + margin - containerSize
        return when {
            before < 0f -> before
            after > 0f -> after
            else -> 0f
        }
    }
}
