package dev.niltsiar.tvhome

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** [SettingsHost] over the real graph: what the page has to tell shows in the page itself, never as a system toast. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = TvHomeApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class SettingsHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val graph get() = (compose.activity.application as TvHomeApplication).graph
    private lateinit var viewModel: MoleculeSettingsViewModel

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun noticeSays(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("settings:notice"))).assertExists()

    /** The page over a theme with one picture, with TV settings that do not open. */
    private fun show() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        val theme = ThemeDirectory(compose.activity)
        File(theme.dir!!, "config.json").writeText("{}")
        theme.wallpaperFile!!.writeBytes(byteArrayOf(1, 2, 3))
        compose.setContent {
            val scope = rememberCoroutineScope()
            viewModel =
                remember {
                    MoleculeSettingsViewModel(
                        graph.configRepository,
                        graph.settingsSources,
                        scope,
                        RecompositionMode.Immediate,
                        EmptyCoroutineContext,
                    )
                }
            SettingsHost(graph = graph, viewModel = viewModel, onChooseDefaultHome = {}, onOpenTvSettings = { false }, onReorder = {})
        }
        compose.waitUntil(10_000) { viewModel.uiState.value.wallpaper != null }
    }

    @Test
    fun a_picked_wallpaper_is_confirmed_in_the_page_for_about_2_s_and_not_by_a_toast() {
        show()
        viewModel.actions.onOpenWallpaperPicker()
        compose.waitUntil(10_000) { viewModel.uiState.value.wallpaperPicker != null }

        viewModel.actions.onPickWallpaper(1)
        compose.waitUntil(10_000) { exists("settings:notice") }

        noticeSays("Wallpaper changed")
        compose.mainClock.advanceTimeBy(1_500)
        assertTrue("still showing after 1.5 s", exists("settings:notice"))
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse("gone after 2.5 s", exists("settings:notice"))
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun tv_settings_that_do_not_open_are_told_in_the_page_and_not_by_a_toast() {
        show()

        compose.onNodeWithTag("settings:tv_settings").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        noticeSays("Not available on this TV")
        assertEquals(0, ShadowToast.shownToastCount())
    }
}
