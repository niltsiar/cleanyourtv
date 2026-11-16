package dev.niltsiar.tvhome

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.feature.home.shelfHasRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * What Home's room for the app_shelf cards depends on besides the config, as Home last had it: the window [HomeHost] reports
 * and the app list Home loaded. Launcher settings reads [shelfRoom] to say when a layout has no room.
 */
class HomeShape {
    private val window = MutableStateFlow<HomeWindow?>(null)
    private val tiles = MutableStateFlow<HomeTiles?>(null)

    /** Whether Home has room for the cards with a config, in its window with its tiles; null until Home has had both. */
    val shelfRoom: Flow<((TvConfig) -> Boolean)?> =
        combine(window, tiles) { shown, loaded ->
            if (shown == null || loaded == null) {
                null
            } else {
                { config: TvConfig -> shelfHasRoom(config, loaded.count, loaded.docked, shown.size, shown.density) }
            }
        }

    fun windowShown(
        size: IntSize,
        density: Density,
    ) {
        window.value = HomeWindow(size, density)
    }

    fun appsLoaded(list: AppList) {
        tiles.value = HomeTiles(list.apps.size, list.docked)
    }
}

private data class HomeWindow(
    val size: IntSize,
    val density: Density,
)

private data class HomeTiles(
    val count: Int,
    val docked: Int,
)
