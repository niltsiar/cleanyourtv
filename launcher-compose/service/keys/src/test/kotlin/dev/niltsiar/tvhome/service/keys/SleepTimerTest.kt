package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.overlayCardLook
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration
import kotlin.math.abs

val SleepTimerTest by testSuite {
    robolectricTestSuite<SleepTimerTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = ConfigApplication::class
            },
    )
}

private const val MINUTE = 60_000L
private val SleepOn = TvConfig(sleepTimer = true)

class SleepTimerTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                var service: KeyService = Robolectric.setupService(KeyService::class.java)
                var device = 100

                init {
                    SharedPresses.reset()
                    SleepTimer.state.value = SleepState()
                    bootCount(1)
                }

                fun bootCount(count: Int) = Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, count)

                fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

                fun key(
                    action: Int,
                    code: Int,
                ) = KeyEvent(1_000, 1_000, action, code, 0, 0, device, 0, 0)

                fun down(code: Int) = service.handleKey(key(KeyEvent.ACTION_DOWN, code))

                fun up(code: Int) = service.handleKey(key(KeyEvent.ACTION_UP, code))

                fun tap(code: Int): Pair<Boolean, Boolean> {
                    device += 1
                    return down(code) to up(code)
                }

                fun slept() = shadowOf(service).globalActionsPerformed.count { it == AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN }

                fun windows(): List<View> =
                    Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java)).views

                fun cards(): List<View> =
                    windows().filter {
                        (it.layoutParams as? WindowManager.LayoutParams)?.type ==
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    }

                /** The card's fill in its start padding, beside the text. */
                fun cardFill(): Int {
                    val card = cards().single()
                    val density = card.resources.displayMetrics.density
                    return card.drawn().getPixel((12 * density).toInt(), card.height / 2)
                }

                /** Sets the timer to [minutes], stepping from Off. */
                fun setTimer(minutes: Int) {
                    while (SleepTimer.state.value.step != minutes) service.stepSleepTimer()
                }

                fun screenOff() {
                    shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)
                    app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
                    shadowOf(Looper.getMainLooper()).idle()
                }

                /** Android binds a new service: the earlier one is destroyed, the new one reads the config. */
                fun rebind(config: TvConfig = SleepOn) {
                    service.onDestroy()
                    service = Robolectric.setupService(KeyService::class.java)
                    service.start(config)
                }
            }
        } closeWith {
            service.onDestroy()
        } asContextForEach {
            test("the sleep timer steps off 15 30 60 90 120 and back to off") {
                service.start(SleepOn)

                val steps = List(6) { service.stepSleepTimer() }

                steps shouldBe listOf(15, 30, 60, 90, 120, 0)
                SleepTimer.state.value shouldBe SleepState()
            }

            test("the sleep timer fires the sleep action at the end and not before") {
                service.start(SleepOn)
                setTimer(15)

                idleFor(15 * MINUTE - 1_000)
                slept() shouldBe 0

                idleFor(1_000)
                slept() shouldBe 1
                SleepTimer.state.value shouldBe SleepState()
                cards().shouldBeEmpty()
            }

            test("the sleep card shows at 60 s left over the playing app without taking focus or touch") {
                service.start(SleepOn)
                setTimer(15)

                idleFor(14 * MINUTE - 1_000)
                cards().shouldBeEmpty()

                idleFor(1_000)
                val card = cards().single()
                val flags = (card.layoutParams as WindowManager.LayoutParams).flags
                (flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0).shouldBeTrue()
                (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0).shouldBeTrue()
                (card as TextView).text.toString() shouldBe "The TV turns off in 60 s. OK: 15 more minutes. Back: cancel."
            }

            test("the sleep card keeps its Glass grey in Glass") {
                service.start(SleepOn)
                setTimer(15)
                idleFor(14 * MINUTE)

                cardFill() shouldBeWithin (0xE6202124.toInt() to 1)
            }

            test("the sleep card takes the look and house seed of the moment it shows, after the timer was set") {
                service.start(SleepOn)
                setTimer(15)
                service.start(SleepOn.copy(look = Look.Expressive))
                val seed = (app as ConfigApplication).houseSeed.value
                idleFor(14 * MINUTE)

                val expressive = overlayCardLook(Look.Expressive, seed).fill.toArgb()
                expressive shouldNotBe overlayCardLook(Look.Glass, seed).fill.toArgb()
                cardFill() shouldBeWithin (expressive to 1)
            }

            test("ok on the sleep card adds 15 minutes and hides it") {
                service.start(SleepOn)
                setTimer(15)
                idleFor(14 * MINUTE)

                tap(KeyEvent.KEYCODE_DPAD_CENTER) shouldBe (true to true)

                cards().shouldBeEmpty()
                idleFor(16 * MINUTE - 1_000)
                slept() shouldBe 0
                idleFor(1_000)
                slept() shouldBe 1
            }

            test("back on the sleep card cancels the timer so it never fires and the state is off") {
                service.start(SleepOn)
                setTimer(15)
                idleFor(14 * MINUTE)

                tap(KeyEvent.KEYCODE_BACK) shouldBe (true to true)

                cards().shouldBeEmpty()
                SleepTimer.state.value shouldBe SleepState()
                rebind()
                SleepTimer.state.value shouldBe SleepState()
                idleFor(2 * MINUTE)
                slept() shouldBe 0
            }

            test("other keys pass through while the sleep card shows and so does an ok pressed before it") {
                service.start(SleepOn)
                setTimer(15)
                idleFor(14 * MINUTE - 1_000)
                device += 1
                down(KeyEvent.KEYCODE_DPAD_CENTER).shouldBeFalse()
                idleFor(1_000)

                up(KeyEvent.KEYCODE_DPAD_CENTER).shouldBeFalse()
                tap(KeyEvent.KEYCODE_DPAD_UP) shouldBe (false to false)
                tap(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) shouldBe (false to false)

                cards().size shouldBe 1
            }

            test("screen off cancels the sleep timer so it never fires after a later wake") {
                service.start(SleepOn)
                setTimer(30)

                screenOff()
                shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(true)
                rebind()
                idleFor(30 * MINUTE)

                slept() shouldBe 0
                SleepTimer.state.value shouldBe SleepState()
            }

            test("a service destroyed while the screen is off drops the sleep timer") {
                service.start(SleepOn)
                setTimer(30)

                // No broadcast: the screen-off receiver must not be what drops it.
                shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(false)
                rebind()
                idleFor(30 * MINUTE)

                slept() shouldBe 0
            }

            test("a rebind in the same boot keeps the end time") {
                service.start(SleepOn)
                setTimer(30)
                val set = SleepTimer.state.value
                idleFor(10 * MINUTE)

                rebind()

                SleepTimer.state.value shouldBe set
                idleFor(20 * MINUTE - 1_000)
                slept() shouldBe 0
                idleFor(1_000)
                slept() shouldBe 1
            }

            test("a rebind after a new boot drops the end time") {
                service.start(SleepOn)
                setTimer(30)

                bootCount(2)
                rebind()
                idleFor(30 * MINUTE)

                SleepTimer.state.value shouldBe SleepState()
                slept() shouldBe 0
            }

            test("a value the service reads leaves the sleep timer running") {
                service.start(SleepOn)
                setTimer(15)
                val set = SleepTimer.state.value

                service.start(SleepOn.copy(appKeys = listOf("190:back")))

                SleepTimer.state.value shouldBe set
                idleFor(15 * MINUTE)
                slept() shouldBe 1
            }

            test("turning sleep_timer off cancels the timer and hides the card") {
                service.start(SleepOn)
                setTimer(15)
                idleFor(14 * MINUTE)
                cards().size shouldBe 1

                service.start(TvConfig())

                cards().shouldBeEmpty()
                SleepTimer.state.value shouldBe SleepState()
                idleFor(MINUTE)
                slept() shouldBe 0
                service.stepSleepTimer() shouldBe null
            }

            test("the minutes left count down a minute at a time and end with the timer") {
                service.start(SleepOn)
                setTimer(15)
                val seen = mutableListOf<Int?>()
                val following = CoroutineScope(Dispatchers.Main.immediate).launch { SleepTimer.minutesLeft().collect { seen += it } }

                idleFor(MINUTE)
                seen shouldBe listOf(15, 14)

                idleFor(14 * MINUTE)
                seen shouldBe (15 downTo 1).toList() + null
                following.cancel()
            }

            test("the sleep_timer word steps the timer and says the new value") {
                service.start(SleepOn.copy(appKeys = listOf("190:sleep_timer")))

                tap(190) shouldBe (true to true)

                SleepTimer.state.value.step shouldBe 15
                ShadowToast.getTextOfLatestToast() shouldContain "Sleep timer: 15 min"
            }

            test("with sleep_timer off the sleep_timer word sets nothing") {
                service.start(TvConfig(appKeys = listOf("190:sleep_timer")))

                tap(190) shouldBe (true to true)

                SleepTimer.state.value shouldBe SleepState()
            }

            test("the sleep timer is available only with the service bound from android 9") {
                sleepTimerAvailable(bound = true, sdk = 27).shouldBeFalse()
                sleepTimerAvailable(bound = true, sdk = 28).shouldBeTrue()
                sleepTimerAvailable(bound = false, sdk = 31).shouldBeFalse()
            }
        }
    })

/** Each channel of this ARGB colour is within the given distance of the given colour's. */
private infix fun Int.shouldBeWithin(expected: Pair<Int, Int>) {
    val (colour, tolerance) = expected
    val far = listOf(24, 16, 8, 0).filter { abs((this shr it and 0xFF) - (colour shr it and 0xFF)) > tolerance }
    withClue("${Integer.toHexString(this)} against ${Integer.toHexString(colour)}") { far.shouldBeEmpty() }
}
