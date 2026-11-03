package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazePerformanceMode
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.GlassIconButton
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.glassSource
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry

private const val COLUMNS = DOCK_SIZE
private val ScreenMargin = 40.dp
private val TileGap = 14.dp
private val TopBarTop = 20.dp
private val TopBarHeight = 56.dp
private val ContentTop = TopBarTop + TopBarHeight + 20.dp
private val TileShape = RoundedCornerShape(12.dp)

/** Where the focused row rests when it scrolls, as a fraction of the viewport height. */
private const val FOCUS_PIVOT = 0.3f

/**
 * The Home screen. [state] is read lazily so a focus change recomposes only the part that shows it.
 * The wallpaper and the scrolling content are both glass sources, so the top bar blurs whatever scrolls under it.
 */
@Composable
public fun HomeScreen(
    state: () -> HomeUiState,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Default,
) {
    val glass = rememberGlassState(performanceMode)
    Box(modifier = modifier.fillMaxSize().background(TvTheme.colors.background)) {
        Wallpaper(glass)
        HomeContent(glass, state, onFocus, onClick)
        TopBar(
            glass = glass,
            clock = { state().clock },
            date = { state().date },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun Wallpaper(
    glass: GlassState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().glassSource(glass)) {
        Image(
            painter = painterResource(R.drawable.wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xB305060A)))),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    glass: GlassState,
    state: () -> HomeUiState,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pivotSpec = remember { PivotBringIntoViewSpec(FOCUS_PIVOT) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = ContentTop, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
            modifier = modifier.fillMaxSize().glassSource(glass, zIndex = 1f).semantics { testTagsAsResourceId = true },
        ) {
            item(key = "shelf", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                Shelf(label = state().focusedLabel)
            }
            item(key = "dock", span = { GridItemSpan(maxLineSpan) }, contentType = "dock") {
                Dock(glass = glass, dock = state().dock, onFocus = onFocus, onClick = onClick)
            }
            items(state().grid, key = { it.packageName }, contentType = { "tile" }) { app ->
                AppTile(app = app, onFocus = onFocus, onClick = onClick)
            }
        }
    }
}

@Composable
private fun Shelf(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.BottomStart) {
        BasicText(
            text = label,
            style = TvTheme.typography.shelfTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Dock(
    glass: GlassState,
    dock: List<AppEntry>,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(dock.isNotEmpty()) {
        if (dock.isNotEmpty()) {
            // The grid attaches its items during layout, after this effect starts.
            withFrameNanos { }
            firstTile.requestFocus()
        }
    }
    GlassSurface(glass = glass, modifier = modifier.fillMaxWidth().testTag("dock")) {
        Row(
            modifier = Modifier.focusRestorer().padding(TileGap),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            dock.forEachIndexed { index, app ->
                key(app.packageName) {
                    AppTile(
                        app = app,
                        onFocus = onFocus,
                        onClick = onClick,
                        modifier = Modifier.weight(1f).then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = { onClick(app.packageName) },
        onFocused = { onFocus(app.packageName) },
        shape = TileShape,
        modifier = modifier.aspectRatio(TILE_ASPECT),
    ) {
        Box(Modifier.fillMaxSize().background(TvTheme.colors.surface, TileShape), contentAlignment = Alignment.Center) {
            val image = app.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BasicText(text = app.label, style = TvTheme.typography.tileLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TopBar(
    glass: GlassState,
    clock: () -> String,
    date: () -> String,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        glass = glass,
        modifier = modifier.padding(top = TopBarTop, start = ScreenMargin, end = ScreenMargin).fillMaxWidth().height(TopBarHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(text = clock(), style = TvTheme.typography.clock)
            BasicText(text = date(), style = TvTheme.typography.date, modifier = Modifier.weight(1f))
            GlassIconButton(glass, painterResource(R.drawable.ic_inputs), stringResource(R.string.inputs), onClick = {})
            GlassIconButton(glass, painterResource(R.drawable.ic_wifi), stringResource(R.string.wifi), onClick = {})
            GlassIconButton(glass, painterResource(R.drawable.ic_settings), stringResource(R.string.settings), onClick = {})
        }
    }
}

private const val TILE_ASPECT = 16f / 9f

@Preview(name = "Home 1080p", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TvTheme {
        val apps =
            listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio", "Clock", "Maps", "Notes", "Podcasts")
                .map { AppEntry(packageName = "example.${it.lowercase()}", label = it) }
        val state = homeUiState(apps, java.time.LocalDateTime.of(2026, 11, 3, 20, 15), "example.music", java.util.Locale.ENGLISH)
        HomeScreen(state = { state }, onFocus = {}, onClick = {})
    }
}
