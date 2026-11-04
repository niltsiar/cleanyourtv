package dev.niltsiar.tvhome.data.inputs

import android.content.ComponentName
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TUNER = "com.mediatek.tvinput/.tuner.TunerInputService/HW1"
private val Handler = ComponentName("com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity")

@RunWith(RobolectricTestRunner::class)
class LiveTvTest {
    private val app = Intent(Intent.ACTION_MAIN).setPackage("com.mediatek.wwtv.tvcenter")

    private fun tuned(
        tunerId: String? = TUNER,
        inputId: String? = null,
        handler: ComponentName? = Handler,
    ) = tunedIntent(app, tunerId, "com.mediatek.wwtv.tvcenter", inputId) { handler }

    @Test
    fun the_automatic_tuner_is_the_passthrough_channel_of_the_tuner_in_the_live_tv_package() {
        val intent = tuned()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(TvContract.buildChannelUriForPassthroughInput(TUNER), intent.data)
        assertEquals(TvContract.Channels.CONTENT_ITEM_TYPE, intent.type)
        assertEquals("com.mediatek.wwtv.tvcenter", intent.`package`)
    }

    @Test
    fun a_configured_tuner_opens_its_channel_collection() {
        val intent = tuned(inputId = TUNER)

        assertEquals(TvContract.buildChannelsUriForInput(TUNER), intent.data)
        assertEquals(TvContract.Channels.CONTENT_TYPE, intent.type)
    }

    @Test
    fun the_root_channel_uri_goes_to_the_component_that_handles_the_tuned_intent() {
        val intent = tuned(inputId = TvContract.Channels.CONTENT_URI.toString())

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(TvContract.Channels.CONTENT_URI, intent.data)
        assertEquals(Handler, intent.component)
        assertNull(intent.`package`)
    }

    @Test
    fun the_tile_path_stays_where_the_live_tv_package_does_not_handle_the_tuner_uri_or_there_is_no_tuner() {
        assertSame(app, tuned(handler = null))
        assertSame(app, tuned(tunerId = null))
        assertSame(app, tuned(tunerId = null, inputId = TvContract.Channels.CONTENT_URI.toString()))
        assertEquals(Uri.parse("content://android.media.tv/channel"), TvContract.Channels.CONTENT_URI)
    }
}
