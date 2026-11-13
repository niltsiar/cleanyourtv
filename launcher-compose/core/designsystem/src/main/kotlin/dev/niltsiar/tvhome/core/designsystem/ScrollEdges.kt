package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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

/** The band at an edge of a scrolling panel in which [fadeScrolledEdges] hides the content fully, the first part of [EdgeFade]. */
private val EdgeClear: Dp = 12.dp

/** How far a scrolling panel fades its content out at an edge with more content past it: [EdgeClear], then a 24 dp ramp. */
public val EdgeFade: Dp = 36.dp

/**
 * Fades the content at the top and at the bottom as far as [scroll] has content hidden past that edge. Over [EdgeFade] from
 * the edge, the first [EdgeClear] is hidden and the rest ramps to opaque. Less than [EdgeFade] from an end of the scroll the
 * fade at that edge is weaker in proportion: with a scroll a quarter of [EdgeFade] from its start, the content at the top edge
 * keeps three quarters of its alpha. Put it before the `verticalScroll` it belongs to, so that it fades the viewport.
 */
public fun Modifier.fadeScrolledEdges(scroll: ScrollState): Modifier =
    graphicsLayer {
        val hidden = scroll.value > 0 || scroll.value < scroll.maxValue
        compositingStrategy = if (hidden) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithCache {
        val fade = EdgeFade.toPx()
        val clear = EdgeClear.toPx() / fade
        val mask = Size(size.width, fade)
        // The masks cut the content out (DstOut) as far as they are opaque; the draw's alpha sets how strong a fade is.
        val top = Brush.verticalGradient(0f to Color.Black, clear to Color.Black, 1f to Color.Transparent, endY = fade)
        val bottom = Brush.verticalGradient(0f to Color.Transparent, 1f - clear to Color.Black, 1f to Color.Black, endY = fade)
        onDrawWithContent {
            drawContent()
            val above = (scroll.value / fade).coerceIn(0f, 1f)
            val below = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
            if (above > 0f) drawRect(top, size = mask, alpha = above, blendMode = BlendMode.DstOut)
            if (below > 0f) {
                translate(top = size.height - fade) {
                    drawRect(bottom, size = mask, alpha = below, blendMode = BlendMode.DstOut)
                }
            }
        }
    }

/**
 * [content] in which a focused child scrolls at least [clear] clear of the edge it moves towards, [EdgeFade] by default so
 * that focus stays out of the whole fade of [fadeScrolledEdges], its hidden band and its ramp. A scroll that cannot go further
 * leaves it nearer, and so does a viewport less than the child's height plus twice [clear]: the child then sits in the middle.
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
