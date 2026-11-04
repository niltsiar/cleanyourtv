package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable

/** One row of the tile menu or the Settings menu. */
public enum class MenuRow {
    AddToDock,
    ReplaceInDock,
    Move,
    RemoveFromHome,
    AppInfo,
    NativeSettings,
    EditDock,
    Reorder,
}

/**
 * The open menu. [tileKey] is the tile it belongs to, or null for the Settings button's menu; [rows] are its rows
 * from top to bottom.
 */
@Immutable
public data class HomeMenu(
    val tileKey: String?,
    val rows: List<MenuRow>,
)

/** What the menus and the reorder and edit-dock modes ask of Home. */
@Immutable
public class HomeActions(
    /** A tile asks for its menu: [held] is true for a held OK, false for the MENU key. */
    public val onTileMenu: (key: String, held: Boolean) -> Unit = { _, _ -> },
    public val onSettingsMenu: () -> Unit = {},
    public val onMenuPick: (MenuRow) -> Unit = {},
    /** Back, while a menu or a mode is open. */
    public val onBack: () -> Unit = {},
    public val onToggleReorder: () -> Unit = {},
    public val onReorderMove: (Arrow) -> Unit = {},
    public val onReorderOk: () -> Unit = {},
)

/**
 * The tile menu's rows: "Add to dock" for a grid tile or "Replace in dock" for a dock tile (with dock_row only),
 * "Move", "Remove from Home" with the app library, and "App info" where the TV has a page for it.
 */
internal fun tileMenuRows(
    dockRow: Boolean,
    inDock: Boolean,
    appInfo: Boolean,
    library: Boolean = false,
): List<MenuRow> =
    buildList {
        if (dockRow) add(if (inDock) MenuRow.ReplaceInDock else MenuRow.AddToDock)
        add(MenuRow.Move)
        if (library) add(MenuRow.RemoveFromHome)
        if (appInfo) add(MenuRow.AppInfo)
    }

/** The Settings button's long-press menu: "Native settings" while a vendor settings app is installed, "Edit dock" with dock_row, "Reorder". */
internal fun settingsMenuRows(
    dockRow: Boolean,
    nativeSettings: Boolean,
): List<MenuRow> =
    buildList {
        if (nativeSettings) add(MenuRow.NativeSettings)
        if (dockRow) add(MenuRow.EditDock)
        add(MenuRow.Reorder)
    }
