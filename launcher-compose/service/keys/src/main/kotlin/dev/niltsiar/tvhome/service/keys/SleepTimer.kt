package dev.niltsiar.tvhome.service.keys

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

private const val TAG = "TvHome.Sleep"
private const val MINUTE_MS = 60_000L
private const val CARD_MS = 60_000L
private const val EXTEND_MS = 15 * MINUTE_MS
private const val BOOT_KEY = "sleep_boot"
private const val END_KEY = "sleep_end"
private const val STEP_KEY = "sleep_step"

/** The sleep timer's steps in minutes, Off (0) first. */
internal val SleepSteps = listOf(0, 15, 30, 60, 90, 120)

/** What the sleep timer is set to: [step] minutes, 0 for off, ending at [endsAt] on the [SystemClock.elapsedRealtime] clock. */
public data class SleepState(
    val step: Int = 0,
    val endsAt: Long = 0L,
)

/**
 * sleep_timer for Home: the timer of the bound [KeyService], which runs it. [state] is Off while no service runs one.
 */
public object SleepTimer {
    internal val state = MutableStateFlow(SleepState())

    public val current: StateFlow<SleepState> get() = state

    /** True while the key service is bound on Android 9 or later, which has the global action that puts the TV in standby. */
    public fun available(): Boolean = sleepTimerAvailable(PowerMenu.available(), Build.VERSION.SDK_INT)

    /** Steps the timer to the next of Off, 15, 30, 60, 90 and 120 minutes. Does nothing while no service runs one. */
    public fun step() {
        PowerMenu.bound()?.stepSleepTimer()
    }

    /** The whole minutes left, rounded up, again each time they change; null while the timer is off. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun minutesLeft(): Flow<Int?> =
        state
            .transformLatest { set ->
                var left = set.endsAt - SystemClock.elapsedRealtime()
                while (set.step != 0 && left > 0) {
                    val minutes = (left + MINUTE_MS - 1) / MINUTE_MS
                    emit(minutes.toInt())
                    delay(left - (minutes - 1) * MINUTE_MS)
                    left = set.endsAt - SystemClock.elapsedRealtime()
                }
                emit(null)
            }.distinctUntilChanged()
}

internal fun sleepTimerAvailable(
    bound: Boolean,
    sdk: Int,
): Boolean = bound && sdk >= Build.VERSION_CODES.P

/**
 * The sleep timer of a bound [KeyService], on [timer] and the [now] clock (elapsed time since boot). It keeps the end in
 * [prefs] with the [boot] count, so a service bound again in the same boot carries it on and one after a reboot drops it.
 * [CARD_MS] before the end it shows [card]; while the card shows, OK adds 15 minutes and Back cancels the timer. At the end
 * it calls [sleep]. [state] follows what it is set to.
 */
internal class SleepCountdown(
    private val timer: Timer,
    private val now: () -> Long,
    private val boot: Int,
    private val prefs: SharedPreferences,
    private val state: MutableStateFlow<SleepState>,
    private val card: OverlaySleepCard,
    private val sleep: () -> Unit,
) {
    private var cardShows = false

    /** The key whose DOWN the card took, so its UP is taken too and the app never sees half a press. */
    private var taken: Int? = null
    private val showCard =
        Runnable {
            cardShows = true
            card.show(((state.value.endsAt - now() + 999) / 1_000).toInt())
        }
    private val fire =
        Runnable {
            cancel()
            sleep()
        }

    /** Carries on a timer this boot stored, unless its end has passed: the TV never sleeps without the card first. */
    fun restore() {
        val step = prefs.getInt(STEP_KEY, 0)
        val end = prefs.getLong(END_KEY, 0L)
        if (step != 0 && prefs.getInt(BOOT_KEY, -1) == boot && end > now()) arm(SleepState(step, end)) else cancel()
    }

    /** Sets the next step from the current one, Off after 120; returns its minutes. */
    fun step(): Int {
        val next = SleepSteps[(SleepSteps.indexOf(state.value.step) + 1) % SleepSteps.size]
        if (next == 0) cancel() else arm(SleepState(next, now() + next * MINUTE_MS))
        return next
    }

    /** Off, and the stored end with it. */
    fun cancel() {
        stop()
        prefs
            .edit()
            .remove(BOOT_KEY)
            .remove(END_KEY)
            .remove(STEP_KEY)
            .apply()
    }

    /** Stops counting and hides the card, and keeps the stored end for the next service. */
    fun stop() {
        timer.cancel(showCard)
        timer.cancel(fire)
        hideCard()
        state.value = SleepState()
    }

    /** Takes only OK and Back while the card shows, and the UP of a key it took. */
    fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) {
            if (code != taken) return false
            taken = null
            return true
        }
        if (!cardShows || event.action != KeyEvent.ACTION_DOWN) return false
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val set = state.value
                arm(set.copy(endsAt = set.endsAt + EXTEND_MS))
            }

            KeyEvent.KEYCODE_BACK -> {
                cancel()
            }

            else -> {
                return false
            }
        }
        taken = code
        return true
    }

    private fun arm(set: SleepState) {
        timer.cancel(showCard)
        timer.cancel(fire)
        hideCard()
        state.value = set
        prefs
            .edit()
            .putInt(BOOT_KEY, boot)
            .putLong(END_KEY, set.endsAt)
            .putInt(STEP_KEY, set.step)
            .apply()
        val left = set.endsAt - now()
        timer.post(showCard, (left - CARD_MS).coerceAtLeast(0))
        timer.post(fire, left)
    }

    private fun hideCard() {
        if (!cardShows) return
        cardShows = false
        card.hide()
    }
}

/**
 * The card in a corner over whatever plays, in an accessibility overlay window that takes no focus and no touch, so the
 * playing app keeps both. A TV that refuses the window still sleeps on time.
 */
internal class OverlaySleepCard(
    private val context: Context,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    fun show(seconds: Int) {
        hide()
        val density = context.resources.displayMetrics.density
        val card =
            TextView(context).apply {
                text = context.getString(R.string.sleep_card, seconds)
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                val padH = (24 * density).toInt()
                val padV = (16 * density).toInt()
                setPadding(padH, padV, padH, padV)
                background =
                    GradientDrawable().apply {
                        setColor(0xE6202124.toInt())
                        cornerRadius = 16 * density
                    }
            }
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = (48 * density).toInt()
                    y = (27 * density).toInt()
                }
        try {
            windows.addView(card, params)
            view = card
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "no sleep card: $e")
        }
    }

    fun hide() {
        view?.let(windows::removeView)
        view = null
    }
}
