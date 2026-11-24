package dev.niltsiar.tvhome.service.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.GlassMenu
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "TvHome.Panel"
private val EdgeX = 48.dp
private val EdgeY = 27.dp

/** How long after a mute or volume key the panel reads the mute state again, for a TV that applies the change later. */
internal const val MUTE_SETTLE_MS: Long = 500

/** The rows of the quick panel; the last two are the Restart question's. */
internal enum class PanelRow {
    SleepTimer,
    Inputs,
    Screensaver,
    Mute,
    Restart,
    LauncherSettings,
    CancelRestart,
    ConfirmRestart,
}

/** quick_panel for Home: the panel of the bound [KeyService]. */
public object QuickPanel {
    /** True while a bound key service has the panel: quick_panel and the key filter on. */
    public fun available(): Boolean = PowerMenu.bound()?.quickPanel != null

    /** Opens the panel over whatever is in front. Does nothing while [available] is false. */
    public fun open() {
        PowerMenu.bound()?.quickPanel?.open()
    }
}

/** The look of the panel's window: the theme's motion, glass_style and look. */
internal data class PanelLook(
    val motion: Motion,
    val focusStyle: FocusStyle,
    val look: Look,
)

/**
 * quick_panel of a bound [KeyService], which gives it the keys while the key filter is on. The Home key is its own: held
 * for [HOLD_MS] it opens the panel as the time passes, released earlier it calls [goHome]. While the panel shows, Back
 * and Home close it, the D-pad and OK reach its window, the volume keys go on as usual, and any other key closes it and
 * goes on. The screen going off closes it. A row runs through [run]; the rows that leave the screen close the panel first,
 * Sleep timer and Mute keep it open. With [restartAsks] (restart_press) Restart asks first in the panel, Cancel focused.
 * No public broadcast tells of a mute change, so [muted] is read on open, at once after the Mute row, and [MUTE_SETTLE_MS]
 * after that row or a mute or volume key.
 */
internal class QuickPanelControl(
    private val context: Context,
    private val timer: Timer,
    private val look: PanelLook,
    private val seed: StateFlow<Color>,
    sleepTimer: Boolean,
    private val restartAsks: Boolean,
    private val goHome: () -> Unit,
    private val run: (PanelRow) -> Unit,
) {
    private val window = OverlayQuickPanel(context)
    private val mainRows =
        buildList {
            if (sleepTimer) add(PanelRow.SleepTimer)
            addAll(listOf(PanelRow.Inputs, PanelRow.Screensaver, PanelRow.Mute, PanelRow.Restart, PanelRow.LauncherSettings))
        }
    private var confirming by mutableStateOf(false)
    private var focus by mutableStateOf<PanelRow?>(null)
    private var held = false

    /** The key whose DOWN the panel took, so its UP is taken too and the app never sees half a press. */
    private var taken: Int? = null
    private var screenOff: BroadcastReceiver? = null
    private val hold =
        Runnable {
            held = true
            open()
        }
    private val audio = context.getSystemService(AudioManager::class.java)
    private val readMute = Runnable { muted = audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true }

    var isOpen: Boolean = false
        private set

    /** Whether the media stream is muted; the Mute row follows it. */
    var muted: Boolean by mutableStateOf(false)
        private set

    val rows: List<PanelRow> get() = if (confirming) listOf(PanelRow.CancelRestart, PanelRow.ConfirmRestart) else mainRows

    /** Opens the panel with its first row focused; not while the screen is off. */
    fun open() {
        if (isOpen || !context.getSystemService(PowerManager::class.java).isInteractive) return
        confirming = false
        focus = null
        readMute.run()
        isOpen = window.show { Content() }
        if (!isOpen) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = close()
            }
        screenOff = receiver
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    /** Closes the panel and drops a Home press it saw go down. */
    fun close() {
        release()
        timer.cancel(readMute)
        screenOff?.let(context::unregisterReceiver)
        screenOff = null
        window.hide()
        isOpen = false
    }

    /** For a service that stops receiving keys: a Home press whose UP will not come opens nothing. */
    fun release() {
        timer.cancel(hold)
        held = false
    }

    /** The Home key, DOWN and UP both taken. */
    fun homeKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    timer.cancel(hold)
                    held = false
                    timer.post(hold, HOLD_MS)
                }
            }

            KeyEvent.ACTION_UP -> {
                timer.cancel(hold)
                if (!held && !event.isCanceled) goHome()
                held = false
            }
        }
        return true
    }

    /** A key while the panel shows: true to take it, false to pass it to the panel's window, null for the service to go on. */
    fun handleKey(event: KeyEvent): Boolean? {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && code == taken) {
            taken = null
            return true
        }
        if (!isOpen) return null
        return when (code) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    close()
                    taken = code
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                false
            }

            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) readMuteLater()
                null
            }

            else -> {
                close()
                null
            }
        }
    }

    fun pick(row: PanelRow) {
        when {
            row == PanelRow.SleepTimer -> {
                run(row)
            }

            row == PanelRow.Mute -> {
                run(row)
                readMute.run()
                readMuteLater()
            }

            row == PanelRow.Restart && restartAsks -> {
                focus = PanelRow.CancelRestart
                confirming = true
            }

            row == PanelRow.CancelRestart -> {
                focus = PanelRow.Restart
                confirming = false
            }

            else -> {
                close()
                run(row)
            }
        }
    }

    private fun readMuteLater() {
        timer.cancel(readMute)
        timer.post(readMute, MUTE_SETTLE_MS)
    }

    @Composable
    private fun Content() {
        val step by SleepTimer.current.collectAsState()
        val seed = seed.collectAsState()
        TvTheme(look = look.look, seed = { seed.value }, motion = look.motion, focusStyle = look.focusStyle) {
            QuickPanelContent(rows, confirming, focus, step.step, muted, ::pick, ::close)
        }
    }
}

