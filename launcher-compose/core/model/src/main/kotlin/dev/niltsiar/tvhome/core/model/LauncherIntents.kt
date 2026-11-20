package dev.niltsiar.tvhome.core.model

/** Intent actions that reach Home and the clock screensaver from other parts of the app, such as the remote key service. */
public object LauncherIntents {
    /** Open the App library over Home. Sent by the "library" action word of app_keys. */
    public const val APP_LIBRARY: String = "dev.niltsiar.tvhome.action.APP_LIBRARY"

    /** The system's input switcher, which Home opens as its Inputs list. */
    public const val VIEW_INPUTS: String = "com.android.tv.action.VIEW_INPUTS"

    /** Start the clock screensaver as an activity. */
    public const val CLOCK: String = "dev.niltsiar.tvhome.action.CLOCK"

    /** A boolean extra on the Home intent the key service sends when the TV comes on (power_on_home and power_on_home_always). */
    public const val EXTRA_POWER_ON: String = "dev.niltsiar.tvhome.extra.POWER_ON"
}
