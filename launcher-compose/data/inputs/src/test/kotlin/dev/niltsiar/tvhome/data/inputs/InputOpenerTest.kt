package dev.niltsiar.tvhome.data.inputs

import android.content.Intent
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PORT = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
private const val CHANNEL_TYPE = "vnd.android.cursor.item/channel"

@RunWith(RobolectricTestRunner::class)
class InputOpenerTest {
    private val started = mutableListOf<Intent>()
    private var refuse: (Intent) -> Boolean = { false }

    private fun open(
        config: TvConfig = TvConfig(liveTvPackage = null),
        id: String = PORT,
        parent: String? = null,
    ): Boolean =
        InputOpener(config) { intent ->
            started += intent
            !refuse(intent)
        }.open(id, parent)

    @Test
    fun the_standard_way_is_a_view_of_the_channel_uri_with_a_mime_type() {
        assertTrue(open())

        val view = started.single()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("content://android.media.tv/passthrough/" + android.net.Uri.encode(PORT), view.dataString)
        assertEquals(CHANNEL_TYPE, view.type)
        assertTrue(view.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun the_live_tv_package_is_tried_first_so_that_no_chooser_shows_and_then_anything() {
        refuse = { it.`package` != null }

        assertTrue(open(TvConfig(liveTvPackage = "com.tv.live")))

        assertEquals(listOf<String?>("com.tv.live", null), started.map { it.`package` })
    }

    @Test
    fun a_refusal_does_not_stop_the_fallbacks_and_a_total_refusal_returns_false() {
        refuse = { true }

        assertFalse(open(TvConfig(liveTvPackage = "com.tv.live")))

        assertEquals(2, started.size)
    }

    @Test
    fun an_input_action_gets_the_same_uri_where_nothing_takes_the_view() {
        refuse = { it.action == Intent.ACTION_VIEW }

        assertTrue(open(TvConfig(liveTvPackage = null, inputAction = "com.xgimi.action.hdmiPlayer")))

        val last = started.last()
        assertEquals("com.xgimi.action.hdmiPlayer", last.action)
        assertEquals(CHANNEL_TYPE, last.type)
        assertEquals(started.first().data, last.data)
    }

    @Test
    fun a_mapped_input_sends_the_input_action_with_its_source_number() {
        val config = TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY", inputSources = listOf("HW5=23"))

        assertTrue(open(config))

        val sent = started.single()
        assertEquals("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY", sent.action)
        assertEquals(23, sent.getIntExtra("input", -1))
    }

    @Test
    fun a_cec_device_uses_the_source_number_of_its_port_but_opens_its_own_input() {
        val config = TvConfig(liveTvPackage = null, inputAction = "vendor.PLAY", inputSources = listOf("HW5=23"))
        refuse = { it.action == "vendor.PLAY" }

        assertTrue(open(config, id = "com.mediatek.tvinput/.cec/CEC7", parent = PORT))

        assertEquals(listOf("vendor.PLAY", Intent.ACTION_VIEW), started.map { it.action })
        assertEquals(23, started[0].getIntExtra("input", -1))
        assertTrue(started[1].dataString!!.contains("CEC7"))
    }

    @Test
    fun an_activity_in_input_action_starts_itself_ahead_of_the_standard_way_for_unmapped_inputs() {
        val config = TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer/.ExternalSourceActivity")

        assertTrue(open(config))

        val sent = started.single()
        assertEquals("com.xiaomi.mitv.tvplayer", sent.component?.packageName)
        assertNull(sent.data)
    }

    @Test
    fun a_failed_vendor_picker_falls_through_to_the_standard_way() {
        refuse = { it.component != null }

        assertTrue(open(TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer/.ExternalSourceActivity")))

        assertEquals(Intent.ACTION_VIEW, started.last().action)
    }

    @Test
    fun an_input_mapped_to_a_dash_skips_the_vendor_route_and_a_scheme_replaces_the_uri_scheme_and_the_type() {
        val config = TvConfig(liveTvPackage = null, inputAction = "vendor.PLAY", inputSources = listOf("HW5=-"), inputUriScheme = "live-tv")

        assertTrue(open(config))

        val view = started.single()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("live-tv", view.data?.scheme)
        assertNull(view.type)
    }
}
