package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.showLibraryPill

/**
 * The shape of the dock and grid, from the theme: [columns] is the dock's size and the grid's width, [rowsBelowDock]
 * the grid rows that must fit under the dock, [singleRow] one sideways row of every app, and [fillDock] whether grid
 * apps take the dock slots that dock_apps leaves free, [tileMenu] whether a held OK or MENU opens a tile's menu (else a held
 * OK opens its App info), [libraryPill] whether the App library pill follows the last row, and [showNames] whether every
 * tile has its name under it (always_show_names), which the dock panel and each grid row grow to hold, and
 * [inputArrival] whether the Inputs button offers a device that was just switched on (input_arrival), and [habitMark]
 * whether the app usually opened at this time of day has a mark under its tile (habit_hint), and [unusedFade] whether
 * grid tiles of apps not opened for a while are drawn faded (unused_fade_days), and [dockTitle] whether the band above the dock
 * names the focused tile (dock_title); its reorder, dock-edit, library and top-bar lines show either way.
 */
@Immutable
public data class HomeLayout(
    val columns: Int,
    val rowsBelowDock: Int,
    val singleRow: Boolean,
    val fillDock: Boolean,
    val tileMenu: Boolean = true,
    val libraryPill: Boolean = false,
    val showNames: Boolean = false,
    val inputArrival: Boolean = false,
    val habitMark: Boolean = false,
    val unusedFade: Boolean = false,
    val dockTitle: Boolean = true,
)

public fun TvConfig.homeLayout(): HomeLayout =
    HomeLayout(
        columns = columns,
        rowsBelowDock = rowsBelowDock,
        singleRow = singleRow,
        fillDock = fillDock,
        tileMenu = tileMenu,
        libraryPill = showLibraryPill(appLibrary, libraryButton, showLibraryPill),
        showNames = alwaysShowNames,
        inputArrival = inputArrival && showInputsPill,
        habitMark = habitHint,
        unusedFade = unusedFadeDays > 0,
        dockTitle = dockTitle,
    )

private const val MIN_TILE_SHARE = 0.85f
internal const val TILE_ASPECT = 16f / 9f

/**
 * The share of the grid's [gridWidth] that its tiles may use so that the dock and [rowsBelowDock] rows below it fit
 * in [availableHeight] (all in dp, [gap] between tiles, [dockPadding] above and below the dock's tiles). 1 when they fit at full
 * width or [rowsBelowDock] is 0; never under 0.85, so tiles stay a recognisable size and the extra rows scroll.
 */
internal fun tileWidthShare(
    gridWidth: Float,
    availableHeight: Float,
    columns: Int,
    rowsBelowDock: Int,
    gap: Float,
    dockPadding: Float = gap,
): Float {
    if (rowsBelowDock <= 0 || columns <= 0 || gridWidth <= 0f || availableHeight <= 0f) return 1f
    // The height of the dock panel (a tile and its padding) plus the rows is linear in the grid width; solve for the width.
    val rows = rowsBelowDock
    val tilesWidth = (availableHeight - 2 * dockPadding - rows * gap) * TILE_ASPECT * columns
    val fitting = (tilesWidth + (columns + 1) * gap + rows * (columns - 1) * gap) / (rows + 1)
    return (fitting / gridWidth).coerceIn(MIN_TILE_SHARE, 1f)
}

/** How far the band and the dock sit lower in dock focus than in the grid layout, in dp. */
internal const val DOCK_LOWERING = 120f

/**
 * How much of [DOCK_LOWERING] dock focus uses, in dp: all of it for the peek of the first row ([rowsBelowDock] 0), and
 * with [rowsBelowDock] full rows to show (at most the [gridRows] there are) only what is left of the screen under the
 * last of them and [bottomMargin]. [shelfBottom] is where the dock panel starts; the dock panel is a tile and a [dockPadding]
 * above and below it, and a row is a [gap] and a tile; the dock panel and each row also hold [nameRoom] for a tile's name.
 */
internal fun dockLowering(
    windowHeight: Float,
    shelfBottom: Float,
    tileHeight: Float,
    gap: Float,
    dockPadding: Float = gap,
    gridRows: Int,
    rowsBelowDock: Int,
    bottomMargin: Float = 48f,
    nameRoom: Float = 0f,
): Float {
    val rows = minOf(rowsBelowDock, gridRows).coerceAtLeast(0)
    val lastBottom = shelfBottom + tileHeight + nameRoom + 2 * dockPadding + rows * (gap + tileHeight + nameRoom)
    return (windowHeight - bottomMargin - lastBottom).coerceIn(0f, DOCK_LOWERING)
}

/**
 * The grid tile that Down must reach from the tile at [row] and [column] (row -1 is the dock) when the cell straight
 * below does not exist, so the library pill under the last row does not capture the key; null when a tile is below or
 * the tile is in the last row. [gridCount] tiles fill the grid [columns] wide.
 */
internal fun gridDownOverride(
    row: Int,
    column: Int,
    gridCount: Int,
    columns: Int,
): Int? {
    if (gridCount <= 0 || columns <= 0) return null
    val below = (row + 1) * columns + column
    val lastRow = (gridCount - 1) / columns
    return if (row + 1 <= lastRow && below > gridCount - 1) gridCount - 1 else null
}

/**
 * The key of the tile Up from the library pill reaches: in the last row of the grid (the dock when the grid is empty),
 * under the column of the tile that had focus last, or the last-focused tile itself in a single row. Null with no tile.
 */
internal fun pillUpKey(
    dock: List<AppEntry>,
    grid: List<AppEntry>,
    columns: Int,
    singleRow: Boolean,
    focusedKey: String?,
): String? {
    if (singleRow) return dock.firstOrNull { it.key == focusedKey }?.key ?: dock.lastOrNull()?.key
    val inGrid = grid.indexOfFirst { it.key == focusedKey }
    val column = if (inGrid >= 0) inGrid % columns else dock.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
    if (grid.isEmpty()) return dock.getOrNull(column.coerceAtMost(dock.lastIndex))?.key
    val lastRowStart = (grid.lastIndex / columns) * columns
    return grid[minOf(lastRowStart + column, grid.lastIndex)].key
}
