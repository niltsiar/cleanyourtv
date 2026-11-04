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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.drop
import java.time.LocalDateTime
import java.util.Locale

private val ScreenMargin = 40.dp
private val TileGap = 14.dp
private val TopBarTop = 20.dp
private val TopBarHeight = 56.dp
private val ContentTop = TopBarTop + TopBarHeight + 20.dp
private val TileShape = RoundedCornerShape(12.dp)
private val ShelfHeight = 72.dp
private val ShelfTitleGap = 12.dp
private val ShelfBottom = ContentTop + ShelfHeight
private val ShelfFade = 40.dp
private val ShelfFullyShown = ShelfBottom - 4.dp
private val ShelfRise = 14.dp
private val GridBottom = 48.dp
private const val TILE_ASPECT = 16f / 9f
private const val ICON_VIEWPORT = 24f
private const val INPUTS_ARROW_SLIDE = 2.5f
private const val SETTINGS_TURN = 90f
private const val ARC_DIM = 0.2f
private const val ARC_DIM_END = 0.15f
private const val ARC_STAGGER = 0.3f
private const val ARC_RELIGHT = 0.35f

/**
 * The Home screen. [state] is read lazily, inside the items that show each field. A focus change keeps the dock and
 * grid list instances, so the dock and the tiles skip while the shelf label updates.
 * The wallpaper and the scrolling content are both glass sources, so the top bar and the dock glass what is behind them.
 * The top bar's surfaces sit in one glass container and merge when they come near each other.
 * The shelf title stays under the top bar; rows fade out as they scroll up to it.
 * [homePresses] counts Home key presses that reach Home while it shows; each one scrolls to the top and focuses the first dock tile.
 * [layout] is the theme's: dock size and grid columns, rows that must fit below the dock, one sideways row.
 */
@Composable
public fun HomeScreen(
    state: () -> HomeUiState,
    layout: HomeLayout,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    homePresses: () -> Int = { 0 },
) {
    val glass = rememberGlassState()
    val dockFocus = remember { FocusRequester() }
    Box(modifier = modifier.fillMaxSize().background(TvTheme.colors.background)) {
        Wallpaper(glass)
        HomeContent(glass, dockFocus, state, layout, onFocus, onClick, homePresses)
        ShelfTitle(
            label = { state().focusedLabel },
            modifier = Modifier.padding(top = ContentTop, start = ScreenMargin).height(ShelfHeight - ShelfTitleGap),
        )
        TopBar(
            glass = glass,
            dockFocus = dockFocus,
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
    dockFocus: FocusRequester,
    state: () -> HomeUiState,
    layout: HomeLayout,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    homePresses: () -> Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The focused tile rests one gap inside the dock panel, so at rest the dock panel's top edge is the shelf's bottom edge.
    val pivotPx = with(density) { (ShelfBottom + TileGap).toPx() }
    val pivotSpec = remember(pivotPx) { PivotBringIntoViewSpec(pivotPx) }
    val window = LocalWindowInfo.current.containerSize
    val gridWidth = with(density) { window.width.toDp() } - ScreenMargin * 2
    val belowShelf = with(density) { window.height.toDp() } - ShelfBottom - GridBottom
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
                Spacer(Modifier.fillMaxWidth().height(ShelfHeight))
            }
            item(key = "dock", span = { GridItemSpan(maxLineSpan) }, contentType = "dock") {
                Dock(
                    glass = glass,
                    focus = dockFocus,
                    firstTile = firstDockTile,
                    dock = state().dock,
                    layout = layout,
                    rowTileWidth = rowTileWidth,
                    onFocus = onFocus,
                    onClick = onClick,
                    modifier = Modifier.fadeUnderShelf(gridState, "dock"),
                )
            }
            items(state().grid, key = { it.key }, contentType = { "tile" }) { app ->
                AppTile(app = app, onFocus = onFocus, onClick = onClick, modifier = Modifier.fadeUnderShelf(gridState, app.key))
            }
        }
    }
}

