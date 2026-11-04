package dev.niltsiar.tvhome.data.inputs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import dev.niltsiar.tvhome.core.config.TvConfig

/** A live_tv_input_id of this value asks the Live TV app to leave any HDMI source selected by its own UI (#1941). */
private val RootChannelUri = TvContract.Channels.CONTENT_URI.toString()

/** The Live TV app of live_tv_package: where its launch intent and its tuner come from. */
internal class LiveTv(
    private val context: Context,
    private val config: TvConfig,
) {
    private val rootChannel = config.liveTvInputId == RootChannelUri

    /** The app's leanback entry point, or the live_tv_action when it has none and something answers it; null without the app. */
    fun appIntent(): Intent? {
        val pkg = config.liveTvPackage ?: return null
        val pm = context.packageManager
        val leanback = pm.getLeanbackLaunchIntentForPackage(pkg)
        val action = config.liveTvAction
        if (leanback != null || action == null) return leanback
        return Intent(action).setPackage(pkg).takeIf { pm.resolveActivity(it, 0) != null }
    }

    /** The tuner input to tune, by the rules of [chooseTuner]. */
    fun tuner(inputs: List<TvInput>): String? = chooseTuner(inputs, config.liveTvInputId, rootChannel)

    /** What the Live TV row opens: [appIntent], or the tuner's channels when the Live TV package itself handles them. */
    fun intent(tunerId: String?): Intent? {
        val app = appIntent() ?: return null
        val pkg = config.liveTvPackage ?: return app
        return tunedIntent(app, tunerId, pkg, config.liveTvInputId) { it.resolveActivity(context.packageManager) }
    }
}

/**
 * Tunes [tunerId] only when the Live TV package itself handles the URI. The unrestricted input player also resolves
 * it (that is how HDMI opens), but on the AIWA AI PONT SA that player stays black, and the Live TV package may not
 * handle the URI at all: then [app], the same path as the Live TV tile, stays. An explicit tuner uses its channel
 * collection (DVB-S rather than DVB-T/C); the automatic one keeps the passthrough route that some OEM players need.
 * With the root channel URI as live_tv_input_id the stock launcher's way is used: that URI, sent to the handler (#1941).
 */
internal fun tunedIntent(
    app: Intent,
    tunerId: String?,
    pkg: String,
    liveTvInputId: String?,
    handler: (Intent) -> ComponentName?,
): Intent {
    if (tunerId == null) return app
    val rootChannel = liveTvInputId == RootChannelUri
    val configured = liveTvInputId != null && !rootChannel
    val tuned =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                if (configured) TvContract.buildChannelsUriForInput(tunerId) else TvContract.buildChannelUriForPassthroughInput(tunerId),
                if (configured) TvContract.Channels.CONTENT_TYPE else TvContract.Channels.CONTENT_ITEM_TYPE,
            ).setPackage(pkg)
    val component = handler(tuned) ?: return app
    return if (rootChannel) Intent(Intent.ACTION_VIEW).setData(TvContract.Channels.CONTENT_URI).setComponent(component) else tuned
}
