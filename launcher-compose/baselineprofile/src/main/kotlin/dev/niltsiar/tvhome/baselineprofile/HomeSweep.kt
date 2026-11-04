package dev.niltsiar.tvhome.baselineprofile

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "dev.niltsiar.tvhome"

private const val HOME_READY_TIMEOUT_MS = 10_000L
private const val FOCUS_TIMEOUT_MS = 2_000L
private const val IMAGES_SETTLE_MS = 2_000L
private const val DOCK_STEPS = 5
private const val GRID_ROWS = 4
private const val GRID_COLUMNS_SWEPT = 3

/** Every app tile, in the dock and in the grid: HomeScreen tags each one `tile:<packageName>`. */
private val TILE_TAG = Pattern.compile("tile:.+")

/** BySelector is a mutable builder, so each use needs a new one. */
private fun anyTile() = By.res(TILE_TAG)

private fun focusedTile() = anyTile().focused(true)

/** Launches Home and waits until at least one app tile is on screen. */
internal fun MacrobenchmarkScope.launchHome() {
    pressHome()
    startActivityAndWait()
    check(device.wait(Until.hasObject(anyTile()), HOME_READY_TIMEOUT_MS)) { "No app tile appeared" }
}

/** Tile images arrive in a second emission; this lets them land so the sweep draws the real tiles. */
internal fun awaitTileImages() = Thread.sleep(IMAGES_SETTLE_MS)

/**
 * The scripted journey: right across the dock, down into the grid, right and left, back up and left, then one
 * burst down with no wait between presses, as a held key sends, and back up. The key sequence is fixed, so both
 * hosts do the same work; the two checks prove that focus started on the first dock tile and reached the grid.
 */
internal fun MacrobenchmarkScope.dpadSweep() {
    val dockTiles = checkNotNull(device.findObject(By.res("dock"))) { "Dock not on screen" }.findObjects(anyTile())
    check(dockTiles.isNotEmpty()) { "Dock has no tiles" }
    val dockTags = dockTiles.map { it.resourceName }.toSet()
    val firstDockTile = dockTiles.minBy { it.visibleBounds.left }.resourceName
    awaitFocusedTile("the first dock tile at the start") { it == firstDockTile }

    repeat(DOCK_STEPS) { device.pressDPadRight() }
    repeat(GRID_ROWS) { device.pressDPadDown() }
    awaitFocusedTile("a grid tile after the descent") { it !in dockTags }
    repeat(GRID_COLUMNS_SWEPT) { device.pressDPadRight() }
    repeat(GRID_COLUMNS_SWEPT) { device.pressDPadLeft() }
    repeat(GRID_ROWS) { device.pressDPadUp() }
    repeat(DOCK_STEPS) { device.pressDPadLeft() }

    pressWithoutWaiting(KeyEvent.KEYCODE_DPAD_DOWN, GRID_ROWS)
    repeat(GRID_ROWS) { device.pressDPadUp() }
}

private fun MacrobenchmarkScope.awaitFocusedTile(
    description: String,
    accept: (String) -> Boolean,
) {
    // Accessibility queries run on the app's main thread, so wait until the focus and scroll animations end.
    device.waitForIdle()
    val found =
        device.wait(
            Condition<UiDevice, String?> { it.findObject(focusedTile())?.resourceName?.takeIf(accept) },
            FOCUS_TIMEOUT_MS,
        )
    checkNotNull(found) {
        "Focus is not on $description; focused tile: ${device.findObject(focusedTile())?.resourceName}"
    }
}

/** UiDevice.pressDPad* waits for idle before each key, and pressKeyCodes sends one chord, so neither makes a burst. */
private fun pressWithoutWaiting(
    keyCode: Int,
    times: Int,
) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    repeat(times) {
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event =
                KeyEvent(
                    downTime,
                    SystemClock.uptimeMillis(),
                    action,
                    keyCode,
                    0,
                    0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                )
            check(automation.injectInputEvent(event, true)) { "Could not inject key $keyCode" }
        }
    }
}
