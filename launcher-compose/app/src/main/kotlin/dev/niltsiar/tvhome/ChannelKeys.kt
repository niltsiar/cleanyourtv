package dev.niltsiar.tvhome

import android.view.KeyEvent

/** Whether Home consumes the key: Channel Up and Down with home_ignore_channel_keys on, whatever the action. */
internal fun isIgnoredChannelKey(
    ignoreChannelKeys: Boolean,
    keyCode: Int,
): Boolean = ignoreChannelKeys && (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN)
