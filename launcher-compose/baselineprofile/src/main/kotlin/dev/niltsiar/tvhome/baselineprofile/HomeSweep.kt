package dev.niltsiar.tvhome.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "dev.niltsiar.tvhome"

private const val HOME_READY_TIMEOUT_MS = 10_000L
private const val IMAGES_SETTLE_MS = 2_000L
private const val DOCK_STEPS = 5
private const val GRID_ROWS = 4
private const val GRID_COLUMNS_SWEPT = 3

/** Launches Home and waits until the dock is on screen. */
internal fun MacrobenchmarkScope.launchHome() {
    pressHome()
    startActivityAndWait()
    check(device.wait(Until.hasObject(By.res("dock")), HOME_READY_TIMEOUT_MS)) { "Dock never appeared" }
}

/**
 * The scripted journey: right across the dock, down into the grid several rows, right and left,
 * then back up to the dock. Focus starts on the first dock tile.
 */
internal fun MacrobenchmarkScope.dpadSweep() {
    // Tile images arrive in a second emission; let them land so the sweep draws the real tiles.
    Thread.sleep(IMAGES_SETTLE_MS)
    repeat(DOCK_STEPS) { device.pressDPadRight() }
    repeat(GRID_ROWS) { device.pressDPadDown() }
    repeat(GRID_COLUMNS_SWEPT) { device.pressDPadRight() }
    repeat(GRID_COLUMNS_SWEPT) { device.pressDPadLeft() }
    repeat(GRID_ROWS) { device.pressDPadUp() }
    repeat(DOCK_STEPS) { device.pressDPadLeft() }
}
