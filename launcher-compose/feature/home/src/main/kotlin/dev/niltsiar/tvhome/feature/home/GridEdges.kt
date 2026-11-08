package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
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
import androidx.compose.ui.unit.dp

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

/**
 * A row at the top counts as fully visible once it is this far inside the edge. At the bottom a row counts as whole once it
 * ends at the edge. Both are counted in over this much travel, so a row that scrolls past an edge does not make the fade jump.
 */
private const val SETTLE = 24f
private const val MASK_STOPS = 32
private const val SHELF_KEY = "shelf"

/** The least a grid row's tile shows above the bottom fade for the row to draw: a smaller part reads as a rendering fault. */
internal val GridRowMinVisible = 48.dp

/** The [SETTLE] distance on a screen [height] pixels high, in pixels. */
internal fun settleOf(height: Float): Float = SETTLE * height / FRAME_HEIGHT

/**
 * The grid's edges, drawn as a mask in the draw phase. Nothing is drawn above [cutoff] or lower than the fade
 * [FADE_GAP] below the last fully visible row, which runs [BOTTOM_FADE_RUN] and is gone by [BOTTOM_FADE_LIMIT]. At the top
 * a fade of [TOP_FADE_RUN] ends [FADE_GAP] above the first fully visible row. A row is an item of the grid with its [low]
 * shift, the dock included; [cutoff] is the line under which a row at the top counts as fully visible. Each fade is a
 * layer the size of its strip, and the top one is only drawn while an item is in it. With [names], a grid item holds the room for its
 * tile's name under the tile.
 */
internal fun Modifier.gridEdges(
    grid: LazyGridState,
    low: () -> Float,
    cutoff: Dp,
    names: Boolean = false,
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
            var solidTop = Float.POSITIVE_INFINITY
            var partialTop = Float.NEGATIVE_INFINITY
            for (i in info.visibleItemsInfo.indices) {
                val item = info.visibleItemsInfo[i]
                if (item.key == SHELF_KEY) continue
                val itemTop = item.offset.y + shift
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

            val wanted = bottomFade(info, shift, size.height, GridRowMinVisible.toPx(), names)
            val bottomStart = wanted.start
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
 * The bottom fade of the grid whose items [info] lists, [shift] lower than laid out, in a viewport [height] pixels high: it
 * starts [FADE_GAP] under the last row that ends [SETTLE] inside the edge, and moves to the next row's bottom as that row comes
 * to within [SETTLE] of the edge. A row that is not whole but whose tile has [minVisible] above the fade at its lowest place
 * puts it there, so the row draws. A row is whole once its tile ends at the edge: with [names] the room under a grid tile may hang over
 * it, and the fade starts under the tile until the name is within [SETTLE] of coming in.
 */
internal fun bottomFade(
    info: LazyGridLayoutInfo,
    shift: Float,
    height: Float,
    minVisible: Float,
    names: Boolean = false,
): BottomMask {
    val unit = height / FRAME_HEIGHT
    val lowest = height * BOTTOM_FADE_LIMIT / FRAME_HEIGHT - (BOTTOM_FADE_RUN + FADE_GAP) * unit
    var solidBottom = Float.NaN
    var nextBottom = Float.POSITIVE_INFINITY
    var shownRow = false
    for (i in info.visibleItemsInfo.indices) {
        val item = info.visibleItemsInfo[i]
        if (item.key == SHELF_KEY) continue
        val itemTop = item.offset.y + shift
        val itemBottom = itemTop + item.size.height
        val isTile = names && item.index >= GRID_ITEMS_BEFORE && item.key != LIBRARY_FOCUS
        val tileBottom = if (isTile) itemTop + item.size.width / TILE_ASPECT else itemBottom
        if (tileBottom <= height) {
            val nameIn = ((height + SETTLE * unit - itemBottom) / (SETTLE * unit)).coerceIn(0f, 1f)
            val bottom = tileBottom + nameIn * (itemBottom - tileBottom)
            if (solidBottom.isNaN() || bottom > solidBottom) solidBottom = bottom
        } else {
            if (tileBottom < nextBottom) nextBottom = tileBottom
            if (item.key != LIBRARY_FOCUS && itemTop + minVisible <= lowest + FADE_GAP * unit) shownRow = true
        }
    }
    val joined = if (nextBottom.isInfinite()) 1f else ((height + SETTLE * unit - nextBottom) / (SETTLE * unit)).coerceIn(0f, 1f)
    val anchor =
        when {
            solidBottom.isNaN() -> lowest
            nextBottom.isInfinite() -> solidBottom
            else -> solidBottom + joined * (nextBottom - solidBottom)
        }
    val start = minOf((if (shownRow) maxOf(anchor, lowest) else anchor) + FADE_GAP * unit, height)
    return BottomMask(start = start, end = minOf(start + BOTTOM_FADE_RUN * unit, height * BOTTOM_FADE_LIMIT / FRAME_HEIGHT))
}

/** The bottom mask: whole down to [start], then falling to gone at [end]. */
internal data class BottomMask(
    val start: Float,
    val end: Float,
) {
    /** A gradient from [start] to the screen's bottom edge at [height]. */
    fun brush(height: Float): Brush {
        val stops =
            Array(MASK_STOPS + 1) { i ->
                val fraction = i / MASK_STOPS.toFloat()
                val y = start + fraction * (height - start)
                val alpha =
                    if (y <= start) {
                        1f
                    } else if (y >= end) {
                        0f
                    } else {
                        (end - y) / (end - start)
                    }
                fraction to Color.Black.copy(alpha = alpha)
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
