package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
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

/**
 * Stops the key service filtering keys while a game controller is connected. With key filtering on, Android routes every
 * key through the accessibility layer and re-injects the ones the service passes on, and games (Steam Link with an Xbox
 * controller, on a TCL G07) stop seeing the controller's buttons as the controller's. The remote buttons the service
 * handles do nothing until the controller disconnects; filtering then comes back. Started only with key_filter on too: with
 * it off the service never filters, and this must not turn filtering back on.
 */
internal class GamepadPassthrough(
    private val service: AccessibilityService,
) : InputManager.InputDeviceListener {
    private val input = service.getSystemService(Context.INPUT_SERVICE) as InputManager

    fun start() {
        input.registerInputDeviceListener(this, null)
        update()
    }

    fun stop() {
        input.unregisterInputDeviceListener(this)
    }

    private fun gamepadConnected(): Boolean =
        input.inputDeviceIds.any { id ->
            input.getInputDevice(id)?.let { !it.isVirtual && isGamepad(it.sources) } == true
        }

    private fun update() {
        val info = service.serviceInfo ?: return
        val filter = !gamepadConnected()
        val filtering = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (filter == filtering) return
        // Without the flag no more keys come, the UP of a button held right now included.
        if (!filter) SharedPresses.release(service)
        info.flags = info.flags xor AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        service.serviceInfo = info
    }

    override fun onInputDeviceAdded(deviceId: Int) = update()

    override fun onInputDeviceRemoved(deviceId: Int) = update()

    override fun onInputDeviceChanged(deviceId: Int) = update()
}
