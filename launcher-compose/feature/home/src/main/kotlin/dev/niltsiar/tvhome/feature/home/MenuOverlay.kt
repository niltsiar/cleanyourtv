package dev.niltsiar.tvhome.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.BesideAnchor
import dev.niltsiar.tvhome.core.designsystem.GlassMenu

@StringRes
private fun MenuRow.label(): Int =
    when (this) {
        MenuRow.AddToDock -> R.string.tile_add_to_dock
        MenuRow.ReplaceInDock -> R.string.tile_replace_in_dock
        MenuRow.Move -> R.string.tile_move
        MenuRow.RemoveFromHome -> R.string.tile_remove_from_home
        MenuRow.AppInfo -> R.string.app_info
        MenuRow.NativeSettings -> R.string.native_settings
        MenuRow.EditDock -> R.string.dock_edit
        MenuRow.Reorder -> R.string.reorder
        MenuRow.PowerMenu -> R.string.power_menu
    }

private val MenuTopLimit = TopBarTop + TopBarHeight + 8.dp
private val MenuClearance = 16.dp
private val TileMenuBottom = 60.dp
private val SettingsRowHeight = 42.dp
private val SettingsPadding = 8.dp

/**
 * The tile menu or the Settings menu: [rows], each tagged `menu:<row name>`, never over the top bar. A [tileMenu] is a
 * whole number of tiles wide and lines up with its tile's column ([anchor] is the tile). A dock tile's menu ([dockTile] is
 * the app's name, which heads it) opens above the tile; a grid tile's sits beside it and under [bandBottom], the bottom
 * of the band over the grid, when it fits there. The Settings menu hangs from the Settings button with shorter rows, so
 * it ends clear of the dock.
 */
@Composable
internal fun MenuOverlay(
    rows: List<MenuRow>,
    anchor: State<Rect>,
    tileMenu: Boolean,
    bandBottom: Dp,
    onPick: (MenuRow) -> Unit,
    modifier: Modifier = Modifier,
    dockTile: String? = null,
) {
    if (tileMenu) {
        GlassMenu(
            rows,
            anchor,
            alignEnd = false,
            label = MenuRow::label,
            rowTag = { "menu:${it.name}" },
            onPick = onPick,
            modifier = modifier,
            topLimit = MenuTopLimit,
            beside =
                if (dockTile != null) {
                    null
                } else {
                    BesideAnchor(
                        top = bandBottom + MenuClearance,
                        fallbackTop = TopBarTop + TopBarHeight + MenuClearance,
                        gap = TileGap,
                        bottomMargin = TileMenuBottom,
                    )
                },
            title = dockTile,
            above = dockTile != null,
            columnGap = TileGap,
            edgeMargin = ScreenMargin,
        )
    } else {
        GlassMenu(
            rows,
            anchor,
            alignEnd = true,
            label = MenuRow::label,
            rowTag = { "menu:${it.name}" },
            onPick = onPick,
            modifier = modifier,
            topLimit = MenuTopLimit,
            rowHeight = SettingsRowHeight,
            padding = SettingsPadding,
        )
    }
}

@StringRes
internal fun ReorderHint.label(): Int =
    when (this) {
        ReorderHint.Reorder -> R.string.reorder_hint
        ReorderHint.DockChoosePlace -> R.string.dock_edit_hint
        ReorderHint.DockChooseApp -> R.string.dock_edit_pick_app
        ReorderHint.DockPlaceForApp -> R.string.dock_edit_pick_place
    }
