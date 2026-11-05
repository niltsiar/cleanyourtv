package dev.niltsiar.tvhome

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The clock screensaver that Home starts itself after home_idle_clock_minutes without input, or on Back with
 * back_opens_clock; the key service starts it too, over any app, for the "screensaver" word of app_keys where the firmware
 * has no screensaver of its own. Some TVs' own screens (CVTE's live TV) do not keep the screen on, so the system
 * screensaver would cut into programmes; this one starts only on those triggers. Any key closes it.
 */
class ClockActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent { ClockHost(graph) }
    }

    /** Never left behind in the back stack. */
    override fun onPause() {
        super.onPause()
        finish()
    }

    /** The key that wakes the clock should not also act on Home, so it is consumed here. */
    @SuppressLint("RestrictedApi") // androidx marks the override restricted, but it is the only hook that sees every key first
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        finish()
        return true
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
