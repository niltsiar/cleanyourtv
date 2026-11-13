package dev.niltsiar.tvhome

import android.view.KeyEvent
import android.view.MotionEvent
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.robolectric.Robolectric

val ClockActivityTest by testSuite {
    robolectricTestSuite<ClockActivityTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
            },
    )
}

class ClockActivityTestContent :
    RobolectricTestSuiteContent({
        fun clock() =
            Robolectric
                .buildActivity(ClockActivity::class.java)
                .create()
                .start()
                .resume()

        fun key(action: Int) = KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER)

        test("a key is consumed and its release closes the clock") {
            val controller = clock()
            val activity = controller.get()

            activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN)).shouldBeTrue()
            activity.isFinishing.shouldBeFalse()
            activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP)).shouldBeTrue()
            activity.isFinishing.shouldBeTrue()
        }

        test("a remote motion closes the clock") {
            val activity = clock().get()

            activity.dispatchGenericMotionEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0f, 0f, 0)).shouldBeTrue()

            activity.isFinishing.shouldBeTrue()
        }

        test("leaving the clock closes it so it is never left in the back stack") {
            val controller = clock()

            controller.pause()

            controller.get().isFinishing.shouldBeTrue()
        }
    })
