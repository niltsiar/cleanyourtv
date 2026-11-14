package dev.niltsiar.tvhome.data.system

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val TimeChangesTest by testSuite {
    robolectricTestSuite<TimeChangesTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class TimeChangesTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()

                init {
                    // Before Android 13 the compat call asks for this permission of its own to register a not-exported receiver.
                    shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                }

                val registered get() = shadowOf(app).registeredReceivers.size
            }
        } asContextForEach {
            test("the receiver stays until every screen that started it has stopped") {
                val before = registered
                val changes = TimeChanges(app)

                changes.onStart() // Home
                changes.onStart() // the clock screensaver opens before Home stops
                changes.onStop()
                registered shouldBe before + 1

                changes.onStop()
                registered shouldBe before
            }
        }
    })
