package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
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
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf

/** focus_habit_app on the real presenter and screen: which arrivals on Home put focus on the usual app's tile. */
val HomeUsualAppFocusUiTest by testSuite {
    robolectricTestSuite<HomeUsualAppFocusUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeUsualAppFocusUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeUsualAppFocusUiTestFixture() } asContextForEach {
            test("an arrival from an app focuses the usual tile and scrolls it into view") {
                showHome(UsualAppFocus)

                arriveFromApp()

                compose.onNodeWithTag("tile:pkg.18").assertIsFocused().assertIsDisplayed()
            }

            test("the Home key while on Home focuses the first tile even with a usual app") {
                showHome(UsualAppFocus)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

                homePresses++
                settle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("Back from an app keeps the remembered tile") {
                showHome(UsualAppFocus)
                press(Key.DirectionRight)
                press(Key.DirectionRight)

                leaveForApp()
                viewModel.onStarted()
                settle()

                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
            }

            test("a key pressed before the usual app is found keeps focus where it is") {
                showHome(UsualAppFocus)
                answer = CompletableDeferred()
                arriveFromApp()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                press(Key.DirectionRight)
                answer.complete(Unit)
                settle()

                compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
            }

            test("a usual app found only in the library leaves the first tile focused") {
                usual = "pkg.library"
                showHome(UsualAppFocus.copy(appLibrary = true))

                arriveFromApp()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("with habit_hint off an arrival focuses the first tile") {
                showHome(UsualAppFocus.copy(habitHint = false))

                arriveFromApp()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("with focus_habit_app off an arrival focuses the first tile") {
                showHome(UsualAppFocus.copy(focusHabitApp = false))

                arriveFromApp()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }
        }
    })

/** habit_hint and focus_habit_app on, six columns. */
private val UsualAppFocus = SixColumns.copy(habitHint = true, focusHabitApp = true)

private class HomeUsualAppFocusUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    /** The usual app the habit lookup answers with, once [answer] completes. */
    var usual: String? = "pkg.18"

    var answer = CompletableDeferred(Unit)

    var homePresses by mutableIntStateOf(0)

    var arrivalPress by mutableIntStateOf(NO_ARRIVAL)

    lateinit var viewModel: MoleculeHomeViewModel

    fun showHome(
        config: TvConfig,
        startArrival: Int = NO_ARRIVAL,
    ) {
        arrivalPress = startArrival
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                habit = {
                    answer.await()
                    usual
                },
            )
        compose.setContent {
            TvTheme {
                viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    homePresses = { homePresses },
                    arrivalPress = { arrivalPress },
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                )
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        settle()
    }

    /** Home leaves for an app, and the Home key brings it back, as MainActivity reports an arrival from outside. */
    fun arriveFromApp() {
        leaveForApp()
        viewModel.onStarted()
        homePresses++
        arrivalPress = homePresses
        settle()
    }

    fun leaveForApp() {
        viewModel.onStopped()
        settle()
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }
}
