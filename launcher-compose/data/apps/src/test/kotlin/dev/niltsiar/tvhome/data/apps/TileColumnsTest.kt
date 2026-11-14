package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment

val TileColumnsTest by testSuite {
    robolectricTestSuite<TileColumnsTestContent>(
        "sdk 33, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 33
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TileColumnsTestContent :
    RobolectricTestSuiteContent({
        fun widthOf(list: AppList) =
            list.apps
                .single()
                .image!!
                .width

        test("a change of columns gives the same provider tiles at the new width") {
            val context = RuntimeEnvironment.getApplication()
            installTvApp(context, "a.tv")
            val config = MutableStateFlow(TvConfig(columns = 6))
            val provider = PackageManagerAppsProvider(context, config)

            runBlocking {
                val lists = Channel<AppList>(Channel.UNLIMITED)
                val collecting = launch { provider.apps.collect { lists.send(it) } }
                withTimeout(LOAD_TIMEOUT_MS) {
                    lists.receive()
                    widthOf(lists.receive()) shouldBe tileWidthPx(1920, 6, 2f)

                    config.value = config.value.copy(columns = 4)
                    widthOf(lists.receive()) shouldBe tileWidthPx(1920, 4, 2f)

                    config.value = config.value.copy(rowsBelowDock = 2)
                    config.value = config.value.copy(columns = 7)
                    widthOf(lists.receive()) shouldBe tileWidthPx(1920, 7, 2f)
                }
                collecting.cancel()
            }
        }
    })

private const val LOAD_TIMEOUT_MS = 10_000L
