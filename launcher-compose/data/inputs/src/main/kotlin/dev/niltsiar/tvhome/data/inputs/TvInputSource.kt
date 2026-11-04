package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

private const val TAG = "TvHome.Inputs"

/** TvInputManager, read into [TvInput]s. Every call is a binder call: keep them off the main thread. */
internal class TvInputSource(
    private val context: Context,
) {
    private val manager: TvInputManager? = context.getSystemService(TvInputManager::class.java)

    /** Every input the TV lists. HDMI ports carry their state when [withState]; reading it is one more call each. */
    fun inputs(withState: Boolean = true): List<TvInput> =
        try {
            val tv = manager
            tv?.tvInputList.orEmpty().map { it.toInput(tv, withState) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot list the inputs: $e")
            emptyList()
        }

    /** The HDMI port a CEC device's input sits on; null for a port or an input the TV no longer lists. */
    fun parentOf(inputId: String): String? =
        try {
            manager?.getTvInputInfo(inputId)?.parentId
        } catch (e: RuntimeException) {
            null
        }

    /** One item now, then one for each input added, removed, updated or changing state; faster ones are merged. */
    fun changes(): Flow<Unit> =
        callbackFlow {
            val tv = manager
            val callback =
                object : TvInputManager.TvInputCallback() {
                    override fun onInputStateChanged(
                        inputId: String,
                        state: Int,
                    ) {
                        trySend(Unit)
                    }

                    override fun onInputAdded(inputId: String) {
                        trySend(Unit)
                    }

                    override fun onInputRemoved(inputId: String) {
                        trySend(Unit)
                    }

                    override fun onInputUpdated(inputId: String) {
                        trySend(Unit)
                    }
                }
            try {
                tv?.registerCallback(callback, Handler(Looper.getMainLooper()))
            } catch (e: RuntimeException) {
                Log.w(TAG, "cannot follow the inputs: $e")
            }
            trySend(Unit)
            awaitClose { runCatching { tv?.unregisterCallback(callback) } }
        }.conflate()

    private fun TvInputInfo.toInput(
        tv: TvInputManager?,
        withState: Boolean,
    ): TvInput {
        val type =
            when (type) {
                TvInputInfo.TYPE_HDMI -> PortType.Hdmi
                TvInputInfo.TYPE_TUNER -> PortType.Tuner
                else -> PortType.Other
            }
        return TvInput(
            id = id,
            parentId = parentId,
            type = type,
            system = (serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            passthrough = isPassthroughInput,
            hidden = isHidden(context),
            label = loadLabel(context)?.toString(),
            customLabel = loadCustomLabel(context)?.toString(),
            state =
                if (withState && tv != null && isPassthroughInput && type == PortType.Hdmi) {
                    tv.getInputState(id).toPortState()
                } else {
                    PortState.None
                },
        )
    }
}

private fun Int.toPortState(): PortState =
    when (this) {
        TvInputManager.INPUT_STATE_CONNECTED -> PortState.Connected
        TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> PortState.Standby
        TvInputManager.INPUT_STATE_DISCONNECTED -> PortState.Disconnected
        else -> PortState.None
    }
