package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrimImagesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val orderFile = File(context.filesDir, ORDER_FILE_NAME)

    /** Collects [flow] and runs [block] with a function that waits for its next emission. */
    private fun <T> collecting(
        flow: Flow<T>,
        block: suspend (next: suspend () -> T) -> Unit,
    ) = runBlocking {
        val emitted = Channel<T>(Channel.UNLIMITED)
        val job = launch { flow.collect { emitted.send(it) } }
        try {
            withTimeout(LOAD_TIMEOUT_MS) { block { emitted.receive() } }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun trimmed_tiles_keep_their_keys_and_order_lose_their_images_and_get_them_back_on_the_next_start() {
        for (pkg in listOf("a.tv", "b.tv", "c.tv")) installTvApp(context, pkg)
        val keys = listOf("c.tv/.Main", "a.tv/.Main", "b.tv/.Main")
        val provider = PackageManagerAppsProvider(context, MutableStateFlow(TvConfig()))
        runBlocking { provider.saveOrder(keys, docked = -1) }
        val written = orderFile.readText()

        collecting(provider.apps) { next ->
            next()
            assertEquals(listOf(true, true, true), next().apps.map { it.image != null })

            provider.trimImages()
            val trimmed = next()
            assertEquals(keys, trimmed.apps.map { it.key })
            assertEquals(listOf(null, null, null), trimmed.apps.map { it.image })

            provider.onStart()
            val reloaded = next()
            assertEquals(keys, reloaded.apps.map { it.key })
            assertEquals(listOf(true, true, true), reloaded.apps.map { it.image != null })
        }

        assertEquals(written, orderFile.readText())
    }

    @Test
    fun trimmed_top_bar_apps_lose_their_icons_and_get_them_back_on_the_next_start() {
        val component = ComponentName("com.bar", "com.bar.Main")
        shadowOf(context.packageManager).addActivityIfNotPresent(component)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
        val provider = PackageManagerAppsProvider(context, MutableStateFlow(TvConfig(topBarApps = listOf("com.bar"))))

        collecting(provider.topBarApps) { next ->
            assertNotNull(next().single().image)

            provider.trimImages()
            val trimmed: AppEntry = next().single()
            assertEquals("com.bar/.Main", trimmed.key)
            assertNull(trimmed.image)

            provider.onStart()
            assertNotNull(next().single().image)
        }
    }
}

private const val LOAD_TIMEOUT_MS = 10_000L
