package dev.niltsiar.tvhome.core.model

import android.content.Intent
import android.content.pm.PackageManager

/** The app's TV entry point, or its phone one; null when it is uninstalled or disabled. */
public fun PackageManager.tvLaunchIntent(packageName: String): Intent? =
    getLeanbackLaunchIntentForPackage(packageName) ?: getLaunchIntentForPackage(packageName)
