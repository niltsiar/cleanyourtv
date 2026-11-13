package dev.niltsiar.tvhome

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choice
import dev.niltsiar.tvhome.core.config.Choices
import io.kotest.matchers.booleans.shouldBeFalse
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The real MainActivity over the real repository. It runs on its own SDK level, so Robolectric gives it classes of its own:
 * after another test of the JVM had run, or after a second MainActivity, Home showed no apps. The likely cause is Compose's UI
 * dispatcher, made once per class loader.
 */
val MainActivityConfigTest by testSuite {
    robolectricTestSuite<MainActivityConfigTestContent>(
        "sdk 33, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 33
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class MainActivityConfigTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val app: Application = RuntimeEnvironment.getApplication()
                val graph get() = (app as TvHomeApplication).graph

                val tvWithNineApps =
                    object : ExternalResource() {
                        override fun before() {
                            shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                            for (n in 1..9) installTvApp("pkg.$n")
                        }
                    }

                val compose = createAndroidComposeRule<MainActivity>()

                val rules: RuleChain =
                    rule(
                        RuleChain
                            .outerRule(tvWithNineApps)
                            .around(compose)
                            .around(
                                object : ExternalResource() {
                                    override fun before() = waitForTheTiles()
                                },
                            ),
                    )

                fun waitForTheTiles() {
                    compose.waitUntil(10_000) { tileWidth() != null }
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

                fun tileWidth(): Dp? =
                    if (exists(
                            "tile:pkg.1/.Main",
                        )
                    ) {
                        compose.onNodeWithTag("tile:pkg.1/.Main").getBoundsInRoot().let { it.right - it.left }
                    } else {
                        null
                    }

                fun isFocused(tag: String) =
                    exists(tag) && compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Focused]

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    compose.waitForIdle()
                }
            }
        } asContextForEach {
            test("a stored choice reaches what home shows with no restart") {
                compose.waitUntil(10_000) { isFocused("tile:pkg.1/.Main") }
                val before = tileWidth()!!
                choose(Choices.Columns, 6)
                compose.waitUntil(10_000) { tileWidth()?.let { it < before } == true }

                exists("name:pkg.2/.Main").shouldBeFalse()
                choose(Choices.AlwaysShowNames, true)
                compose.waitUntil(10_000) { exists("name:pkg.2/.Main") }
                choose(Choices.AlwaysShowNames, false)
                compose.waitUntil(10_000) { !exists("name:pkg.2/.Main") }

                exists("library:pill").shouldBeFalse()
                choose(Choices.AppLibrary, true)
                compose.waitUntil(10_000) { exists("library:pill") }
                compose.onNodeWithTag("tile:pkg.1/.Main").requestFocus()
                repeat(2) { press(Key.DirectionDown) }
                compose.onNodeWithTag("library:pill").assertIsFocused()
                press(Key.DirectionCenter)
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitUntil(10_000) { exists("library") }

                choose(Choices.AppLibrary, false)
                compose.waitUntil(10_000) { !exists("library") && !exists("library:pill") }

                choose(Choices.AppLibrary, true)
                compose.waitUntil(10_000) { exists("library:pill") }
                compose.mainClock.advanceTimeBy(1_000)
                exists("library").shouldBeFalse()
            }
        }
    })
