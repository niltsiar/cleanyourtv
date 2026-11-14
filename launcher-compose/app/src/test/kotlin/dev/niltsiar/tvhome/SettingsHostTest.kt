package dev.niltsiar.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.Settings
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.data.shelf.READ_TV_LISTINGS
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.OptionLabel
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

private const val SHELF_ON = """{"app_shelf": true}"""
private const val ALLOW_ACCESS = "Allow access in App info, under Permissions"

/** The permission dialog's action, its answer's extras and the caller the framework gives it; the SDK keeps them hidden. */
private const val REQUEST_PERMISSIONS = "android.content.pm.action.REQUEST_PERMISSIONS"
private const val PERMISSION_NAMES = "android.content.pm.extra.REQUEST_PERMISSIONS_NAMES"
private const val PERMISSION_RESULTS = "android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS"
private const val PERMISSIONS_WHO = "@android:requestPermissions:"
private val PermissionController = ComponentName("org.robolectric.permissioncontroller", "org.robolectric.permissioncontroller.Grant")

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

                /** The page over a theme with one picture and [config], with TV settings that do not open. */
                fun show(config: String = "{}") {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    val theme = ThemeDirectory(compose.activity)
                    File(theme.dir!!, "config.json").writeText(config)
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

                fun shelfAccessAsked(): Boolean =
                    shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions?.toList() == listOf(READ_TV_LISTINGS)

                /** OK on the Apps and channels row, with the system's [rationale] for READ_TV_LISTINGS at that time. */
                fun openShelfSources(rationale: Boolean) {
                    shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(READ_TV_LISTINGS, rationale)
                    val before = shadowOf(compose.activity).lastRequestedPermission
                    compose.onNodeWithTag("settings:shelf_sources").performSemanticsAction(SemanticsActions.OnClick)
                    compose.waitUntil(30_000) { shadowOf(compose.activity).lastRequestedPermission !== before }
                }

                /**
                 * The system refuses the last request for READ_TV_LISTINGS, with [rationale] to show after it. The answer goes
                 * through the framework's dispatch, which ends the request: an activity asks again only once it has its answer.
                 */
                fun refuseShelfAccess(rationale: Boolean) {
                    val request = shadowOf(compose.activity).lastRequestedPermission
                    shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(READ_TV_LISTINGS, rationale)
                    val answer =
                        Intent()
                            .putExtra(PERMISSION_NAMES, request.requestedPermissions)
                            .putExtra(PERMISSION_RESULTS, intArrayOf(PackageManager.PERMISSION_DENIED))
                    val page = shadowOf(compose.activity)
                    compose.runOnUiThread {
                        page.internalCallDispatchActivityResult(PERMISSIONS_WHO, request.requestCode, Activity.RESULT_OK, answer)
                    }
                    compose.waitForIdle()
                }

                /** The activities started since the last call, the permission request among them. */
                fun started(): List<Intent> = generateSequence { shadowOf(compose.activity.application).nextStartedActivity }.toList()

                fun noticeShows(text: String) =
                    compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("settings:notice"))).fetchSemanticsNodes().isNotEmpty()
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

            test("the apps and channels picker without read tv listings asks for it and tells nothing in the page") {
                show()

                viewModel.actions.onOpenShelfPicker()
                compose.waitUntil(30_000) { shelfAccessAsked() }
                compose.waitForIdle()

                exists("settings:notice").shouldBeFalse()
            }

            test("the apps and channels picker with nothing to list says so in the page and asks for nothing") {
                shadowOf(compose.activity.application).grantPermissions(READ_TV_LISTINGS)
                show()

                viewModel.actions.onOpenShelfPicker()
                compose.waitUntil(30_000) { exists("settings:notice") }

                noticeSays("No app shares items to show")
                shelfAccessAsked().shouldBeFalse()
            }

            test("ok on apps and channels after a refusal the system no longer asks about says where to allow access and opens app info") {
                show(SHELF_ON)

                openShelfSources(rationale = false)
                refuseShelfAccess(rationale = false)

                noticeSays(ALLOW_ACCESS)
                val opened = started().single { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS }
                opened.data shouldBe "package:${compose.activity.packageName}".toUri()
            }

            test("when app info cannot open, the notice alone says where to allow access") {
                // Only the permission request resolves: App info has no activity to open.
                shadowOf(compose.activity.packageManager).apply {
                    addActivityIfNotPresent(PermissionController)
                    addIntentFilterForActivity(
                        PermissionController,
                        IntentFilter(REQUEST_PERMISSIONS).apply { addCategory(Intent.CATEGORY_DEFAULT) },
                    )
                }
                shadowOf(compose.activity.application).checkActivities(true)
                show(SHELF_ON)

                openShelfSources(rationale = false)
                refuseShelfAccess(rationale = false)

                noticeSays(ALLOW_ACCESS)
                started().map { it.action } shouldNotContain Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            }

            test("a refusal made in the dialog, the first or the one the system stops asking after, opens nothing") {
                show(SHELF_ON)
                for ((before, after) in listOf(false to true, true to false)) {
                    openShelfSources(rationale = before)
                    refuseShelfAccess(rationale = after)

                    withClue("rationale $before before, $after after") {
                        noticeShows(ALLOW_ACCESS).shouldBeFalse()
                        started().map { it.action } shouldNotContain Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                }
            }

            test("coming back to the page reads read tv listings again") {
                show(SHELF_ON)
                viewModel.uiState.value.shelfSources shouldBe OptionLabel.Res(SettingsR.string.settings_shelf_no_access)

                shadowOf(compose.activity.application).grantPermissions(READ_TV_LISTINGS)
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

                compose.waitUntil(30_000) {
                    viewModel.uiState.value.shelfSources == OptionLabel.Res(SettingsR.string.settings_shelf_none)
                }
            }
        }
    })
