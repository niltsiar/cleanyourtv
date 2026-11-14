package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val TileMenuRowsTest by testSuite {
    test("a grid tile offers add to dock then move then app info") {
        tileMenuRows(dockRow = true, inDock = false, appInfo = true) shouldBe listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.AppInfo)
    }

    test("a dock tile offers replace in dock in the same place") {
        tileMenuRows(dockRow = true, inDock = true, appInfo = true) shouldBe listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo)
    }

    test("without dock row there is no dock row in the menu") {
        tileMenuRows(dockRow = false, inDock = true, appInfo = true) shouldBe listOf(MenuRow.Move, MenuRow.AppInfo)
    }

    test("a tv without an app info page gets no app info row") {
        tileMenuRows(dockRow = true, inDock = false, appInfo = false) shouldBe listOf(MenuRow.AddToDock, MenuRow.Move)
    }

    test("the app library adds remove from home between move and app info") {
        tileMenuRows(dockRow = true, inDock = true, appInfo = true, library = true) shouldBe
            listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.RemoveFromHome, MenuRow.AppInfo)
        tileMenuRows(dockRow = false, inDock = false, appInfo = false, library = true) shouldBe listOf(MenuRow.Move, MenuRow.RemoveFromHome)
    }

    test("the settings menu offers launcher settings first then native settings edit dock and reorder") {
        settingsMenuRows(dockRow = true, nativeSettings = true) shouldBe
            listOf(MenuRow.LauncherSettings, MenuRow.NativeSettings, MenuRow.EditDock, MenuRow.Reorder)
        settingsMenuRows(dockRow = true, nativeSettings = false) shouldBe
            listOf(MenuRow.LauncherSettings, MenuRow.EditDock, MenuRow.Reorder)
        settingsMenuRows(dockRow = false, nativeSettings = false) shouldBe listOf(MenuRow.LauncherSettings, MenuRow.Reorder)
    }

    test("restart comes last and only while the key service is bound") {
        settingsMenuRows(dockRow = true, nativeSettings = false, powerMenu = true) shouldBe
            listOf(MenuRow.LauncherSettings, MenuRow.EditDock, MenuRow.Reorder, MenuRow.PowerMenu)
        settingsMenuRows(dockRow = false, nativeSettings = false, powerMenu = false) shouldBe
            listOf(MenuRow.LauncherSettings, MenuRow.Reorder)
    }
}
