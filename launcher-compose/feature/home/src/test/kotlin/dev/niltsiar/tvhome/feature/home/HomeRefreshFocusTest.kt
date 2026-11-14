package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import java.util.Locale

val HomeRefreshFocusTest by testSuite {
    robolectricTestSuite<HomeRefreshFocusTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeRefreshFocusTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeRefreshFocusTestFixture() } asContextForEach {
            test("focus stays on a tile when the focused dock app is removed") {
                showHome(SixColumns)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

                apps = apps.filterNot { it.key == "pkg.3" }
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.3").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("focus stays on a tile when the focused grid app is removed") {
                showHome(SixColumns)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                apps = apps.filterNot { it.key == "pkg.7" }
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("four columns make a four tile dock and a four wide grid") {
                showHome(TvConfig(columns = 4))
                repeat(3) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.4").assertIsFocused()

                press(Key.DirectionDown)

                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
            }

            test("left and right stop at the ends of the dock") {
                showHome(TvConfig(columns = 4))

                repeat(5) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.4").assertIsFocused()

                repeat(5) { press(Key.DirectionLeft) }
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("a single row walks through every app sideways") {
                showHome(TvConfig(columns = 4, singleRow = true))
                repeat(8) { press(Key.DirectionRight) }

                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
            }
        }
    })

private class HomeRefreshFocusTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var apps by mutableStateOf(NineApps)

    var focused by mutableStateOf<String?>(null)

    fun showHome(config: TvConfig) {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(homeApps(apps, config = config), Evening, focused, Locale.ENGLISH) },
                    layout = config.homeLayout(),
                    onFocus = { focused = it },
                    onClick = {},
                )
            }
        }
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
