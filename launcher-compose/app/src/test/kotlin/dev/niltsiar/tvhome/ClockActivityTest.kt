package dev.niltsiar.tvhome

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = TvHomeApplication::class)
class ClockActivityTest {
    private fun clock() =
        Robolectric
            .buildActivity(ClockActivity::class.java)
            .create()
            .start()
            .resume()

    private fun key(action: Int) = KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER)

    @Test
    fun a_key_is_consumed_and_its_release_closes_the_clock() {
        val controller = clock()
        val activity = controller.get()

        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN)))
        assertFalse(activity.isFinishing)
        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP)))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun a_remote_motion_closes_the_clock() {
        val activity = clock().get()

        assertTrue(activity.dispatchGenericMotionEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0f, 0f, 0)))

        assertTrue(activity.isFinishing)
    }

    @Test
    fun leaving_the_clock_closes_it_so_it_is_never_left_in_the_back_stack() {
        val controller = clock()

        controller.pause()

        assertTrue(controller.get().isFinishing)
    }
}
