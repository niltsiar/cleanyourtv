package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.ScrollCuePillHeight
import dev.niltsiar.tvhome.core.designsystem.drawScrollDownCue

/** The cue that more apps lie below the grid: the reorder arrow's colours, which read on any wallpaper. */
private val MoreCueChevron = ChevronColor
private val MoreCuePill = ArrowDiscColor

/** The least the band under the last shown row has over the cue's pill: a cue that fits only by touching a tile is not drawn. */
private val MoreCueClear = 4.dp

/**
 * Draws the down cue over the grid, in the draw phase, while a row of apps lies below the screen ([rows] in all, [columns] wide):
 * centred in the band between the last row that draws and the screen's bottom edge. [low] is how far the group is lowered and
 * [fold] the animated first row that is hidden: the cue fades in and out with the row it marks, as that row does.
 */
internal fun Modifier.moreCue(
    grid: LazyGridState,
    low: () -> Float,
    fold: () -> Float,
    columns: Int,
    rows: Int,
    names: Boolean,
): Modifier =
    drawWithContent {
        drawContent()
        val room = (ScrollCuePillHeight + MoreCueClear).toPx()
        val cue = moreCuePlace(grid.layoutInfo, low(), fold(), size.height, columns, rows, GridRowMinVisible.toPx(), names, room)
        if (cue != null && cue.alpha > 0f) {
            drawScrollDownCue(
                cue.centre,
                MoreCueChevron.copy(alpha = MoreCueChevron.alpha * cue.alpha),
                MoreCuePill.copy(alpha = MoreCuePill.alpha * cue.alpha),
            )
        }
    }

/** Where the cue's middle is, in pixels from the top, and how opaque it is, from 0 to 1. */
internal data class MoreCuePlace(
    val centre: Float,
    val alpha: Float,
)

/**
 * Where the cue goes in a viewport [height] pixels high, or null for none. Its alpha is how far the last row that draws has
 * faded in by [fold], the way [FocusDim.fitsAbove] fades that row. A row draws when
 * [drawsAbove] says so; the cue shows only when a row that draws is not the last of the [rows], and the band from the bottom of
 * the last row that draws (a row holds the room for its tile's name when [names]) to the screen's edge is at least [room] high.
 */
internal fun moreCuePlace(
    info: LazyGridLayoutInfo,
    low: Float,
    fold: Float,
    height: Float,
    columns: Int,
    rows: Int,
    minVisible: Float,
    names: Boolean,
    room: Float,
): MoreCuePlace? {
    val shift = low - info.viewportStartOffset
    val end = bottomFade(info, shift, height, minVisible, names).end
    var lastRow = -1
    var lastBottom = 0f
    for (i in info.visibleItemsInfo.indices) {
        val item = info.visibleItemsInfo[i]
        val index = item.index - GRID_ITEMS_BEFORE
        if (index < 0 || item.key == LIBRARY_FOCUS) continue
        val top = item.offset.y + shift
        if (!item.drawsAbove(end, top, minVisible)) continue
        val row = index / columns
        val bottom = top + item.size.height
        if (row > lastRow) {
            lastRow = row
            lastBottom = bottom
        } else if (row == lastRow) {
            lastBottom = maxOf(lastBottom, bottom)
        }
    }
    if (lastRow < 0 || lastRow >= rows - 1) return null
    val band = height - lastBottom
    return if (band < room) null else MoreCuePlace(lastBottom + band / 2f, (fold - lastRow).coerceIn(0f, 1f))
}

/** Whether a grid item whose top is at [top] shows [minVisible] of its tile above [end], the end of the bottom fade, or all of it if that is less. */
internal fun LazyGridItemInfo.drawsAbove(
    end: Float,
    top: Float,
    minVisible: Float,
): Boolean = end - top >= minOf(minVisible, size.width / TILE_ASPECT)
