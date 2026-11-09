package dev.niltsiar.tvhome.data.wallpaper

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * The picture Home is showing, for clock_wallpaper: read through the same playback state as Home's wallpaper, the on-TV
 * wallpaper choice included, darkened by clock_wallpaper_dim instead of wallpaper_dim. Home only plays while it is on
 * screen, so the screensaver starts on the picture that stands and, asked again, carries the rotation on: [current] moves
 * to the next picture when its time is up.
 */
public class ScreensaverWallpaper internal constructor(
    private val provider: WallpaperProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(
        context: Context,
        config: StateFlow<TvConfig>,
        wallpaperChoice: StateFlow<String?>,
        theme: ThemeDirectory,
    ) : this(WallpaperProvider(context, config, wallpaperChoice, theme, dim = TvConfig::clockWallpaperDim, thumbnails = false))

    /**
     * The picture for now, decoded for the screen, or null when the theme has none or it cannot be read. It moves to the
     * next picture when the interval is up, and records that as Home does. Decodes off the main thread.
     */
    public suspend fun current(): ImageBitmap? = withContext(io) { provider.advance(LocalDateTime.now()).picture }
}