/** Fades the item with the row it is in: fully visible from just above the shelf's bottom edge down, gone [ShelfFade] above that. */
private fun Modifier.fadeUnderShelf(
    grid: LazyGridState,
    key: Any,
): Modifier = this then FadeUnderShelfElement(grid, key)

private data class FadeUnderShelfElement(
    val grid: LazyGridState,
    val key: Any,
) : ModifierNodeElement<FadeUnderShelfNode>() {
    override fun create() = FadeUnderShelfNode(grid, key)

    override fun update(node: FadeUnderShelfNode) {
        node.grid = grid
        node.key = key
    }
}

private class FadeUnderShelfNode(
    var grid: LazyGridState,
    var key: Any,
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
                alpha = ((top - (ShelfFullyShown.toPx() - fade)) / fade).coerceIn(0f, 1f)
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
                Modifier.graphicsLayer {
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
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = { onClick(app.key) },
        onFocused = { onFocus(app.key) },
        shape = TileShape,
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

@Composable
private fun TopBar(
    glass: GlassState,
    dockFocus: FocusRequester,
    clock: () -> String,
    date: () -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(top = TopBarTop, start = ScreenMargin, end = ScreenMargin)
                .fillMaxWidth()
                .height(TopBarHeight)
                .focusProperties { down = dockFocus }
                .glassContainer(glass),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassSurface(glass = glass, modifier = Modifier.fillMaxHeight(), shape = CircleShape) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(text = clock(), style = TvTheme.typography.clock)
                BasicText(text = date(), style = TvTheme.typography.date)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIconButton(glass, stringResource(R.string.inputs), onClick = {}) { focus ->
            IconPart(R.drawable.ic_inputs_frame)
            IconPart(R.drawable.ic_inputs_arrow) {
                translationX = focus() * INPUTS_ARROW_SLIDE * size.width / ICON_VIEWPORT
            }
        }
        GlassIconButton(glass, stringResource(R.string.wifi), onClick = {}) { focus ->
            IconPart(R.drawable.ic_wifi_dot)
            IconPart(R.drawable.ic_wifi_arc_inner) { alpha = wifiArcAlpha(focus(), arc = 0) }
            IconPart(R.drawable.ic_wifi_arc_outer) { alpha = wifiArcAlpha(focus(), arc = 1) }
        }
        GlassIconButton(glass, stringResource(R.string.settings), onClick = {}) { focus ->
            IconPart(R.drawable.ic_settings) { rotationZ = SETTINGS_TURN * focus() }
        }
    }
}

/** One layer of a top bar icon, drawn over the others in the same 24-unit viewport; [layer] runs in the draw phase. */
@Composable
private fun IconPart(
    @DrawableRes icon: Int,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                layer()
            },
        colorFilter = ColorFilter.tint(TvTheme.colors.onSurface),
    )
}

/**
 * The Wi-Fi arcs on focus [progress] (0 idle, 1 focused): both dim at once, then light again one after another,
 * the inner arc first. Both are fully lit at 0 and from 0.8 on.
 */
private fun wifiArcAlpha(
    progress: Float,
    arc: Int,
): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = ARC_DIM_END + arc * ARC_STAGGER
    return when {
        p < ARC_DIM_END -> 1f - (1f - ARC_DIM) * p / ARC_DIM_END
        else -> (ARC_DIM + (1f - ARC_DIM) * (p - start) / ARC_RELIGHT).coerceIn(ARC_DIM, 1f)
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

@Composable
private fun HomePreview(glassKind: GlassKind) {
    TvTheme(glassKind = glassKind) {
        val apps =
            listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio", "Clock", "Maps", "Notes", "Podcasts")
                .map { AppEntry(key = "example.${it.lowercase()}", packageName = "example.${it.lowercase()}", label = it) }
        val layout = HomeLayout(columns = 6, rowsBelowDock = 0, singleRow = false, fillDock = true)
        val state = homeUiState(HomeApps(AppList(apps), layout), LocalDateTime.of(2026, 11, 3, 20, 15), "example.music", Locale.ENGLISH)
        HomeScreen(state = { state }, layout = layout, onFocus = {}, onClick = {})
    }
}
