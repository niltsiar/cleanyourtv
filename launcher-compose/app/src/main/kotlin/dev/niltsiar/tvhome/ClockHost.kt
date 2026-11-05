package dev.niltsiar.tvhome

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.niltsiar.tvhome.core.config.is24Hour
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.feature.clock.ClockScene
import dev.niltsiar.tvhome.feature.clock.clockFace

/** The clock screensaver of the config: for [ClockActivity] and [ClockDream], which differ only in the window they put it in. */
@Composable
internal fun ClockHost(graph: AppGraph) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val datePattern = stringResource(R.string.date_format)
    // Home is stopped while this shows, so this screen keeps the time and time zone broadcasts coming.
    DisposableEffect(graph) {
        graph.timeChanges.onStart()
        onDispose { graph.timeChanges.onStop() }
    }
    val time by graph.minuteClock.now.collectAsState()
    val hour24 = remember(graph) { graph.config.clockFormat.is24Hour { DateFormat.is24HourFormat(context) } }
    val face = remember(time, hour24, datePattern, locale) { time?.let { clockFace(it, hour24, datePattern, locale) } }
    // Home's current picture, read again each minute so a collection's rotation goes on while the clock is up. It decodes
    // off the main thread, and the glow shows until the first one is ready.
    val picture by produceState<ImageBitmap?>(initialValue = null, time) {
        if (graph.config.clockWallpaper) value = graph.screensaverWallpaper.current()
    }
    TvTheme(motion = graph.config.motion()) {
        ClockScene(face = face, glow = graph.config.clockGlow, picture = picture)
    }
}
