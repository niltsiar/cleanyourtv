package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Environment
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LauncherIntents
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.time.Duration

val KeyServiceTest by testSuite {
    robolectricTestSuite<KeyServiceTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = ConfigApplication::class
            },
    )
}

class KeyServiceTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                val packages = shadowOf(app.packageManager)
                val recorder = app as ConfigApplication
                val home = ComponentName(app.packageName, "${app.packageName}.MainActivity")
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                lateinit var service: KeyService
                var device = 100

                fun installLeanbackApp(packageName: String) {
                    val component = ComponentName(packageName, "$packageName.Main")
                    packages.addActivityIfNotPresent(component)
                    packages.addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
                    )
                }

                fun started(): Intent? = shadowOf(app).nextStartedActivity

                fun key(
                    action: Int,
                    code: Int,
                    scan: Int = 0,
                    downAt: Long = 1_000,
                    at: Long = downAt,
                    flags: Int = 0,
                ) = KeyEvent(downAt, at, action, code, 0, 0, device, scan, flags)

                fun tap(
                    code: Int,
                    scan: Int = 0,
                ): Boolean {
                    val down = service.handleKey(key(KeyEvent.ACTION_DOWN, code, scan))
                    val up = service.handleKey(key(KeyEvent.ACTION_UP, code, scan, at = 1_100))
                    return down && up
                }

                fun pressInputButton(code: Int): Pair<Boolean, Boolean> {
                    val down = service.onKeyEvent(key(KeyEvent.ACTION_DOWN, code))
                    val up = service.onKeyEvent(key(KeyEvent.ACTION_UP, code, at = 1_100))
                    return down to up
                }

                fun stockLauncherIsHome(stock: String) {
                    fun resolved(
                        pkg: String,
                        name: String,
                    ) = ResolveInfo().apply {
                        activityInfo =
                            ActivityInfo().apply {
                                packageName = pkg
                                this.name = name
                            }
                    }
                    packages.addResolveInfoForIntent(Intent(homeIntent), resolved(stock, "$stock.HomeActivity"))
                    packages.addResolveInfoForIntent(Intent(homeIntent).setPackage(stock), resolved(stock, "$stock.HomeActivity"))
                }

                fun pushTheme(json: String) {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(app).dir!!, "config.json").writeText(json)
                }

                /** The service reads its config off the main thread, then starts on it: wait until [code] is mapped. */
                fun awaitMapped(code: Int) {
                    val deadline = System.currentTimeMillis() + 30_000
                    while (System.currentTimeMillis() < deadline) {
                        device += 1
                        if (tap(code)) break
                        shadowOf(Looper.getMainLooper()).idle()
                        Thread.sleep(10)
                    }
                    shadowOf(app).clearNextStartedActivities()
                    device += 1
                }

                fun screenOn() = app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))

                fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

                fun pick(
                    picks: MutableStateFlow<String?>,
                    app: String?,
                ) {
                    picks.value = app
                    shadowOf(Looper.getMainLooper()).idle()
                }

                fun homeStarted() = generateSequence { started() }.any { it.hasCategory(Intent.CATEGORY_HOME) }

                val alwaysOnWithChoice = TvConfig(powerOnHomeAlways = true, openOnStartChoice = true)

                init {
                    SharedPresses.reset()
                    GuardKeep.reset()
                    packages.addActivityIfNotPresent(home)
                    packages.addIntentFilterForActivity(
                        home,
                        IntentFilter(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        },
                    )
                    service = Robolectric.setupService(KeyService::class.java)
                }

                fun holdKeys() = TvConfig(appKeys = listOf("190:com.netflix.ninja", "hold:190:org.xbmc.kodi"))

                fun recordedServices(): Set<String> = ServiceRepair.recorded(app)

                fun optInToStandbyExit() {
                    GuardKeep.setOffInStandby(app, true)
                    shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                }

                fun screenOff() {
                    shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)
                    app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
                    shadowOf(Looper.getMainLooper()).idle()
                }

                fun watchingScreenOn() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }
            }
        } closeWith {
            // PowerMenu is static: a service left bound outlives this test's context, and a later GuardKeep call unregisters its receivers there.
            service.onDestroy()
        } asContextForEach {
            test("a remapped button opens its app on a tap and is consumed") {
                installLeanbackApp("com.netflix.ninja")
                service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

                tap(190).shouldBeTrue()

                started()?.component?.packageName shouldBe "com.netflix.ninja"
                started().shouldBeNull()
            }

            test("an app a remapped button opens is noted under its home tile key") {
                installLeanbackApp("com.netflix.ninja")
                val phone = ComponentName("com.netflix.ninja", "com.netflix.ninja.Phone")
                packages.addActivityIfNotPresent(phone)
                packages.addIntentFilterForActivity(phone, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
                recorder.recordConfig.value = TvConfig(cinematicHome = true)
                service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

                tap(190).shouldBeTrue()

                recorder.lastOpened.times.value.keys shouldBe setOf("com.netflix.ninja/.Main")
            }

            test("with the record off an app a remapped button opens is not noted") {
                installLeanbackApp("com.netflix.ninja")
                service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

                tap(190).shouldBeTrue()

                started()?.component?.packageName shouldBe "com.netflix.ninja"
                recorder.lastOpened.times.value shouldBe (emptyMap<String, Long>())
            }

            test("a button that is not mapped passes through") {
                service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)).shouldBeFalse()
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)).shouldBeFalse()
                started().shouldBeNull()
            }

            test("a scancode entry wins over the keycode it shares a button with") {
                installLeanbackApp("com.amazon.amazonvideo.livingroom")
                installLeanbackApp("com.google.android.youtube.tv")
                service.start(
                    TvConfig(appKeys = listOf("196:com.google.android.youtube.tv", "scan:490:com.amazon.amazonvideo.livingroom")),
                )

                tap(196, scan = 490).shouldBeTrue()

                started()?.component?.packageName shouldBe "com.amazon.amazonvideo.livingroom"
            }

            test("a held button opens its hold target and not the tap one") {
                installLeanbackApp("com.netflix.ninja")
                installLeanbackApp("org.xbmc.kodi")
                service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja", "hold:190:org.xbmc.kodi")))

                service.handleKey(key(KeyEvent.ACTION_DOWN, 190))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))
                service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

                started()?.component?.packageName shouldBe "org.xbmc.kodi"
                started().shouldBeNull()
            }

            test("the inputs and library words reach homes own activity") {
                service.start(TvConfig(appKeys = listOf("365:inputs", "366:library")))

                tap(365)
                val inputs = started()
                inputs?.action shouldBe LauncherIntents.VIEW_INPUTS
                inputs?.component shouldBe home

                device += 1
                tap(366)
                val library = started()
                library?.action shouldBe LauncherIntents.APP_LIBRARY
                library?.component shouldBe home
            }

            test("an input target opens the passthrough channel uri") {
                service.start(TvConfig(appKeys = listOf("4062:input:com.tcl.tvinput/.TvPassThroughService/HW1")))

                tap(4062)

                val view = started()
                view?.action shouldBe Intent.ACTION_VIEW
                view?.type shouldBe "vnd.android.cursor.item/channel"
                view?.dataString.orEmpty() shouldContain "HW1"
            }

            test("the back word performs the global action and ignore swallows the button") {
                service.start(TvConfig(appKeys = listOf("111:back", "112:ignore")))

                tap(111).shouldBeTrue()
                device += 1
                tap(112).shouldBeTrue()

                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
                started().shouldBeNull()
            }

            test("the settings key opens settings on the up and a listed keycode does too") {
                service.start(TvConfig(settingsKeyKeycodes = listOf(1234), settingsKeyScancodes = listOf(159)))

                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SETTINGS)).shouldBeTrue()
                started().shouldBeNull()
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SETTINGS, at = 1_100)).shouldBeTrue()
                started()?.action shouldBe Settings.ACTION_SETTINGS

                device += 1
                tap(1234).shouldBeTrue()
                started()?.action shouldBe Settings.ACTION_SETTINGS

                device += 1
                tap(0, scan = 159).shouldBeTrue()
                started()?.action shouldBe Settings.ACTION_SETTINGS
            }

            test("an app keys entry for a settings keycode opens its app and not settings") {
                installLeanbackApp("com.netflix.ninja")
                installLeanbackApp("org.xbmc.kodi")
                service.start(
                    TvConfig(
                        appKeys = listOf("${KeyEvent.KEYCODE_SETTINGS}:com.netflix.ninja", "83:org.xbmc.kodi"),
                        settingsKeyKeycodes = listOf(83),
                    ),
                )

                tap(KeyEvent.KEYCODE_SETTINGS).shouldBeTrue()
                started()?.component?.packageName shouldBe "com.netflix.ninja"
                started().shouldBeNull()

                device += 1
                tap(83).shouldBeTrue()
                started()?.component?.packageName shouldBe "org.xbmc.kodi"
                started().shouldBeNull()
            }

            test("a button named in settings key scancodes opens settings whatever app keys says for its keycode") {
                installLeanbackApp("com.netflix.ninja")
                service.start(TvConfig(appKeys = listOf("83:com.netflix.ninja"), settingsKeyScancodes = listOf(159)))

                tap(83, scan = 159).shouldBeTrue()

                started()?.action shouldBe Settings.ACTION_SETTINGS
                started().shouldBeNull()
            }

            test("a hold entry alone on the settings key leaves the tap to settings and opens its app when held") {
                installLeanbackApp("org.xbmc.kodi")
                service.start(TvConfig(appKeys = listOf("hold:${KeyEvent.KEYCODE_SETTINGS}:org.xbmc.kodi")))

                tap(KeyEvent.KEYCODE_SETTINGS).shouldBeTrue()
                started()?.action shouldBe Settings.ACTION_SETTINGS

                device += 1
                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SETTINGS))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SETTINGS, at = 1_000 + HOLD_MS + 100))

                started()?.component?.packageName shouldBe "org.xbmc.kodi"
                started().shouldBeNull()
            }

            test("the settings key clears the settings task only with settings key clear task") {
                service.start(TvConfig())
                tap(KeyEvent.KEYCODE_SETTINGS).shouldBeTrue()
                started()?.flags shouldBe Intent.FLAG_ACTIVITY_NEW_TASK

                device += 1
                service.start(TvConfig(settingsKeyClearTask = true))
                tap(KeyEvent.KEYCODE_SETTINGS).shouldBeTrue()
                started()?.flags shouldBe (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            test("the tv and input buttons pass through with the default config") {
                service.start(TvConfig())

                for (code in listOf(KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_TV_INPUT)) {
                    pressInputButton(code) shouldBe (false to false)
                }
                started().shouldBeNull()
            }

            test("the tv and input buttons are consumed and open the switcher on the up with capture input keys") {
                service.start(TvConfig(captureInputKeys = true))

                for (code in listOf(KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_TV_INPUT)) {
                    service.onKeyEvent(key(KeyEvent.ACTION_DOWN, code)).shouldBeTrue()
                    started().shouldBeNull()
                    service.onKeyEvent(key(KeyEvent.ACTION_UP, code, at = 1_100)).shouldBeTrue()
                    started()?.action shouldBe LauncherIntents.VIEW_INPUTS
                }
            }

            test("a keycode a theme lists in input key keycodes is consumed with the default config") {
                service.start(TvConfig(inputKeyKeycodes = listOf(4100)))

                pressInputButton(4100) shouldBe (true to true)
                started()?.action shouldBe LauncherIntents.VIEW_INPUTS
            }

            test("the switcher request goes to homes own activity when no app answers it") {
                shadowOf(app).checkActivities(true)
                service.start(TvConfig(captureInputKeys = true))

                pressInputButton(KeyEvent.KEYCODE_TV_INPUT)

                val inputs = started()
                inputs?.action shouldBe LauncherIntents.VIEW_INPUTS
                inputs?.component shouldBe home
            }

            test("a listed app key beats the input button it shares") {
                installLeanbackApp("com.netflix.ninja")
                service.start(TvConfig(appKeys = listOf("${KeyEvent.KEYCODE_TV_INPUT}:com.netflix.ninja"), captureInputKeys = true))

                tap(KeyEvent.KEYCODE_TV_INPUT)

                started()?.component?.packageName shouldBe "com.netflix.ninja"
            }

            test("all apps opens the launcher with a home intent when it is the home app") {
                service.start(TvConfig())

                tap(KeyEvent.KEYCODE_ALL_APPS).shouldBeTrue()

                val open = started()
                open?.action shouldBe Intent.ACTION_MAIN
                open?.`package` shouldBe app.packageName
                open?.hasCategory(Intent.CATEGORY_HOME) shouldBe true
            }

            test("while the stock launcher stays home the launcher starts like an app and not with a home intent") {
                stockLauncherIsHome("com.stock.launcher")
                service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

                tap(KeyEvent.KEYCODE_ALL_APPS).shouldBeTrue()

                val open = started()
                open?.component shouldBe home
                open?.action.shouldBeNull()
                open?.hasCategory(Intent.CATEGORY_HOME) shouldBe true
            }

            test("a window of the stock home sends the tv back to the launcher and other windows do not") {
                stockLauncherIsHome("com.stock.launcher")
                service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.netflix.ninja", "com.netflix.ninja.Main")
                started().shouldBeNull()
                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.stock.launcher", "com.stock.launcher.HomeActivity")
                started()?.component shouldBe home
            }

            test("a short home press opens the launcher after the system saw it and a long one does not") {
                stockLauncherIsHome("com.stock.launcher")
                service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)).shouldBeFalse()
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 1_120)).shouldBeFalse()
                started().shouldBeNull()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOME_KEY_DELAY_MS))
                started()?.component shouldBe home

                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, downAt = 5_000))
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, downAt = 5_000, at = 6_500))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                started().shouldBeNull()
            }

            test("home long press home takes the home key and opens the launcher on the up") {
                service.start(TvConfig(homeLongPressHome = true))

                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)).shouldBeTrue()
                started().shouldBeNull()
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 2_000)).shouldBeTrue()
                started().shouldNotBeNull()

                device += 1
                service.start(TvConfig())
                service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 2_000)).shouldBeFalse()
            }

            test("key filter false drops the filter flag true sets it again and a redirect asks for window events") {
                val filtering = AccessibilityServiceInfo().apply { flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }

                adjustServiceInfo(filtering, keyFilter = true, windowEvents = false).shouldBeFalse()
                filtering.flags shouldBe AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

                adjustServiceInfo(filtering, keyFilter = false, windowEvents = false).shouldBeTrue()
                filtering.flags shouldBe 0
                filtering.eventTypes shouldBe 0

                adjustServiceInfo(filtering, keyFilter = true, windowEvents = true).shouldBeTrue()
                filtering.flags shouldBe AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                filtering.eventTypes shouldBe AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            }

            test("a front entry follows the activity in front") {
                val tvCenter = ComponentName("com.vt.tv.tvcenter", "com.vt.tv.tvcenter.Main")
                packages.addActivityIfNotPresent(tvCenter)
                installLeanbackApp("com.vt.tv.tvcenter")
                service.start(TvConfig(appKeys = listOf("172:com.vt.tv.tvcenter", "front:172:home")))

                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, tvCenter.packageName, tvCenter.className)
                tap(172).shouldBeTrue()
                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_HOME)
                started().shouldBeNull()

                // A dialog is not an activity: the app under it stays in front.
                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.vt.tv.tvcenter", "android.app.AlertDialog")
                device += 1
                tap(172)
                shadowOf(service).globalActionsPerformed.size shouldBe 2
            }

            test("power on home always opens home 800 ms after the screen comes on") {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10)) // past the boot window
                service.start(TvConfig(powerOnHomeAlways = true))
                idleFor(2_000)
                started().shouldBeNull()

                screenOn()
                idleFor(POWER_ON_ALWAYS_DELAY_MS - 1)
                started().shouldBeNull()
                idleFor(1)
                started()?.hasCategory(Intent.CATEGORY_HOME) shouldBe true
            }

            test("power on home always waits when a key was pressed after the screen came on") {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
                service.start(TvConfig(powerOnHomeAlways = true))
                idleFor(2_000)
                shadowOf(app).clearNextStartedActivities()

                screenOn()
                idleFor(300)
                service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                idleFor(POWER_ON_ALWAYS_DELAY_MS)

                started().shouldBeNull()
            }

            test("a pick written after the bind stops power on home always and clearing it brings it back") {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
                val picks = MutableStateFlow<String?>(null)
                service.follow(MutableStateFlow(alwaysOnWithChoice), picks)

                pick(picks, "com.netflix.ninja")
                screenOn()
                idleFor(POWER_ON_ALWAYS_DELAY_MS * 2)
                homeStarted().shouldBeFalse()

                pick(picks, null)
                screenOn()
                idleFor(POWER_ON_ALWAYS_DELAY_MS)
                homeStarted().shouldBeTrue()
            }

            test("a pick written after the bind stops the live tv redirect when it is live tv") {
                val picks = MutableStateFlow<String?>("com.netflix.ninja")
                service.follow(MutableStateFlow(TvConfig(powerOnHome = true, openOnStartChoice = true)), picks)
                screenOn()
                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.mediatek.wwtv.tvcenter", null)
                homeStarted().shouldBeTrue()

                pick(picks, "com.mediatek.wwtv.tvcenter")
                screenOn()
                service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.mediatek.wwtv.tvcenter", null)
                homeStarted().shouldBeFalse()
            }

            test("clearing the pick inside the boot window does not send home over the screen in front") {
                val picks = MutableStateFlow<String?>("com.netflix.ninja")
                service.follow(MutableStateFlow(alwaysOnWithChoice), picks)
                idleFor(2_000)

                pick(picks, null)
                idleFor(2_000)

                homeStarted().shouldBeFalse()
            }

            test("a pick that leaves the plan as it was does not start the service again") {
                installLeanbackApp("org.xbmc.kodi")
                val picks = MutableStateFlow<String?>("com.netflix.ninja")
                service.follow(MutableStateFlow(holdKeys()), picks)
                service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

                pick(picks, "org.xbmc.kodi")
                idleFor(HOLD_MS)
                service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

                started()?.component?.packageName shouldBe "org.xbmc.kodi"
            }

            test("a service bound while the user is locked reads the theme only after the unlock") {
                installLeanbackApp("com.netflix.ninja")
                pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")
                shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(false)

                service.connected()
                service.handleKey(key(KeyEvent.ACTION_DOWN, 190)).shouldBeFalse()

                shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
                app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
                awaitMapped(190)
                tap(190).shouldBeTrue()
                started()?.component?.packageName shouldBe "com.netflix.ninja"
            }

            test("a service bound after the unlock reads the theme at once") {
                installLeanbackApp("com.netflix.ninja")
                pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")

                service.connected()

                awaitMapped(190)
                tap(190).shouldBeTrue()
                started()?.component?.packageName shouldBe "com.netflix.ninja"
            }

            test("the power menu opens through the bound service and is gone once it is destroyed") {
                service.connected()

                PowerMenu.available().shouldBeTrue()
                PowerMenu.open()
                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)

                service.onDestroy()

                PowerMenu.available().shouldBeFalse()
                PowerMenu.open()
                shadowOf(service).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            }

            test("the destroy of an earlier service does not unbind the one that replaced it") {
                val replacement = Robolectric.setupService(KeyService::class.java)
                service.connected()
                replacement.connected()

                service.onDestroy()

                PowerMenu.available().shouldBeTrue()
                PowerMenu.open()
                shadowOf(replacement).globalActionsPerformed shouldBe listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                replacement.onDestroy()
            }

            test("a choice the service does not read leaves it running and a pressed hold stands") {
                installLeanbackApp("org.xbmc.kodi")
                val configs = MutableStateFlow(holdKeys())
                service.follow(configs)
                service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

                configs.value = configs.value.copy(columns = 4, rowsBelowDock = 2, appLibrary = true, clockFormat = ClockFormat.Hour12)
                idleFor(HOLD_MS)
                service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

                started()?.component?.packageName shouldBe "org.xbmc.kodi"
            }

            test("a value the service reads starts it again with the new maps") {
                installLeanbackApp("org.xbmc.kodi")
                installLeanbackApp("com.plex.tv")
                val configs = MutableStateFlow(holdKeys())
                service.follow(configs)
                service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

                configs.value = configs.value.copy(appKeys = listOf("190:com.plex.tv"))
                idleFor(HOLD_MS)
                service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))
                started().shouldBeNull()

                device += 1
                tap(190).shouldBeTrue()
                started()?.component?.packageName shouldBe "com.plex.tv"
            }

            test("with restore key services the service records itself on load and drops the record when unbound") {
                service.start(TvConfig(restoreKeyServices = true))

                recordedServices() shouldBe setOf(KeyService::class.java.name)

                service.onUnbind(Intent())

                recordedServices() shouldBe emptySet<String>()
            }

            test("without restore key services nothing is recorded") {
                service.start(TvConfig())

                recordedServices() shouldBe emptySet<String>()
            }

            test("with the opt in and the permission the screen going off makes the service wait for the screen") {
                optInToStandbyExit()
                service.start(TvConfig())

                screenOff()

                watchingScreenOn().shouldBeTrue()
            }

            test("without the permission the screen going off leaves the service bound") {
                GuardKeep.setOffInStandby(app, true)
                shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                service.start(TvConfig())

                screenOff()

                watchingScreenOn().shouldBeFalse()
            }

            test("without the opt in the screen going off leaves the service bound") {
                shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
                service.start(TvConfig())

                screenOff()

                watchingScreenOn().shouldBeFalse()
            }

            test("an opt in that arrives after the service bound arms it") {
                service.start(TvConfig())
                PowerMenu.bind(service)

                optInToStandbyExit()
                GuardKeep.ensureKeyService(app)
                screenOff()

                watchingScreenOn().shouldBeTrue()
            }

            test("turning the opt in off disarms a bound service") {
                optInToStandbyExit()
                service.start(TvConfig())
                PowerMenu.bind(service)

                GuardKeep.setOffInStandby(app, false)
                GuardKeep.ensureKeyService(app)
                screenOff()

                watchingScreenOn().shouldBeFalse()
            }

            test("a service armed while the screen is already off steps out at once") {
                optInToStandbyExit()
                shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)

                service.start(TvConfig())

                watchingScreenOn().shouldBeTrue()
            }

            test("a service that stepped out for standby keeps its repair record when it unbinds") {
                optInToStandbyExit()
                service.start(TvConfig(restoreKeyServices = true))

                screenOff()
                service.onUnbind(Intent())

                ServiceRepair.recorded(app) shouldBe setOf(KeyService::class.java.name)
            }
        }
    })
