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
    private val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Test
    fun the_library_and_inputs_actions_ask_for_them() {
        assertEquals(HomeRequest.OpenLibrary, homeRequestOf(Intent(LauncherIntents.APP_LIBRARY)))
        assertEquals(HomeRequest.ShowInputs, homeRequestOf(Intent(LauncherIntents.VIEW_INPUTS)))
    }

    @Test
    fun an_action_sent_through_the_home_intent_still_asks_for_itself() {
        assertEquals(HomeRequest.OpenLibrary, homeRequestOf(Intent(LauncherIntents.APP_LIBRARY).addCategory(Intent.CATEGORY_HOME)))
    }

    @Test
    fun the_home_key_is_the_home_category_alone() {
        assertEquals(HomeRequest.HomeKey, homeRequestOf(home))
        assertEquals(HomeRequest.None, homeRequestOf(Intent(Intent.ACTION_MAIN)))
        assertEquals(HomeRequest.None, homeRequestOf(null))
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
