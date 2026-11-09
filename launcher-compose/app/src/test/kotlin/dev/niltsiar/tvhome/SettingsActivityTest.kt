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
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.OptionLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** The settings page as its own activity, on the real graph: a theme file sets what it offers, and the keys go in at the root. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = TvHomeApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class SettingsActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val graph get() = (app as TvHomeApplication).graph

    /** The theme file has to be on disk before the graph first reads it. */
    private fun writeTheme(config: String) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(config)
    }

    private fun open(config: String = "{}"): ActivityController<SettingsActivity> {
        writeTheme(config)
        return Robolectric.buildActivity(SettingsActivity::class.java).setup()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun back_closes_the_settings() {
        val settings = open()
        assertFalse(settings.get().isFinishing)

        settings.get().onBackPressedDispatcher.onBackPressed()

        assertTrue(settings.get().isFinishing)
    }

    @Test
    fun only_an_ok_result_with_the_reorder_extra_asks_for_reorder_mode() {
        assertTrue(LauncherSettingsPage.parseResult(Activity.RESULT_OK, LauncherSettingsPage.reorderResult()))
        assertFalse(LauncherSettingsPage.parseResult(Activity.RESULT_OK, Intent()))
        assertFalse(LauncherSettingsPage.parseResult(Activity.RESULT_OK, null))
        assertFalse(LauncherSettingsPage.parseResult(Activity.RESULT_CANCELED, LauncherSettingsPage.reorderResult()))
    }

    @Test
    fun ok_on_the_reorder_row_ends_the_page_with_the_result_that_asks_home_for_reorder_mode() {
        val settings = open("""{"launcher_settings_button": true}""")
        compose.onNodeWithTag("settings:reorder").assertIsFocused()

        press(Key.DirectionCenter)

        val page = shadowOf(settings.get())
        assertTrue(settings.get().isFinishing)
        assertTrue(LauncherSettingsPage.parseResult(page.resultCode, page.resultIntent))
    }

    @Test
    fun the_open_on_start_row_and_its_picker_name_an_app_by_the_same_label_when_its_activity_and_application_labels_differ() {
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
                withTimeout(10_000) { viewModel.uiState.first { it.openOnStart == OptionLabel.Text("Activity label") } }

                viewModel.actions.onOpenPicker()
                val picker = withTimeout(10_000) { viewModel.uiState.first { it.picker != null } }.picker

                assertEquals(listOf(OpenOnStartApp("Activity label", "pkg.tv")), picker?.apps)
            }
        } finally {
            scope.cancel()
        }
    }

    /** An exported LEANBACK_LAUNCHER activity, `pkg/pkg.Main`, whose own label is not its application's. */
    private fun installTvApp(
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
            addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
        }
    }
}
