package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
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

    private fun liveTvApp() {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage("com.tv.live")
        val info =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.tv.live"
                        name = "com.tv.live.Main"
                    }
            }
        shadowOf(context.packageManager).addResolveInfoForIntent(launch, info)
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
        validated,
        StandardTestDispatcher(testScheduler),
    )

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
                OpenOnStart(context, MutableStateFlow(TvConfig(bootToLiveTv = true, liveTvPackage = "com.missing")), MutableStateFlow(true))
            withoutApp.openAfterBoot { opened += it }
            assertEquals(emptyList<Intent>(), opened)
        }

    @Test
    fun boot_to_live_tv_turned_on_later_applies_to_the_same_opener() =
        runTest {
            liveTvApp()
            bootCount(7)
            val config = MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live"))
            val opener = OpenOnStart(context, config, MutableStateFlow(true), StandardTestDispatcher(testScheduler))

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

            OpenOnStart(context, MutableStateFlow(TvConfig(bootToLiveTv = false, liveTvPackage = "com.tv.live")), MutableStateFlow(true))
                .openAfterBoot { opened += it }

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
}
