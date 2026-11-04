package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.config.TvConfig

/**
 * The shape of the dock and grid, from the theme: [columns] is the dock's size and the grid's width, [rowsBelowDock]
 * the grid rows that must fit under the dock, [singleRow] one sideways row of every app, and [fillDock] whether grid
 * apps take the dock slots that dock_apps leaves free.
 */
@Immutable
public data class HomeLayout(
    val columns: Int,
    val rowsBelowDock: Int,
    val singleRow: Boolean,
    val fillDock: Boolean,
)

public fun TvConfig.homeLayout(): HomeLayout =
    HomeLayout(columns = columns, rowsBelowDock = rowsBelowDock, singleRow = singleRow, fillDock = fillDock)

private const val MIN_TILE_SHARE = 0.85f
private const val TILE_ASPECT = 16f / 9f

/**
 * The share of the grid's [gridWidth] that its tiles may use so that the dock and [rowsBelowDock] rows below it fit
 * in [availableHeight] (all in dp, [gap] between tiles and as the dock panel's padding). 1 when they fit at full
 * width or [rowsBelowDock] is 0; never under 0.85, so tiles stay a recognisable size and the extra rows scroll.
 */
internal fun tileWidthShare(
    gridWidth: Float,
    availableHeight: Float,
    columns: Int,
    rowsBelowDock: Int,
    gap: Float,
): Float {
    if (rowsBelowDock <= 0 || columns <= 0 || gridWidth <= 0f || availableHeight <= 0f) return 1f
    // The height of the dock panel (a tile and its padding) plus the rows is linear in the grid width; solve for the width.
    val rows = rowsBelowDock
    val tilesWidth = (availableHeight - (rows + 2) * gap) * TILE_ASPECT * columns
    val fitting = (tilesWidth + (columns + 1) * gap + rows * (columns - 1) * gap) / (rows + 1)
    return (fitting / gridWidth).coerceIn(MIN_TILE_SHARE, 1f)
}
