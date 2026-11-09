package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LauncherIntents
import dev.niltsiar.tvhome.core.model.startActivitySafely
import dev.niltsiar.tvhome.data.system.SystemSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TvHome.Keys"
private const val BOOT_PREFS = "boot"
private const val PROBE_BOOT_KEY = "probe_boot"

/**
 * The remote's buttons that only the stock launcher answered, from any app. One accessibility service does it all:
 * app_keys (keycodes, scancodes, "hold:" and "front:" entries), the Settings key, the Input button (input_key_keycodes,
 * and with capture_input_keys KEYCODE_TV and KEYCODE_TV_INPUT), KEYCODE_ALL_APPS and, with home_long_press_home, the
 * Home key itself, which opens Home. Every other key passes through untouched. Also the redirects that bring Home back:
 * stock_home_redirect, power_on_home and power_on_home_always. With restore_key_services, [ServiceRepair] puts the service
 * back on the enabled list after a force stop. With the opt-in broadcast of [GuardConfig], the service leaves the list while
 * the screen is off and [GuardKeep] lists it again at screen-on.
 *
 * The config and the Open on start pick come from the app's [ConfigRepositoryOwner] and are followed: the maps are read in
 * again when a value the service uses changes (see [KeyServiceConfig]).
 *
 * Enable over adb with scripts/switch-home.sh, or in the TV's accessibility settings.
 */
public class KeyService : AccessibilityService() {
    private val timer = HandlerTimer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var following: Job? = null
    private var config = TvConfig().forKeyService(null)
    private var keys = AppKeys(emptyList())
    private var settings: SystemSettings? = null
    private var launcher: TargetLauncher? = null
    private var volume: VolumeRepeat? = null
    private var powerOn: PowerOnHome? = null
    private var homeOpener: HomeOpener? = null
    private var stockHome: Set<String> = emptySet()
    private var frontPackage = ""
    private var unlockReceiver: BroadcastReceiver? = null
    private var screenOnReceiver: BroadcastReceiver? = null
    private var gamepads: GamepadPassthrough? = null
    private var recorded = false

    /** True after the first [start] of a bind: only that one arms the boot window, so a pick changed in Settings cannot send Home again. */
    private var armedAtBind = false
    private var standbyExit: BroadcastReceiver? = null

    /** disableSelf is how the service leaves for standby, not the user turning it off. */
    private var steppingOut = false
    private val homeAfterKey = Runnable { openHome() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected()
    }

