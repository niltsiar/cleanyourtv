package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.view.accessibility.AccessibilityNodeInfo

private const val SYSTEM_UI = "com.android.systemui"
private const val LOOK_EVERY_MS = 150L
private const val LOOK_FOR_MS = 3_000L

/**
 * restart_press: opens the system power dialog and clicks its Restart item, for a TV whose dialog the remote cannot move
 * focus in (the AOSP phone dialog on the Sony XR-75X95J). The item is the clickable one that holds the framework's own
 * restart label, the string the dialog shows, in a window of the system UI. While no such window shows exactly one such
 * item, it looks again every [LOOK_EVERY_MS] for [LOOK_FOR_MS]; then it clicks nothing, closes what opened with Back
 * unless Home is in front, and calls [failed]. Without the framework label it opens nothing.
 */
internal class RestartPress(
    private val timer: Timer,
    private val global: (Int) -> Boolean,
    private val activeRoot: () -> AccessibilityNodeInfo?,
    private val homePackage: String,
    private val failed: () -> Unit,
) {
    private val label = frameworkRestartLabel()
    private var looked = 0L
    private val look = Runnable { look() }

    fun start() {
        if (label.isNullOrEmpty()) {
            failed()
            return
        }
        global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        timer.post(look, LOOK_EVERY_MS)
    }

    fun cancel() = timer.cancel(look)

    private fun look() {
        looked += LOOK_EVERY_MS
        val root = activeRoot()
        val item = root?.takeIf { it.packageName == SYSTEM_UI }?.let { restartItem(it, label.orEmpty()) }
        when {
            item != null -> {
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            looked < LOOK_FOR_MS -> {
                timer.post(look, LOOK_EVERY_MS)
            }

            else -> {
                if (root?.packageName != homePackage) global(AccessibilityService.GLOBAL_ACTION_BACK)
                failed()
            }
        }
    }
}

/** The framework's restart label in the TV's language, which the AOSP power dialog shows; null where the framework has none. */
private fun frameworkRestartLabel(): String? {
    val system = Resources.getSystem()
    val id = system.getIdentifier("global_action_restart", "string", "android")
    return if (id == 0) null else system.getString(id)
}

/** The clickable node around the only text under [root] that reads [label]; null when none or more than one does. */
private fun restartItem(
    root: AccessibilityNodeInfo,
    label: String,
): AccessibilityNodeInfo? {
    val found = mutableListOf<AccessibilityNodeInfo?>()

    fun walk(
        node: AccessibilityNodeInfo,
        clickable: AccessibilityNodeInfo?,
    ) {
        val around = if (node.isClickable) node else clickable
        if (node.text?.toString() == label) found += around
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, around) }
    }
    walk(root, null)
    return found.singleOrNull()
}
