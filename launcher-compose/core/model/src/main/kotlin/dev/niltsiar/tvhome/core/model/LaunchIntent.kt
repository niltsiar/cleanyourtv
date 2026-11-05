package dev.niltsiar.tvhome.core.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

private const val TAG = "TvHome.Launch"

/** The app's TV entry point, or its phone one; null when it is uninstalled or disabled. */
public fun PackageManager.tvLaunchIntent(packageName: String): Intent? =
    getLeanbackLaunchIntentForPackage(packageName) ?: getLaunchIntentForPackage(packageName)

/** Starts [intent]; false, with a log line, when no activity answers it (a disabled app) or a permission blocks it. */
public fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "cannot open $intent: $e")
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "cannot open $intent: $e")
        false
    }
