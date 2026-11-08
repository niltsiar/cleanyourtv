package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The `fade` of [HeroScrim] is the alpha of what it draws, read in the layer phase. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HeroScrimAlphaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val level = mutableFloatStateOf(1f)

    private fun showScrim() {
        val look = mutableStateOf(HeroScrimLook(darkening = 0.6f, homeLeft = 0f))
        val shelf = TitleExtent().apply { width = { 400f } }
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                HeroScrim(look, boxHeight = 120.dp, shelf = shelf, band = TitleExtent(), shift = { 0f }, fade = { level.floatValue })
            }
        }
        compose.waitForIdle()
    }

    private fun redInsideScrim(): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return Math.round(pixels[(100 * density).toInt(), ((ContentTop + 60.dp).value * density).toInt()].red * 255)
    }

    @Test
    fun the_scrim_is_as_dark_as_its_fade_says() {
        showScrim()
        val whole = redInsideScrim()

        level.floatValue = 0.5f
        compose.waitForIdle()
        val half = redInsideScrim()

        level.floatValue = 0f
        compose.waitForIdle()
        val none = redInsideScrim()

        assertTrue(
            "red under the scrim: $whole at fade 1, $half at 0.5, $none at 0",
            whole < 140 && half in (whole + 30)..(none - 30) && none == 255,
        )
    }
}
