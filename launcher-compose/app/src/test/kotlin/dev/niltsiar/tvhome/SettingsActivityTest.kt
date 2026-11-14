package dev.niltsiar.tvhome

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Environment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.OptionLabel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** The settings page as its own activity, on the real graph: a theme file sets what it offers, and the keys go in at the root. */
val SettingsActivityTest by testSuite {
    robolectricTestSuite<SettingsActivityTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class SettingsActivityTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createEmptyComposeRule())

                val app: Application = RuntimeEnvironment.getApplication()
                val graph get() = (app as TvHomeApplication).graph

                /** The theme file has to be on disk before the graph first reads it. */
                fun writeTheme(config: String) {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(app).dir!!, "config.json").writeText(config)
                }

                fun open(config: String = "{}"): ActivityController<SettingsActivity> {
                    writeTheme(config)
                    return Robolectric.buildActivity(SettingsActivity::class.java).setup()
                }

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    compose.waitForIdle()
                }

                /** An exported LEANBACK_LAUNCHER activity, `pkg/pkg.Main`, whose own label is not its application's. */
                fun installTvApp(
                    pkg: String,
                    activityLabel: String,
                    applicationLabel: String,
                ) {
                    val component = ComponentName(pkg, "$pkg.Main")
                    val application =
                        ApplicationInfo().apply {
                            packageName = pkg
                            nonLocalizedLabel = applicationLabel
                        }
                    val activity =
                        ActivityInfo().apply {
                            packageName = pkg
                            name = component.className
                            exported = true
                            applicationInfo = application
                            nonLocalizedLabel = activityLabel
                        }
                    shadowOf(app.packageManager).apply {
                        installPackage(
                            PackageInfo().apply {
                                packageName = pkg
                                applicationInfo = application
                            },
                        )
                        addOrUpdateActivity(activity)
                        addIntentFilterForActivity(
                            component,
                            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                        )
                    }
                }
            }
        } asContextForEach {
            test("back closes the settings") {
                val settings = open()
                settings.get().isFinishing.shouldBeFalse()

                settings.get().onBackPressedDispatcher.onBackPressed()

                settings.get().isFinishing.shouldBeTrue()
            }

            test("only an ok result with the reorder extra asks for reorder mode") {
                LauncherSettingsPage.parseResult(Activity.RESULT_OK, LauncherSettingsPage.reorderResult()).shouldBeTrue()
                LauncherSettingsPage.parseResult(Activity.RESULT_OK, Intent()).shouldBeFalse()
                LauncherSettingsPage.parseResult(Activity.RESULT_OK, null).shouldBeFalse()
                LauncherSettingsPage.parseResult(Activity.RESULT_CANCELED, LauncherSettingsPage.reorderResult()).shouldBeFalse()
            }

            test("ok on the reorder row ends the page with the result that asks home for reorder mode") {
                val settings = open("""{"launcher_settings_button": true}""")
                compose.onNodeWithTag("settings:reorder").assertIsFocused()

                press(Key.DirectionCenter)

                val page = shadowOf(settings.get())
                settings.get().isFinishing.shouldBeTrue()
                LauncherSettingsPage.parseResult(page.resultCode, page.resultIntent).shouldBeTrue()
            }

            test("the open on start row and its picker name an app by the same label when its activity and application labels differ") {
                installTvApp("pkg.tv", activityLabel = "Activity label", applicationLabel = "Application label")
                writeTheme("""{"open_on_start_choice": true}""")
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    val viewModel =
                        MoleculeSettingsViewModel(
                            graph.configRepository,
                            graph.settingsSources,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    runBlocking {
                        graph.configRepository.set(Choices.OpenOnStart, "pkg.tv")
                        withTimeout(30_000) { viewModel.uiState.first { it.openOnStart == OptionLabel.Text("Activity label") } }

                        viewModel.actions.onOpenPicker()
                        val picker = withTimeout(30_000) { viewModel.uiState.first { it.picker != null } }.picker

                        picker?.apps shouldBe listOf(OpenOnStartApp("Activity label", "pkg.tv"))
                    }
                } finally {
                    scope.cancel()
                }
            }
        }
    })
