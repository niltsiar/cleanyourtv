package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenedRecord
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A focus move in the grid composes no card again and recomposes no grid item: each item is a composition of its own, and
 * one that recomposes costs the frame even when all it holds skips.
 */
val LibraryRecompositionTest by testSuite {
    robolectricTestSuite<LibraryRecompositionTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LibraryRecompositionTestContent :
    RobolectricTestSuiteContent({
        // The cards of the least used order carry an opened line, and a focus move still composes none of them again.
        for (leastUsed in listOf(false, true)) {
            val order = if (leastUsed) "in the least used order " else ""
            testSuite(if (leastUsed) "least used order" else "usual order") {
                testFixture { LibraryRecompositionFixture(leastUsed) } asContextForEach {
                    test("${order}a focus move along and down the grid composes no card again") {
                        press(Key.DirectionRight)
                        press(Key.DirectionDown)

                        compose.onNodeWithTag("card:${key("App 08")}").assertIsFocused()
                        cards.count shouldBe 0
                    }

                    test("${order}a focus move along and down the grid recomposes no grid item") {
                        press(Key.DirectionRight)
                        press(Key.DirectionDown)

                        compose.onNodeWithTag("card:${key("App 08")}").assertIsFocused()
                        withClue(
                            items.why
                                .distinct()
                                .take(8)
                                .toString(),
                        ) { items.count shouldBe 0 }
                    }
                }
            }
        }
    })

@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
private class LibraryRecompositionFixture(
    private val leastUsed: Boolean,
) : JUnit4RulesContext() {
    val compose = createAndroidComposeRule<ComponentActivity>()

    val rules: RuleChain =
        rule(
            RuleChain
                .outerRule(compose)
                .around(
                    object : ExternalResource() {
                        override fun before() = showLibrary()

                        override fun after() = stopCounting()
                    },
                ),
        )

    val cards = CardCompositions()
    val items = ItemCompositions()

    fun showLibrary() {
        Composer.setTracer(cards)
        val library =
            FakeLibrary((1..23).map { libraryApp("App %02d".format(it)) }).apply {
                record = OpenedRecord(times = mapOf(key("App 23") to NOW - 3 * DAY), since = NOW - 90 * DAY)
            }
        val config = TvConfig(appLibrary = true, libraryLeastUsed = leastUsed)
        lateinit var viewModel: MoleculeLibraryViewModel
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            val root = currentComposer.composition
            DisposableEffect(root) {
                items.root = root
                val observing = root.setObserver(items)
                onDispose { observing?.dispose() }
            }
            TvTheme {
                val scope = rememberCoroutineScope()
                viewModel =
                    remember {
                        MoleculeLibraryViewModel(
                            library.sources,
                            MutableStateFlow(config),
                            Locale.ENGLISH,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val state = viewModel.uiState.collectAsState()
                LibraryOverlay(state = { state.value }, layout = config.libraryLayout(), actions = viewModel.actions, art = { null })
            }
        }
        viewModel.onOpen()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("card:${key("App 01")}").assertIsFocused()
        withClue("cards composed at first: ${cards.count}") { cards.count shouldBeGreaterThanOrEqualTo 18 }
        if (leastUsed) {
            press(Key.Menu)
            compose.onNodeWithTag("library:order").assertTextEquals("Least used")
        }
        cards.count = 0
        items.count = 0
        items.why.clear()
    }

    fun stopCounting() {
        Composer.setTracer(Idle)
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}

@OptIn(InternalComposeTracingApi::class)
private class CardCompositions : CompositionTracer {
    var count = 0

    override fun isTraceInProgress() = true

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) {
        if (info.startsWith("dev.niltsiar.tvhome.feature.library.LibraryCardView ")) count++
    }

    override fun traceEventEnd() = Unit
}

@OptIn(ExperimentalComposeRuntimeApi::class)
private class ItemCompositions : CompositionObserver {
    var root: Any? = null
    var count = 0

    override fun onBeginComposition(composition: ObservableComposition) {
        if (composition !== root) count++
    }

    override fun onScopeEnter(scope: RecomposeScope) = Unit

    override fun onReadInScope(
        scope: RecomposeScope,
        value: Any,
    ) = Unit

    override fun onScopeExit(scope: RecomposeScope) = Unit

    override fun onEndComposition(composition: ObservableComposition) = Unit

    val why = mutableListOf<String>()

    override fun onScopeInvalidated(
        scope: RecomposeScope,
        value: Any?,
    ) {
        why += "${value?.javaClass?.name} $value"
    }

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
