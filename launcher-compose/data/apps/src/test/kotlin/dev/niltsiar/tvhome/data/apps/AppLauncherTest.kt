package dev.niltsiar.tvhome.data.apps

import android.content.Intent
import dev.niltsiar.tvhome.core.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {
    private val tile = AppEntry(key = "com.shop/.Main", packageName = "com.shop", label = "Shop", leanback = true)

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
}
