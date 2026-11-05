package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OrderSaveTest {
    private val context = RuntimeEnvironment.getApplication()
    private val config = TvConfig(dockApps = listOf("a.dock", "b.dock"))
    private val file = File(context.filesDir, ORDER_FILE_NAME)

    @Test
    fun a_saved_order_goes_to_order_txt_under_the_dock_apps_it_was_made_with() {
        val saved = runBlocking { PackageManagerAppsProvider(context, config).saveOrder(listOf("x/.X", "y/.Y"), docked = 1) }

        assertTrue(saved)
        assertEquals(Order("a.dock,b.dock", listOf("x/.X", "y/.Y"), docked = 1), readOrder(file))
    }

    @Test
    @Config(sdk = [31])
    fun saving_an_order_makes_the_apps_flow_emit_the_tiles_in_that_order() {
        for (pkg in listOf("a.tv", "b.tv", "c.tv")) installTvApp(context, pkg)
        val provider = PackageManagerAppsProvider(context, config)

        val next =
            runBlocking {
                val lists = Channel<List<String>>(Channel.UNLIMITED)
                val collecting = launch { provider.apps.collect { list -> lists.send(list.apps.map { it.key }) } }
                withTimeout(LOAD_TIMEOUT_MS) {
                    lists.receive()
                    val keys = lists.receive()
                    assertEquals(listOf("a.tv/.Main", "b.tv/.Main", "c.tv/.Main"), keys)
                    provider.saveOrder(keys.reversed(), docked = -1)
                    lists.receive()
                }.also { collecting.cancel() }
            }

        assertEquals(listOf("c.tv/.Main", "b.tv/.Main", "a.tv/.Main"), next)
    }
}

private const val LOAD_TIMEOUT_MS = 10_000L
