package dev.niltsiar.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.niltsiar.tvhome.core.model.AppOpened
import dev.niltsiar.tvhome.core.model.startActivitySafely
import dev.niltsiar.tvhome.core.model.tvLaunchIntent

/**
 * Opens the app named by market://launch?id=<package>, the package-name launch the Android TV Remote service sends
 * (Home Assistant's android_tv_remote) and the stock launcher normally answers. It has no UI of its own.
 */
class OpenPackageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // An explicit intent skips the filter, and an opaque URI such as x:y throws on getQueryParameter.
        val packageName = intent.data?.takeIf { it.isHierarchical }?.getQueryParameter("id")
        if (!packageName.isNullOrBlank()) {
            packageManager.tvLaunchIntent(packageName)?.let {
                if (startActivitySafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) (application as AppOpened).opened(it)
            }
        }
        finish()
    }
}
