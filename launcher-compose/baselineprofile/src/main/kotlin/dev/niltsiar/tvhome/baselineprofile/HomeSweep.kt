package dev.niltsiar.tvhome.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The package under test. The baseline profile plugin passes the tested variant's application id, so the profile
 * generator gets the package people install and the benchmark gets its ".benchmark" copy.
 */
internal val targetPackage: String by lazy {
    checkNotNull(InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")) {
        "androidx.benchmark.targetPackageName is not set: run this through Gradle, whose baseline profile plugin passes it"
    }
}

/**
 * On-TV path of the script that `scripts/spike-gate.sh` stages to write the owner's theme into the benchmark package; null
 * when the gate gave none. The dock the sweep needs comes from that theme.
 */
private val themeScript: String? by lazy { InstrumentationRegistry.getArguments().getString("tvhome.themeScript") }

private const val THEME_PUSHED = "theme pushed"

/**
 * Writes the theme again. Below API 34 Macrobenchmark resets the compilation by uninstalling and installing the package, once
 * per test and before its first warmup, which deletes the theme. The script stops the app, so the next start reads the theme.
 */
internal fun MacrobenchmarkScope.pushTheme() {
    val script = themeScript ?: return
    val output = device.executeShellCommand("sh $script").trim()
    check(output.endsWith(THEME_PUSHED)) { "The theme did not reach $packageName: $output" }
}

/** The class name does not change with the benchmark build's package suffix, and that build has no launcher intent filter. */
private const val MAIN_ACTIVITY = "dev.niltsiar.tvhome.MainActivity"

private const val HOME_READY_TIMEOUT_MS = 10_000L
private const val FOCUS_TIMEOUT_MS = 2_000L
private const val IMAGES_TIMEOUT_MS = 10_000L
private const val DOCK_STEPS = 5
private const val GRID_ROWS = 4
private const val GRID_COLUMNS_SWEPT = 3
private const val TOP_BAR_STEPS = 2

/** Every app tile, in the dock and in the grid: HomeScreen tags each one `tile:<component key>`. */
private val TILE_TAG = Pattern.compile("tile:.+")

/** An image tile describes itself with the app's label; a tile still waiting for its art shows the label as text only. */
private val ANY_DESCRIPTION = Pattern.compile(".+")

/** BySelector is a mutable builder, so each use needs a new one. */
private fun anyTile() = By.res(TILE_TAG)

private fun focusedTile() = anyTile().focused(true)

/** Launches Home and waits until at least one app tile is on screen. */
internal fun MacrobenchmarkScope.launchHome() {
    pressHome()
    startActivityAndWait(Intent().setComponent(ComponentName(targetPackage, MAIN_ACTIVITY)))
    check(device.wait(Until.hasObject(anyTile()), HOME_READY_TIMEOUT_MS)) { "No app tile appeared" }
}

/**
 * Tile images arrive in a second emission, all together; this waits until a tile shows one, so the sweep draws the
 * real tiles. A tile with art carries a content description, one without shows its label as text only.
 */
internal fun MacrobenchmarkScope.awaitTileImages() {
    val shown =
        device.wait(
            Condition<UiDevice, Boolean> {
                // Home recomposes while images land and can replace a node between the find and the read.
                try {
                    it.findObjects(anyTile()).any { tile -> tile.contentDescription != null || tile.hasObject(By.desc(ANY_DESCRIPTION)) }
                } catch (_: StaleObjectException) {
                    false
                }
            },
            IMAGES_TIMEOUT_MS,
        )
    check(shown == true) { "No tile showed an image within $IMAGES_TIMEOUT_MS ms" }
}

/**
 * The scripted journey: right across the dock, down into the grid, right and left, back up and left, then one
 * burst down with no wait between presses, as a held key sends, and back up, and last up into the top bar's
 * buttons and back down. The key sequence is fixed, so every run does the same work; the two checks prove that
 * focus started on the first dock tile and reached the grid.
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

    device.pressDPadUp()
    repeat(TOP_BAR_STEPS) { device.pressDPadRight() }
    device.pressDPadDown()
}

private fun MacrobenchmarkScope.awaitFocusedTile(
    description: String,
    accept: (String) -> Boolean,
) {
    // Accessibility queries run on the app's main thread, so wait until the focus and scroll animations end.
    device.waitForIdle()
    val found =
        device.wait(
            Condition<UiDevice, String?> {
                // Home recomposes while images land and can replace the node between the find and the read.
                try {
                    it.findObject(focusedTile())?.resourceName?.takeIf(accept)
                } catch (_: StaleObjectException) {
                    null
                }
            },
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
