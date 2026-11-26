package dev.niltsiar.tvhome.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.ViewConfiguration
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
private const val PAGE_TIMEOUT_MS = 5_000L

/** Three seconds of repeats at the usual 50 ms repeat delay, enough to take focus through a library of several dozen rows. */
private const val LIBRARY_HOLD_REPEATS = 60

/** More stops than the Settings page lists, every optional row shown. */
private const val SETTINGS_STOPS_MAX = 60

/** Longer than a look flip takes: it waits up to LookFlip.ARRIVAL_MS (1 s) for the new look, then runs its spec. */
private const val FLIP_SETTLE_MS = 2_000L

/** MainActivity opens the App library on this action, which the "library" word of app_keys sends. */
private const val APP_LIBRARY_ACTION = "dev.niltsiar.tvhome.action.APP_LIBRARY"

/** Every app tile, in the dock and in the grid: HomeScreen tags each one `tile:<component key>`. */
private val TILE_TAG = Pattern.compile("tile:.+")

/** An image tile describes itself with the app's label; a tile still waiting for its art shows the label as text only. */
private val ANY_DESCRIPTION = Pattern.compile(".+")

/** A card of the App library: LibraryCardView tags each one `card:<component key>`, and its parts carry a second colon. */
private val CARD_TAG = Pattern.compile("card:[^:]+")

/** An option of the Settings page's Look row: SettingsScreen tags each one `option:Look:<index>`, Glass first. */
private val LOOK_OPTION_TAG = Pattern.compile("option:Look:\\d+")

/** The Settings page's Follow theme row, checked while no choice is stored: OK on it removes every stored choice. */
private val FOLLOW_THEME_TAG = Pattern.compile("settings:follow_theme")

/** BySelector is a mutable builder, so each use needs a new one. */
private fun anyTile() = By.res(TILE_TAG)

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
    awaitFocused(TILE_TAG, "the first dock tile at the start") { it == firstDockTile }

    repeat(DOCK_STEPS) { device.pressDPadRight() }
    repeat(GRID_ROWS) { device.pressDPadDown() }
    awaitFocused(TILE_TAG, "a grid tile after the descent") { it !in dockTags }
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

/** Waits until the focused node tagged to match [tag] passes [accept], and returns its tag. */
private fun MacrobenchmarkScope.awaitFocused(
    tag: Pattern,
    description: String,
    accept: (String) -> Boolean = { true },
): String {
    // Accessibility queries run on the app's main thread, so wait until the focus and scroll animations end.
    device.waitForIdle()
    val found =
        device.wait(
            Condition<UiDevice, String?> {
                // Home and the library recompose while images land and can replace the node between the find and the read.
                try {
                    it.findObject(By.res(tag).focused(true))?.resourceName?.takeIf(accept)
                } catch (_: StaleObjectException) {
                    null
                }
            },
            FOCUS_TIMEOUT_MS,
        )
    return checkNotNull(found) {
        "Focus is not on $description; focused node: ${device.findObject(By.focused(true))?.resourceName}"
    }
}

/** Sends [action] to MainActivity, which is single-task, so a running Home gets it as a new intent. */
private fun MacrobenchmarkScope.sendToHome(action: String) {
    val output = device.executeShellCommand("am start -a $action -n $targetPackage/$MAIN_ACTIVITY")
    check("Error" !in output) { "am start -a $action failed: $output" }
}

/** From Home, opens the App library the way a remote key bound to it does, and waits for focus on its first card. */
internal fun MacrobenchmarkScope.openLibrary() {
    sendToHome(APP_LIBRARY_ACTION)
    check(device.wait(Until.hasObject(By.res("library")), PAGE_TIMEOUT_MS)) {
        "The App library did not open: the theme needs app_library on"
    }
    awaitFocused(CARD_TAG, "a library card after the library opened")
}

/**
 * Holds Down in the open App library, as a held remote key sends it: one press, then repeats at the system's rate, so focus
 * runs down the grid and scrolls it through. Back then closes the library, which forgets the focused card, so the next open
 * starts on the first one again.
 */
internal fun MacrobenchmarkScope.libraryHoldDown() {
    val first = awaitFocused(CARD_TAG, "a library card before the hold")
    holdKey(KeyEvent.KEYCODE_DPAD_DOWN, LIBRARY_HOLD_REPEATS)
    awaitFocused(CARD_TAG, "a card further down after the hold") { it != first }
    device.pressBack()
    check(device.wait(Until.gone(By.res("library")), PAGE_TIMEOUT_MS)) { "Back did not close the App library" }
}

/**
 * Opens the Launcher settings page through the action MainActivity answers, which works whatever the theme puts in the top
 * bar, and moves down until a stop tagged to match [tag] has focus. Which rows come first depends on the config, so it moves
 * one stop at a time. A page already open closes first, so the checks below cannot read it while it goes.
 */