    internal fun connected() {
        PowerMenu.bind(this)
        // The service is directBootAware: Android 11 skips a service that is not while the user is still locked at boot,
        // writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the unlock. The theme and the stored
        // choices live in credential-protected storage. The repository would serve the theme alone until the unlock, so the
        // service waits for the unlock itself and its first start() already carries the choices.
        val users = getSystemService(UserManager::class.java)
        if (users.isUserUnlocked) {
            connect()
            return
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    stopWaitingForUnlock()
                    connect()
                }
            }
        unlockReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked) {
            stopWaitingForUnlock()
            connect()
        }
    }

    private fun connect() {
        following?.cancel()
        armedAtBind = false
        val owner = application as ConfigRepositoryOwner
        // The first access builds the repository, which reads the store: off the main thread.
        following =
            scope.launch {
                val repository = withContext(Dispatchers.IO) { owner.configRepository }
                collectChanges(repository.config, repository.openOnStartApp)
            }
    }

    /** Starts the service on each value of [configs] and [picks] (the Open on start app) that differs in what it reads. */
    internal fun follow(
        configs: Flow<TvConfig>,
        picks: Flow<String?> = flowOf(null),
    ): Job = scope.launch { collectChanges(configs, picks) }

    private suspend fun collectChanges(
        configs: Flow<TvConfig>,
        picks: Flow<String?>,
    ) {
        combine(configs, picks) { config, pick -> config.forKeyService(pick) }.distinctUntilChanged().collect { start(it) }
    }

    private fun stopWaitingForUnlock() {
        unlockReceiver?.let(::unregisterReceiver)
        unlockReceiver = null
    }

    internal fun start(config: TvConfig) = start(config.forKeyService(null))

    /** Reads [config] in: the maps, the redirects and the window events they need. */
    private fun start(config: KeyServiceConfig) {
        val armedUntil = powerOn?.armedUntil ?: 0L
        val atBind = !armedAtBind
        armedAtBind = true
        tearDown()
        this.config = config
        // The record is in credential-protected storage, so it is written here, after the unlock, not at bind.
        recorded = config.restoreKeyServices
        if (recorded) ServiceRepair.connected(this, javaClass)
        keys = AppKeys(config.appKeys)
        val settings = SystemSettings(this).also { settings = it }
        launcher = TargetLauncher(this, settings, ::performGlobalAction, config.liveTvPackage)
        if (config.consumeVolumeKeys) {
            val audio = getSystemService(AudioManager::class.java)
            volume = VolumeRepeat(timer) { audio?.adjustVolume(it, AudioManager.FLAG_SHOW_UI) }
        }
        stockHome = stockHomeActivities(this, config.stockHomeRedirect)
        homeOpener = HomeOpener(this, config.stockHomeRedirect, stockHome)
        val plan = config.powerOn
        if (plan.enabled) armPowerOnHome(plan, armedUntil, atBind)
        // The service XML subscribes to no events, so the redirects ask for window changes only when one is on.
        val windowEvents = stockHome.isNotEmpty() || plan.liveTv.isNotEmpty() || keys.watchesFront
        serviceInfo?.let {
            if (adjustServiceInfo(it, config.keyFilter, windowEvents)) serviceInfo = it
        }
        if (stockHome.isNotEmpty() || plan.liveTv.isNotEmpty()) probeAtBoot()
        if (config.gamepadPassthrough && config.keyFilter) gamepads = GamepadPassthrough(this).also { it.start() }
        applyStandbyOptIn()
    }

    /** Arms or disarms the standby exit from the opt-in; [GuardKeep] calls it so a bound service sees an opt-in that arrives later. */
    internal fun applyStandbyOptIn() {
        if (!leavesInStandby(GuardKeep.offInStandby(this), GuardKeep.permitted(this))) {
            disarmStandbyExit()
            return
        }
        if (standbyExit != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = leaveForStandby()
            }
        standbyExit = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        if (!getSystemService(PowerManager::class.java).isInteractive) leaveForStandby()
    }

    private fun disarmStandbyExit() {
        standbyExit?.let(::unregisterReceiver)
        standbyExit = null
    }

    private fun leaveForStandby() {
        steppingOut = true
        GuardKeep.stepOut(this)
    }

    private fun armPowerOnHome(
        plan: PowerOnPlan,
        armedUntil: Long,
        atBind: Boolean,
    ) {
        val power = PowerOnHome(plan, SystemClock::elapsedRealtime, timer, armedUntil, ::openHome).also { powerOn = it }
        if (atBind) power.armAtBind()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = power.onScreenOn()
            }
        screenOnReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    /**
     * stock_home_redirect at boot: the stock home is often on screen before Android binds this service, so no window event
     * for it arrives (#1116). Once per boot, open [ProbeActivity]: when it closes, whatever was in front comes back and
     * sends a window event, and [handleWindow] covers it only if it is the stock home. An HDMI input or an app that the TV
     * resumed at boot is left alone.
     */
    private fun probeAtBoot() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val prefs = getSharedPreferences(BOOT_PREFS, MODE_PRIVATE)
        if (!firstBindAfterBoot(boot, prefs.getInt(PROBE_BOOT_KEY, -1), SystemClock.elapsedRealtime())) return
        prefs.edit().putInt(PROBE_BOOT_KEY, boot).apply()
        startActivitySafely(
            Intent(this, ProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    private fun tearDown() {
        SharedPresses.release(this)
        volume?.stop()
        volume = null
        powerOn?.stop()
        powerOn = null
        screenOnReceiver?.let(::unregisterReceiver)
        screenOnReceiver = null
        disarmStandbyExit()
        timer.cancel(homeAfterKey)
        gamepads?.stop()
        gamepads = null
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean = handleKey(event)

    /** The order matters: a volume key, then a scancode entry (more specific than any keycode), the Settings key, keycode entries, the Home keys, the Input button. */
    internal fun handleKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) powerOn?.onKeyDown(event.keyCode)
        if (volume?.handle(event) == true) return true
        val launch = launcher?.let { it::launch } ?: return false
        if (handleAppKey(event, keys.scanTap(event.scanCode, frontPackage), keys.holdScancodes[event.scanCode], this, launch)) return true
        if (isSettingsKey(event)) {
            if (released(event)) openSettings()
            return true
        }
        if (handleAppKey(event, keys.keyTap(event.keyCode, frontPackage), keys.holdKeycodes[event.keyCode], this, launch)) return true
        // home_long_press_home: Android never sees the Home key, so a held press cannot turn into ALL_APPS.
        if (config.homeLongPressHome && event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (released(event)) openHome()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && stockHome.isNotEmpty()) {
            redirectHomeKey(event)
            return false
        }
        if (event.keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            if (released(event)) openHome()
            return true
        }
        if (isInputKey(event.keyCode)) {
            if (released(event)) openInputSwitcher()
            return true
        }
        return false
    }

    private fun released(event: KeyEvent) = event.action == KeyEvent.ACTION_UP && !event.isCanceled

    private fun isSettingsKey(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_SETTINGS ||
            event.scanCode in config.settingsKeyScancodes ||
            event.keyCode in config.settingsKeyKeycodes

    private fun isInputKey(keyCode: Int): Boolean =
        keyCode in config.inputKeyKeycodes ||
            (config.captureInputKeys && (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT))

    private fun openSettings() {
        val opened = settings?.openSettings(config.settingsKeyClearTask) == true
        if (!opened) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
    }

    /** The system's input switcher, which the TV's Live TV app or Home answers; on its own it does nothing once the stock launcher is disabled. */
    private fun openInputSwitcher() {
        try {
            startActivity(Intent(LauncherIntents.VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no input switcher: $e")
            launcher?.launch("inputs")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot open the input switcher: $e")
        }
    }

    /**
     * stock_home_redirect on the Home key: when the stock home is already on screen (it came up at boot, before Android
     * bound this service), Home brings it up again with no window change, so the window redirect never fires and the TV
     * stays on the stock home until another app opens (#1116). A short press opens this launcher shortly after the system
     * has handled the key; a long press (Google TV's quick settings) is left alone. Held time, not repeats: key repeats are
     * made after accessibility filtering, so none arrive here.
     */
    private fun redirectHomeKey(event: KeyEvent) {
        if (released(event) && isShortPress(event.downTime, event.eventTime, ViewConfiguration.getLongPressTimeout().toLong())) {
            timer.post(homeAfterKey, HOME_KEY_DELAY_MS)
        }
    }

    private fun openHome() {
        homeOpener?.open()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handleWindow(event.eventType, event.packageName, event.className)
    }

    internal fun handleWindow(
        type: Int,
        pkg: CharSequence?,
        cls: CharSequence?,
    ) {
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // app_keys "front:" entries: only activities count, so a toast or the volume bar doesn't change the app in front.
        if (keys.watchesFront && pkg != null && cls != null) {
            try {
                val window = ComponentName(pkg.toString(), cls.toString())
                packageManager.getActivityInfo(window, 0)
                frontPackage = window.packageName
            } catch (e: PackageManager.NameNotFoundException) {
                // a dialog, panel or overlay: the app under it is still in front
            }
        }
        // power_on_home: the firmware opened Live TV by itself at power-on, before anyone pressed a key.
        if (powerOn?.redirects(pkg) == true) {
            openHome()
            return
        }
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        if (cls != null && cls.toString() in stockHome) openHome()
    }

    /** Unbound by Android: no UP will reach the service to cancel a hold it saw go down. */
    override fun onUnbind(intent: Intent): Boolean {
        SharedPresses.release(this)
        if (recorded && !steppingOut) ServiceRepair.disconnected(this, javaClass)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        PowerMenu.unbind(this)
        scope.cancel()
        stopWaitingForUnlock()
        tearDown()
        super.onDestroy()
    }

    override fun onInterrupt() {}
}

/**
 * key_filter false drops the filter flag, so the service stays enabled for nothing but stops filtering (some TCL Android
 * 12 firmware loses key repeat once any service filters); [windowEvents] subscribes to window changes. True when [info]
 * changed.
 */
internal fun adjustServiceInfo(
    info: AccessibilityServiceInfo,
    keyFilter: Boolean,
    windowEvents: Boolean,
): Boolean {
    val flags = if (keyFilter) info.flags else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
    val events = if (windowEvents) info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else info.eventTypes
    if (flags == info.flags && events == info.eventTypes) return false
    info.flags = flags
    info.eventTypes = events
    return true
}
