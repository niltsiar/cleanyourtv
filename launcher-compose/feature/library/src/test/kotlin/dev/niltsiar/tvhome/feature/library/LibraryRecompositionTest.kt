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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenedRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A focus move in the grid composes no card again and recomposes no grid item: each item is a composition of its own, and
 * one that recomposes costs the frame even when all it holds skips.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
open class LibraryRecompositionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val cards =
        object : CompositionTracer {
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

    private val items =
        object : CompositionObserver {
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

    protected open val leastUsed = false

    @Before
    fun showLibrary() {
        Composer.setTracer(cards)
        val library =
            FakeLibrary((1..23).map { libraryApp("App %02d".format(it)) }).apply {
                record = OpenedRecord(times = mapOf(key("App 23") to NOW - 3 * DAY), since = NOW - 90 * DAY)
            }
        val config = TvConfig(appLibrary = true, libraryLeastUsed = leastUsed)
        lateinit var viewModel: MoleculeLibraryViewModel
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
                            config,
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
        assertTrue("cards composed at first: ${cards.count}", cards.count >= 18)
        if (leastUsed) {
            press(Key.Menu)
            compose.onNodeWithTag("library:order").assertTextEquals("Least used")
        }
        cards.count = 0
        items.count = 0
        items.why.clear()
    }

    @After
    fun stopCounting() {
        Composer.setTracer(Idle)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun a_focus_move_along_and_down_the_grid_composes_no_card_again() {
        press(Key.DirectionRight)
        press(Key.DirectionDown)

        compose.onNodeWithTag("card:${key("App 08")}").assertIsFocused()
        assertEquals(0, cards.count)
    }

    @Test
    fun a_focus_move_along_and_down_the_grid_recomposes_no_grid_item() {
        press(Key.DirectionRight)
        press(Key.DirectionDown)

        compose.onNodeWithTag("card:${key("App 08")}").assertIsFocused()
        assertEquals(
            items.why
                .distinct()
                .take(8)
                .toString(),
            0,
            items.count,
        )
    }
}

/** The cards of the least used order carry an opened line, and a focus move still composes none of them again. */
class LeastUsedLibraryRecompositionTest : LibraryRecompositionTest() {
    override val leastUsed = true
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