private fun MacrobenchmarkScope.openSettingsAt(tag: Pattern) {
    if (device.hasObject(By.res("settings"))) {
        device.pressBack()
        check(device.wait(Until.gone(By.res("settings")), PAGE_TIMEOUT_MS)) { "Back did not close the Launcher settings page" }
    }
    sendToHome("$targetPackage.action.LAUNCHER_SETTINGS")
    check(device.wait(Until.hasObject(By.res("settings")), PAGE_TIMEOUT_MS)) { "The Launcher settings page did not open" }
    // The page focuses its first row a frame after it shows; a Down before that would land elsewhere.
    check(device.wait(Until.hasObject(By.res("settings").hasDescendant(By.focused(true))), FOCUS_TIMEOUT_MS)) {
        "Nothing on the Launcher settings page has focus"
    }
    repeat(SETTINGS_STOPS_MAX) {
        device.waitForIdle()
        if (device.hasObject(By.res(tag).focused(true))) return
        device.pressDPadDown()
    }
    error("Nothing matching $tag had focus after $SETTINGS_STOPS_MAX steps down the Settings page")
}

/** Opens the Launcher settings page on its Look row, where focus lands on the checked Style. */
internal fun MacrobenchmarkScope.openLookRow() = openSettingsAt(LOOK_OPTION_TAG)

/** Whether no Settings choice is stored, so the config is the theme's alone, as the Follow theme row shows it. */
internal fun MacrobenchmarkScope.followsTheme(): Boolean {
    openSettingsAt(FOLLOW_THEME_TAG)
    return device.hasObject(By.res(FOLLOW_THEME_TAG).checked(true))
}

/** Picks Follow theme, which removes every stored Settings choice: the config is the theme's alone again. */
internal fun MacrobenchmarkScope.followTheme() {
    openSettingsAt(FOLLOW_THEME_TAG)
    device.pressDPadCenter()
    check(device.wait(Until.hasObject(By.res(FOLLOW_THEME_TAG).checked(true)), PAGE_TIMEOUT_MS)) {
        "Follow theme did not take: a choice is still stored"
    }
}

/**
 * On the Look row, which has focus, picks the Style that is not checked, and waits until the flip to it has ended. Two calls
 * bring back the look the row started with, but as a stored choice; [followTheme] removes it.
 */
internal fun MacrobenchmarkScope.flipLook() {
    val from = awaitFocused(LOOK_OPTION_TAG, "the checked Look option")
    val to = device.findObjects(By.res(LOOK_OPTION_TAG)).first { it.resourceName != from }
    val target = to.resourceName
    val fromBounds = checkNotNull(device.findObject(By.res(from))) { "$from left the screen" }.visibleBounds
    if (to.visibleBounds.left > fromBounds.left) device.pressDPadRight() else device.pressDPadLeft()
    awaitFocused(LOOK_OPTION_TAG, target) { it == target }
    device.pressDPadCenter()
    check(device.wait(Until.hasObject(By.res(target).checked(true)), PAGE_TIMEOUT_MS)) { "$target was not checked after OK" }
    SystemClock.sleep(FLIP_SETTLE_MS)
}

/** UiDevice.pressDPad* waits for idle before each key, and pressKeyCodes sends one chord, so neither makes a burst. */
private fun pressWithoutWaiting(
    keyCode: Int,
    times: Int,
) {
    repeat(times) {
        val downTime = SystemClock.uptimeMillis()
        injectKey(downTime, KeyEvent.ACTION_DOWN, keyCode)
        injectKey(downTime, KeyEvent.ACTION_UP, keyCode)
    }
}

/**
 * Holds [keyCode] down as the input dispatcher repeats a held key: the press, then after the repeat timeout [repeats] repeats
 * at the repeat delay, the first one flagged as a long press, then the release.
 */
private fun holdKey(
    keyCode: Int,
    repeats: Int,
) {
    val downTime = SystemClock.uptimeMillis()
    injectKey(downTime, KeyEvent.ACTION_DOWN, keyCode)
    SystemClock.sleep(ViewConfiguration.getKeyRepeatTimeout().toLong())
    for (repeat in 1..repeats) {
        injectKey(downTime, KeyEvent.ACTION_DOWN, keyCode, repeat, if (repeat == 1) KeyEvent.FLAG_LONG_PRESS else 0)
        SystemClock.sleep(ViewConfiguration.getKeyRepeatDelay().toLong())
    }
    injectKey(downTime, KeyEvent.ACTION_UP, keyCode)
}

private fun injectKey(
    downTime: Long,
    action: Int,
    keyCode: Int,
    repeat: Int = 0,
    flags: Int = 0,
) {
    val event =
        KeyEvent(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            keyCode,
            repeat,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            flags,
            InputDevice.SOURCE_KEYBOARD,
        )
    check(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)) { "Could not inject key $keyCode" }
}
