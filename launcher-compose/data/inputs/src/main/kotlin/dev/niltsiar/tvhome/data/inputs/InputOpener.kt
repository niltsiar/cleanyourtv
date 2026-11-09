package dev.niltsiar.tvhome.data.inputs

import android.content.ComponentName
import android.content.Intent
import android.media.tv.TvContract
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.flow.StateFlow

private const val CHANNEL_TYPE = "vnd.android.cursor.item/channel"

/**
 * Switches to a passthrough input (HDMI, AV). [start] starts an activity and says whether one started.
 *
 * An input mapped in `input_sources` is opened by sending `input_action` with its source number as the int extra
 * "input" (Xiaomi China firmware, whose tvplayer answers com.xiaomi.mitv.tvplayer.EXTSRC_PLAY and nothing else).
 * An `input_action` written as "package/.Activity" starts that activity itself, ahead of the standard way, for
 * firmware that answers the channel URI with "unsupported" (MiTV-NSOP0). Otherwise the standard way is ACTION_VIEW
 * on the input's channel URI, which the Live TV app handles; where nothing takes that, `input_action` is sent
 * with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). With `input_uri_scheme`
 * set (YaOS: live-tv) the same URI goes out with that scheme and no MIME type. A player guarded by a permission
 * this build does not declare refuses with SecurityException, which must not take Home down with it.
 */
internal class InputOpener(
    private val config: StateFlow<TvConfig>,
    private val start: (Intent) -> Boolean,
) {
    /** True when an activity started. [parentId] is the HDMI port of a CEC device: the vendor source number belongs to the port (MiTV-MFFU0). */
    fun open(
        inputId: String,
        parentId: String?,
    ): Boolean {
        val current = config.value
        val action = current.inputAction
        val source = mappedSource(current.inputSources, parentId ?: inputId)
        if (source != null && source >= 0 && action != null) {
            if (start(actionIntent(action).putExtra("input", source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
        } else if (source == null && action != null && isComponent(action)) {
            // The vendor's own source picker: the overlay the remote's Source button opens.
            if (start(actionIntent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        val scheme = current.inputUriScheme
        val view =
            (
                if (scheme == null) {
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, CHANNEL_TYPE)
                } else {
                    Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(scheme).build())
                }
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Aim it at the TV app first: apps installed later can claim the same channel URI, and with two or more
        // handlers Android shows a chooser instead of the input (WhaleOS).
        current.liveTvPackage?.let { if (start(Intent(view).setPackage(it))) return true }
        if (start(view)) return true
        if (action == null) return false
        return start(actionIntent(action).setDataAndType(uri, CHANNEL_TYPE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** `input_action` as an intent: an explicit component when it reads "package/.Activity", else an action. */
    private fun actionIntent(action: String): Intent {
        val component = if (isComponent(action)) ComponentName.unflattenFromString(action) else null
        return if (component != null) Intent().setComponent(component) else Intent(action)
    }
}
