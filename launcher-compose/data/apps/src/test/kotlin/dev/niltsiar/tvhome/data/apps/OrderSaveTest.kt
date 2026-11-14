package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment
import java.io.File

val OrderSaveTest by testSuite {
    robolectricTestSuite<OrderSaveTestContent>("default sdk")
    robolectricTestSuite<OrderSaveSdk31TestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

private fun dockConfig() = MutableStateFlow(TvConfig(dockApps = listOf("a.dock", "b.dock")))

class OrderSaveTestContent :
    RobolectricTestSuiteContent({
        test("a saved order goes to order txt under the dock apps it was made with") {
            val context = RuntimeEnvironment.getApplication()
            val file = File(context.filesDir, ORDER_FILE_NAME)

            val saved = runBlocking { PackageManagerAppsProvider(context, dockConfig()).saveOrder(listOf("x/.X", "y/.Y"), docked = 1) }

            saved.shouldBeTrue()
            readOrder(file) shouldBe Order("a.dock,b.dock", listOf("x/.X", "y/.Y"), docked = 1)
        }
    })

class OrderSaveSdk31TestContent :
    RobolectricTestSuiteContent({
        test("saving an order makes the apps flow emit the tiles in that order") {
            val context = RuntimeEnvironment.getApplication()
            for (pkg in listOf("a.tv", "b.tv", "c.tv")) installTvApp(context, pkg)
            val provider = PackageManagerAppsProvider(context, dockConfig())

            val next =
                runBlocking {
                    val lists = Channel<List<String>>(Channel.UNLIMITED)
                    val collecting = launch { provider.apps.collect { list -> lists.send(list.apps.map { it.key }) } }
                    withTimeout(LOAD_TIMEOUT_MS) {
                        lists.receive()
                        val keys = lists.receive()
                        keys shouldBe listOf("a.tv/.Main", "b.tv/.Main", "c.tv/.Main")
                        provider.saveOrder(keys.reversed(), docked = -1)
                        lists.receive()
                    }.also { collecting.cancel() }
                }

            next shouldBe listOf("c.tv/.Main", "b.tv/.Main", "a.tv/.Main")
        }
    })

private const val LOAD_TIMEOUT_MS = 10_000L
