package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
}
