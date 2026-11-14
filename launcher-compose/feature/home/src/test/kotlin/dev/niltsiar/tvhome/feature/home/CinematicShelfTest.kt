package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertTextEquals
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.matchers.shouldBe
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.Locale

val CinematicShelfTest by testSuite {
    robolectricTestSuite<CinematicShelfTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class CinematicShelfTestContent :
    RobolectricTestSuiteContent({
        testFixture { CinematicShelfTestFixture() } asContextForEach {
            test("a focused dock tile puts its name on the cinematic shelf and leaves the plain title empty") {
                compose.onNodeWithTag("shelf:name").assertTextEquals("App 1")
                compose.onNodeWithTag("shelf:title").assertTextEquals("")

                press(Key.DirectionRight)

                compose.onNodeWithTag("shelf:name").assertTextEquals("App 2")
            }

            test("a grid tile goes back to the plain shelf title") {
                press(Key.DirectionDown)

                compose.onNodeWithTag("shelf:title").assertTextEquals("App 7")
            }

            test("each focused dock app asks for its art once per focus") {
                press(Key.DirectionRight)
                press(Key.DirectionRight)

                artAsked shouldBe listOf("pkg.1", "pkg.2", "pkg.3")
            }
        }
    })

private class CinematicShelfTestFixture : JUnit4RulesContext() {
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

    var focusedKey by mutableStateOf<String?>(null)

    val artAsked = mutableListOf<String>()

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
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(homeApps(), Evening, focusedKey, Locale.ENGLISH, cinematic = true) },
                    layout = SixColumns.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    shelfLayout = ShelfLayout(cinematic = true, scrimAlpha = 0f, animated = false),
                    heroArt = {
                        artAsked += it
                        null
                    },
                )
            }
        }
        compose.waitForIdle()
    }
}
