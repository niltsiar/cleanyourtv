package dev.niltsiar.tvhome.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.GlassIconButton
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.glassContainer
import dev.niltsiar.tvhome.core.designsystem.glassSource
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.drop
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

internal val ScreenMargin = 40.dp
private val TileGap = 14.dp
internal val TopBarTop = 20.dp
internal val TopBarHeight = 56.dp
private val ContentTop = TopBarTop + TopBarHeight + 20.dp
private val CaptionTop = TopBarTop + TopBarHeight + 8.dp
private val TileShape = RoundedCornerShape(12.dp)
private val ShelfHeight = 72.dp
private val ShelfTitleGap = 12.dp
private val ShelfFade = 40.dp
private val ShelfFullyShownInset = 4.dp
private val ShelfRise = 14.dp
private val GridBottom = 48.dp
private const val TILE_ASPECT = 16f / 9f

/**
 * The Home screen. [state] is read lazily, inside the items that show each field. A focus change keeps the dock and
 * grid list instances, so the dock and the tiles skip while the shelf label updates.
 * The wallpaper and the scrolling content are both glass sources, so the top bar and the dock glass what is behind them.
 * The top bar's surfaces sit in one glass container and merge when they come near each other.
 * The shelf title stays under the top bar; rows fade out as they scroll up to it.
 * [homePresses] counts Home key presses that reach Home while it shows; each one scrolls to the top and focuses the first dock tile.
 * [layout] is the theme's: dock size and grid columns, rows that must fit below the dock, one sideways row.
 * [topBar] is the theme's top bar: which buttons show and how.
 * [wallpaperLayout] is the theme's say over the picture behind Home, which [HomeUiState.wallpaper] supplies.
 * [shelfLayout] is the theme's cinematic shelf and focus colours; [heroArt] gives it the softened art of a tile.
 * The cinematic shelf makes the shelf taller.
 */
