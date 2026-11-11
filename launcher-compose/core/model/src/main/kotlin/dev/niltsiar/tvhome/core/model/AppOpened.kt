package dev.niltsiar.tvhome.core.model

import android.content.Intent

/**
 * Notes that this launcher just started the app [Intent] names, for the record of when it last opened each app. The
 * Application implements it: the key service cannot depend on the app module, whose graph holds the record.
 */
public fun interface AppOpened {
    public fun opened(intent: Intent)
}
