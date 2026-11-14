package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow

/** A cold start: the window asks Home for focus on its first frame, the app list arrives frames later, and its tiles show names until their art lands. */
val HomeColdStartFocusTest by testSuite {
    robolectricTestSuite<HomeColdStartFocusTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeColdStartFocusTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeColdStartFocusTestFixture() } asContextForEach {
            test("focus goes to dock slot 1 and no top bar button is ever focused when the apps arrive after the first frame") {
                val sources = sources(apps = apps)
                compose.runOnUiThread {
                    compose.activity.window.decorView
                        .requestFocusFromTouch()
                }
                compose.setContent {
                    TvTheme {
                        val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                        val state = viewModel.uiState.collectAsState()
                        HomeScreen(
                            state = { state.value },
                            layout = config.homeLayout(),
                            onFocus = viewModel::onFocus,
                            onClick = {},
                            topBar = config.topBarLayout(),
                            shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                            actions = viewModel.actions,
                        )
                    }
                }
                compose.mainClock.autoAdvance = false
                compose.runOnUiThread {
                    compose.activity.window.decorView
                        .requestFocus()
                }
                val seen = mutableListOf<String>()
                repeat(10) {
                    compose.mainClock.advanceTimeByFrame()
                    seen += focusedTags()
                }
                apps.tryEmit(AppList(NineApps))
                repeat(20) {
                    compose.mainClock.advanceTimeByFrame()
                    seen += focusedTags()
                }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                seen.filter { it != "tile:pkg.1" }.distinct() shouldBe (emptyList<String>())
            }

            test("the name on a tile without art reads 4 5 to 1 on its fill under the dock shade of top bar focus") {
                val ratio = nameContrastUnderTopBarShade(darkTheme = true)

                withClue("$ratio") { ratio shouldBeGreaterThanOrEqualTo 4.5f }
            }

            test("the name on a tile without art reads 4 5 to 1 on its fill with night mode off") {
                val ratio = nameContrastUnderTopBarShade(darkTheme = false)

                withClue("$ratio") { ratio shouldBeGreaterThanOrEqualTo 4.5f }
            }
        }
    })

private class HomeColdStartFocusTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val config = SixColumns.copy(cinematicHome = true, appLibrary = true, libraryButton = true)

    val apps = MutableSharedFlow<AppList>(replay = 1)

    fun focusedTags(): List<String> =
        compose
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
            .fetchSemanticsNodes()
            .map {
                it.config.getOrNull(SemanticsProperties.TestTag)
                    ?: it.config.getOrNull(SemanticsProperties.ContentDescription).toString()
            }

    fun nameContrastUnderTopBarShade(darkTheme: Boolean): Float {
        var fill = Color.Unspecified
        apps.tryEmit(AppList(NineApps))
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme(darkTheme = darkTheme) {
                val viewModel = rememberHomeViewModel(sources(apps = apps), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                fill = TvTheme.colors.surface
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()

        val name = compose.onNodeWithTag("tile:pkg.1").textStyle().color

        fun shaded(color: Color) = Color.Black.copy(alpha = TOP_BAR_DOCK_SHADE).compositeOver(color)
        return (shaded(name).luminance() + 0.05f) / (shaded(fill).luminance() + 0.05f)
    }
}
