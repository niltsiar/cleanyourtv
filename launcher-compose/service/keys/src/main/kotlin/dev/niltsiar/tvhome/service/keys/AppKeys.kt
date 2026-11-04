package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent

/** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
internal const val HOLD_MS: Long = 500

private val NumericKey = Regex("[0-9]{1,6}")
private val KeyName = Regex("KEYCODE_[A-Z0-9_]+")

/**
 * keycode to target. The key is a number or a KEYCODE_ name ([names] resolves it; 0, KEYCODE_UNKNOWN, means it is not
 * one). Entries without a colon, with an unknown key or with no target are skipped; for a keycode listed twice the last
 * entry wins.
 */
internal fun parseAppKeys(
    entries: Iterable<String?>,
    names: (String) -> Int = KeyEvent::keyCodeFromString,
): Map<Int, String> {
    val keys = HashMap<Int, String>()
    for (entry in entries) {
        val parts = entry?.split(":", limit = 2) ?: continue
        val target = parts.getOrNull(1)?.trim()
        if (target.isNullOrEmpty()) continue
        val key = parts[0].trim()
        val code =
            when {
                NumericKey.matches(key) -> key.toInt()
                KeyName.matches(key) -> names(key)
                else -> continue
            }
        if (code > 0) keys[code] = target
    }
    return keys
}

/**
 * scancode to target, from the "scan:490:com.wbd.hbomax" entries. For remotes that send several app buttons as one
 * keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and Disney+ are all keycode 196, scancodes
 * 485, 490 and 491). Other entries are skipped; for a scancode listed twice the last entry wins.
 */
internal fun parseAppScancodes(entries: Iterable<String?>): Map<Int, String> {
    val keys = HashMap<Int, String>()
    for (entry in entries) {
        val parts = entry?.split(":", limit = 3) ?: continue
        if (parts.size != 3 || parts[0].trim() != "scan" || parts[2].trim().isEmpty()) continue
        val code = parts[1].trim()
        if (!NumericKey.matches(code)) continue
        val scan = code.toInt()
        if (scan > 0) keys[scan] = parts[2].trim()
    }
    return keys
}

/** The entries with this prefix ("hold:", "front:"), the prefix removed, so the normal parsers can read them. */
private fun prefixed(
    entries: Iterable<String?>,
    prefix: String,
): List<String> =
    entries.mapNotNull {
        it
            ?.trim()
            ?.takeIf { text -> text.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
    }

/**
 * The app_keys of a theme, read apart: the tap entries, the "hold:" entries (the long-press action) and the "front:"
 * entries (the tap action while the button's own app is already in front), each for keycodes and for "scan:" scancodes.
 */
internal class AppKeys(
    entries: List<String?>,
    names: (String) -> Int = KeyEvent::keyCodeFromString,
) {
    val keycodes: Map<Int, String> = parseAppKeys(entries, names)
    val scancodes: Map<Int, String> = parseAppScancodes(entries)
    val holdKeycodes: Map<Int, String> = parseAppKeys(prefixed(entries, "hold:"), names)
    val holdScancodes: Map<Int, String> = parseAppScancodes(prefixed(entries, "hold:"))
    val frontKeycodes: Map<Int, String> = parseAppKeys(prefixed(entries, "front:"), names)
    val frontScancodes: Map<Int, String> = parseAppScancodes(prefixed(entries, "front:"))

    /** Whether any entry depends on the app in front, which needs window events. */
    val watchesFront: Boolean get() = frontKeycodes.isNotEmpty() || frontScancodes.isNotEmpty()

    /** A tap's target for a scancode: the "front:" entry while the button's own app is in front, else the usual one. */
    fun scanTap(
        scancode: Int,
        frontPackage: String,
    ): String? = tap(scancodes, frontScancodes, scancode, frontPackage)

    fun keyTap(
        keycode: Int,
        frontPackage: String,
    ): String? = tap(keycodes, frontKeycodes, keycode, frontPackage)

    private fun tap(
        keys: Map<Int, String>,
        front: Map<Int, String>,
        code: Int,
        frontPackage: String,
    ): String? {
        val target = keys[code]
        val alt = front[code]
        return if (alt != null && inFront(target, frontPackage)) alt else target
    }
}

/**
 * True when a tap's target is the app already in front ("package" or "package/.Activity"), so its "front:" entry applies
 * instead. Inputs and action words never match.
 */
internal fun inFront(
    target: String?,
    front: String?,
): Boolean {
    if (target == null || front.isNullOrEmpty()) return false
    return target.substringBefore('/') == front
}

/** The target a mapped key opens: its hold entry after [HOLD_MS], otherwise its normal one. */
internal fun tapOrHold(
    tap: String?,
    hold: String?,
    heldMs: Long,
): String? = if (heldMs >= HOLD_MS && hold != null) hold else tap
