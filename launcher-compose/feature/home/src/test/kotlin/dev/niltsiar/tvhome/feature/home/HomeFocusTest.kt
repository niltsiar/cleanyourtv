package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputsMenu
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.Locale

val HomeFocusTest by testSuite {
    robolectricTestSuite<HomeFocusTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeFocusTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeFocusTestFixture() } asContextForEach {
            test("down from the top bar moves focus back to the dock") {
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()

                press(Key.DirectionDown)

                compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()
            }

            test("down from the top bar returns to the dock tile that had focus") {
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                press(Key.DirectionUp)
                press(Key.DirectionDown)

                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
            }

            test("home pressed while in the grid focuses the first dock tile") {
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                homePresses++
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }
        }
    })

private class HomeFocusTestFixture : JUnit4RulesContext() {
    val compose = createAndroidComposeRule<ComponentActivity>()

    val rules: RuleChain =
        rule(
            RuleChain
                .outerRule(compose)
                .around(
                    object : ExternalResource() {
                        override fun before() = showHome()
                    },
                ),
        )

    var homePresses by mutableIntStateOf(0)

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    fun showHome() {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        val state = homeUiState(homeApps(), Evening, null, Locale.ENGLISH, inputs = InputsMenu(available = true))
        compose.setContent {
            TvTheme {
                HomeScreen(state = { state }, layout = SixColumns.homeLayout(), onFocus = {}, onClick = {}, homePresses = { homePresses })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }
}
