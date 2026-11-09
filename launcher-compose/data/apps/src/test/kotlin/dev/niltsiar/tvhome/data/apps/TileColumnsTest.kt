package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h540dp-xhdpi")
class TileColumnsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun widthOf(list: AppList) =
        list.apps
            .single()
            .image!!
            .width

    @Test
    fun a_change_of_columns_gives_the_same_provider_tiles_at_the_new_width() {
        installTvApp(context, "a.tv")
        val config = MutableStateFlow(TvConfig(columns = 6))
        val provider = PackageManagerAppsProvider(context, config)

        runBlocking {
            val lists = Channel<AppList>(Channel.UNLIMITED)
            val collecting = launch { provider.apps.collect { lists.send(it) } }
            withTimeout(LOAD_TIMEOUT_MS) {
                lists.receive()
                assertEquals(tileWidthPx(1920, 6, 2f), widthOf(lists.receive()))

                config.value = config.value.copy(columns = 4)
                assertEquals(tileWidthPx(1920, 4, 2f), widthOf(lists.receive()))

                config.value = config.value.copy(rowsBelowDock = 2)
                config.value = config.value.copy(columns = 7)
                assertEquals(tileWidthPx(1920, 7, 2f), widthOf(lists.receive()))
            }
            collecting.cancel()
        }
    }
}

private const val LOAD_TIMEOUT_MS = 10_000L
