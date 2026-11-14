package dev.niltsiar.tvhome

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** [SettingsHost] over the real graph: what the page has to tell shows in the page itself, never as a system toast. */
val SettingsHostTest by testSuite {
    robolectricTestSuite<SettingsHostTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsHostTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                private val graph get() = (compose.activity.application as TvHomeApplication).graph
                lateinit var viewModel: MoleculeSettingsViewModel

                fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

                fun noticeSays(text: String) =
                    compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("settings:notice"))).assertExists()

                /** The page over a theme with one picture, with TV settings that do not open. */
                fun show() {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    val theme = ThemeDirectory(compose.activity)
                    File(theme.dir!!, "config.json").writeText("{}")
                    theme.wallpaperFile!!.writeBytes(byteArrayOf(1, 2, 3))
                    compose.setContent {
                        val scope = rememberCoroutineScope()
                        viewModel =
                            remember {
                                MoleculeSettingsViewModel(
                                    graph.configRepository,
                                    graph.settingsSources,
                                    scope,
                                    RecompositionMode.Immediate,
                                    EmptyCoroutineContext,
                                )
                            }
                        SettingsHost(
                            graph = graph,
                            viewModel = viewModel,
                            onChooseDefaultHome = {},
                            onOpenTvSettings = { false },
                            onReorder = {},
                        )
                    }
                    compose.waitUntil(30_000) { viewModel.uiState.value.wallpaper != null }
                }
            }
        } asContextForEach {
            test("a picked wallpaper is confirmed in the page for about 2 s and not by a toast") {
                show()
                viewModel.actions.onOpenWallpaperPicker()
                compose.waitUntil(30_000) { viewModel.uiState.value.wallpaperPicker != null }

                viewModel.actions.onPickWallpaper(1)
                compose.waitUntil(30_000) { exists("settings:notice") }

                noticeSays("Wallpaper changed")
                compose.mainClock.advanceTimeBy(1_500)
                withClue("still showing after 1.5 s") { exists("settings:notice").shouldBeTrue() }
                compose.mainClock.advanceTimeBy(1_000)
                withClue("gone after 2.5 s") { exists("settings:notice").shouldBeFalse() }
                ShadowToast.shownToastCount() shouldBe 0
            }

            test("tv settings that do not open are told in the page and not by a toast") {
                show()

                compose.onNodeWithTag("settings:tv_settings").performSemanticsAction(SemanticsActions.OnClick)
                compose.waitForIdle()

                noticeSays("Not available on this TV")
                ShadowToast.shownToastCount() shouldBe 0
            }
        }
    })
