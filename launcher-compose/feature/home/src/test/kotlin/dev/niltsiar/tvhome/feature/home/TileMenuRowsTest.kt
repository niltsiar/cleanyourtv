package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TileMenuRowsTest {
    @Test
    fun a_grid_tile_offers_add_to_dock_then_move_then_app_info() {
        assertEquals(
            listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.AppInfo),
            tileMenuRows(dockRow = true, inDock = false, appInfo = true),
        )
    }

    @Test
    fun a_dock_tile_offers_replace_in_dock_in_the_same_place() {
        assertEquals(
            listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo),
            tileMenuRows(dockRow = true, inDock = true, appInfo = true),
        )
    }

    @Test
    fun without_dock_row_there_is_no_dock_row_in_the_menu() {
        assertEquals(listOf(MenuRow.Move, MenuRow.AppInfo), tileMenuRows(dockRow = false, inDock = true, appInfo = true))
    }

    @Test
    fun a_tv_without_an_app_info_page_gets_no_app_info_row() {
        assertEquals(listOf(MenuRow.AddToDock, MenuRow.Move), tileMenuRows(dockRow = true, inDock = false, appInfo = false))
    }

    @Test
    fun the_app_library_adds_remove_from_home_between_move_and_app_info() {
        assertEquals(
            listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.RemoveFromHome, MenuRow.AppInfo),
            tileMenuRows(dockRow = true, inDock = true, appInfo = true, library = true),
        )
        assertEquals(
            listOf(MenuRow.Move, MenuRow.RemoveFromHome),
            tileMenuRows(dockRow = false, inDock = false, appInfo = false, library = true),
        )
    }

    @Test
    fun the_settings_menu_offers_native_settings_edit_dock_and_reorder_in_that_order() {
        assertEquals(
            listOf(MenuRow.NativeSettings, MenuRow.EditDock, MenuRow.Reorder),
            settingsMenuRows(dockRow = true, nativeSettings = true),
        )
        assertEquals(listOf(MenuRow.EditDock, MenuRow.Reorder), settingsMenuRows(dockRow = true, nativeSettings = false))
        assertEquals(listOf(MenuRow.Reorder), settingsMenuRows(dockRow = false, nativeSettings = false))
    }
}
