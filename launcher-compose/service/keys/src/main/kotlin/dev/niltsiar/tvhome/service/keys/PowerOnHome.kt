package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent
import dev.niltsiar.tvhome.core.config.TvConfig

/** How long after boot the first bind still counts as "at boot", and how long power_on_home stays armed from boot. */
internal const val BOOT_WINDOW_MS: Long = 3 * 60 * 1000L

/** How long power_on_home stays armed after the screen turns on without a reboot. */
internal const val SCREEN_ON_WINDOW_MS: Long = 60 * 1000L

/** power_on_home_always: after the firmware has brought its last source back (Philips: within 0.5 s). */
internal const val POWER_ON_ALWAYS_DELAY_MS: Long = 800L

/** Lets the system see the Home key first, so a long press still does what the firmware gives it. */
internal const val HOME_KEY_DELAY_MS: Long = 250L

internal fun isShortPress(
    downTime: Long,
    upTime: Long,
    longPressTimeoutMs: Long,
): Boolean = upTime - downTime < longPressTimeoutMs

internal fun firstBindAfterBoot(
    boot: Int,
    lastBoot: Int,
    uptimeMs: Long,
): Boolean = boot >= 0 && boot != lastBoot && uptimeMs < BOOT_WINDOW_MS

internal fun stillArmed(
    now: Long,
    armedUntil: Long,
): Boolean = now < armedUntil

internal fun powerOnRedirect(
    liveTv: String,
    pkg: CharSequence?,
    now: Long,
    armedUntil: Long,
): Boolean = liveTv.isNotEmpty() && pkg != null && liveTv.contentEquals(pkg) && now < armedUntil

/**
 * What the power-on keys ask for. [liveTv] is the Live TV package to send back to Home (power_on_home), empty when off.
 * Both are off with boot_to_live_tv. An Open on start pick opens its app on purpose: Live TV picked is not sent back
 * to Home, and any pick turns [always] off.
 */
internal data class PowerOnPlan(
    val liveTv: String,
    val always: Boolean,
) {
    val enabled: Boolean get() = liveTv.isNotEmpty() || always
}

/** [pick] is the Open on start app, empty for none; it counts only with open_on_start_choice. */
internal fun powerOnPlan(
    config: TvConfig,
    pick: String,
): PowerOnPlan {
    val openOnStart = if (config.openOnStartChoice) pick else ""
    val liveTv = config.liveTvPackage.orEmpty()
    return PowerOnPlan(
        liveTv = if (config.powerOnHome && !config.bootToLiveTv && liveTv != openOnStart) liveTv else "",
        always = config.powerOnHomeAlways && !config.bootToLiveTv && openOnStart.isEmpty(),
    )
}

/**
 * power_on_home and power_on_home_always. A Live TV window the firmware opens by itself at power-on goes back to Home until
 * the first key press; with [PowerOnPlan.always], Home also comes up [POWER_ON_ALWAYS_DELAY_MS] after boot or screen-on,
 * whatever is in front, unless a key was pressed since. [uptime] is the time since boot in ms.
 */
internal class PowerOnHome(
    private val plan: PowerOnPlan,
    private val uptime: () -> Long,
    private val timer: Timer,
    carriedArmedUntil: Long = 0L,
    private val openHome: () -> Unit,
) {
    /** Up to when the Live TV redirect and the delayed Home stay armed, on the [uptime] clock; 0 when disarmed. */
    var armedUntil = carriedArmedUntil
        private set
    private val homeAtPowerOn = Runnable { if (stillArmed(uptime(), armedUntil)) openHome() }

    /** The service connected: armed for the boot window when this bind is part of the boot. */
    fun armAtBind() {
        if (uptime() >= BOOT_WINDOW_MS) return
        armedUntil = BOOT_WINDOW_MS
        if (plan.always) timer.post(homeAtPowerOn, POWER_ON_ALWAYS_DELAY_MS)
    }

    fun onScreenOn() {
        armedUntil = uptime() + SCREEN_ON_WINDOW_MS
        if (plan.always) timer.post(homeAtPowerOn, POWER_ON_ALWAYS_DELAY_MS)
    }

    /** The first key after power-on hands the TV to the user (power and wake keys don't count). */
    fun onKeyDown(keyCode: Int) {
        if (armedUntil != 0L && keyCode != KeyEvent.KEYCODE_POWER && keyCode != KeyEvent.KEYCODE_WAKEUP) armedUntil = 0
    }

    /** Whether a window of [pkg] that just came up is the Live TV app opened before anyone pressed a key. */
    fun redirects(pkg: CharSequence?): Boolean = powerOnRedirect(plan.liveTv, pkg, uptime(), armedUntil)

    fun stop() {
        timer.cancel(homeAtPowerOn)
    }
}
