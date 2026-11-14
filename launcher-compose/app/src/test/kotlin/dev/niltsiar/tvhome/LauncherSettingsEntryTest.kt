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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choice
import dev.niltsiar.tvhome.core.config.Choices
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * The ways into the Launcher settings page and back, on the real MainActivity. A controller drives the lifecycle so Home stops
 * under the page as it does on a TV: ActivityScenario.moveToState blocks inside a Compose test body. It runs on an SDK level
 * no other test class uses, for the reason MainActivityConfigTest gives.
 */
val LauncherSettingsEntryTest by testSuite {
    robolectricTestSuite<LauncherSettingsEntryTestContent>(
        "sdk 34, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 34
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LauncherSettingsEntryTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val app: Application = RuntimeEnvironment.getApplication()
                val graph get() = (app as TvHomeApplication).graph
                lateinit var home: ActivityController<MainActivity>

                val tvWithNineApps =
                    object : ExternalResource() {
                        override fun before() {
                            shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                            for (n in 1..9) installTvApp("pkg.$n")
                        }
                    }

                val compose = createEmptyComposeRule()

                val rules: RuleChain =
                    rule(
                        RuleChain
                            .outerRule(tvWithNineApps)
                            .around(compose)
                            .around(
                                object : ExternalResource() {
                                    override fun before() = showHome()
                                },
                            ),
                    )

                fun showHome() {
                    home = Robolectric.buildActivity(MainActivity::class.java).setup()
                    compose.waitUntil(30_000) { isFocused("tile:pkg.1/.Main") }
                }

                /** An exported LEANBACK_LAUNCHER activity, `pkg/pkg.Main`. */
                fun installTvApp(pkg: String) {
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
                        addIntentFilterForActivity(
                            component,
                            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                        )
                    }
                }

                fun <T> choose(
                    choice: Choice<T>,
                    value: T,
                ) = runBlocking { graph.configRepository.set(choice, value) }

                fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

                fun isFocused(tag: String) =
                    exists(tag) && compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Focused]

                fun settingsButtonFocused() =
                    compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().config[SemanticsProperties.Focused]

                fun send(intent: Intent) {
                    home.newIntent(intent)
                    compose.waitForIdle()
                }

                fun openedPage() = shadowOf(home.get()).nextStartedActivityForResult

                /** The page opens, Home stops under it, [choices] are made there, the page closes with [resultCode] and Home resumes. */
                fun closeThePageAfter(
                    resultCode: Int = Activity.RESULT_CANCELED,
                    result: Intent? = null,
                    whileOpen: () -> Unit = {},
                    choices: () -> Unit,
                ) {
                    send(Intent(launcherSettingsAction(app.packageName)))
                    val page = openedPage()
                    page.shouldNotBeNull()
                    home.pause().stop()
                    choices()
                    whileOpen()
                    shadowOf(home.get()).receiveResult(page.intent, resultCode, result)
                    home.restart().start().resume()
                    compose.waitForIdle()
                }
            }
        } asContextForEach {
            test("the launcher settings action opens the page and another action does not") {
                send(Intent("${app.packageName}.action.SOMETHING_ELSE"))
                openedPage().shouldBeNull()

                send(Intent("dev.example.other.action.LAUNCHER_SETTINGS"))
                openedPage().shouldBeNull()

                send(Intent(launcherSettingsAction(app.packageName)))
                openedPage().intent.component?.className shouldBe SettingsActivity::class.java.name
            }

            test("closing the page after names and columns changed leaves focus on the settings button") {
                closeThePageAfter(choices = {
                    choose(Choices.AlwaysShowNames, true)
                    choose(Choices.Columns, 6)
                })

                compose.waitUntil(30_000) { exists("name:pkg.2/.Main") && settingsButtonFocused() }
            }

            test("the reorder result of the page starts reorder mode and not the settings focus") {
                closeThePageAfter(
                    resultCode = Activity.RESULT_OK,
                    result = LauncherSettingsPage.reorderResult(),
                ) { choose(Choices.Columns, 6) }

                compose.waitUntil(30_000) { exists("reorder:hint") }
                settingsButtonFocused().shouldBeFalse()
            }

            test("a home key that ends the page keeps the usual home focus") {
                closeThePageAfter(
                    whileOpen = { send(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) },
                    choices = { choose(Choices.Columns, 6) },
                )

                compose.waitUntil(30_000) { isFocused("tile:pkg.1/.Main") }
                settingsButtonFocused().shouldBeFalse()
            }
        }
    })
