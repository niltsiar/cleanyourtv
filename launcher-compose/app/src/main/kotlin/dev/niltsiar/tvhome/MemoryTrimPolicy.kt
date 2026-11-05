package dev.niltsiar.tvhome

import android.content.ComponentCallbacks2

/**
 * Whether the picture's memory goes at [level] of onTrimMemory. Only while Home is not on screen: RUNNING_LOW and
 * CRITICAL also arrive while it is visible, and UI_HIDDEN is the ordinary trip to an app, where keeping the tiles is
 * what makes the return instant.
 */
@Suppress("DEPRECATION") // RUNNING_LOW and CRITICAL are deprecated levels the reference launcher also acts on
internal fun shouldReleaseOnTrim(
    level: Int,
    homeStarted: Boolean,
): Boolean =
    !homeStarted &&
        (
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        )
