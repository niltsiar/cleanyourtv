package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme

/** A Home created again, on the real view model, puts focus back on the tile it remembers. */
val HomeFocusMemoryUiTest by testSuite {
    robolectricTestSuite<HomeFocusMemoryUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeFocusMemoryUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeFocusMemoryUiTestFixture() } asContextForEach {
            test("in a single row a tile beyond the first screen gets focus and is scrolled into view") {
                showHome(SixColumns.copy(singleRow = true), remembered = "pkg.9", slot = 8)

                compose.onNodeWithTag("tile:pkg.9").assertIsFocused().assertIsDisplayed()
            }

            test("a tile in the grid gets focus") {
                showHome(SixColumns, remembered = "pkg.8", slot = 7)

                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
            }

            test("with nothing remembered the first tile gets focus") {
                showHome(SixColumns, remembered = null, slot = 0)

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }
        }
    })

private class HomeFocusMemoryUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    fun showHome(
        config: TvConfig,
        remembered: String?,
        slot: Int,
    ) {
        val memory =
            FocusMemory().also {
                it.key = remembered
                it.slot = slot
            }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources(focusMemory = memory), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
    }
}
