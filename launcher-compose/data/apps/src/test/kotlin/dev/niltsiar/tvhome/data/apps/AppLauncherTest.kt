package dev.niltsiar.tvhome.data.apps

import android.content.Intent
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {
    private val tile = AppEntry(key = "com.shop/.Main", packageName = "com.shop", label = "Shop", leanback = true)

    private val context = RuntimeEnvironment.getApplication()
    private val lastOpened = LastOpened(context, Dispatchers.Unconfined)
    private val logFile = File(context.filesDir, "launch_log.txt")
    private val launchLog = LaunchLog(logFile, enabled = true, io = Dispatchers.Unconfined)

    private fun launcher(
        launchActions: Map<String, String> = emptyMap(),
        recordOpened: Boolean = true,
    ) = AppLauncher(context, launchActions, lastOpened, recordOpened, launchLog)

    private fun intent(
        app: AppEntry = tile,
        action: String? = null,
        resolves: Boolean = true,
    ) = launchIntent(app, action) { resolves }!!

    @Test
    fun a_tile_starts_its_own_activity_in_a_new_task() {
        val intent = intent()

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals("com.shop", intent.component?.packageName)
        assertEquals("com.shop.Main", intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, intent.flags)
    }

    @Test
    fun a_tv_app_launches_with_the_leanback_category_and_a_phone_app_without() {
        assertTrue(intent().hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        assertFalse(intent(tile.copy(leanback = false)).hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
    }

    @Test
    fun a_launch_action_replaces_the_start_screen_inside_the_package() {
        val intent = intent(action = "com.shop.action.FEATURED")

        assertEquals("com.shop.action.FEATURED", intent.action)
        assertEquals("com.shop", intent.`package`)
        assertNull(intent.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test
    fun a_view_action_opens_the_uri_inside_the_package() {
        val intent = intent(action = "view: bbox://live?a=b ")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("bbox://live?a=b", intent.dataString)
        assertEquals("com.shop", intent.`package`)
    }

    @Test
    fun an_action_nothing_answers_keeps_the_normal_launch() {
        assertEquals("com.shop", intent(action = "com.shop.action.MISSING", resolves = false).component?.packageName)
    }

    @Test
    fun an_empty_view_and_an_input_action_keep_the_normal_launch() {
        assertEquals(Intent.ACTION_MAIN, intent(action = "view:", resolves = false).action)
        assertEquals(Intent.ACTION_MAIN, intent(action = "input:HW5").action)
    }

    @Test
    fun a_key_that_is_not_a_component_makes_no_intent() {
        assertNull(launchIntent(tile.copy(key = "not a component"), null) { true })
    }

    @Test
    fun a_tile_with_an_input_action_reports_its_input() {
        val launcher = launcher(mapOf("com.shop" to "input:HW5"))

        assertEquals("HW5", launcher.inputId(tile))
        assertNull(launcher.inputId(tile.copy(packageName = "com.other")))
    }

    @Test
    fun a_tile_with_an_input_action_switches_input_without_starting_anything_or_noting_a_launch() {
        val result = launcher(mapOf("com.shop" to "input:HW5")).open(tile)

        assertEquals(LaunchResult.SwitchInput(InputTarget.TvInput("HW5")), result)
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(emptyMap<String, Long>(), lastOpened.times.value)
    }

    @Test
    fun an_opened_tile_is_noted_as_last_opened_only_when_the_theme_shows_it() {
        assertEquals(LaunchResult.Opened, launcher(recordOpened = false).open(tile))
        assertEquals(emptyMap<String, Long>(), lastOpened.times.value)

        assertEquals(LaunchResult.Opened, launcher(recordOpened = true).open(tile))
        assertEquals(setOf(tile.key), lastOpened.times.value.keys)
    }

    @Test
    fun a_tile_that_cannot_start_reports_it_and_is_not_noted() {
        val result = launcher().open(tile.copy(key = "not a component"))

        assertEquals(LaunchResult.CantOpen, result)
        assertEquals(emptyMap<String, Long>(), lastOpened.times.value)
    }

    @Test
    fun an_opened_tile_adds_one_record_with_its_package_to_the_launch_log() {
        launcher(recordOpened = false).open(tile)

        assertEquals(listOf("com.shop"), logFile.readLines().map { it.substringAfter(' ') })
    }

    @Test
    fun a_tile_that_switches_input_or_cannot_start_adds_nothing_to_the_launch_log() {
        launcher(mapOf("com.shop" to "input:HW5")).open(tile)
        launcher().open(tile.copy(key = "not a component"))

        assertFalse(logFile.exists())
    }
}
