package dev.niltsiar.tvhome.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focus.trace
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Launcher settings' focus paths in Glass and in Expressive: the same focused tags key by key, and every tagged node with
 * the same bounds in both looks at every step.
 */
val SettingsFocusScriptUiTest by testSuite {
    robolectricTestSuite<SettingsFocusScriptUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsFocusScriptUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { SettingsFocusScriptUiTestFixture() } asContextForEach {
            test("the page walks its rows and chips the same way in both looks") {
                val keys =
                    listOf(
                        Key.DirectionDown,
                        Key.DirectionDown,
                        Key.DirectionDown,
                        Key.DirectionRight,
                        Key.DirectionRight,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionUp,
                    )
                bothLooks(keys) shouldBe
                    listOf(
                        "option:Columns:0",
                        "option:RowsBelowDock:0",
                        "option:DockOpacity:0",
                        "option:ClockFormat:0",
                        "option:ClockFormat:1",
                        "option:ClockFormat:2",
                        "settings:wallpaper",
                        "settings:wallpaper",
                        "option:ClockFormat:0",
                    )
            }

            test("the look group follows open on start, with the focused tile row under style only in expressive") {
                val keys = List(9) { Key.DirectionDown } + List(2) { Key.DirectionUp }
                val top =
                    listOf(
                        "option:Columns:0",
                        "option:RowsBelowDock:0",
                        "option:DockOpacity:0",
                        "option:ClockFormat:0",
                        "settings:wallpaper",
                        "option:Animations:0",
                        "settings:open_on_start",
                    )

                show()
                compose.trace(keys.map { key -> { press(key) } }).tags shouldBe
                    top +
                    listOf(
                        "option:Look:0",
                        "option:AlwaysShowNames:1",
                        "option:ShowGreeting:0",
                        "option:AlwaysShowNames:1",
                        "option:Look:0",
                    )
                look = Look.Expressive
                config = TvConfig(look = Look.Expressive)
                run++
                settle()
                compose.trace(keys.map { key -> { press(key) } }).tags shouldBe
                    top +
                    listOf(
                        "option:Look:1",
                        "option:ExpressiveTile:0",
                        "option:AlwaysShowNames:1",
                        "option:ExpressiveTile:0",
                        "option:Look:1",
                    )
            }
        }
    })

private class SettingsFocusScriptUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var look by mutableStateOf(Look.Glass)

    /** What the page lists: the same rows in both looks unless a test changes it. */
    var config by mutableStateOf(TvConfig())

    /** A new value builds the page again from nothing. */
    var run by mutableIntStateOf(0)

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun show() {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            key(run) {
                TvTheme(look = look) {
                    SettingsScreen(
                        state = {
                            SettingsUiState(
                                settingsSections(config),
                                followsTheme = true,
                                openOnStart = OptionLabel.Res(R.string.settings_off),
                                wallpaper = OptionLabel.Res(R.string.wallpaper_theme_default),
                            )
                        },
                        actions = SettingsActions(),
                        onChooseDefaultHome = {},
                        onOpenTvSettings = {},
                    )
                }
            }
        }
        settle()
    }

    /** The focused tags of [keys] in Glass, after the same keys in Expressive gave the same tags and bounds. */
    fun bothLooks(keys: List<Key>): List<String> {
        show()
        val steps = keys.map { key -> { press(key) } }
        val glass = compose.trace(steps)
        look = Look.Expressive
        run++
        settle()
        val expressive = compose.trace(steps)
        withClue("tags") { expressive.tags shouldBe glass.tags }
        glass.bounds.indices.forEach { step -> withClue("bounds after step $step") { expressive.bounds[step] shouldBe glass.bounds[step] } }
        return glass.tags
    }
}
