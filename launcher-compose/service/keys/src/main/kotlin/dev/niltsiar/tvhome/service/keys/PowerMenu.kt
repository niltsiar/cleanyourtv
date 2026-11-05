package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService

/**
 * The system power menu (Restart, and Power off where the firmware has it), which a launcher cannot open itself: only
 * the bound [KeyService] can. Home offers Restart while [available].
 */
public object PowerMenu {
    private var bound: KeyService? = null

    internal fun bind(service: KeyService) {
        bound = service
    }

    /** An earlier service that Android destroys after its replacement bound does not unbind the replacement. */
    internal fun unbind(service: KeyService) {
        if (bound === service) bound = null
    }

    public fun available(): Boolean = bound != null

    /** The TV asks before it acts. Does nothing while no service is bound. */
    public fun open() {
        bound?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }
}
