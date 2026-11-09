package dev.niltsiar.tvhome

import android.content.Intent
import android.content.pm.PackageManager
import dev.niltsiar.tvhome.core.model.LauncherIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeRequestsTest {
    private companion object {
        const val PACKAGE = "dev.niltsiar.tvhome"
    }

    private val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Test
    fun the_library_and_inputs_actions_ask_for_them() {
        assertEquals(HomeRequest.OpenLibrary, homeRequestOf(Intent(LauncherIntents.APP_LIBRARY), PACKAGE))
        assertEquals(HomeRequest.ShowInputs, homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS), PACKAGE))
    }

    @Test
    fun the_launcher_settings_action_of_this_package_opens_the_page_and_no_other_does() {
        assertEquals(
            HomeRequest.OpenLauncherSettings,
            homeRequestOf(Intent(launcherSettingsAction(PACKAGE)), PACKAGE),
        )
        assertEquals("dev.example.tv.action.LAUNCHER_SETTINGS", launcherSettingsAction("dev.example.tv"))
        assertEquals(HomeRequest.None, homeRequestOf(Intent(launcherSettingsAction("dev.example.tv")), PACKAGE))
        assertEquals(HomeRequest.None, homeRequestOf(Intent(LauncherIntents.CLOCK), PACKAGE))
        assertEquals(
            HomeRequest.None,
            homeRequestOf(Intent(launcherSettingsAction(PACKAGE)).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), PACKAGE),
        )
    }

    @Test
    fun an_action_sent_through_the_home_intent_still_asks_for_itself() {
        assertEquals(HomeRequest.OpenLibrary, homeRequestOf(Intent(LauncherIntents.APP_LIBRARY).addCategory(Intent.CATEGORY_HOME), PACKAGE))
    }

    @Test
    fun an_intent_the_system_replays_from_history_asks_for_nothing() {
        val fromHistory = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

        assertEquals(HomeRequest.None, homeRequestOf(Intent(LauncherIntents.APP_LIBRARY).addFlags(fromHistory), PACKAGE))
        assertEquals(HomeRequest.None, homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS).addFlags(fromHistory), PACKAGE))
        assertEquals(
            HomeRequest.HomeKey,
            homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS).addCategory(Intent.CATEGORY_HOME).addFlags(fromHistory), PACKAGE),
        )
    }

    @Test
    fun the_home_key_is_the_home_category_alone() {
        assertEquals(HomeRequest.HomeKey, homeRequestOf(home, PACKAGE))
        assertEquals(HomeRequest.None, homeRequestOf(Intent(Intent.ACTION_MAIN), PACKAGE))
        assertEquals(HomeRequest.None, homeRequestOf(null, PACKAGE))
    }

    @Test
    fun the_view_inputs_handler_is_enabled_when_asked_and_back_to_the_manifest_default_when_not() {
        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val default = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

        assertEquals(enabled, viewInputsHandlerChange(on = true, current = default))
        assertEquals(default, viewInputsHandlerChange(on = false, current = enabled))
    }

    @Test
    fun the_view_inputs_handler_is_not_written_when_it_already_is_as_asked() {
        assertNull(viewInputsHandlerChange(on = true, current = PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
        assertNull(viewInputsHandlerChange(on = false, current = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
    }
}
