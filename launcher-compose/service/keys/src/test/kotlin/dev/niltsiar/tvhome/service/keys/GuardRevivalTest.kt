package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val GuardRevivalTest by testSuite {
    robolectricTestSuite<GuardRevivalTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class GuardRevivalTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val users = shadowOf(app.getSystemService(UserManager::class.java))
                val ownKeys = ComponentName(app, KeyService::class.java).flattenToShortString()

                fun enabled(): String? = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

                init {
                    GuardKeep.reset()
                    GuardKeep.setOffInStandby(app, true)
                    shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                    Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
                }
            }
        } asContextForEach {
            test("the system binding the listener after a kill puts the key service back on the list") {
                Robolectric.setupService(GuardRevival::class.java).onListenerConnected()

                enabled() shouldBe ownKeys
            }

            test("a listener bound before the unlock waits for it") {
                users.setUserUnlocked(false)
                val listener = Robolectric.setupService(GuardRevival::class.java)

                listener.onListenerConnected()
                enabled() shouldBe ""

                users.setUserUnlocked(true)
                app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
                shadowOf(Looper.getMainLooper()).idle()

                enabled() shouldBe ownKeys
            }
        }
    })