@Composable
public fun HomeScreen(
    state: () -> HomeUiState,
    layout: HomeLayout,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    homePresses: () -> Int = { 0 },
    topBar: TopBarLayout = TopBarLayout(),
    wallpaperLayout: WallpaperLayout = WallpaperLayout(),
    shelfLayout: ShelfLayout = ShelfLayout(),
    heroArt: suspend (String) -> ImageBitmap? = { null },
    onOpenSettings: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
) {
    val glass = rememberGlassState()
    val dockFocus = remember { FocusRequester() }
    val backdrop = rememberCinematicBackdrop(shelfLayout, shelf = { state().shelf }, heroArt = heroArt)
    val shelfHeight = if (shelfLayout.cinematic) CinematicShelfHeight else ShelfHeight
    val shelfModifier = Modifier.padding(top = ContentTop, start = ScreenMargin).height(shelfHeight - ShelfTitleGap)
    Box(modifier = modifier.fillMaxSize().background(wallpaperLayout.background)) {
        WallpaperBackdrop(glass, wallpaper = { state().wallpaper }, layout = wallpaperLayout, cinematic = backdrop)
        HomeContent(glass, dockFocus, state, layout, shelfLayout, ContentTop + shelfHeight, onFocus, onClick, homePresses)
        if (shelfLayout.cinematic) CinematicShelf(shelf = { state().shelf }, layout = shelfLayout, modifier = shelfModifier)
        ShelfTitle(
            label = { state().let { if (shelfLayout.cinematic && it.shelf != null) "" else it.focusedLabel } },
            modifier = shelfModifier,
        )
        WallpaperCaption(
            caption = { state().wallpaper.caption },
            crossfade = wallpaperLayout.crossfade,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = CaptionTop, end = ScreenMargin),
        )
        TopBar(
            glass = glass,
            dockFocus = dockFocus,
            state = state,
            layout = topBar,
            onAppClick = onClick,
            onOpenSettings = onOpenSettings,
            onOpenNetwork = onOpenNetwork,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    glass: GlassState,
    dockFocus: FocusRequester,
    state: () -> HomeUiState,
    layout: HomeLayout,
    shelfLayout: ShelfLayout,
    shelfBottom: Dp,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    homePresses: () -> Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The focused tile rests one gap inside the dock panel, so at rest the dock panel's top edge is the shelf's bottom edge.
    val pivotPx = with(density) { (shelfBottom + TileGap).toPx() }
    val pivotSpec = remember(pivotPx) { PivotBringIntoViewSpec(pivotPx) }
    val window = LocalWindowInfo.current.containerSize
    val gridWidth = with(density) { window.width.toDp() } - ScreenMargin * 2
    val belowShelf = with(density) { window.height.toDp() } - shelfBottom - GridBottom
    val share =
        if (layout.singleRow) {
            1f
        } else {
            tileWidthShare(gridWidth.value, belowShelf.value, layout.columns, layout.rowsBelowDock, TileGap.value)
        }
    val sideMargin = ScreenMargin + gridWidth * ((1f - share) / 2f)
    val rowTileWidth = (gridWidth - TileGap * (layout.columns + 1)) / layout.columns
    val gridState = rememberLazyGridState()
    val firstDockTile = remember { FocusRequester() }
    LaunchedEffect(gridState) {
        snapshotFlow(homePresses).drop(1).collect {
            gridState.scrollToItem(0)
            if (state().dock.isNotEmpty()) {
                // The dock attaches during the layout that follows the scroll.
                withFrameNanos { }
                firstDockTile.requestFocus()
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(layout.columns),
            contentPadding = PaddingValues(start = sideMargin, end = sideMargin, top = ContentTop, bottom = GridBottom),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
            modifier = modifier.fillMaxSize().glassSource(glass, zIndex = 1f).semantics { testTagsAsResourceId = true },
        ) {
            item(key = "shelf", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                Spacer(Modifier.fillMaxWidth().height(shelfBottom - ContentTop))
            }
            item(key = "dock", span = { GridItemSpan(maxLineSpan) }, contentType = "dock") {
                Dock(
                    glass = glass,
                    focus = dockFocus,
                    firstTile = firstDockTile,
                    dock = state().dock,
                    layout = layout,
                    shelfLayout = shelfLayout,
                    rowTileWidth = rowTileWidth,
                    onFocus = onFocus,
                    onClick = onClick,
                    modifier = Modifier.fadeUnderShelf(gridState, "dock", shelfBottom),
                )
            }
            items(state().grid, key = { it.key }, contentType = { "tile" }) { app ->
                AppTile(
                    app = app,
                    accent = shelfLayout.focusAccent(app.accent, inDock = false),
                    onFocus = onFocus,
                    onClick = onClick,
                    modifier = Modifier.fadeUnderShelf(gridState, app.key, shelfBottom),
                )
            }
        }
    }
}

/** Fades the item with the row it is in: fully visible from just above the shelf's bottom edge down, gone [ShelfFade] above that. */
private fun Modifier.fadeUnderShelf(
    grid: LazyGridState,
    key: Any,
    shelfBottom: Dp,
): Modifier = this then FadeUnderShelfElement(grid, key, shelfBottom)

private data class FadeUnderShelfElement(
    val grid: LazyGridState,
    val key: Any,
    val shelfBottom: Dp,
) : ModifierNodeElement<FadeUnderShelfNode>() {
    override fun create() = FadeUnderShelfNode(grid, key, shelfBottom)

    override fun update(node: FadeUnderShelfNode) {
        node.grid = grid
        node.key = key
        node.shelfBottom = shelfBottom
    }
}

private class FadeUnderShelfNode(
    var grid: LazyGridState,
    var key: Any,
    var shelfBottom: Dp,
) : Modifier.Node(),
    LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val info = grid.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@placeWithLayer
                val top = item.offset.y - info.viewportStartOffset
                val fade = ShelfFade.toPx()
                alpha = ((top - ((shelfBottom - ShelfFullyShownInset).toPx() - fade)) / fade).coerceIn(0f, 1f)
            }
        }
    }
}

