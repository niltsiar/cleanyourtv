package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
public fun HomeRoute(
    apps: List<AppEntry>,
    modifier: Modifier = Modifier,
) {
    HomeScreen(clock = rememberClockText(), apps = apps, modifier = modifier)
}

@Composable
internal fun HomeScreen(
    clock: String,
    apps: List<AppEntry>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvTheme.colors.background)
                .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BasicText(text = clock, style = TvTheme.typography.clock)
            Spacer(Modifier.height(48.dp))
            AppRow(apps)
        }
    }
}

@Composable
private fun AppRow(
    apps: List<AppEntry>,
    modifier: Modifier = Modifier,
) {
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            // The lazy row attaches its first item during layout, after this effect starts.
            withFrameNanos { }
            firstTile.requestFocus()
        }
    }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
            )
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "tileScale")
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (focused) TvTheme.colors.accent else Color.Transparent
    Box(
        modifier =
            modifier
                .size(width = 160.dp, height = 90.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(TvTheme.colors.surface)
                .border(3.dp, borderColor, shape)
                .onFocusChanged { focused = it.isFocused }
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = app.label.take(1), style = TvTheme.typography.tileTitle)
            BasicText(text = app.label, style = TvTheme.typography.tileLabel)
        }
    }
}

@Composable
private fun rememberClockText(): String {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var text by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(formatter) {
        while (true) {
            val now = LocalTime.now()
            delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
            text = LocalTime.now().format(formatter)
        }
    }
    return text
}

@Preview(name = "Home 1080p", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TvTheme {
        HomeScreen(
            clock = "20:15",
            apps =
                listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio")
                    .map { AppEntry(packageName = "example.${it.lowercase()}", label = it) },
        )
    }
}
