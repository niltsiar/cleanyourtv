package dev.niltsiar.tvhome.data.shelf

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.tvLaunchIntent
import java.net.URISyntaxException

private const val TAG = "TvHome.Shelf"

/**
 * Opens [item] in a new task: the intent the app published when it opens an exported activity of that same app, or else, or
 * when that one fails to start, the app's own launch intent. False when neither starts.
 */
public fun Context.startShelfItem(item: ShelfItem): Boolean =
    publishedIntent(item)?.let(::startShelfIntent) == true ||
        packageManager.tvLaunchIntent(item.packageName)?.let(::startShelfIntent) == true

/**
 * The intent comes from another app's row, so any failure to start it counts as not started, such as an extra too big for the
 * binder.
 */
private fun Context.startShelfIntent(intent: Intent): Boolean =
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "cannot open $intent: $e")
        false
    }

/**
 * The item's intent_uri as an intent bound to its publishing package, with no selector and no URI grants, so Home never passes
 * its own URI permissions on: `parseUri` without URI_ALLOW_UNSAFE drops the grant flags and sets no clip data. Null when it is
 * missing or unreadable, names another app, carries `file:` data, cannot be resolved, or resolves to no exported activity of
 * the publishing app; else it names the activity it resolved to, so the start cannot go elsewhere.
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
    if (intent.data?.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true)) return refused(pkg, "carries file: data")
    // Intent.setPackage throws while a selector is set.
    intent.selector = null
    intent.setPackage(pkg)
    val activity =
        try {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        } catch (e: RuntimeException) {
            // An intent too big for the binder makes the call throw here, on the main thread.
            return refused(pkg, "not resolved: $e")
        }
    if (activity == null || activity.packageName != pkg || !activity.exported) return refused(pkg, "opens no activity of it")
    intent.component = ComponentName(activity.packageName, activity.name)
    return intent
}

private fun refused(
    pkg: String,
    why: String,
): Intent? {
    Log.w(TAG, "item intent of $pkg not followed, $why")
    return null
}
