package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.collectAsState
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * A focus move recomposes no row: the focus fill and the label's brightness are drawn from animations, so a row
 * recomposes only when its own selection changes.
 */
val SettingsRecompositionTest by testSuite {
    robolectricTestSuite<SettingsRecompositionTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
class SettingsRecompositionTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = createAndroidComposeRule<ComponentActivity>()

                val app: Application = RuntimeEnvironment.getApplication()
                lateinit var settings: SettingsHarness

                /** Counts the SettingRow bodies that run; the compiler calls it for each composable that does not skip. */
                val rows =
                    object : CompositionTracer {
                        var count = 0

                        override fun isTraceInProgress() = true

                        override fun traceEventStart(
                            key: Int,
                            dirty1: Int,
                            dirty2: Int,
                            info: String,
                        ) {
                            if (info.startsWith("dev.niltsiar.tvhome.feature.settings.SettingRow ")) count++
                        }

                        override fun traceEventEnd() = Unit
                    }

                val rules: RuleChain =
                    rule(
                        RuleChain
                            .outerRule(compose)
                            .around(
                                object : ExternalResource() {
                                    override fun before() = showSettings()

                                    override fun after() = stopCounting()
                                },
                            ),
                    )

                fun showSettings() {
                    Composer.setTracer(rows)
                    settings = SettingsHarness(app, """{"columns": 6}""", uiThread = true)
                    // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    compose.setContent {
                        TvTheme {
                            val state = settings.viewModel.uiState.collectAsState()
                            SettingsScreen({ state.value }, settings.viewModel.actions, onChooseDefaultHome = {}, onOpenTvSettings = {})
                        }
                    }
                    compose.waitForIdle()
                    compose.onNodeWithTag("option:Columns:2").assertIsFocused()
                    withClue("rows composed at first: ${rows.count}") {
                        rows.count shouldBeGreaterThanOrEqualTo
                            (settings.state.sections.sumOf { it.rows.size })
                    }
                    rows.count = 0
                }

                fun stopCounting() {
                    Composer.setTracer(Idle)
                    settings.stop()
                }

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    compose.waitForIdle()
                }
            }
        } asContextForEach {
            test("a focus move along a row and down the list recomposes no row") {
                press(Key.DirectionRight)
                press(Key.DirectionLeft)
                press(Key.DirectionDown)
                press(Key.DirectionDown)
                press(Key.DirectionUp)

                compose.onNodeWithTag("option:RowsBelowDock:0").assertIsFocused()
                rows.count shouldBe 0
            }

            test("a pick recomposes the row it changes alone") {
                press(Key.DirectionRight)
                press(Key.DirectionCenter)
                compose.waitUntil(10_000) {
                    shadowOf(Looper.getMainLooper()).idle()
                    settings.row(SettingsRowId.Columns).selected == 3
                }
                compose.waitForIdle()

                rows.count shouldBe 1
            }
        }
    })

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
