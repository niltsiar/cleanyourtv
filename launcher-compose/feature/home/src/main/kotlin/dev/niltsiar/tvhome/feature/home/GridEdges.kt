package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp

/**
 * The fades are specified on a screen 1080 pixels high; every distance below is in those pixels and is scaled by the
 * real height over [FRAME_HEIGHT].
 */
private const val FRAME_HEIGHT = 1080f

/** The fades keep this far from the rows they leave whole, below the last fully visible row and above the first. */
private const val FADE_GAP = 16f
private const val BOTTOM_FADE_RUN = 72f
private const val BOTTOM_FADE_LIMIT = 1060f
private const val TOP_FADE_RUN = 120f

/** The row under the last fully visible one shows at most this much of its top, and at most [PEEK_ALPHA]. */
private const val PEEK_MAX = 40f
private const val PEEK_ALPHA = 0.5f

/**
 * A row counts as fully visible once it is this far inside the edge, and is counted in over this much travel, so a row
 * that scrolls past an edge does not make the fade jump.
 */
private const val SETTLE = 24f
private const val MASK_STOPS = 32
private const val SHELF_KEY = "shelf"

/**
 * The grid's edges, drawn as a mask in the draw phase. Nothing is drawn above [cutoff] or lower than the fade
 * [FADE_GAP] below the last fully visible row, which runs [BOTTOM_FADE_RUN] and is gone by [BOTTOM_FADE_LIMIT]; in its
 * first [PEEK_MAX] the next row shows at no more than [PEEK_ALPHA]. At the top a fade of [TOP_FADE_RUN] ends [FADE_GAP]
 * above the first fully visible row. A row is an item of the grid with its [low] shift, the dock included; [cutoff] is the
 * line under which a row at the top counts as fully visible. Each fade is a layer the size of its strip, and the top one
 * is only drawn while an item is in it.
 */
internal fun Modifier.gridEdges(
    grid: LazyGridState,
    low: () -> Float,
    cutoff: Dp,
): Modifier =
    drawWithCache {
        val top = cutoff.toPx()
        val layerPaint = Paint()
        var cached: Pair<BottomMask, Brush>? = null
        onDrawWithContent {
            val unit = size.height / FRAME_HEIGHT
            val settle = SETTLE * unit
            val info = grid.layoutInfo
            val shift = low() - info.viewportStartOffset
            var solidBottom = Float.NaN
            var nextBottom = Float.POSITIVE_INFINITY
            var nextTop = Float.POSITIVE_INFINITY
            var solidTop = Float.POSITIVE_INFINITY
            var partialTop = Float.NEGATIVE_INFINITY
            for (i in info.visibleItemsInfo.indices) {
                val item = info.visibleItemsInfo[i]
                if (item.key == SHELF_KEY) continue
                val itemTop = item.offset.y + shift
                val itemBottom = itemTop + item.size.height
                if (itemBottom <= size.height - settle) {
                    if (solidBottom.isNaN() || itemBottom > solidBottom) solidBottom = itemBottom
                } else if (itemBottom < nextBottom) {
                    nextBottom = itemBottom
                    nextTop = itemTop
                }
                if (itemTop >= top + settle) solidTop = minOf(solidTop, itemTop) else partialTop = maxOf(partialTop, itemTop)
            }

            val topAnchor =
                when {
                    solidTop.isInfinite() -> top
                    partialTop > top -> solidTop + ((partialTop - top) / settle).coerceIn(0f, 1f) * (partialTop - solidTop)
                    else -> solidTop
                }
            val topEnd = topAnchor - FADE_GAP * unit
            val topStart = topEnd - TOP_FADE_RUN * unit
            var topHasContent = false
            for (i in info.visibleItemsInfo.indices) {
                val item = info.visibleItemsInfo[i]
                val itemTop = item.offset.y + shift
                if (item.key != SHELF_KEY && itemTop < topEnd && itemTop + item.size.height > topStart) topHasContent = true
            }

            val joined = if (nextBottom.isInfinite()) 1f else ((size.height - nextBottom) / settle).coerceIn(0f, 1f)
            val bottomAnchor =
                when {
                    solidBottom.isNaN() -> size.height * BOTTOM_FADE_LIMIT / FRAME_HEIGHT - (BOTTOM_FADE_RUN + FADE_GAP) * unit
                    nextBottom.isInfinite() -> solidBottom
                    else -> solidBottom + joined * (nextBottom - solidBottom)
                }
            val bottomStart = minOf(bottomAnchor + FADE_GAP * unit, size.height)
            val peekTop = if (nextTop < size.height) nextTop else Float.POSITIVE_INFINITY
            val wanted =
                BottomMask(
                    start = bottomStart,
                    end = minOf(bottomStart + BOTTOM_FADE_RUN * unit, size.height * BOTTOM_FADE_LIMIT / FRAME_HEIGHT),
                    peekTop = peekTop,
                    peekEnd = if (peekTop.isInfinite()) 0f else peekTop + minOf(PEEK_MAX * unit, size.height - peekTop),
                    joined = joined,
                )
            val bottomMask =
                cached?.takeIf { it.first == wanted }?.second
                    ?: wanted.brush(size.height).also { cached = wanted to it }

            if (topHasContent) {
                drawMasked(
                    topStart,
                    topEnd,
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = topStart, endY = topEnd),
                    layerPaint,
                )
            }
            clipRect(top = topEnd, bottom = bottomStart) { this@onDrawWithContent.drawContent() }
            drawMasked(bottomStart, size.height, bottomMask, layerPaint)
        }
    }

/**
 * The bottom mask: whole down to [start], gone at [end], and from [peekTop] to [peekEnd] no more than [PEEK_ALPHA] falling
 * to 0, the row there counted [joined] (0 to 1) as whole already.
 */
private data class BottomMask(
    val start: Float,
    val end: Float,
    val peekTop: Float,
    val peekEnd: Float,
    val joined: Float,
) {
    private fun alphaAt(y: Float): Float {
        val fade =
            if (y <= start) {
                1f
            } else if (y >= end) {
                0f
            } else {
                (end - y) / (end - start)
            }
        val peek = if (y < peekTop) 1f else joined + (1f - joined) * PEEK_ALPHA * ((peekEnd - y) / (peekEnd - peekTop)).coerceIn(0f, 1f)
        return minOf(fade, peek)
    }

    /** A gradient from [start] to the screen's bottom edge at [height], sampled, because the mask is the lower of two ramps. */
    fun brush(height: Float): Brush {
        val stops =
            Array(MASK_STOPS + 1) { i ->
                val fraction = i / MASK_STOPS.toFloat()
                fraction to Color.Black.copy(alpha = alphaAt(start + fraction * (height - start)))
            }
        return Brush.verticalGradient(colorStops = stops, startY = start, endY = height)
    }
}

/** The content between [top] and [bottom], its alpha multiplied by [mask]'s in a layer of that strip only. */
private fun ContentDrawScope.drawMasked(
    top: Float,
    bottom: Float,
    mask: Brush,
    paint: Paint,
) {
    if (bottom <= top) return
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, top, size.width, bottom), paint)
    clipRect(top = top, bottom = bottom) { this@drawMasked.drawContent() }
    drawRect(mask, topLeft = Offset(0f, top), size = Size(size.width, bottom - top), blendMode = BlendMode.DstIn)
    canvas.restore()
}