/** The focused app's name. It rises into place and fades in again each time the label changes. */
@Composable
private fun ShelfTitle(
    label: () -> String,
    modifier: Modifier = Modifier,
) {
    val text = label()
    val reveal = remember(text) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        BasicText(
            text = text,
            style = TvTheme.typography.shelfTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.testTag("shelf:title").graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = (0.3f + 0.7f * reveal.value).coerceIn(0f, 1f)
                    translationY = (1f - reveal.value) * ShelfRise.toPx()
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    glass: GlassState,
    focus: FocusRequester,
    firstTile: FocusRequester,
    dock: List<AppEntry>,
    layout: HomeLayout,
    shelfLayout: ShelfLayout,
    rowTileWidth: Dp,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(dock.isNotEmpty()) {
        if (dock.isNotEmpty()) {
            // The grid attaches its items during layout, after this effect starts.
            withFrameNanos { }
            firstTile.requestFocus()
        }
    }
    GlassSurface(glass = glass, modifier = modifier.fillMaxWidth().testTag("dock")) {
        if (layout.singleRow) {
            // The grid's pivot scroll is vertical; this row scrolls sideways the usual way.
            CompositionLocalProvider(LocalBringIntoViewSpec provides RevealBringIntoViewSpec) {
                LazyRow(
                    modifier = Modifier.focusRequester(focus).focusRestorer(),
                    contentPadding = PaddingValues(TileGap),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    itemsIndexed(dock, key = { _, app -> app.key }) { index, app ->
                        AppTile(
                            app = app,
                            accent = shelfLayout.focusAccent(app.accent, inDock = true),
                            onFocus = onFocus,
                            onClick = onClick,
                            modifier = Modifier.width(rowTileWidth).then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                        )
                    }
                }
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .focusRequester(focus)
                        .focusRestorer()
                        .focusGroup()
                        .padding(TileGap),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                dock.forEachIndexed { index, app ->
                    key(app.key) {
                        AppTile(
                            app = app,
                            accent = shelfLayout.focusAccent(app.accent, inDock = true),
                            onFocus = onFocus,
                            onClick = onClick,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                                    .stopAtDockEnds(first = index == 0, last = index == dock.lastIndex),
                        )
                    }
                }
                // Slots with no app stay empty, so the tiles keep the width of a column.
                repeat(layout.columns - dock.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The dock is a row of its own: Left and Right stop at its first and last tile instead of jumping to a wider grid tile. */
private fun Modifier.stopAtDockEnds(
    first: Boolean,
    last: Boolean,
): Modifier =
    if (!first && !last) {
        this
    } else {
        focusProperties {
            if (first) left = FocusRequester.Cancel
            if (last) right = FocusRequester.Cancel
        }
    }

@Composable
private fun AppTile(
    app: AppEntry,
    accent: Color,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = { onClick(app.key) },
        onFocused = { onFocus(app.key) },
        shape = TileShape,
        accent = accent,
        modifier = modifier.aspectRatio(TILE_ASPECT).testTag("tile:${app.key}"),
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

@Preview(name = "Home 1080p glass", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenGlassPreview() {
    HomePreview(GlassKind.Refractive)
}

@Preview(name = "Home 1080p flat", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenFlatPreview() {
    HomePreview(GlassKind.Flat)
}

private val PreviewAccents = listOf(0xFFE53935, 0xFFE91E63, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1)

/** The softened art of a colourful app: a diagonal blend, as the 48 by 27 picture the shelf stretches over the screen. */
private fun previewArt(accent: Color): ImageBitmap {
    val image = ImageBitmap(48, 27)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(48f, 27f)) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF00BCD4), accent, Color(0xFF1B1F2A)), start = Offset.Zero, end = Offset(48f, 27f)))
    }
    return image
}

/** Home as the owner sees it: the cinematic shelf, with the first dock tile focused (the dock takes focus when it shows). */
@Composable
private fun HomePreview(glassKind: GlassKind) {
    TvTheme(glassKind = glassKind) {
        val apps =
            listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio", "Clock", "Maps", "Notes", "Podcasts")
                .mapIndexed { i, name ->
                    val id = "example.${name.lowercase()}"
                    AppEntry(key = id, packageName = id, label = name, accent = Color(PreviewAccents[i % PreviewAccents.size]))
                }
        val layout = HomeLayout(columns = 6, rowsBelowDock = 0, singleRow = false, fillDock = true)
        val header = HeaderStyle(hour24 = false, datePattern = "EEEE  d MMMM", greetingName = "Sam")
        val network = NetworkState(NetworkLink.Wifi, ssid = "Living room", validated = true)
        val wallpaper = Wallpaper(picture = ImageBitmap.imageResource(R.drawable.wallpaper), caption = "Red Spider Nebula · Hubble")
        val now = LocalDateTime.of(2026, 11, 3, 20, 15)
        val yesterday =
            now
                .minusDays(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val state =
            homeUiState(
                HomeApps(AppList(apps), layout),
                now,
                "example.movies",
                Locale.ENGLISH,
                header,
                network,
                wallpaper = wallpaper,
                cinematic = true,
                lastOpened = mapOf("example.movies" to yesterday),
            )
        val art = remember { previewArt(Color(PreviewAccents[0])) }
        HomeScreen(
            state = { state },
            layout = layout,
            onFocus = {},
            onClick = {},
            topBar = TopBarLayout(wifiStatusColor = true),
            shelfLayout = ShelfLayout(cinematic = true, animated = false),
            heroArt = { art },
        )
    }
}
