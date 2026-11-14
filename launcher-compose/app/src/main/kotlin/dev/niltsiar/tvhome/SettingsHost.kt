package dev.niltsiar.tvhome

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.data.shelf.READ_TV_LISTINGS
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.PageNotice
import dev.niltsiar.tvhome.feature.settings.SettingsEffect
import dev.niltsiar.tvhome.feature.settings.SettingsScreen
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

/**
 * Launcher settings in the TV theme of the config, which the choices on this screen change as they are made. A refused
 * write, a stored wallpaper, an empty Apps and channels list and TV settings that [onOpenTvSettings] could not open are told
 * in the page's notice. When App highlights needs READ_TV_LISTINGS the page asks for it; after a refusal the system no longer
 * asks about, the notice says where to allow it and the launcher's App info opens. Whether Home holds it is read again each
 * time the page starts.
 */
@Composable
internal fun SettingsHost(
    graph: AppGraph,
    viewModel: MoleculeSettingsViewModel,
    onChooseDefaultHome: () -> Unit,
    onOpenTvSettings: () -> Boolean,
    onReorder: () -> Unit,
) {
    val notice = remember { PageNotice() }
    val activity = checkNotNull(LocalActivity.current)
    // Whether the system had a rationale to show before the last request: the person had refused the permission once.
    var refusedBefore by rememberSaveable { mutableStateOf(false) }
    val shelfAccess =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.actions.onShelfAccessAnswered()
            // No rationale before the request or after it: the system refused with no dialog, as it does once it stops asking.
            if (!granted && !refusedBefore && !activity.shouldShowRequestPermissionRationale(READ_TV_LISTINGS)) {
                notice.show(SettingsR.string.settings_shelf_allow_access)
                graph.systemSettings.openAppInfo(activity.packageName)
            }
        }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.actions.onShelfAccessAnswered() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.SaveFailed -> {
                    notice.show(SettingsR.string.settings_save_failed)
                }

                SettingsEffect.WallpaperApplied -> {
                    notice.show(SettingsR.string.wallpaper_applied)
                }

                SettingsEffect.AskShelfAccess -> {
                    refusedBefore = activity.shouldShowRequestPermissionRationale(READ_TV_LISTINGS)
                    shelfAccess.launch(READ_TV_LISTINGS)
                }

                SettingsEffect.NoShelfSources -> {
                    notice.show(SettingsR.string.settings_shelf_nothing)
                }
            }
        }
    }
    val config = graph.configRepository.config.collectAsStateWithLifecycle()
    val motion by config.derived(TvConfig::motion)
    val focusStyle by config.derived(TvConfig::focusStyle)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    TvTheme(motion = motion, focusStyle = focusStyle) {
        SettingsScreen(
            state = { state.value },
            actions = viewModel.actions,
            onChooseDefaultHome = onChooseDefaultHome,
            onOpenTvSettings = { if (!onOpenTvSettings()) notice.show(R.string.not_available) },
            onReorder = onReorder,
            notice = notice,
            art = viewModel.art,
        )
    }
}
