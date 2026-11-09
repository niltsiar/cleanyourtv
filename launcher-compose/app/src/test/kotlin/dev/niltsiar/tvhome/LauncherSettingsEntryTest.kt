package dev.niltsiar.tvhome

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import dev.niltsiar.tvhome.core.config.Choice
import dev.niltsiar.tvhome.core.config.Choices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The ways into the Launcher settings page and back, on the real MainActivity. A controller drives the lifecycle so Home stops
 * under the page as it does on a TV: ActivityScenario.moveToState blocks inside a Compose test body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TvHomeApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class LauncherSettingsEntryTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val graph get() = (app as TvHomeApplication).graph
    private lateinit var home: ActivityController<MainActivity>

    private val tvWithNineApps =
        object : ExternalResource() {
            override fun before() {
                shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                for (n in 1..9) installTvApp("pkg.$n")
            }
        }
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(tvWithNineApps).around(compose)

    @Before
    fun showHome() {
        home = Robolectric.buildActivity(MainActivity::class.java).setup()
        compose.waitUntil(10_000) { isFocused("tile:pkg.1/.Main") }
    }

    /** An exported LEANBACK_LAUNCHER activity, `pkg/pkg.Main`. */
    private fun installTvApp(pkg: String) {
        val component = ComponentName(pkg, "$pkg.Main")
        val application = ApplicationInfo().apply { packageName = pkg }
        val activity =
            ActivityInfo().apply {
                packageName = pkg
                name = component.className
                exported = true
                applicationInfo = application
                nonLocalizedLabel = pkg
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

    private fun <T> choose(
        choice: Choice<T>,
        value: T,
    ) = runBlocking { graph.configRepository.set(choice, value) }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun isFocused(tag: String) = exists(tag) && compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Focused]

    private fun settingsButtonFocused() =
        compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().config[SemanticsProperties.Focused]

    private fun send(intent: Intent) {
        home.newIntent(intent)
        compose.waitForIdle()
    }

    private fun openedPage() = shadowOf(home.get()).nextStartedActivityForResult

    /** The page opens, Home stops under it, [choices] are made there, the page closes with [resultCode] and Home resumes. */
    private fun closeThePageAfter(
        resultCode: Int = Activity.RESULT_CANCELED,
        result: Intent? = null,
        whileOpen: () -> Unit = {},
        choices: () -> Unit,
    ) {
        send(Intent(launcherSettingsAction(app.packageName)))
        val page = openedPage()
        assertNotNull(page)
        home.pause().stop()
        choices()
        whileOpen()
        shadowOf(home.get()).receiveResult(page.intent, resultCode, result)
        home.restart().start().resume()
        compose.waitForIdle()
    }

    @Test
    fun the_launcher_settings_action_opens_the_page_and_another_action_does_not() {
        send(Intent("${app.packageName}.action.SOMETHING_ELSE"))
        assertNull(openedPage())

        send(Intent("dev.example.other.action.LAUNCHER_SETTINGS"))
        assertNull(openedPage())

        send(Intent(launcherSettingsAction(app.packageName)))
        assertEquals(SettingsActivity::class.java.name, openedPage().intent.component?.className)
    }

    @Test
    fun closing_the_page_after_names_and_columns_changed_leaves_focus_on_the_settings_button() {
        closeThePageAfter(choices = {
            choose(Choices.AlwaysShowNames, true)
            choose(Choices.Columns, 6)
        })

        compose.waitUntil(10_000) { exists("name:pkg.2/.Main") && settingsButtonFocused() }
    }

    @Test
    fun the_reorder_result_of_the_page_starts_reorder_mode_and_not_the_settings_focus() {
        closeThePageAfter(resultCode = Activity.RESULT_OK, result = LauncherSettingsPage.reorderResult()) { choose(Choices.Columns, 6) }

        compose.waitUntil(10_000) { exists("reorder:hint") }
        assertFalse(settingsButtonFocused())
    }

    @Test
    fun a_home_key_that_ends_the_page_keeps_the_usual_home_focus() {
        closeThePageAfter(
            whileOpen = { send(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) },
            choices = { choose(Choices.Columns, 6) },
        )

        compose.waitUntil(10_000) { isFocused("tile:pkg.1/.Main") }
        assertFalse(settingsButtonFocused())
    }
}
