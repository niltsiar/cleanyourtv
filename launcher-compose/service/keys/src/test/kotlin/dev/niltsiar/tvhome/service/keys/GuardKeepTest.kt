package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val KEYS = "dev.niltsiar.tvhome/.service.keys.KeyService"
private const val OTHER = "com.other.app/.Service"
private val keyService = ComponentName("dev.niltsiar.tvhome", "dev.niltsiar.tvhome.service.keys.KeyService")

val GuardDecisionTest by testSuite {
    robolectricTestSuite<GuardDecisionTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class GuardDecisionTestContent :
    RobolectricTestSuiteContent({
        test("the standby exit needs both the opt in and the permission") {
            leavesInStandby(optedIn = true, permitted = true).shouldBeTrue()
            leavesInStandby(optedIn = true, permitted = false).shouldBeFalse()
            leavesInStandby(optedIn = false, permitted = true).shouldBeFalse()
            leavesInStandby(optedIn = false, permitted = false).shouldBeFalse()
        }

        test("a wake request writes the list with the screen on and waits with it off") {
            wakeStep(optedIn = true, permitted = true, interactive = true) shouldBe WakeStep.PutBack
            wakeStep(optedIn = true, permitted = true, interactive = false) shouldBe WakeStep.WaitForScreenOn
        }

        test("a wake request does nothing without the opt in or without the permission") {
            wakeStep(optedIn = false, permitted = true, interactive = true) shouldBe WakeStep.Idle
            wakeStep(optedIn = true, permitted = false, interactive = true) shouldBe WakeStep.Idle
            wakeStep(optedIn = true, permitted = false, interactive = false) shouldBe WakeStep.Idle
        }

        test("the service is added once after the other entries in their order") {
            withService("$OTHER:com.third.app/.Service", keyService) shouldBe "$OTHER:com.third.app/.Service:$KEYS"
        }

        test("an absent empty or null text list becomes the service alone") {
            withService(null, keyService) shouldBe KEYS
            withService("", keyService) shouldBe KEYS
            withService("null", keyService) shouldBe KEYS
        }

        test("empty entries are dropped and the others keep their text") {
            withService("$OTHER:", keyService) shouldBe "$OTHER:$KEYS"
            withService(":com.third.app/com.third.app.Service", keyService) shouldBe "com.third.app/com.third.app.Service:$KEYS"
        }

        test("a listed service is not added again in either spelling") {
            withService("$OTHER:$KEYS", keyService).shouldBeNull()
            withService("dev.niltsiar.tvhome/dev.niltsiar.tvhome.service.keys.KeyService", keyService).shouldBeNull()
        }
    })

val GuardKeepTest by testSuite {
    robolectricTestSuite<GuardKeepTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class GuardKeepTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()

                val ownKeys = ComponentName(app, KeyService::class.java).flattenToShortString()

                init {
                    GuardKeep.reset()
                }

                fun enabled(): String? = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

                fun setEnabled(list: String) {
                    Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                }

                fun permit(granted: Boolean) {
                    if (granted) {
                        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                    } else {
                        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                    }
                }

                fun screen(on: Boolean) = shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(on)

                fun watchingScreenOn() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }
            }
        } closeWith {
            GuardKeep.reset()
        } asContextForEach {
            test("the opt in is off until it is set and reads off before the unlock") {
                GuardKeep.offInStandby(app).shouldBeFalse()

                GuardKeep.setOffInStandby(app, true)
                GuardKeep.offInStandby(app).shouldBeTrue()

                shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
                GuardKeep.offInStandby(app).shouldBeFalse()
            }

            test("with the opt in and the permission and the screen on the service joins the list and accessibility turns on") {
                GuardKeep.setOffInStandby(app, true)
                permit(true)
                setEnabled(OTHER)
                Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)

                GuardKeep.ensureKeyService(app)

                enabled() shouldBe "$OTHER:$ownKeys"
                Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) shouldBe 1
            }

            test("a service that is listed already leaves the list as it is") {
                GuardKeep.setOffInStandby(app, true)
                permit(true)
                setEnabled("$ownKeys:$OTHER")

                GuardKeep.ensureKeyService(app)

                enabled() shouldBe "$ownKeys:$OTHER"
            }

            test("without the opt in the list is left alone") {
                permit(true)
                setEnabled(OTHER)

                GuardKeep.ensureKeyService(app)

                enabled() shouldBe OTHER
            }

            test("without the permission the list is left alone and no screen watch starts") {
                GuardKeep.setOffInStandby(app, true)
                permit(false)
                setEnabled(OTHER)
                screen(false)

                GuardKeep.ensureKeyService(app)

                enabled() shouldBe OTHER
                watchingScreenOn().shouldBeFalse()
            }

            test("with the screen off the list waits for the screen and is written once when it turns on") {
                GuardKeep.setOffInStandby(app, true)
                permit(true)
                setEnabled(OTHER)
                screen(false)

                GuardKeep.ensureKeyService(app)
                GuardKeep.ensureKeyService(app)

                enabled() shouldBe OTHER
                (shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }) shouldBe 1

                screen(true)
                app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
                shadowOf(Looper.getMainLooper()).idle()

                enabled() shouldBe "$OTHER:$ownKeys"
                watchingScreenOn().shouldBeFalse()
            }
        }
    })
