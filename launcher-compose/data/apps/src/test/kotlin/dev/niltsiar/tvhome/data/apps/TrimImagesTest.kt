package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

val TrimImagesTest by testSuite {
    robolectricTestSuite<TrimImagesTestContent>("sdk 33", testConfig = TestConfig.robolectric { sdk = 33 })
}

class TrimImagesTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val context = RuntimeEnvironment.getApplication()
                val orderFile = File(context.filesDir, ORDER_FILE_NAME)

                /** Collects [flow] and runs [block] with a function that waits for its next emission. */
                fun <T> collecting(
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
            }
        } asContextForEach {
            test("trimmed tiles keep their keys and order lose their images and get them back on the next start") {
                for (pkg in listOf("a.tv", "b.tv", "c.tv")) installTvApp(context, pkg)
                val keys = listOf("c.tv/.Main", "a.tv/.Main", "b.tv/.Main")
                val provider = PackageManagerAppsProvider(context, MutableStateFlow(TvConfig()))
                runBlocking { provider.saveOrder(keys, docked = -1) }
                val written = orderFile.readText()

                collecting(provider.apps) { next ->
                    next()
                    next().apps.map { it.image != null } shouldBe listOf(true, true, true)

                    provider.trimImages()
                    val trimmed = next()
                    trimmed.apps.map { it.key } shouldBe keys
                    trimmed.apps.map { it.image } shouldBe listOf(null, null, null)

                    provider.onStart()
                    val reloaded = next()
                    reloaded.apps.map { it.key } shouldBe keys
                    reloaded.apps.map { it.image != null } shouldBe listOf(true, true, true)
                }

                orderFile.readText() shouldBe written
            }

            test("trimmed top bar apps lose their icons and get them back on the next start") {
                val component = ComponentName("com.bar", "com.bar.Main")
                shadowOf(context.packageManager).addActivityIfNotPresent(component)
                shadowOf(context.packageManager).addIntentFilterForActivity(
                    component,
                    IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
                )
                val provider = PackageManagerAppsProvider(context, MutableStateFlow(TvConfig(topBarApps = listOf("com.bar"))))

                collecting(provider.topBarApps) { next ->
                    next().single().image.shouldNotBeNull()

                    provider.trimImages()
                    val trimmed: AppEntry = next().single()
                    trimmed.key shouldBe "com.bar/.Main"
                    trimmed.image.shouldBeNull()

                    provider.onStart()
                    next().single().image.shouldNotBeNull()
                }
            }
        }
    })

private const val LOAD_TIMEOUT_MS = 10_000L
