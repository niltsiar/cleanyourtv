package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputTarget
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

val OpenOnStartRulesTest by testSuite {
    val self = "dev.niltsiar.tvhome"
    val youtube = "com.google.android.youtube.tv"

    test("first home of a new boot opens the app") {
        openOnStartDue(12, 11, youtube, self).shouldBeTrue()
        openOnStartDue(0, -1, youtube, self).shouldBeTrue()
    }

    test("later home of the same boot stays home") {
        openOnStartDue(12, 12, youtube, self).shouldBeFalse()
    }

    test("unknown boot count stays home") {
        openOnStartDue(-1, -1, youtube, self).shouldBeFalse()
    }

    test("off stays home") {
        openOnStartDue(12, 11, "", self).shouldBeFalse()
        openOnStartDue(12, 11, null, self).shouldBeFalse()
    }

    test("the launcher itself is never opened") {
        openOnStartDue(12, 11, self, self).shouldBeFalse()
    }
}

val OpenOnStartTest by testSuite {
    robolectricTestSuite<OpenOnStartTestContent>("default sdk")
}

@OptIn(ExperimentalCoroutinesApi::class)
class OpenOnStartTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val context: Context = RuntimeEnvironment.getApplication()
                val opened = mutableListOf<Intent>()
                val switched = mutableListOf<InputTarget.TvInput>()

                init {
                    // Before Android 13 the compat call asks for this permission of its own to register a not-exported receiver.
                    shadowOf(
                        RuntimeEnvironment.getApplication(),
                    ).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                }

                fun liveTvApp() = tvApp("com.tv.live")

                /** An app with a TV entry point, as the package manager answers for one package and for all of them. */
                fun tvApp(
                    pkg: String,
                    label: String = pkg,
                ) {
                    val info =
                        ResolveInfo().apply {
                            nonLocalizedLabel = label
                            activityInfo =
                                ActivityInfo().apply {
                                    packageName = pkg
                                    name = "$pkg.Main"
                                }
                        }
                    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    shadowOf(context.packageManager).addResolveInfoForIntent(Intent(launch), info)
                    shadowOf(context.packageManager).addResolveInfoForIntent(Intent(launch).setPackage(pkg), info)
                }

                /** An app with only a phone entry point, `pkg/pkg.Phone`, as the package manager answers for that package. */
                fun phoneApp(pkg: String) {
                    val info =
                        ResolveInfo().apply {
                            activityInfo =
                                ActivityInfo().apply {
                                    packageName = pkg
                                    name = "$pkg.Phone"
                                }
                        }
                    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                    shadowOf(context.packageManager).addResolveInfoForIntent(launch, info)
                }

                /** An installed app with no activity to open. */
                fun appWithoutEntry(pkg: String) {
                    shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkg })
                }

                fun bootCount(count: Int) {
                    Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, count)
                }

                fun TestScope.opener(
                    wait: Int = 0,
                    validated: MutableStateFlow<Boolean> = MutableStateFlow(true),
                ) = OpenOnStart(
                    context,
                    MutableStateFlow(TvConfig(bootToLiveTv = true, liveTvPackage = "com.tv.live", openOnStartNetworkWait = wait)),
                    MutableStateFlow(null),
                    validated,
                    StandardTestDispatcher(testScheduler),
                )

                fun TestScope.pickOpener(
                    config: MutableStateFlow<TvConfig>,
                    pick: StateFlow<String?> = MutableStateFlow(null),
                    validated: MutableStateFlow<Boolean> = MutableStateFlow(true),
                ) = OpenOnStart(context, config, pick, validated, StandardTestDispatcher(testScheduler))

                val chosen = TvConfig(openOnStartChoice = true)

                val screenOn = Intent(Intent.ACTION_SCREEN_ON)

                fun TestScope.listenForWakes(opener: OpenOnStart) {
                    backgroundScope.launch { opener.wakes.collect { opener.openOnWake { opened += it } } }
                    runCurrent()
                }

                fun TestScope.wake() {
                    context.sendBroadcast(screenOn)
                    shadowOf(Looper.getMainLooper()).idle()
                    runCurrent()
                }

                fun watchingScreenOn() =
                    shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.any {
                        it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON)
                    }
            }
        } asContextForEach {
            test("boot to live tv opens the live tv app once per boot") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val opener = opener()

                    opener.openAfterBoot(switched::add) { opened += it }
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.live")

                    bootCount(8)
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened shouldHaveSize 2
                }
            }

            test("the boot is written before the app starts") {
                runTest {
                    liveTvApp()
                    bootCount(7)

                    opener().openAfterBoot(switched::add) {
                        context.getSharedPreferences("boot", Context.MODE_PRIVATE).getInt("live_tv_boot", -1) shouldBe 7
                        opened += it
                    }

                    opened shouldHaveSize 1
                }
            }

            test("an unknown boot count or a missing app opens nothing") {
                runTest {
                    liveTvApp()
                    opener().openAfterBoot(switched::add) { opened += it }
                    opened.shouldBeEmpty()

                    bootCount(7)
                    val withoutApp =
                        OpenOnStart(
                            context,
                            MutableStateFlow(TvConfig(bootToLiveTv = true, liveTvPackage = "com.missing")),
                            MutableStateFlow(null),
                            MutableStateFlow(true),
                        )
                    withoutApp.openAfterBoot(switched::add) { opened += it }
                    opened.shouldBeEmpty()
                }
            }

            test("boot to live tv turned on later applies to the same opener") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val config = MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live"))
                    val opener = pickOpener(config)

                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.shouldBeEmpty()

                    config.value = config.value.copy(bootToLiveTv = true)
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.live")
                }
            }

            test("without boot to live tv nothing opens") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val opener = pickOpener(MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live")))

                    opener.openAfterBoot(switched::add) { opened += it }

                    opened.shouldBeEmpty()
                }
            }

            test("online the app opens at once and offline it waits for the network or the end of the wait") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val network = MutableStateFlow(false)
                    val job = launch { opener(wait = 60, validated = network).openAfterBoot(switched::add) { opened += it } }

                    advanceTimeBy(59_000)
                    runCurrent()
                    opened shouldHaveSize 0

                    network.value = true
                    job.join()
                    opened shouldHaveSize 1
                }
            }

            test("without a network the app opens when the wait is over") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val job =
                        launch {
                            opener(wait = 60, validated = MutableStateFlow(false)).openAfterBoot(switched::add) { opened += it }
                        }

                    advanceTimeBy(59_999)
                    opened shouldHaveSize 0
                    advanceTimeBy(2)
                    job.join()

                    opened shouldHaveSize 1
                }
            }

            test("leaving home during the wait keeps the app closed and the boot handled") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val job =
                        launch {
                            opener(wait = 60, validated = MutableStateFlow(false)).openAfterBoot(switched::add) { opened += it }
                        }
                    advanceTimeBy(10_000)

                    job.cancel()
                    opener().openAfterBoot(switched::add) { opened += it }

                    opened.shouldBeEmpty()
                }
            }

            test("an input launch action for the live tv app switches to that input at once and once per boot") {
                runTest {
                    liveTvApp()
                    bootCount(7)
                    val config =
                        TvConfig(
                            bootToLiveTv = true,
                            liveTvPackage = "com.tv.live",
                            launchActions = mapOf("com.tv.live" to "input:a/.B/HW5"),
                            openOnStartNetworkWait = 60,
                        )
                    val opener = pickOpener(MutableStateFlow(config), validated = MutableStateFlow(false))

                    opener.openAfterBoot({
                        context.getSharedPreferences("boot", Context.MODE_PRIVATE).getInt("live_tv_boot", -1) shouldBe 7
                        switched += it
                    }) { opened += it }
                    opener.openAfterBoot(switched::add) { opened += it }

                    switched shouldBe listOf(InputTarget.TvInput("a/.B/HW5"))
                    opened.shouldBeEmpty()
                }
            }

            test("an input launch action for the live tv app does nothing without boot to live tv") {
                runTest {
                    tvApp("com.tv.pick")
                    bootCount(7)
                    val config = chosen.copy(liveTvPackage = "com.tv.live", launchActions = mapOf("com.tv.live" to "input:a/.B/HW5"))

                    pickOpener(MutableStateFlow(config), MutableStateFlow("com.tv.pick")).openAfterBoot(switched::add) { opened += it }

                    switched.shouldBeEmpty()
                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.pick")
                }
            }

            test("the pick opens once per boot when the choice key is on and nothing without it") {
                runTest {
                    tvApp("com.tv.pick")
                    bootCount(7)
                    val config = MutableStateFlow(TvConfig(openOnStartChoice = false))
                    val opener = pickOpener(config, MutableStateFlow("com.tv.pick"))

                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.shouldBeEmpty()

                    config.value = chosen
                    opener.openAfterBoot(switched::add) { opened += it }
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.pick")

                    bootCount(8)
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened shouldHaveSize 2
                }
            }

            test("boot to live tv wins over the pick") {
                runTest {
                    liveTvApp()
                    tvApp("com.tv.pick")
                    bootCount(7)
                    val config = MutableStateFlow(chosen.copy(bootToLiveTv = true, liveTvPackage = "com.tv.live"))

                    pickOpener(config, MutableStateFlow("com.tv.pick")).openAfterBoot(switched::add) { opened += it }

                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.live")
                }
            }

            test("a pick that is gone keeps home and the boot is handled") {
                runTest {
                    bootCount(7)

                    pickOpener(MutableStateFlow(chosen), MutableStateFlow("com.gone")).openAfterBoot(switched::add) { opened += it }

                    opened.shouldBeEmpty()
                    context.getSharedPreferences("boot", Context.MODE_PRIVATE).getInt("live_tv_boot", -1) shouldBe 7
                }
            }

            test("a pick with only a phone entry opens that entry at boot and on a wake") {
                runTest {
                    phoneApp("com.phone.pick")
                    bootCount(7)
                    val opener = pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), MutableStateFlow("com.phone.pick"))

                    opener.openAfterBoot(switched::add) { opened += it }
                    listenForWakes(opener)
                    wake()

                    opened.map { it.component?.className } shouldBe listOf("com.phone.pick.Phone", "com.phone.pick.Phone")
                }
            }

            test("a pick with neither a tv nor a phone entry keeps home at boot and on a wake") {
                runTest {
                    appWithoutEntry("com.no.entry")
                    bootCount(7)
                    val opener = pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), MutableStateFlow("com.no.entry"))

                    opener.openAfterBoot(switched::add) { opened += it }
                    listenForWakes(opener)
                    wake()

                    opened.shouldBeEmpty()
                }
            }

            test("the launcher itself picked opens nothing") {
                runTest {
                    bootCount(7)

                    pickOpener(MutableStateFlow(chosen), MutableStateFlow(context.packageName)).openAfterBoot(switched::add) {
                        opened += it
                    }

                    opened.shouldBeEmpty()
                }
            }

            test("a change of pick applies to the same opener") {
                runTest {
                    tvApp("com.tv.first")
                    tvApp("com.tv.second")
                    bootCount(7)
                    val pick = MutableStateFlow("com.tv.first")
                    val opener = pickOpener(MutableStateFlow(chosen), pick)
                    opener.skipThisBoot()

                    pick.value = "com.tv.second"
                    bootCount(8)
                    opener.openAfterBoot(switched::add) { opened += it }

                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.second")
                }
            }

            test("skipping this boot makes it handled and the next boot opens the pick") {
                runTest {
                    tvApp("com.tv.pick")
                    bootCount(7)
                    val opener = pickOpener(MutableStateFlow(chosen), MutableStateFlow("com.tv.pick"))

                    opener.skipThisBoot()
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened.shouldBeEmpty()

                    bootCount(8)
                    opener.openAfterBoot(switched::add) { opened += it }
                    opened shouldHaveSize 1
                }
            }

            test("the network wait holds the pick too") {
                runTest {
                    tvApp("com.tv.pick")
                    bootCount(7)
                    val network = MutableStateFlow(false)
                    val opener =
                        pickOpener(MutableStateFlow(chosen.copy(openOnStartNetworkWait = 60)), MutableStateFlow("com.tv.pick"), network)
                    val job = launch { opener.openAfterBoot(switched::add) { opened += it } }

                    advanceTimeBy(30_000)
                    runCurrent()
                    opened shouldHaveSize 0

                    network.value = true
                    job.join()
                    opened shouldHaveSize 1
                }
            }

            test("a wake opens the pick when the choice and the wake keys are on") {
                runTest {
                    tvApp("com.tv.pick")
                    listenForWakes(
                        pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), MutableStateFlow("com.tv.pick")),
                    )

                    wake()
                    wake()

                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.pick", "com.tv.pick")
                }
            }

            test("with either key off a wake opens nothing and no receiver listens") {
                runTest {
                    tvApp("com.tv.pick")
                    val onlyWake = TvConfig(openOnStartChoice = false, openOnStartOnWake = true)
                    val onlyChoice = TvConfig(openOnStartChoice = true, openOnStartOnWake = false)
                    listenForWakes(pickOpener(MutableStateFlow(onlyWake), MutableStateFlow("com.tv.pick")))
                    listenForWakes(pickOpener(MutableStateFlow(onlyChoice), MutableStateFlow("com.tv.pick")))

                    wake()

                    opened.shouldBeEmpty()
                    watchingScreenOn().shouldBeFalse()
                }
            }

            test("the wake keys apply while home lives") {
                runTest {
                    tvApp("com.tv.pick")
                    val config = MutableStateFlow(chosen)
                    listenForWakes(pickOpener(config, MutableStateFlow("com.tv.pick")))
                    wake()
                    opened shouldHaveSize 0
                    watchingScreenOn().shouldBeFalse()

                    config.value = chosen.copy(openOnStartOnWake = true)
                    runCurrent()
                    watchingScreenOn().shouldBeTrue()
                    wake()
                    opened shouldHaveSize 1

                    config.value = chosen
                    runCurrent()
                    watchingScreenOn().shouldBeFalse()
                }
            }

            test("a changed pick applies at the next wake") {
                runTest {
                    tvApp("com.tv.first")
                    tvApp("com.tv.second")
                    val pick = MutableStateFlow("com.tv.first")
                    listenForWakes(pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), pick))
                    wake()

                    pick.value = "com.tv.second"
                    wake()

                    opened.map { it.component?.packageName } shouldBe listOf("com.tv.first", "com.tv.second")
                }
            }

            test("a wake without a pick or with a gone app or the launcher opens nothing") {
                runTest {
                    val pick = MutableStateFlow<String?>(null)
                    listenForWakes(pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), pick))

                    wake()
                    pick.value = "com.gone"
                    wake()
                    pick.value = context.packageName
                    wake()

                    opened.shouldBeEmpty()
                }
            }

            test("a wake waits for the network like the boot") {
                runTest {
                    tvApp("com.tv.pick")
                    val network = MutableStateFlow(false)
                    val config = MutableStateFlow(chosen.copy(openOnStartOnWake = true, openOnStartNetworkWait = 60))
                    listenForWakes(pickOpener(config, MutableStateFlow("com.tv.pick"), network))

                    wake()
                    advanceTimeBy(30_000)
                    runCurrent()
                    opened shouldHaveSize 0

                    network.value = true
                    runCurrent()
                    opened shouldHaveSize 1
                }
            }

            test("the picker lists one entry per package with a tv entry by label ignoring case without the launcher") {
                runTest {
                    tvApp("com.zeta", "Zeta")
                    tvApp("com.alpha", "alpha")
                    tvApp("com.alpha", "alpha again")
                    tvApp("com.beta", "Beta")
                    tvApp(context.packageName, "This launcher")

                    val apps = pickOpener(MutableStateFlow(chosen)).apps()

                    apps.map { it.label to it.packageName } shouldBe
                        listOf("alpha" to "com.alpha", "Beta" to "com.beta", "Zeta" to "com.zeta")
                }
            }
        }
    })
