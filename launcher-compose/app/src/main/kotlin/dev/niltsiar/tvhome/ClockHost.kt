package dev.niltsiar.tvhome

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.feature.clock.ClockFace
import dev.niltsiar.tvhome.feature.clock.ClockScene
import dev.niltsiar.tvhome.feature.clock.clockFace
import dev.niltsiar.tvhome.feature.home.is24Hour
import dev.niltsiar.tvhome.feature.home.minuteClock
import java.util.Locale

/** The clock screensaver of the config: for [ClockActivity] and [ClockDream], which differ only in the window they put it in. */
@Composable
internal fun ClockHost(graph: AppGraph) {
    val context = LocalContext.current
    val datePattern = stringResource(R.string.date_format)
    val face by produceState<ClockFace?>(initialValue = null) {
        val hour24 = graph.config.clockFormat.is24Hour { DateFormat.is24HourFormat(context) }
        minuteClock().collect { time -> value = time?.let { clockFace(it, hour24, datePattern, Locale.getDefault()) } }
    }
    // Home's current picture, read once: it decodes off the main thread, and the glow shows until it is ready.
    val picture by produceState<ImageBitmap?>(initialValue = null) {
        if (graph.config.clockWallpaper) value = graph.screensaverWallpaper.current()
    }
    TvTheme(motion = graph.config.motion()) {
        ClockScene(face = face, glow = graph.config.clockGlow, picture = picture)
    }
}
