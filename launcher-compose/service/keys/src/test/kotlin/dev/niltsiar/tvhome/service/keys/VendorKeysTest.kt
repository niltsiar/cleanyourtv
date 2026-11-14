package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.KeyEvent
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.system.SystemSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

val VendorKeysTest by testSuite {
    robolectricTestSuite<VendorKeysTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
            },
    )
}

class VendorKeysTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                var settingsOpened = 0
                var config = MutableStateFlow(TvConfig(settingsKeyKeycodes = listOf(83)))
                var vendorKeys = keys()

                fun keys(vararg entries: String): VendorKeys {
                    config = MutableStateFlow(TvConfig(appKeys = entries.asList(), settingsKeyKeycodes = listOf(83)))
                    return VendorKeys(app, config, SystemSettings(app), {}) { settingsOpened++ }
                }

                fun key(
                    action: Int,
                    code: Int,
                    device: Int,
                    at: Long = 1_000,
                ) = KeyEvent(1_000, at, action, code, 0, 0, device, 0)

                init {
                    for (pkg in listOf("com.netflix.ninja", "org.xbmc.kodi")) {
                        val component = ComponentName(pkg, "$pkg.Main")
                        shadowOf(app.packageManager).addActivityIfNotPresent(component)
                        shadowOf(app.packageManager).addIntentFilterForActivity(
                            component,
                            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                        )
                    }
                }
            }
        } asContextForEach {
            test("a vendor key opens its app on a tap") {
                vendorKeys = keys("4062:com.netflix.ninja")

                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 501)).shouldBeTrue()
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 501, at = 1_100)).shouldBeTrue()

                shadowOf(app).nextStartedActivity?.component?.packageName shouldBe "com.netflix.ninja"
            }

            test("a keycode below 1000 is left to the key service") {
                vendorKeys = keys("190:com.netflix.ninja")

                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 190, device = 502)).shouldBeFalse()
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 190, device = 502, at = 1_100)).shouldBeFalse()
                shadowOf(app).nextStartedActivity.shouldBeNull()
            }

            test("a hold that home saw go down is dropped when home leaves the front") {
                vendorKeys = keys("4062:com.netflix.ninja", "hold:4062:org.xbmc.kodi")

                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 503))
                vendorKeys.release()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS * 2))

                shadowOf(app).nextStartedActivity.shouldBeNull()
            }

            test("a held vendor key opens its hold app") {
                vendorKeys = keys("4062:com.netflix.ninja", "hold:4062:org.xbmc.kodi")

                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 504))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))

                shadowOf(app).nextStartedActivity?.component?.packageName shouldBe "org.xbmc.kodi"
            }

            test("a global action on a vendor key goes through the bound key service") {
                val service = Robolectric.setupService(KeyService::class.java)
                PowerMenu.bind(service)
                try {
                    vendorKeys = keys("4062:back")

                    vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 507)).shouldBeTrue()
                    vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 507, at = 1_100)).shouldBeTrue()

                    shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
                    shadowOf(app).nextStartedActivity.shouldBeNull()
                } finally {
                    PowerMenu.unbind(service)
                }
            }

            test("a settings keycode opens settings on the up only") {
                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 83, device = 505)).shouldBeTrue()
                settingsOpened shouldBe 0
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 83, device = 505, at = 1_100)).shouldBeTrue()
                settingsOpened shouldBe 1
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 84, device = 505, at = 1_200)).shouldBeFalse()
            }

            test("a changed app key map and settings keycode apply to the next key") {
                vendorKeys = keys("4062:com.netflix.ninja")
                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 506)).shouldBeTrue()
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 506, at = 1_100))
                shadowOf(app).nextStartedActivity?.component?.packageName shouldBe "com.netflix.ninja"

                config.value = config.value.copy(appKeys = listOf("4062:org.xbmc.kodi"), settingsKeyKeycodes = listOf(84))
                vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 506, at = 2_000))
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 506, at = 2_100))

                shadowOf(app).nextStartedActivity?.component?.packageName shouldBe "org.xbmc.kodi"
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 83, device = 506, at = 2_200)).shouldBeFalse()
                vendorKeys.handle(key(KeyEvent.ACTION_UP, 84, device = 506, at = 2_300)).shouldBeTrue()
            }
        }
    })
