package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.view.KeyEvent
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowAudioManager
import java.time.Duration

private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val FILTER = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

/** Keeps the info a service sets: with no system connection, Robolectric's getServiceInfo is always null. */
@Implements(AccessibilityService::class)
class ShadowKeptServiceInfo : ShadowAccessibilityService() {
    private var info: AccessibilityServiceInfo? = null

    @Implementation
    fun getServiceInfo(): AccessibilityServiceInfo? = info

    @Implementation
    fun setServiceInfo(info: AccessibilityServiceInfo) {
        this.info = info
    }
}

/** Notes each volume step, which Robolectric's AudioManager drops. */
@Implements(AudioManager::class)
class ShadowVolumeSteps : ShadowAudioManager() {
    val steps = mutableListOf<Int>()

    @Implementation
    fun adjustVolume(
        direction: Int,
        flags: Int,
    ) {
        steps += direction
    }
}

/**
 * TestBalloon sets a sandbox's shadows once, and suites with the same instrumentation share one sandbox, so the shadows
 * above would follow the suite set up last. A portable class of this suite's own gives it a sandbox of its own.
 */
private object OwnSandbox

val KeyFilterPauseTest by testSuite {
    robolectricTestSuite<KeyFilterPauseTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = ConfigApplication::class
                shadows += ShadowKeptServiceInfo::class
                shadows += ShadowVolumeSteps::class
                portableClasses += OwnSandbox::class
            },
    )
}

class KeyFilterPauseTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                lateinit var service: KeyService

                fun filtering() = service.serviceInfo.flags and FILTER != 0

                fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

                fun volumeChanged() {
                    app.sendBroadcast(Intent(VOLUME_CHANGED))
                    shadowOf(Looper.getMainLooper()).idle()
                }

                fun watchingVolume() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(VOLUME_CHANGED) }

                fun steps() = Shadow.extract<ShadowVolumeSteps>(service.getSystemService(AudioManager::class.java)).steps

                init {
                    SharedPresses.reset()
                    service = Robolectric.setupService(KeyService::class.java)
                    service.serviceInfo = AccessibilityServiceInfo().apply { flags = FILTER }
                }
            }
        } asContextForEach {
            test("with volume pauses filter filtering stops until 4 s after the last volume change") {
                service.start(TvConfig(volumePausesFilter = true))
                filtering().shouldBeTrue()

                volumeChanged()
                filtering().shouldBeFalse()
                idleFor(3_000)
                volumeChanged()
                idleFor(VOLUME_PAUSE_MS - 1)
                filtering().shouldBeFalse()
                idleFor(1)

                filtering().shouldBeTrue()
            }

            test("a new config during a volume pause turns filtering back on") {
                service.start(TvConfig(volumePausesFilter = true))
                volumeChanged()
                filtering().shouldBeFalse()

                service.start(TvConfig())

                filtering().shouldBeTrue()
            }

            test("no volume change is watched without volume pauses filter or without key filter") {
                service.start(TvConfig())
                watchingVolume().shouldBeFalse()

                service.start(TvConfig(keyFilter = false, volumePausesFilter = true))
                watchingVolume().shouldBeFalse()
                volumeChanged()
                idleFor(VOLUME_PAUSE_MS)

                filtering().shouldBeFalse()
            }

            test("a held volume key stops repeating when key filtering drops") {
                service.start(TvConfig(consumeVolumeKeys = true, volumePausesFilter = true))
                service.handleKey(KeyEvent(1_000, 1_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
                idleFor(VOLUME_REPEAT_DELAY_MS + VOLUME_REPEAT_INTERVAL_MS)
                steps() shouldBe listOf(AudioManager.ADJUST_RAISE, AudioManager.ADJUST_RAISE)

                volumeChanged()
                idleFor(1_000)

                steps().size shouldBe 2
            }
        }
    })
