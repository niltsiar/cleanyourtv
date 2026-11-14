package dev.niltsiar.tvhome.data.shelf

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.tvLaunchIntent
import java.net.URISyntaxException

private const val TAG = "TvHome.Shelf"

private const val GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

/**
 * The intent that opens [item]: the one the app published when it opens an exported activity of that same app, or else the
 * app's own launch intent; null when the app has neither. Both start a new task.
 */
public fun Context.shelfIntent(item: ShelfItem): Intent? =
    (publishedIntent(item) ?: packageManager.tvLaunchIntent(item.packageName))?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * The item's intent_uri as an intent bound to its publishing package, with no selector, no clip data and no URI grants, so
 * Home never passes its own URI permissions on. Null when it is missing or unreadable, names another app, or resolves to no
 * exported activity of the publishing app.
 */
internal fun Context.publishedIntent(item: ShelfItem): Intent? {
    val uri = item.intentUri ?: return null
    val pkg = item.packageName
    val intent =
        try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            return refused(pkg, "unreadable: $e")
        } catch (e: IllegalArgumentException) {
            return refused(pkg, "unreadable: $e")
        }
    val component = intent.component?.packageName
    if (component != null && component != pkg) return refused(pkg, "names $component")
    val target = intent.`package`
    if (target != null && target != pkg) return refused(pkg, "names $target")
    // Intent.setPackage throws while a selector is set.
    intent.selector = null
    intent.setPackage(pkg)
    intent.clipData = null
    intent.flags = intent.flags and GRANT_FLAGS.inv()
    val activity = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    if (activity == null || activity.packageName != pkg || !activity.exported) return refused(pkg, "opens no activity of it")
    return intent
}

private fun refused(
    pkg: String,
    why: String,
): Intent? {
    Log.w(TAG, "item intent of $pkg not followed, $why")
    return null
}
