package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

class OpenOnStartRulesTest {
    private val self = "dev.niltsiar.tvhome"
    private val youtube = "com.google.android.youtube.tv"

    @Test
    fun first_home_of_a_new_boot_opens_the_app() {
        assertTrue(openOnStartDue(12, 11, youtube, self))
        assertTrue(openOnStartDue(0, -1, youtube, self))
    }

    @Test
    fun later_home_of_the_same_boot_stays_home() {
        assertFalse(openOnStartDue(12, 12, youtube, self))
    }

    @Test
    fun unknown_boot_count_stays_home() {
        assertFalse(openOnStartDue(-1, -1, youtube, self))
    }

    @Test
    fun off_stays_home() {
        assertFalse(openOnStartDue(12, 11, "", self))
        assertFalse(openOnStartDue(12, 11, null, self))
    }

    @Test
    fun the_launcher_itself_is_never_opened() {
        assertFalse(openOnStartDue(12, 11, self, self))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OpenOnStartTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val opened = mutableListOf<Intent>()

    init {
        // Before Android 13 the compat call asks for this permission of its own to register a not-exported receiver.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    private fun liveTvApp() = tvApp("com.tv.live")

    /** An app with a TV entry point, as the package manager answers for one package and for all of them. */
    private fun tvApp(
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

    private fun bootCount(count: Int) {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, count)
    }

    private fun TestScope.opener(
        wait: Int = 0,
        validated: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ) = OpenOnStart(
        context,
        MutableStateFlow(TvConfig(bootToLiveTv = true, liveTvPackage = "com.tv.live", openOnStartNetworkWait = wait)),
        MutableStateFlow(""),
        validated,
        StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.pickOpener(
        config: MutableStateFlow<TvConfig>,
        pick: MutableStateFlow<String> = MutableStateFlow(""),
        validated: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ) = OpenOnStart(context, config, pick, validated, StandardTestDispatcher(testScheduler))

    private val chosen = TvConfig(openOnStartChoice = true)

    @Test
    fun boot_to_live_tv_opens_the_live_tv_app_once_per_boot() =
        runTest {
            liveTvApp()
            bootCount(7)
            val opener = opener()

            opener.openAfterBoot { opened += it }
            opener.openAfterBoot { opened += it }
            assertEquals(listOf("com.tv.live"), opened.map { it.component?.packageName })

            bootCount(8)
            opener.openAfterBoot { opened += it }
            assertEquals(2, opened.size)
        }

    @Test
    fun the_boot_is_written_before_the_app_starts() =
        runTest {
            liveTvApp()
            bootCount(7)

            opener().openAfterBoot {
                assertEquals(7, context.getSharedPreferences("boot", Context.MODE_PRIVATE).getInt("live_tv_boot", -1))
                opened += it
            }

            assertEquals(1, opened.size)
        }

    @Test
    fun an_unknown_boot_count_or_a_missing_app_opens_nothing() =
        runTest {
            liveTvApp()
            opener().openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)

            bootCount(7)
            val withoutApp =
                OpenOnStart(
                    context,
                    MutableStateFlow(TvConfig(bootToLiveTv = true, liveTvPackage = "com.missing")),
                    MutableStateFlow(""),
                    MutableStateFlow(true),
                )
            withoutApp.openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun boot_to_live_tv_turned_on_later_applies_to_the_same_opener() =
        runTest {
            liveTvApp()
            bootCount(7)
            val config = MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live"))
            val opener = pickOpener(config)

            opener.openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)

            config.value = config.value.copy(bootToLiveTv = true)
            opener.openAfterBoot { opened += it }
            assertEquals(listOf("com.tv.live"), opened.map { it.component?.packageName })
        }

    @Test
    fun without_boot_to_live_tv_nothing_opens() =
        runTest {
            liveTvApp()
            bootCount(7)

            pickOpener(MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live"))).openAfterBoot { opened += it }

            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun online_the_app_opens_at_once_and_offline_it_waits_for_the_network_or_the_end_of_the_wait() =
        runTest {
            liveTvApp()
            bootCount(7)
            val network = MutableStateFlow(false)
            val job = launch { opener(wait = 60, validated = network).openAfterBoot { opened += it } }

            advanceTimeBy(59_000)
            runCurrent()
            assertEquals(0, opened.size)

            network.value = true
            job.join()
            assertEquals(1, opened.size)
        }

    @Test
    fun without_a_network_the_app_opens_when_the_wait_is_over() =
        runTest {
            liveTvApp()
            bootCount(7)
            val job = launch { opener(wait = 60, validated = MutableStateFlow(false)).openAfterBoot { opened += it } }

            advanceTimeBy(59_999)
            assertEquals(0, opened.size)
            advanceTimeBy(2)
            job.join()

            assertEquals(1, opened.size)
        }

    @Test
    fun leaving_home_during_the_wait_keeps_the_app_closed_and_the_boot_handled() =
        runTest {
            liveTvApp()
            bootCount(7)
            val job = launch { opener(wait = 60, validated = MutableStateFlow(false)).openAfterBoot { opened += it } }
            advanceTimeBy(10_000)

            job.cancel()
            opener().openAfterBoot { opened += it }

            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun the_pick_opens_once_per_boot_when_the_choice_key_is_on_and_nothing_without_it() =
        runTest {
            tvApp("com.tv.pick")
            bootCount(7)
            val config = MutableStateFlow(TvConfig(openOnStartChoice = false))
            val opener = pickOpener(config, MutableStateFlow("com.tv.pick"))

            opener.openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)

            config.value = chosen
            opener.openAfterBoot { opened += it }
            opener.openAfterBoot { opened += it }
            assertEquals(listOf("com.tv.pick"), opened.map { it.component?.packageName })

            bootCount(8)
            opener.openAfterBoot { opened += it }
            assertEquals(2, opened.size)
        }

    @Test
    fun boot_to_live_tv_wins_over_the_pick() =
        runTest {
            liveTvApp()
            tvApp("com.tv.pick")
            bootCount(7)
            val config = MutableStateFlow(chosen.copy(bootToLiveTv = true, liveTvPackage = "com.tv.live"))

            pickOpener(config, MutableStateFlow("com.tv.pick")).openAfterBoot { opened += it }

            assertEquals(listOf("com.tv.live"), opened.map { it.component?.packageName })
        }

    @Test
    fun a_pick_that_is_gone_keeps_home_and_the_boot_is_handled() =
        runTest {
            bootCount(7)

            pickOpener(MutableStateFlow(chosen), MutableStateFlow("com.gone")).openAfterBoot { opened += it }

            assertEquals(emptyList<Intent>(), opened)
            assertEquals(7, context.getSharedPreferences("boot", Context.MODE_PRIVATE).getInt("live_tv_boot", -1))
        }

    @Test
    fun the_launcher_itself_picked_opens_nothing() =
        runTest {
            bootCount(7)

            pickOpener(MutableStateFlow(chosen), MutableStateFlow(context.packageName)).openAfterBoot { opened += it }

            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun a_change_of_pick_applies_to_the_same_opener() =
        runTest {
            tvApp("com.tv.first")
            tvApp("com.tv.second")
            bootCount(7)
            val pick = MutableStateFlow("com.tv.first")
            val opener = pickOpener(MutableStateFlow(chosen), pick)
            opener.skipThisBoot()

            pick.value = "com.tv.second"
            bootCount(8)
            opener.openAfterBoot { opened += it }

            assertEquals(listOf("com.tv.second"), opened.map { it.component?.packageName })
        }

    @Test
    fun skipping_this_boot_makes_it_handled_and_the_next_boot_opens_the_pick() =
        runTest {
            tvApp("com.tv.pick")
            bootCount(7)
            val opener = pickOpener(MutableStateFlow(chosen), MutableStateFlow("com.tv.pick"))

            opener.skipThisBoot()
            opener.openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)

            bootCount(8)
            opener.openAfterBoot { opened += it }
            assertEquals(1, opened.size)
        }

    @Test
    fun the_network_wait_holds_the_pick_too() =
        runTest {
            tvApp("com.tv.pick")
            bootCount(7)
            val network = MutableStateFlow(false)
            val opener = pickOpener(MutableStateFlow(chosen.copy(openOnStartNetworkWait = 60)), MutableStateFlow("com.tv.pick"), network)
            val job = launch { opener.openAfterBoot { opened += it } }

            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(0, opened.size)

            network.value = true
            job.join()
            assertEquals(1, opened.size)
        }

    private val screenOn = Intent(Intent.ACTION_SCREEN_ON)

    private fun TestScope.listenForWakes(opener: OpenOnStart) {
        backgroundScope.launch { opener.wakes.collect { opener.openOnWake { opened += it } } }
        runCurrent()
    }

    private fun TestScope.wake() {
        context.sendBroadcast(screenOn)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun watchingScreenOn() =
        shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.any {
            it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON)
        }

    @Test
    fun a_wake_opens_the_pick_when_the_choice_and_the_wake_keys_are_on() =
        runTest {
            tvApp("com.tv.pick")
            listenForWakes(pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), MutableStateFlow("com.tv.pick")))

            wake()
            wake()

            assertEquals(listOf("com.tv.pick", "com.tv.pick"), opened.map { it.component?.packageName })
        }

    @Test
    fun with_either_key_off_a_wake_opens_nothing_and_no_receiver_listens() =
        runTest {
            tvApp("com.tv.pick")
            val onlyWake = TvConfig(openOnStartChoice = false, openOnStartOnWake = true)
            val onlyChoice = TvConfig(openOnStartChoice = true, openOnStartOnWake = false)
            listenForWakes(pickOpener(MutableStateFlow(onlyWake), MutableStateFlow("com.tv.pick")))
            listenForWakes(pickOpener(MutableStateFlow(onlyChoice), MutableStateFlow("com.tv.pick")))

            wake()

            assertEquals(emptyList<Intent>(), opened)
            assertFalse(watchingScreenOn())
        }

    @Test
    fun the_wake_keys_apply_while_home_lives() =
        runTest {
            tvApp("com.tv.pick")
            val config = MutableStateFlow(chosen)
            listenForWakes(pickOpener(config, MutableStateFlow("com.tv.pick")))
            wake()
            assertEquals(0, opened.size)
            assertFalse(watchingScreenOn())

            config.value = chosen.copy(openOnStartOnWake = true)
            runCurrent()
            assertTrue(watchingScreenOn())
            wake()
            assertEquals(1, opened.size)

            config.value = chosen
            runCurrent()
            assertFalse(watchingScreenOn())
        }

    @Test
    fun a_changed_pick_applies_at_the_next_wake() =
        runTest {
            tvApp("com.tv.first")
            tvApp("com.tv.second")
            val pick = MutableStateFlow("com.tv.first")
            listenForWakes(pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), pick))
            wake()

            pick.value = "com.tv.second"
            wake()

            assertEquals(listOf("com.tv.first", "com.tv.second"), opened.map { it.component?.packageName })
        }