/**
 * The panel: [rows] in a glass menu at the top right of the screen, under its title (the Restart question while
 * [confirming]), with focus on [focus], the first row when it is null. The Sleep timer row ends with [sleepStep]: Off, or
 * its minutes. The Mute row says Unmute while [muted]. OK on a row calls [onPick]; Back, should it reach the window, calls
 * [onBack].
 */
@Composable
internal fun QuickPanelContent(
    rows: List<PanelRow>,
    confirming: Boolean,
    focus: PanelRow?,
    sleepStep: Int,
    muted: Boolean,
    onPick: (PanelRow) -> Unit,
    onBack: () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val anchor =
        remember(density) {
            derivedStateOf {
                val right = size.width - with(density) { EdgeX.toPx() }
                val top = with(density) { EdgeY.toPx() }
                Rect(right, top, right, top)
            }
        }
    val sleepValue =
        if (sleepStep == 0) stringResource(R.string.panel_sleep_off) else stringResource(R.string.panel_sleep_minutes, sleepStep)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onPreviewKeyEvent {
                if (it.key != Key.Back) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyDown) onBack()
                true
            },
    ) {
        key(confirming) {
            GlassMenu(
                rows,
                anchor,
                alignEnd = true,
                label = { if (it == PanelRow.Mute && muted) R.string.panel_unmute else it.label() },
                rowTag = { "panel:${it.name}" },
                onPick = onPick,
                title = stringResource(if (confirming) R.string.panel_restart_question else R.string.panel_title),
                dividerBefore = { it == PanelRow.Restart || it == PanelRow.ConfirmRestart },
                warn = { it == PanelRow.Restart || it == PanelRow.ConfirmRestart },
                focusRow = rows.indexOf(focus).coerceAtLeast(0),
                value = { if (it == PanelRow.SleepTimer) sleepValue else null },
            )
        }
    }
}

private fun PanelRow.label(): Int =
    when (this) {
        PanelRow.SleepTimer -> R.string.panel_sleep_timer
        PanelRow.Inputs -> R.string.panel_inputs
        PanelRow.Screensaver -> R.string.panel_screensaver
        PanelRow.Mute -> R.string.panel_mute
        PanelRow.Restart -> R.string.panel_restart
        PanelRow.LauncherSettings -> R.string.panel_launcher_settings
        PanelRow.CancelRestart -> R.string.panel_restart_cancel
        PanelRow.ConfirmRestart -> R.string.panel_restart_confirm
    }

/**
 * The panel's window: a full-screen accessibility overlay that takes focus, so the D-pad moves in it natively while the
 * app under it keeps playing. A ComposeView outside an activity brings its own lifecycle and saved-state owners. A TV that
 * refuses the window gets no panel.
 */
internal class OverlayQuickPanel(
    private val context: Context,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: PanelOwner? = null

    /** True when the window shows. */
    fun show(content: @Composable () -> Unit): Boolean {
        val owner = PanelOwner()
        val panel =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent(content)
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                0,
                PixelFormat.TRANSLUCENT,
            )
        try {
            windows.addView(panel, params)
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "no quick panel: $e")
            owner.destroy()
            return false
        }
        view = panel
        this.owner = owner
        return true
    }

    fun hide() {
        view?.let(windows::removeView)
        view = null
        owner?.destroy()
        owner = null
    }
}

private class PanelOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
