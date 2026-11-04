package dev.niltsiar.tvhome.data.wallpaper

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * The picture Home is showing, for clock_wallpaper: read through the same playback state as Home's wallpaper, darkened by
 * clock_wallpaper_dim instead of wallpaper_dim. Home only plays while it is on screen, so the screensaver takes the
 * picture that stands and does not rotate on.
 */
public class ScreensaverWallpaper internal constructor(
    private val provider: WallpaperProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(
        context: Context,
        config: TvConfig,
        theme: ThemeDirectory,
    ) : this(WallpaperProvider(context, config, theme, dim = config.clockWallpaperDim))

    /** The picture decoded for the screen, or null when the theme has none or it cannot be read. Decodes off the main thread. */
    public suspend fun current(): ImageBitmap? = withContext(io) { provider.advance(LocalDateTime.now()).picture }
}
