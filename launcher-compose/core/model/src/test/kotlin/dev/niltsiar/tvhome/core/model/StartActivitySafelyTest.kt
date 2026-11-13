package dev.niltsiar.tvhome.core.model

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val StartActivitySafelyTest by testSuite {
    robolectricTestSuite<StartActivitySafelyTestContent>("default sdk")
}

class StartActivitySafelyTestContent :
    RobolectricTestSuiteContent({
        fun context(): Context = RuntimeEnvironment.getApplication()

        fun intent() = Intent("vendor.PLAY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        test("an activity that starts gives true") {
            context().startActivitySafely(intent()).shouldBeTrue()

            shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.action shouldBe "vendor.PLAY"
        }

        test("an intent nothing answers gives false") {
            shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)

            context().startActivitySafely(intent()).shouldBeFalse()
        }

        test("a permission the app lacks gives false") {
            val refusing =
                object : ContextWrapper(context()) {
                    override fun startActivity(intent: Intent) = throw SecurityException("needs a permission")
                }

            refusing.startActivitySafely(intent()).shouldBeFalse()
        }
    })
