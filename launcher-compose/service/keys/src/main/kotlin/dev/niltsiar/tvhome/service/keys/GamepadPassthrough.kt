package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.view.InputDevice

/**
 * A gamepad has all the SOURCE_GAMEPAD bits and the SOURCE_JOYSTICK ones, as dumpsys input reports them on a TCL G07.
 * Either alone is not enough: the Bluetooth voice remote reports a joystick (BT_RC833_U1: 0x01000311), and the firmware's
 * virtual-search keyboard, which isVirtual() misses, reports a gamepad (0x00000701). An Xbox controller reports both
 * (0x01000511).
 */
internal fun isGamepad(sources: Int): Boolean =
    sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD &&
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

/** How long a volume change stops key filtering, with volume_pauses_filter. */
internal const val VOLUME_PAUSE_MS: Long = 4_000

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

/**
 * Stops the key service filtering keys while a game controller is connected (gamepad_passthrough). With key filtering on,
 * Android routes every key through the accessibility layer and re-injects the ones the service passes on, and games (Steam
 * Link with an Xbox controller, on a TCL G07) stop seeing the controller's buttons as the controller's. The remote buttons
 * the service handles do nothing until the controller disconnects; filtering then comes back.
 *
 * With volume_pauses_filter it also stops filtering for [VOLUME_PAUSE_MS] after each volume change. On a Xiaomi MOEU0
 * (Google TV 14) the window policy changes volume once per press while any service filters keys, so a held volume key
 * never repeats, and the service never sees the volume keys, so consume_volume_keys cannot repeat them either. The first
 * press steps once, a press or hold within the pause repeats as usual, and the remote buttons work again once it ends.
 *
 * Started only with key_filter on too: with it off the service never filters, and this must not turn filtering back on.
 * [onFilterOff] runs each time filtering stops, as no more keys come, the UP of a button held right now included.
 */
internal class GamepadPassthrough(
    private val service: AccessibilityService,
    private val gamepads: Boolean,
    private val volumePauses: Boolean,
    private val timer: Timer,
    private val onFilterOff: () -> Unit,
) : InputManager.InputDeviceListener {
    private val input = service.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val resume = Runnable { update() }
    private val volumeChanged =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                timer.cancel(resume)
                timer.post(resume, VOLUME_PAUSE_MS)
                setFilter(false)
            }
        }

    fun start() {
        // Only for gamepads: a device event ends a volume pause early.
        if (gamepads) input.registerInputDeviceListener(this, null)
        // A protected system broadcast, so Android 14 needs no exported flag.
        if (volumePauses) service.registerReceiver(volumeChanged, IntentFilter(VOLUME_CHANGED_ACTION))
        update()
    }

    fun stop() {
        if (gamepads) input.unregisterInputDeviceListener(this)
        if (volumePauses) service.unregisterReceiver(volumeChanged)
        timer.cancel(resume)
    }

    private fun gamepadConnected(): Boolean =
        input.inputDeviceIds.any { id ->
            input.getInputDevice(id)?.let { !it.isVirtual && isGamepad(it.sources) } == true
        }

    /** Filtering as the gamepads want it; also the end of a volume pause. */
    private fun update() = setFilter(!(gamepads && gamepadConnected()))

    private fun setFilter(filter: Boolean) {
        val info = service.serviceInfo ?: return
        val filtering = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (filter == filtering) return
        if (!filter) onFilterOff()
        info.flags = info.flags xor AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        service.serviceInfo = info
    }

    override fun onInputDeviceAdded(deviceId: Int) = update()

    override fun onInputDeviceRemoved(deviceId: Int) = update()

    override fun onInputDeviceChanged(deviceId: Int) = update()
}
