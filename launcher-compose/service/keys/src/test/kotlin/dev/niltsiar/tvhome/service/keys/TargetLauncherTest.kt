package dev.niltsiar.tvhome.service.keys

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LaunchLog
import dev.niltsiar.tvhome.data.system.SystemSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TargetLauncherTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val hdmi = "com.tv.live/.HdmiInput/HW5"

    private val record = MutableStateFlow(TvConfig(cinematicHome = true))
    private val lastOpened = LastOpened(app, record, Dispatchers.Unconfined)
    private val appLauncher =
        AppLauncher(app, record, lastOpened, LaunchLog(File(app.filesDir, "launch_log.txt"), record, Dispatchers.Unconfined))

    private fun launcher(
        liveTvPackage: String? = null,
        context: Context = app,
    ) = TargetLauncher(context, SystemSettings(app), performGlobal = { false }, liveTvPackage = liveTvPackage, opened = appLauncher)

    private fun installTvApp(pkg: String) {
        val component = ComponentName(pkg, "$pkg.Main")
        shadowOf(app.packageManager).addActivityIfNotPresent(component)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
        )
    }

    private fun installChannelViewer(pkg: String) {
        val component = ComponentName(pkg, "$pkg.Channel")
        shadowOf(app.packageManager).addActivityIfNotPresent(component)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                addDataAuthority(TvContract.AUTHORITY, null)
                addDataType("vnd.android.cursor.item/channel")
            },
        )
    }

    @Test
    fun an_input_key_asks_the_live_tv_app_first() {
        launcher("com.tv.live").launch("input:$hdmi")

        val first = shadowOf(app).nextStartedActivity
        assertEquals("com.tv.live", first.`package`)
        assertEquals(TvContract.buildChannelUriForPassthroughInput(hdmi), first.data)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun an_input_key_falls_back_to_the_plain_intent_when_the_live_tv_app_cannot_start_it() {
        shadowOf(app).checkActivities(true)
        installChannelViewer("com.files")

        launcher("com.tv.live").launch("input:$hdmi")

        val started = shadowOf(app).nextStartedActivity
        assertNull(started.`package`)
        assertEquals(TvContract.buildChannelUriForPassthroughInput(hdmi), started.data)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun an_input_key_sends_only_the_plain_intent_without_a_live_tv_package() {
        for (unset in listOf(null, "", "  ")) {
            launcher(unset).launch("input:$hdmi")

            assertNull(shadowOf(app).nextStartedActivity.`package`)
            assertNull(shadowOf(app).nextStartedActivity)
        }
    }

    @Test
    fun an_app_the_system_refuses_to_start_is_not_noted() {
        installTvApp("com.netflix.ninja")
        val refusing =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) = throw SecurityException("needs a permission")
            }

        launcher(context = refusing).launch("com.netflix.ninja")
        assertEquals(emptyMap<String, Long>(), lastOpened.times.value)

        launcher().launch("com.netflix.ninja")
        assertEquals(setOf("com.netflix.ninja/.Main"), lastOpened.times.value.keys)
    }

    @Test
    fun an_activity_target_that_started_is_noted_under_its_component() {
        installTvApp("com.netflix.ninja")

        launcher().launch("com.netflix.ninja/.Main")

        assertEquals(setOf("com.netflix.ninja/.Main"), lastOpened.times.value.keys)
    }
}
