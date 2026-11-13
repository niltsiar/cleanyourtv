package dev.niltsiar.tvhome.data.apps

import android.app.Application
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppArtTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val provider = PackageManagerAppsProvider(app, MutableStateFlow(TvConfig()))

    @Test
    fun a_tv_app_gets_its_tile_art_at_the_size_asked_for() {
        installTvApp(app, "com.tv.app")

        val art = runBlocking { provider.art("com.tv.app", 192, 108) }!!

        assertEquals(192 to 108, art.width to art.height)
    }

    @Test
    fun an_app_without_a_tv_entry_point_has_no_art() {
        assertNull(runBlocking { provider.art("com.not.installed", 192, 108) })
    }
}
