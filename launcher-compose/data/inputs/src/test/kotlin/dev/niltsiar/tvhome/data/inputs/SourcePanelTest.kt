package dev.niltsiar.tvhome.data.inputs

import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourcePanelTest {
    @Test
    fun the_panel_is_the_component_with_its_action_and_one_int_extra() {
        val intent =
            sourcePanelIntent(
                TvConfig(
                    sourcePanelService = "com.coocaa.tv/.ControlCenterService",
                    sourcePanelAction = "com.coocaa.SHOW",
                    sourcePanelExtra = "control_center_command = 100",
                ),
            )!!

        assertEquals("com.coocaa.tv", intent.component?.packageName)
        assertEquals("com.coocaa.SHOW", intent.action)
        assertEquals(100, intent.getIntExtra("control_center_command", -1))
    }

    @Test
    fun an_extra_that_is_not_a_number_is_left_out_and_no_service_means_no_panel() {
        val intent = sourcePanelIntent(TvConfig(sourcePanelService = "a.b/.C", sourcePanelExtra = "key=abc"))!!

        assertEquals(false, intent.hasExtra("key"))
        assertNull(sourcePanelIntent(TvConfig()))
    }
}
