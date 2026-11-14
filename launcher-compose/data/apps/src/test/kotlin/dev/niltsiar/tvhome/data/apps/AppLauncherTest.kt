package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

val AppLauncherTest by testSuite {
    robolectricTestSuite<AppLauncherTestContent>("default sdk")
}

class AppLauncherTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val tile = AppEntry(key = "com.shop/.Main", packageName = "com.shop", label = "Shop", leanback = true)

                val context = RuntimeEnvironment.getApplication()
                val config = MutableStateFlow(TvConfig(habitHint = true))
                val lastOpened = LastOpened(context, config, Dispatchers.Unconfined)
                val logFile = File(context.filesDir, "launch_log.txt")
                val launchLog = LaunchLog(logFile, config, Dispatchers.Unconfined)

                fun launcher(
                    launchActions: Map<String, String> = emptyMap(),
                    recordOpened: Boolean = true,
                    recordLibraryOpened: Boolean = false,
                ): AppLauncher {
                    config.value =
                        TvConfig(
                            habitHint = true,
                            launchActions = launchActions,
                            cinematicHome = recordOpened,
                            libraryLeastUsed = recordLibraryOpened,
                        )
                    return AppLauncher(context, config, lastOpened, launchLog)
                }

                fun intent(
                    app: AppEntry = tile,
                    action: String? = null,
                    resolves: Boolean = true,
                ) = launchIntent(app, action) { resolves }!!
            }
        } asContextForEach {
            test("a tile starts its own activity in a new task") {
                val intent = intent()

                intent.action shouldBe Intent.ACTION_MAIN
                intent.component?.packageName shouldBe "com.shop"
                intent.component?.className shouldBe "com.shop.Main"
                intent.flags shouldBe (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }

            test("a tv app launches with the leanback category and a phone app without") {
                intent().hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).shouldBeTrue()
                intent(tile.copy(leanback = false)).hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).shouldBeFalse()
            }

            test("a launch action replaces the start screen inside the package") {
                val intent = intent(action = "com.shop.action.FEATURED")

                intent.action shouldBe "com.shop.action.FEATURED"
                intent.`package` shouldBe "com.shop"
                intent.component.shouldBeNull()
                intent.flags shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
            }

            test("a view action opens the uri inside the package") {
                val intent = intent(action = "view: bbox://live?a=b ")

                intent.action shouldBe Intent.ACTION_VIEW
                intent.dataString shouldBe "bbox://live?a=b"
                intent.`package` shouldBe "com.shop"
            }

            test("an action nothing answers keeps the normal launch") {
                intent(action = "com.shop.action.MISSING", resolves = false).component?.packageName shouldBe "com.shop"
            }

            test("an empty view and an input action keep the normal launch") {
                intent(action = "view:", resolves = false).action shouldBe Intent.ACTION_MAIN
                intent(action = "input:HW5").action shouldBe Intent.ACTION_MAIN
            }

            test("a key that is not a component makes no intent") {
                launchIntent(tile.copy(key = "not a component"), null) { true }.shouldBeNull()
            }

            test("a tile with an input action reports its input") {
                val launcher = launcher(mapOf("com.shop" to "input:HW5"))

                launcher.inputId(tile) shouldBe "HW5"
                launcher.inputId(tile.copy(packageName = "com.other")).shouldBeNull()
            }

            test("a tile with an input action switches input without starting anything or noting a launch") {
                val result = launcher(mapOf("com.shop" to "input:HW5")).open(tile)

                result shouldBe LaunchResult.SwitchInput(InputTarget.TvInput("HW5"))
                shadowOf(context).nextStartedActivity.shouldBeNull()
                lastOpened.times.value.shouldBeEmpty()
            }

            test("an opened tile is noted as last opened only when the theme shows it") {
                launcher(recordOpened = false).open(tile) shouldBe LaunchResult.Opened
                lastOpened.times.value.shouldBeEmpty()

                launcher(recordOpened = true).open(tile) shouldBe LaunchResult.Opened
                lastOpened.times.value.keys shouldBe setOf(tile.key)
            }

            test("an app opened from the library is noted as last opened with the cinematic home or a usage key") {
                launcher(recordOpened = false).openFromLibrary(tile).shouldBeTrue()
                lastOpened.times.value.shouldBeEmpty()

                launcher(recordOpened = true).openFromLibrary(tile).shouldBeTrue()
                lastOpened.times.value.keys shouldBe setOf(tile.key)

                val other = tile.copy(key = "com.other/.Main", packageName = "com.other")
                launcher(recordOpened = false, recordLibraryOpened = true).openFromLibrary(other).shouldBeTrue()
                lastOpened.times.value.keys shouldBe setOf(tile.key, other.key)
            }

            test("with the record off no launch is noted from any path") {
                val launcher = launcher(recordOpened = false)

                launcher.open(tile)
                launcher.openFromLibrary(tile)
                launcher.opened(Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.shop", "com.shop.Main")))

                lastOpened.times.value.shouldBeEmpty()
            }

            test("an app started by an intent without a component is noted under the activity it resolves to") {
                val watch = ComponentName("com.tv.live", "com.tv.live.Watch")
                shadowOf(context.packageManager).addActivityIfNotPresent(watch)
                shadowOf(context.packageManager).addIntentFilterForActivity(
                    watch,
                    IntentFilter("com.tv.live.WATCH").apply { addCategory(Intent.CATEGORY_DEFAULT) },
                )

                launcher().opened(Intent("com.tv.live.WATCH").setPackage("com.tv.live"))

                lastOpened.times.value.keys shouldBe setOf("com.tv.live/.Watch")
            }

            test("the same launcher follows the choices as they change") {
                val launcher = launcher(mapOf("com.shop" to "input:HW5"), recordOpened = false)
                launcher.open(tile) shouldBe LaunchResult.SwitchInput(InputTarget.TvInput("HW5"))

                config.value = config.value.copy(launchActions = emptyMap(), cinematicHome = true)
                launcher.open(tile) shouldBe LaunchResult.Opened
                lastOpened.times.value.keys shouldBe setOf(tile.key)

                val other = tile.copy(key = "com.other/.Main", packageName = "com.other")
                config.value = config.value.copy(cinematicHome = false)
                launcher.open(other)
                launcher.openFromLibrary(other)
                lastOpened.times.value.keys shouldBe setOf(tile.key)

                config.value = config.value.copy(unusedFadeDays = 30)
                launcher.openFromLibrary(other)
                lastOpened.times.value.keys shouldBe setOf(tile.key, other.key)
            }

            test("the library opens an app itself whatever its launch action and a failed launch is not noted") {
                val launcher = launcher(mapOf("com.shop" to "input:HW5"), recordLibraryOpened = true)

                launcher.openFromLibrary(tile.copy(key = "not a component")).shouldBeFalse()
                lastOpened.times.value.shouldBeEmpty()

                launcher.openFromLibrary(tile).shouldBeTrue()
                shadowOf(context).nextStartedActivity.component?.className shouldBe "com.shop.Main"
            }

            test("a tile that cannot start reports it and is not noted") {
                val result = launcher().open(tile.copy(key = "not a component"))

                result shouldBe LaunchResult.CantOpen
                lastOpened.times.value.shouldBeEmpty()
            }

            test("an opened tile adds one record with its package to the launch log") {
                launcher(recordOpened = false).open(tile)

                logFile.readLines().map { it.substringAfter(' ') } shouldBe listOf("com.shop")
            }

            test("a tile that switches input or cannot start adds nothing to the launch log") {
                launcher(mapOf("com.shop" to "input:HW5")).open(tile)
                launcher().open(tile.copy(key = "not a component"))

                logFile.exists().shouldBeFalse()
            }
        }
    })