    @Test
    fun a_wake_without_a_pick_or_with_a_gone_app_or_the_launcher_opens_nothing() =
        runTest {
            val pick = MutableStateFlow("")
            listenForWakes(pickOpener(MutableStateFlow(chosen.copy(openOnStartOnWake = true)), pick))

            wake()
            pick.value = "com.gone"
            wake()
            pick.value = context.packageName
            wake()

            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun a_wake_waits_for_the_network_like_the_boot() =
        runTest {
            tvApp("com.tv.pick")
            val network = MutableStateFlow(false)
            val config = MutableStateFlow(chosen.copy(openOnStartOnWake = true, openOnStartNetworkWait = 60))
            listenForWakes(pickOpener(config, MutableStateFlow("com.tv.pick"), network))

            wake()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(0, opened.size)

            network.value = true
            runCurrent()
            assertEquals(1, opened.size)
        }

    @Test
    fun the_picker_lists_one_entry_per_package_with_a_tv_entry_by_label_ignoring_case_without_the_launcher() =
        runTest {
            tvApp("com.zeta", "Zeta")
            tvApp("com.alpha", "alpha")
            tvApp("com.alpha", "alpha again")
            tvApp("com.beta", "Beta")
            tvApp(context.packageName, "This launcher")

            val apps = pickOpener(MutableStateFlow(chosen)).apps()

            assertEquals(
                listOf("alpha" to "com.alpha", "Beta" to "com.beta", "Zeta" to "com.zeta"),
                apps.map { it.label to it.packageName },
            )
        }

    @Test
    fun an_installed_app_has_its_label_and_a_gone_one_has_none() =
        runTest {
            shadowOf(context.packageManager).installPackage(
                PackageInfo().apply {
                    packageName = "com.tv.pick"
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = "com.tv.pick"
                            nonLocalizedLabel = "Pick TV"
                        }
                },
            )
            val opener = pickOpener(MutableStateFlow(chosen))

            assertEquals("Pick TV", opener.label("com.tv.pick"))
            assertNull(opener.label("com.gone"))
        }
}
