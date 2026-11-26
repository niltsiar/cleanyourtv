package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.test.platform.app.InstrumentationRegistry
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LauncherIntents
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

/** A sandbox of this suite's own, so its shadow applies here and nowhere else (docs/testing.md). */
private object PanelSandbox

val QuickPanelTest by testSuite {
    robolectricTestSuite<QuickPanelTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = ConfigApplication::class
                shadows += ToggleMuteAudioManager::class
                portableClasses += PanelSandbox::class
            },
    )
}

/** Robolectric's audio shadow ignores ADJUST_TOGGLE_MUTE, the direction the mute word sends. */
@Implements(AudioManager::class)
class ToggleMuteAudioManager : ShadowAudioManager() {
    @Implementation
    override fun adjustStreamVolume(
        streamType: Int,
        direction: Int,
        flags: Int,
    ) {
        if (direction == AudioManager.ADJUST_TOGGLE_MUTE) {
            setIsStreamMute(streamType, !isStreamMute(streamType))
        } else {
            super.adjustStreamVolume(streamType, direction, flags)
        }
    }
}

private val PanelOn = TvConfig(quickPanel = true)

/** Two house seeds far apart in hue. */
private val Periwinkle = Color(0xFF8B93E8)
private val Ember = Color(0xFFFF6A3D)

class QuickPanelTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val service: KeyService = Robolectric.setupService(KeyService::class.java)
                var device = 100

                init {
                    // The native canvas starts in touch mode, where the panel takes no key focus; a TV has no touch.
                    InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
                    SharedPresses.reset()
                    SleepTimer.state.value = SleepState()
                    Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 1)
                    val home = ComponentName(app.packageName, "${app.packageName}.MainActivity")
                    shadowOf(app.packageManager).addActivityIfNotPresent(home)
                    shadowOf(app.packageManager).addIntentFilterForActivity(
                        home,
                        IntentFilter(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        },
                    )
                }

                fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

                fun key(
                    action: Int,
                    code: Int,
                    downAt: Long,
                    at: Long = downAt,
                ) = KeyEvent(downAt, at, action, code, 0, 0, device, 0, 0)

                fun down(
                    code: Int,
                    at: Long,
                ) = service.handleKey(key(KeyEvent.ACTION_DOWN, code, at))

                fun up(
                    code: Int,
                    downAt: Long,
                    at: Long,
                ) = service.handleKey(key(KeyEvent.ACTION_UP, code, downAt, at))

                fun started(): List<Intent> = generateSequence { shadowOf(app).nextStartedActivity }.toList()

                fun homeStarted() = generateSequence { shadowOf(app).nextStartedActivity }.any { it.hasCategory(Intent.CATEGORY_HOME) }

                /** The panel's window: an accessibility overlay that takes focus, unlike the sleep card. */
                fun panels(): List<View> =
                    Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java)).views.filter {
                        val params = it.layoutParams as? WindowManager.LayoutParams
                        params?.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY &&
                            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
                    }

                fun open(config: TvConfig = PanelOn) {
                    service.start(config)
                    device += 1
                    down(KeyEvent.KEYCODE_HOME, at = 1_000)
                    idleFor(HOLD_MS)
                    up(KeyEvent.KEYCODE_HOME, downAt = 1_000, at = 1_600)
                    panels() shouldHaveSize 1
                }

                /** The panel's fill at its right edge, in a row that has no focus, in [look] with the house [seed]. */
                fun panelFill(
                    look: Look,
                    seed: Color,
                ): Int {
                    (app as ConfigApplication).houseSeed.value = seed
                    open(PanelOn.copy(look = look, animations = false))
                    val panel = panels().single()
                    val density = panel.resources.displayMetrics.density
                    val fill = panel.drawn().getPixel(panel.width - (60 * density).toInt(), (177 * density).toInt())
                    press(KeyEvent.KEYCODE_BACK)
                    panels().shouldBeEmpty()
                    return fill
                }

                /** A key pressed while the panel shows: whether the service took its DOWN and its UP. */
                fun press(code: Int): Pair<Boolean, Boolean> {
                    device += 1
                    return down(code, at = 9_000) to up(code, downAt = 9_000, at = 9_100)
                }

                fun panel(): QuickPanelControl = service.quickPanel!!

                fun stockLauncherIsHome(stock: String) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolved =
                        ResolveInfo().apply {
                            activityInfo =
                                ActivityInfo().apply {
                                    packageName = stock
                                    name = "$stock.HomeActivity"
                                }
                        }
                    shadowOf(app.packageManager).addResolveInfoForIntent(Intent(homeIntent), resolved)
                    shadowOf(app.packageManager).addResolveInfoForIntent(Intent(homeIntent).setPackage(stock), resolved)
                }
            }
        } closeWith {
            service.onDestroy()
        } asContextForEach {
            test("a hold on home opens the panel and a short press goes home") {
                service.start(PanelOn)

                down(KeyEvent.KEYCODE_HOME, at = 1_000).shouldBeTrue()
                idleFor(100)
                up(KeyEvent.KEYCODE_HOME, downAt = 1_000, at = 1_100).shouldBeTrue()
                homeStarted().shouldBeTrue()
                idleFor(HOLD_MS)
                panels().shouldBeEmpty()

                device += 1
                down(KeyEvent.KEYCODE_HOME, at = 5_000).shouldBeTrue()
                idleFor(HOLD_MS)
                panels() shouldHaveSize 1
                up(KeyEvent.KEYCODE_HOME, downAt = 5_000, at = 5_900).shouldBeTrue()
                homeStarted().shouldBeFalse()
                panels() shouldHaveSize 1
            }
            test("with home long press home on a hold opens the panel and a short press goes home once") {
                service.start(PanelOn.copy(homeLongPressHome = true))

                down(KeyEvent.KEYCODE_HOME, at = 1_000).shouldBeTrue()
                up(KeyEvent.KEYCODE_HOME, downAt = 1_000, at = 1_100).shouldBeTrue()
                started().count { it.hasCategory(Intent.CATEGORY_HOME) } shouldBe 1

                device += 1
                down(KeyEvent.KEYCODE_HOME, at = 5_000)
                idleFor(HOLD_MS)
                up(KeyEvent.KEYCODE_HOME, downAt = 5_000, at = 5_900).shouldBeTrue()
                panels() shouldHaveSize 1
                homeStarted().shouldBeFalse()
            }

            test("with stock home redirect on a short press goes home at once and only once and a hold opens the panel") {
                stockLauncherIsHome("com.stock.launcher")
                service.start(PanelOn.copy(stockHomeRedirect = "com.stock.launcher"))

                down(KeyEvent.KEYCODE_HOME, at = 1_000).shouldBeTrue()
                up(KeyEvent.KEYCODE_HOME, downAt = 1_000, at = 1_100).shouldBeTrue()
                idleFor(HOME_KEY_DELAY_MS + HOLD_MS)
                started().count { it.hasCategory(Intent.CATEGORY_HOME) } shouldBe 1

                device += 1
                down(KeyEvent.KEYCODE_HOME, at = 5_000)
                idleFor(HOLD_MS)
                up(KeyEvent.KEYCODE_HOME, downAt = 5_000, at = 5_900)
                idleFor(HOME_KEY_DELAY_MS)
                panels() shouldHaveSize 1
                started().shouldBeEmpty()
            }

            test("with the key filter off home is left to android and the panel is not available") {
                PowerMenu.bind(service)
                service.start(PanelOn.copy(keyFilter = false))

                down(KeyEvent.KEYCODE_HOME, at = 1_000).shouldBeFalse()
                idleFor(HOLD_MS)
                up(KeyEvent.KEYCODE_HOME, downAt = 1_000, at = 1_600).shouldBeFalse()
                panels().shouldBeEmpty()
                QuickPanel.available().shouldBeFalse()

                service.start(PanelOn)
                QuickPanel.available().shouldBeTrue()
            }

            test("home opens the panel through the bound service and an unbound service has none") {
                PowerMenu.bind(service)
                service.start(PanelOn)

                QuickPanel.open()
                idleFor(0)
                panels() shouldHaveSize 1

                service.onDestroy()
                panels().shouldBeEmpty()
                QuickPanel.available().shouldBeFalse()
                QuickPanel.open()
                panels().shouldBeEmpty()
            }

            test("the panel window composes its rows with the first one focused") {
                open()
                idleFor(1_000)

                (panels().single() as ComposeView).hasComposition.shouldBeTrue()
                panels().single().findFocus().shouldNotBeNull()
            }

            test("the panel window draws in the look and house seed the service runs with") {
                val glass = panelFill(Look.Glass, Periwinkle)
                val expressive = panelFill(Look.Expressive, Periwinkle)
                val otherSeed = panelFill(Look.Expressive, Ember)

                expressive shouldNotBe glass
                otherSeed shouldNotBe expressive
            }

            test("back closes the panel and its up is taken") {
                open()

                press(KeyEvent.KEYCODE_BACK) shouldBe (true to true)
                panels().shouldBeEmpty()
                press(KeyEvent.KEYCODE_BACK) shouldBe (false to false)
            }

            test("home closes the panel without going home and a later short press goes home") {
                open()

                press(KeyEvent.KEYCODE_HOME) shouldBe (true to true)
                panels().shouldBeEmpty()
                homeStarted().shouldBeFalse()

                press(KeyEvent.KEYCODE_HOME)
                homeStarted().shouldBeTrue()
            }

            test("the d-pad and ok reach the panel window and the volume keys leave it open") {
                open()

                for (code in listOf(
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                )) {
                    press(code) shouldBe (false to false)
                }
                press(KeyEvent.KEYCODE_VOLUME_UP) shouldBe (false to false)
                panels() shouldHaveSize 1
            }

            test("another button closes the panel and does what it does") {
                open()

                press(KeyEvent.KEYCODE_ALL_APPS) shouldBe (true to true)
                panels().shouldBeEmpty()
                homeStarted().shouldBeTrue()
            }

            test("the screen going off closes the panel and a panel never opens while it is off") {
                open()

                shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)
                app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
                idleFor(0)
                panels().shouldBeEmpty()

                panel().open()
                panels().shouldBeEmpty()
            }

            test("a home press the service stops seeing opens no panel") {
                service.start(PanelOn)

                down(KeyEvent.KEYCODE_HOME, at = 1_000)
                service.onUnbind(Intent())
                idleFor(HOLD_MS)
                panels().shouldBeEmpty()
            }

            test("a value the service reads starts it again and closes the panel") {
                open()

                service.start(PanelOn.copy(appKeys = listOf("190:com.netflix.ninja")))
                panels().shouldBeEmpty()
            }

            test("inputs screen saver and launcher settings close the panel and open what they stand for") {
                open()
                panel().pick(PanelRow.Inputs)
                panels().shouldBeEmpty()
                started().map { it.action } shouldBe listOf(LauncherIntents.VIEW_INPUTS)

                open()
                panel().pick(PanelRow.Screensaver)
                panels().shouldBeEmpty()
                started().map { it.action } shouldBe listOf(LauncherIntents.CLOCK)

                open()
                panel().pick(PanelRow.LauncherSettings)
                panels().shouldBeEmpty()
                started().map { it.action } shouldBe listOf(LauncherIntents.launcherSettings(app.packageName))
            }

            test("mute toggles the sound, leaves the panel open and the row follows the new state") {
                open()
                val audio = service.getSystemService(AudioManager::class.java)
                panel().muted.shouldBeFalse()

                panel().pick(PanelRow.Mute)

                audio.isStreamMute(AudioManager.STREAM_MUSIC).shouldBeTrue()
                panel().muted.shouldBeTrue()
                panels() shouldHaveSize 1

                panel().pick(PanelRow.Mute)
                panel().muted.shouldBeFalse()
            }

            test("the panel reads the mute state when it opens") {
                service.getSystemService(AudioManager::class.java).adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    0,
                )

                open()

                panel().muted.shouldBeTrue()
            }

            test("a mute or volume key while the panel shows updates the mute row once the change settles") {
                open()
                val audio = service.getSystemService(AudioManager::class.java)

                // What the TV does with the remote's own mute key, out of the service's hands.
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                press(KeyEvent.KEYCODE_VOLUME_MUTE) shouldBe (false to false)
                idleFor(MUTE_SETTLE_MS)
                panel().muted.shouldBeTrue()

                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                press(KeyEvent.KEYCODE_VOLUME_UP) shouldBe (false to false)
                idleFor(MUTE_SETTLE_MS)
                panel().muted.shouldBeFalse()
                panels() shouldHaveSize 1
            }

            test("restart opens the power menu and closes the panel") {
                open()

                panel().pick(PanelRow.Restart)

                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                panels().shouldBeEmpty()
            }

            test("with restart press restart asks in the panel with cancel first and only the confirm presses restart") {
                open(PanelOn.copy(restartPress = true))

                panel().pick(PanelRow.Restart)
                panel().rows shouldBe listOf(PanelRow.CancelRestart, PanelRow.ConfirmRestart)
                panels() shouldHaveSize 1
                shadowOf(service).globalActionsPerformed.shouldBeEmpty()

                panel().pick(PanelRow.CancelRestart)
                panel().rows shouldNotContain PanelRow.ConfirmRestart
                panels() shouldHaveSize 1

                panel().pick(PanelRow.Restart)
                panel().pick(PanelRow.ConfirmRestart)
                panels().shouldBeEmpty()
                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            }

            test("the sleep row steps the sleep timer and leaves the panel open") {
                open(PanelOn.copy(sleepTimer = true))
                panel().rows.first() shouldBe PanelRow.SleepTimer

                panel().pick(PanelRow.SleepTimer)

                SleepTimer.current.value.step shouldBe 15
                panels() shouldHaveSize 1
            }

            test("rows that do not work are absent: no sleep row without sleep timer") {
                open()

                panel().rows shouldBe
                    listOf(PanelRow.Inputs, PanelRow.Screensaver, PanelRow.Mute, PanelRow.Restart, PanelRow.LauncherSettings)
            }
        }
    })
