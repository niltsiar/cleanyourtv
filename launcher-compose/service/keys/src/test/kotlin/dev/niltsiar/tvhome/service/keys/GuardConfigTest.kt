package dev.niltsiar.tvhome.service.keys

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
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

val GuardConfigTest by testSuite {
    robolectricTestSuite<GuardConfigTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class GuardConfigTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()

                /** What `am broadcast` prints back: the ordered broadcast's final result data. */
                fun broadcast(extra: Boolean?): String? {
                    var reply: String? = null
                    val intent =
                        Intent(
                            "dev.niltsiar.tvhome.GUARD",
                        ).setClassName(app.packageName, "dev.niltsiar.tvhome.service.keys.GuardConfig")
                    if (extra != null) intent.putExtra("guard_off_in_standby", extra)
                    app.sendOrderedBroadcast(
                        intent,
                        null,
                        object : BroadcastReceiver() {
                            override fun onReceive(
                                context: Context,
                                intent: Intent,
                            ) {
                                reply = resultData
                            }
                        },
                        null,
                        Activity.RESULT_OK,
                        null,
                        null,
                    )
                    shadowOf(Looper.getMainLooper()).idle()
                    return reply
                }

                init {
                    GuardKeep.reset()
                }
            }
        } asContextForEach {
            test("the broadcast sets the opt in and replies with its value") {
                broadcast(true) shouldBe "guard_off_in_standby=true"
                GuardKeep.offInStandby(app).shouldBeTrue()

                broadcast(false) shouldBe "guard_off_in_standby=false"
                GuardKeep.offInStandby(app).shouldBeFalse()
            }

            test("a broadcast without the extra only reads the opt in") {
                GuardKeep.setOffInStandby(app, true)

                broadcast(null) shouldBe "guard_off_in_standby=true"
                GuardKeep.offInStandby(app).shouldBeTrue()
            }

            test("the receiver demands the secure settings permission from whoever sends") {
                val receivers = checkNotNull(app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_RECEIVERS).receivers)
                val config = receivers.single { it.name == GuardConfig::class.java.name }

                config.exported.shouldBeTrue()
                config.permission shouldBe "android.permission.WRITE_SECURE_SETTINGS"
            }
        }
    })
