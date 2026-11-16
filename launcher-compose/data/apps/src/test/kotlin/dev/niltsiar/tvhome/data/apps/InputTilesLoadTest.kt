package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputTile
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment

val InputTilesLoadTest by testSuite {
    robolectricTestSuite<InputTilesLoadTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

private val Console = InputTile("tv/.Cec/HDMI40008", "tv/.Hdmi/HW4", InputIcon.Hdmi)
private val ConsoleTile = AppEntry(key = "input:tv/.Cec/HDMI40008", packageName = "", label = "PlayStation 5 · HDMI 4", input = Console)

/** Runs [steps] against the provider's apps flow: each receive is the next list it emits, after the labels-only first one. */
private fun PackageManagerAppsProvider.follow(steps: suspend (receive: suspend () -> List<AppEntry>) -> Unit) =
    runBlocking {
        val lists = Channel<AppList>(Channel.UNLIMITED)
        val collecting = launch { apps.collect { lists.send(it) } }
        withTimeout(LOAD_TIMEOUT_MS) {
            lists.receive()
            steps { lists.receive().apps }
        }
        collecting.cancel()
    }

class InputTilesLoadTestContent :
    RobolectricTestSuiteContent({
        test("turning input_tiles on shows the pinned inputs at the grid's end, and off takes them away") {
            val context = RuntimeEnvironment.getApplication()
            installTvApp(context, "a.tv")
            val config = MutableStateFlow(TvConfig())
            val provider = PackageManagerAppsProvider(context, config)
            runBlocking { provider.pinInput(Console, "PlayStation 5 · HDMI 4") }.shouldBeTrue()

            provider.follow { receive ->
                receive().map { it.key } shouldBe listOf("a.tv/.Main")
                config.value = config.value.copy(inputTiles = true)
                receive().drop(1) shouldBe listOf(ConsoleTile)
                config.value = config.value.copy(inputTiles = false)
                receive().map { it.key } shouldBe listOf("a.tv/.Main")
            }
        }

        test("pinning an input loads the tiles again with it last, and unpinning takes it away") {
            val context = RuntimeEnvironment.getApplication()
            installTvApp(context, "a.tv")
            val provider = PackageManagerAppsProvider(context, MutableStateFlow(TvConfig(inputTiles = true)))

            provider.follow { receive ->
                receive().map { it.key } shouldBe listOf("a.tv/.Main")
                provider.pinInput(Console, "PlayStation 5 · HDMI 4").shouldBeTrue()
                receive().drop(1) shouldBe listOf(ConsoleTile)
                provider.unpinInput(Console.id).shouldBeTrue()
                receive().map { it.key } shouldBe listOf("a.tv/.Main")
            }
        }
    })

private const val LOAD_TIMEOUT_MS = 30_000L
