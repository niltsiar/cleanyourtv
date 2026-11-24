package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focus.FocusTrace
import dev.niltsiar.tvhome.core.designsystem.focus.trace
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Home's focus paths, key by key, in Glass and in Expressive: each script ends on the same focused tags in both looks, and
 * every tagged node has the same bounds in both at every step.
 */
val FocusScriptUiTest by testSuite {
    robolectricTestSuite<FocusScriptUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class FocusScriptUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { FocusScriptUiTestFixture() } asContextForEach {
            test("home with app_shelf off walks the dock the grid and the top bar the same way in both looks") {
                val keys =
                    listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp, Key.DirectionUp)
                bothLooks(SixColumns, keys + listOf(Key.DirectionRight, Key.DirectionDown)) shouldBe
                    listOf(
                        "tile:pkg.1",
                        "tile:pkg.2",
                        "tile:pkg.3",
                        "tile:pkg.9",
                        "tile:pkg.8",
                        "tile:pkg.3",
                        "cd:Inputs",
                        "cd:Offline",
                        "tile:pkg.3",
                    )
            }

            test("home with app_shelf on walks the shelf cards and the dock the same way in both looks") {
                bothLooks(
                    ShelfOn,
                    listOf(Key.DirectionUp, Key.DirectionRight, Key.DirectionRight, Key.DirectionDown, Key.DirectionRight, Key.DirectionUp),
                ) shouldBe listOf("tile:pkg.1", "shelf:card:0", "shelf:card:1", "shelf:card:2", "tile:pkg.1", "tile:pkg.2", "cd:Inputs")
            }

            test("up from every dock column reaches the same top bar button in both looks") {
                val keys =
                    (0 until 6).flatMap { column ->
                        listOfNotNull(Key.DirectionRight.takeIf { column > 0 }, Key.DirectionUp, Key.DirectionDown)
                    }
                bothLooks(SixColumns, keys) shouldBe
                    listOf("tile:pkg.1") +
                    (1..6).flatMap { column ->
                        val above = if (column == 6) "cd:Offline" else "cd:Inputs"
                        listOfNotNull("tile:pkg.$column".takeIf { column > 1 }, above, "tile:pkg.$column")
                    }
            }
        }
    })

private val ShelfOn = SixColumns.copy(appShelf = true, appShelfSources = listOf("pkg.1"))

private class FocusScriptUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var look by mutableStateOf(Look.Glass)

    /** A new value builds Home again from nothing. */
    var run by mutableIntStateOf(0)

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    val items =
        listOf("Severance", "Andor", "Silo").map {
            ShelfItem(packageName = "pkg.1", title = it, kind = ShelfKind.Next, posterUri = null)
        }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** Lets every spring and the shelf's hold run out before the next read. */
    fun settle() {
        compose.mainClock.advanceTimeBy(SHELF_HOLD_MS + 1_000)
        compose.waitForIdle()
    }

    fun show(config: TvConfig) {
        compose.setContent {
            key(run) {
                TvTheme(look = look) {
                    val sources =
                        remember { sources(apps = flowOf(AppList(apps)), inputs = MutableStateFlow(FourPorts), shelfItems = { items }) }
                    val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = config.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = viewModel::onClick,
                        topBar = config.topBarLayout(),
                        shelfLayout = config.shelfLayout(),
                        dockLook = config.dockLook(),
                        actions = viewModel.actions,
                        onOpenShelfItem = viewModel::onOpenShelfItem,
                        shelfPoster = sources.shelfPoster,
                        onShelfRoom = viewModel::onShelfRoom,
                    )
                }
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        settle()
    }

    fun script(keys: List<Key>): FocusTrace {
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        return compose.trace(keys.map { key -> { press(key) } })
    }

    /** The focused tags of [keys] in Glass, after the same script in Expressive gave the same tags and bounds. */
    fun bothLooks(
        config: TvConfig,
        keys: List<Key>,
    ): List<String> {
        show(config)
        val glass = script(keys)
        look = Look.Expressive
        run++
        settle()
        val expressive = script(keys)
        withClue("tags") { expressive.tags shouldBe glass.tags }
        glass.bounds.indices.forEach { step -> withClue("bounds after step $step") { expressive.bounds[step] shouldBe glass.bounds[step] } }
        return glass.tags
    }
}
