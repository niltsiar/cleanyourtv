package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
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
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A focus move changes the shelf and the focused tile's look, composes no tile again and recomposes no grid item: each
 * grid item is a composition of its own, and one that recomposes costs the frame even when all it holds skips.
 */
val TileRecompositionTest by testSuite {
    robolectricTestSuite<TileRecompositionTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TileRecompositionTestContent :
    RobolectricTestSuiteContent({
        val looks =
            listOf(
                TileLook("plain tiles", "", alwaysShowNames = false, motion = Motion()),
                // The names under the tiles fade in the draw phase, so a focus move recomposes no more than without them.
                TileLook("named tiles", "with names shown, ", alwaysShowNames = true, motion = Motion()),
                // A tile that pops draws over its neighbours by an order read in the placement phase, so a focus move recomposes no more.
                TileLook("popping tiles", "with tiles that pop, ", alwaysShowNames = false, motion = Motion(focusOvershoot = true)),
            )
        for (look in looks) {
            testSuite(look.suite) {
                testFixture { TileRecompositionFixture(look) } asContextForEach {
                    test("${look.prefix}a device offered on the inputs button composes no tile and recomposes no grid item") {
                        inputs.value =
                            FourPorts.copy(
                                arrival = InputArrival("Apple TV \u00B7 HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1),
                            )
                        compose.mainClock.advanceTimeBy(2_100)
                        compose.waitForIdle()

                        compose.onNode(hasContentDescription("Apple TV", substring = true)).assertExists()
                        tiles.count shouldBe 0
                        items.count shouldBe 0
                    }

                    test("${look.prefix}a new habit composes no tile and recomposes no grid item") {
                        habitLooks shouldBe 1
                        habitApp = "pkg.9"
                        compose.mainClock.advanceTimeBy(HABIT_REFRESH_MS)
                        compose.waitForIdle()

                        habitLooks shouldBe 2
                        tiles.count shouldBe 0
                        items.count shouldBe 0
                    }

                    test("${look.prefix}a stored choice that no tile reads composes no tile and the focus moves after it cost no more") {
                        config.value = config.value.copy(clockFormat = ClockFormat.Hour12)
                        compose.waitForIdle()

                        viewModel.uiState.value.clock shouldBe "8:15"
                        tiles.count shouldBe 0
                        items.count shouldBe 0

                        press(Key.DirectionRight)
                        press(Key.DirectionRight)

                        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                        tiles.count shouldBe 0
                        items.count shouldBe 0
                    }

                    test("${look.prefix}a focus move along the dock composes no tile again") {
                        press(Key.DirectionRight)
                        press(Key.DirectionRight)

                        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                        tiles.count shouldBe 0
                    }

                    test("${look.prefix}a focus move into and along the grid composes no tile again") {
                        press(Key.DirectionDown)
                        press(Key.DirectionRight)

                        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
                        tiles.count shouldBe 0
                    }

                    test("${look.prefix}a focus move along the dock recomposes no grid item") {
                        press(Key.DirectionRight)
                        press(Key.DirectionRight)

                        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                        items.count shouldBe 0
                    }

                    test("${look.prefix}a focus move along the grid recomposes no grid item") {
                        press(Key.DirectionDown)
                        items.count = 0
                        press(Key.DirectionRight)
                        press(Key.DirectionRight)

                        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
                        items.count shouldBe 0
                    }
                }
            }
        }
    })

private class TileLook(
    val suite: String,
    val prefix: String,
    val alwaysShowNames: Boolean,
    val motion: Motion,
)

private class TileRecompositionFixture(
    private val look: TileLook,
) : JUnit4RulesContext() {
    val compose = createAndroidComposeRule<ComponentActivity>()

    val rules: RuleChain =
        rule(
            RuleChain
                .outerRule(compose)
                .around(
                    object : ExternalResource() {
                        override fun before() = showHome()

                        override fun after() = stopCounting()
                    },
                ),
        )

    val tiles = TileCompositions()
    val items = ItemCompositions()

    val inputs = MutableStateFlow(FourPorts)
    val config = MutableStateFlow(TvConfig())
    var habitApp = "pkg.3"
    var habitLooks = 0
    lateinit var viewModel: MoleculeHomeViewModel

    @OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
    fun showHome() {
        Composer.setTracer(tiles)
        val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
        config.value =
            TvConfig(
                columns = 6,
                cinematicHome = true,
                appLibrary = true,
                focusOvershoot = look.motion.focusOvershoot,
                alwaysShowNames = look.alwaysShowNames,
                inputArrival = true,
                habitHint = true,
                unusedFadeDays = 60,
            )
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            val home = currentComposer.composition
            DisposableEffect(home) {
                items.home = home
                val observing = home.setObserver(items)
                onDispose { observing?.dispose() }
            }
            TvTheme(motion = look.motion) {
                viewModel =
                    rememberHomeViewModel(
                        sources(
                            apps = flowOf(AppList(apps)),
                            inputs = inputs,
                            habit = { habitApp.also { habitLooks++ } },
                            openedSince = flowOf(0L),
                        ),
                        config,
                        mutableListOf(),
                    )
                val state = viewModel.uiState.collectAsState()
                // As the host reads it: each layout is derived from the config and changes only when its own fields do.
                val current = config.collectAsState()
                val layout by remember { derivedStateOf { current.value.homeLayout() } }
                val topBar by remember { derivedStateOf { current.value.topBarLayout() } }
                val shelfLayout by remember { derivedStateOf { current.value.shelfLayout().copy(scrimAlpha = 0f) } }
                HomeScreen(
                    state = { state.value },
                    layout = layout,
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = topBar,
                    shelfLayout = shelfLayout,
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        withClue("tiles composed at first: ${tiles.count}") { tiles.count shouldBeGreaterThanOrEqualTo 12 }
        viewModel.uiState.value.faded shouldBe setOf("pkg.7", "pkg.8", "pkg.9", "pkg.10", "pkg.11", "pkg.12")
        tiles.count = 0
        items.count = 0
    }

    @OptIn(InternalComposeTracingApi::class)
    fun stopCounting() {
        Composer.setTracer(Idle)
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}

/** Counts the AppTile bodies that run; the compiler calls it for each composable that does not skip. */
@OptIn(InternalComposeTracingApi::class)
private class TileCompositions : CompositionTracer {
    var count = 0

    override fun isTraceInProgress() = true

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) {
        if (info.startsWith("dev.niltsiar.tvhome.feature.home.AppTile ")) count++
    }

    override fun traceEventEnd() = Unit
}

/** Counts the recompositions of the grid's items: every composition under Home's but Home's own. */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class ItemCompositions : CompositionObserver {
    var home: Any? = null
    var count = 0

    override fun onBeginComposition(composition: ObservableComposition) {
        if (composition !== home) count++
    }

    override fun onScopeEnter(scope: RecomposeScope) = Unit

    override fun onReadInScope(
        scope: RecomposeScope,
        value: Any,
    ) = Unit

    override fun onScopeExit(scope: RecomposeScope) = Unit

    override fun onEndComposition(composition: ObservableComposition) = Unit

    override fun onScopeInvalidated(
        scope: RecomposeScope,
        value: Any?,
    ) = Unit

    override fun onScopeDisposed(scope: RecomposeScope) = Unit
}

@OptIn(InternalComposeTracingApi::class)
private object Idle : CompositionTracer {
    override fun isTraceInProgress() = false

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) = Unit

    override fun traceEventEnd() = Unit
}
