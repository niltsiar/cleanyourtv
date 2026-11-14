package dev.niltsiar.tvhome

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Environment
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

/**
 * The real MainActivity as it starts and as the screen wakes, over a theme file and a store written before the graph reads
 * them. It runs on an SDK level no other test class uses, for the reason MainActivityConfigTest gives.
 */
val MainActivityStartTest by testSuite {
    robolectricTestSuite<MainActivityStartTestContent>(
        "sdk 35, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 35
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class MainActivityStartTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createEmptyComposeRule())

                val app: Application = RuntimeEnvironment.getApplication()
                val graph get() = (app as TvHomeApplication).graph

                /** The theme file has to be on disk before the graph first reads it. */
                fun writeTheme(json: String) {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(app).dir!!, "config.json").writeText(json)
                }

                /** Stores choices through a repository of its own, ended before the graph's repository opens the same file. */
                fun store(choices: suspend ConfigRepository.() -> Unit) {
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    runBlocking {
                        ConfigRepository(app, scope).choices()
                        scope.coroutineContext.job.cancelAndJoin()
                    }
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

                fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

                fun showHome(): ActivityController<MainActivity> {
                    for (n in 1..9) installTvApp("pkg.$n")
                    return Robolectric.buildActivity(MainActivity::class.java).setup()
                }

                /** The tiles in the dock's row, null before the first tile is laid out. */
                fun dockRowTiles(): Int? {
                    val tiles =
                        compose
                            .onAllNodes(
                                SemanticsMatcher("a tile") {
                                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") ==
                                        true
                                },
                            ).fetchSemanticsNodes()
                    val first = tiles.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "tile:pkg.1/.Main" } ?: return null
                    return tiles.count { it.boundsInRoot.top == first.boundsInRoot.top }
                }

                fun pick(pkg: String) {
                    runBlocking { graph.configRepository.set(Choices.OpenOnStart, pkg) }
                    compose.waitUntil(30_000) { graph.configRepository.openOnStartApp.value == pkg }
                }

                fun screenOn() {
                    app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
                    compose.waitForIdle()
                }
            }
        } asContextForEach {
            // The app library pill is drawn before the apps arrive, so the first composition shows whether its choice reached it.
            test("the first frame home draws has the stored choices and never the themes") {
                writeTheme("""{"columns": 5, "app_library": false}""")
                store {
                    set(Choices.Columns, 7)
                    set(Choices.AppLibrary, true)
                }
                compose.mainClock.autoAdvance = false

                showHome()
                withClue("no library pill in the first composition") { exists("library:pill").shouldBeTrue() }
                val drawn = mutableListOf<Int?>()
                compose.waitUntil(30_000) {
                    compose.mainClock.advanceTimeByFrame()
                    drawn += dockRowTiles()
                    drawn.last() != null
                }
                repeat(10) {
                    compose.mainClock.advanceTimeByFrame()
                    drawn += dockRowTiles()
                }

                withClue("$drawn") { drawn.filterNotNull().distinct() shouldBe listOf(7) }
                graph.configRepository.config.value.columns shouldBe 7
            }

            test("with open on start on wake a screen on while home is in front opens the picked app") {
                writeTheme("""{"open_on_start_choice": true, "open_on_start_on_wake": true}""")
                val home = showHome()
                pick("pkg.5")
                compose.waitForIdle()
                shadowOf(home.get()).peekNextStartedActivity().shouldBeNull()

                screenOn()

                compose.waitUntil(30_000) { shadowOf(home.get()).peekNextStartedActivity() != null }
                shadowOf(home.get()).nextStartedActivity.component shouldBe ComponentName("pkg.5", "pkg.5.Main")
            }

            // The boot and the wake open the pick through the same MainActivity call, so the wake covers both. unused_fade_days
            // turns the record on as cinematic_home does; the cinematic Home cannot draw under Robolectric.
            test("with the record on the app open on start opens is noted under its home tile key") {
                writeTheme("""{"unused_fade_days": 30, "open_on_start_choice": true, "open_on_start_on_wake": true}""")
                val home = showHome()
                pick("pkg.5")
                compose.waitForIdle()

                screenOn()

                compose.waitUntil(30_000) { shadowOf(home.get()).peekNextStartedActivity() != null }
                runBlocking { graph.homeSources.lastOpened.first() }.keys shouldBe setOf("pkg.5/.Main")
            }

            test("without open on start on wake a screen on opens nothing") {
                writeTheme("""{"open_on_start_choice": true}""")
                val home = showHome()
                pick("pkg.5")
                compose.waitForIdle()

                screenOn()

                shadowOf(home.get()).peekNextStartedActivity().shouldBeNull()
                shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }.shouldBeFalse()
            }
        }
    })
