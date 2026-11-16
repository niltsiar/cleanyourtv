package dev.niltsiar.tvhome.service.media

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val NowPlayingAccessTest by testSuite {
    robolectricTestSuite<NowPlayingAccessApi26Content>("sdk 26", testConfig = TestConfig.robolectric { sdk = 26 })
    robolectricTestSuite<NowPlayingAccessApi29Content>("sdk 29", testConfig = TestConfig.robolectric { sdk = 29 })
    robolectricTestSuite<NowPlayingAccessApi30Content>("sdk 30", testConfig = TestConfig.robolectric { sdk = 30 })
}

private fun app(): Application = RuntimeEnvironment.getApplication()

private fun allowInSetting(app: Application) {
    val listener = NowPlayingAccess.component(app).flattenToString()
    Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", "com.other/.Listener:$listener")
}

class NowPlayingAccessApi26Content :
    RobolectricTestSuiteContent({
        test("on api 26 access is read from the enabled listeners setting") {
            val app = app()
            NowPlayingAccess.granted(app).shouldBeFalse()

            allowInSetting(app)

            NowPlayingAccess.granted(app).shouldBeTrue()
        }
    })

class NowPlayingAccessApi29Content :
    RobolectricTestSuiteContent({
        test("the listener is disabled until switched on and goes back to its manifest state when switched off") {
            val app = app()
            val component = NowPlayingAccess.component(app)
            val packages = app.packageManager
            packages.getServiceInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled.shouldBeFalse()

            NowPlayingAccess.setEnabled(app, true)
            packages.getComponentEnabledSetting(component) shouldBe PackageManager.COMPONENT_ENABLED_STATE_ENABLED

            NowPlayingAccess.setEnabled(app, false)
            packages.getComponentEnabledSetting(component) shouldBe PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }

        test("a low-ram tv up to api 29 has no now playing") {
            val app = app()
            NowPlayingAccess.available(app).shouldBeTrue()

            shadowOf(app.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)

            NowPlayingAccess.available(app).shouldBeFalse()
        }
    })

class NowPlayingAccessApi30Content :
    RobolectricTestSuiteContent({
        test("from api 27 access is read from the notification manager and not the setting") {
            val app = app()
            allowInSetting(app)
            NowPlayingAccess.granted(app).shouldBeFalse()

            val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
            notifications.setNotificationListenerAccessGranted(NowPlayingAccess.component(app), true)

            NowPlayingAccess.granted(app).shouldBeTrue()
        }

        test("from api 30 a low-ram tv still has now playing") {
            val app = app()
            shadowOf(app.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)

            NowPlayingAccess.available(app).shouldBeTrue()
        }
    })
