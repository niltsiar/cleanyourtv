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
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/**
 * The real MainActivity as it starts and as the screen wakes, over a theme file and a store written before the graph reads
 * them. It runs on an SDK level no other test class uses, for the reason MainActivityConfigTest gives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TvHomeApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class MainActivityStartTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val graph get() = (app as TvHomeApplication).graph

    /** The theme file has to be on disk before the graph first reads it. */
    private fun writeTheme(json: String) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(json)
    }

    /** Stores choices through a repository of its own, ended before the graph's repository opens the same file. */
    private fun store(choices: suspend ConfigRepository.() -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking {
            ConfigRepository(app, scope).choices()
            scope.coroutineContext.job.cancelAndJoin()
        }
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

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun showHome(): ActivityController<MainActivity> {
        for (n in 1..9) installTvApp("pkg.$n")
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    /** The tiles in the dock's row, null before the first tile is laid out. */
    private fun dockRowTiles(): Int? {
        val tiles =
            compose
                .onAllNodes(SemanticsMatcher("a tile") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true })
                .fetchSemanticsNodes()
        val first = tiles.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "tile:pkg.1/.Main" } ?: return null
        return tiles.count { it.boundsInRoot.top == first.boundsInRoot.top }
    }

    /** The app library pill is drawn before the apps arrive, so the first composition shows whether its choice reached it. */
    @Test
    fun the_first_frame_home_draws_has_the_stored_choices_and_never_the_themes() {
        writeTheme("""{"columns": 5, "app_library": false}""")
        store {
            set(Choices.Columns, 7)
            set(Choices.AppLibrary, true)
        }
        compose.mainClock.autoAdvance = false

        showHome()
        assertTrue("no library pill in the first composition", exists("library:pill"))
        val drawn = mutableListOf<Int?>()
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            drawn += dockRowTiles()
            drawn.last() != null
        }
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            drawn += dockRowTiles()
        }

        assertEquals("$drawn", listOf(7), drawn.filterNotNull().distinct())
        assertEquals(7, graph.configRepository.config.value.columns)
    }

    private fun pick(pkg: String) {
        runBlocking { graph.configRepository.set(Choices.OpenOnStart, pkg) }
        compose.waitUntil(10_000) { graph.configRepository.openOnStartApp.value == pkg }
    }

    private fun screenOn() {
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        compose.waitForIdle()
    }

    @Test
    fun with_open_on_start_on_wake_a_screen_on_while_home_is_in_front_opens_the_picked_app() {
        writeTheme("""{"open_on_start_choice": true, "open_on_start_on_wake": true}""")
        val home = showHome()
        pick("pkg.5")
        compose.waitForIdle()
        assertNull(shadowOf(home.get()).peekNextStartedActivity())

        screenOn()

        compose.waitUntil(10_000) { shadowOf(home.get()).peekNextStartedActivity() != null }
        assertEquals(ComponentName("pkg.5", "pkg.5.Main"), shadowOf(home.get()).nextStartedActivity.component)
    }

    @Test
    fun without_open_on_start_on_wake_a_screen_on_opens_nothing() {
        writeTheme("""{"open_on_start_choice": true}""")
        val home = showHome()
        pick("pkg.5")
        compose.waitForIdle()

        screenOn()

        assertNull(shadowOf(home.get()).peekNextStartedActivity())
        assertFalse(shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) })
    }
}
