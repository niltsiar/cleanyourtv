package dev.niltsiar.tvhome.feature.home

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalConfiguration
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
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDateTime
import java.time.ZoneId

internal val ScreenMargin = 40.dp
private val TileGap = 24.dp
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
private val HintBottom = 28.dp
private val LibraryRowHeight = 84.dp
private const val TILE_ASPECT = 16f / 9f

/** The grid items before the first grid tile: the shelf spacer and the dock. */
private const val GRID_ITEMS_BEFORE = 2

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
 * [actions] carry the tile menu, the Settings menu and the reorder and edit-dock modes to the state. Holding OK on a tile
 * or the Settings button, or MENU on a tile, opens its menu; in a mode OK and the arrows move tiles. An open menu hangs from
 * the item that opened it, and from [menuAnchor] (in the screen's coordinates) until one does.
 * [glass] is what glass surfaces draw from; the host shares it with the screens it shows over Home. The library pill
 * after the last row ([HomeLayout.libraryPill]) and the top bar's library button call [onOpenLibrary]; when
 * [libraryOpen] goes false again, focus returns to the one that opened it (the pill for any other way in).
 * OK on the Inputs button hangs the TV's input list under it; Back closes the list and returns focus to the button.
 * Each rise of [inputsRequests] (the system asked for the input list) opens the list once the button is laid out.
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
    actions: HomeActions = HomeActions(),
    menuAnchor: Rect = Rect.Zero,
    glass: GlassState = rememberGlassState(),
    libraryOpen: () -> Boolean = { false },
    onOpenLibrary: () -> Unit = {},
    inputsRequests: () -> Int = { 0 },
) {
    val dockFocus = remember { FocusRequester() }
    val currentState = rememberUpdatedState(state)
    val currentActions = rememberUpdatedState(actions)
    val currentOpenLibrary = rememberUpdatedState(onOpenLibrary)
    val input =
        remember {
            HomeInput(
                currentActions,
                carried = derivedStateOf { currentState.value().reorder?.carried },
                dimmed = derivedStateOf { currentState.value().reorder?.dimmed ?: emptySet() },
                reordering = derivedStateOf { currentState.value().reorder != null },
                openLibrary = currentOpenLibrary,
            ).also { it.anchor.value = menuAnchor }
        }
    val menu by remember { derivedStateOf { currentState.value().menu } }
    val hint by remember { derivedStateOf { currentState.value().reorder?.hint } }
    val inputsOpen by remember { derivedStateOf { currentState.value().inputsOpen } }
    val backOpen by remember {
        derivedStateOf { currentState.value().let { it.menu != null || it.reorder != null || it.inputsOpen } }
    }
    BackHandler(enabled = backOpen) { currentActions.value.onBack() }
    LaunchedEffect(Unit) {
        var handled = 0
        snapshotFlow(inputsRequests).collect { requests ->
            if (requests <= handled) return@collect
            handled = requests
            // The button is placed by the second frame; a hidden button never is.
            withFrameNanos { }
            withFrameNanos { }
            if (input.inputsBounds != Rect.Zero) input.openInputs()
        }
    }
    val gridState = rememberLazyGridState()
    val backdrop = rememberCinematicBackdrop(shelfLayout, shelf = { state().shelf }, heroArt = heroArt)
    val shelfHeight = if (shelfLayout.cinematic) CinematicShelfHeight else ShelfHeight
    val shelfModifier = Modifier.padding(top = ContentTop, start = ScreenMargin).height(shelfHeight - ShelfTitleGap)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(wallpaperLayout.background)
                .onPreviewKeyEvent { input.onPreview(it, currentState.value()) },
    ) {
        WallpaperBackdrop(glass, wallpaper = { state().wallpaper }, layout = wallpaperLayout, cinematic = backdrop)
        HomeContent(
            glass,
            dockFocus,
            gridState,
            input,
            state,
            layout,
            shelfLayout,
            ContentTop + shelfHeight,
            onFocus,
            onClick,
            homePresses,
            libraryOpen,
            onOpenLibrary,
        )
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
            onOpenLibrary = {
                input.fromTopBar = true
                onOpenLibrary()
            },
            input = input,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        hint?.let { ReorderHintPill(glass, it, Modifier.align(Alignment.BottomCenter).padding(bottom = HintBottom)) }
        menu?.let { open ->
            key(open) { MenuOverlay(glass, open.rows, input.anchor, alignEnd = open.tileKey == null, onPick = input.actions.onMenuPick) }
        }
        if (inputsOpen) {
            InputsMenu(
                glass,
                rows = { state().inputs.rows },
                anchor = input.anchor,
                animated = topBar.animated,
                onPick = input.actions.onInputPick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    glass: GlassState,
    dockFocus: FocusRequester,
    gridState: LazyGridState,
    input: HomeInput,
    state: () -> HomeUiState,
    layout: HomeLayout,
    shelfLayout: ShelfLayout,
    shelfBottom: Dp,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    homePresses: () -> Int,
    libraryOpen: () -> Boolean,
    onOpenLibrary: () -> Unit,
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
    val firstDockTile = remember { FocusRequester() }
    LaunchedEffect(gridState) {
        snapshotFlow { state().focusRequest }.filterNotNull().collect { request -> focusOn(request.target, gridState, state, input) }
    }
    LaunchedEffect(gridState) {
        var wasOpen = false
        snapshotFlow(libraryOpen).collect { open ->
            if (wasOpen && !open) {
                val target = if (input.fromTopBar) LIBRARY_BUTTON_FOCUS else LIBRARY_FOCUS
                input.fromTopBar = false
                focusOn(target, gridState, state, input)
            }
            wasOpen = open
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow(homePresses).drop(1).collect {
            input.actions.onHomePressed()
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
            modifier =
                modifier
                    .fillMaxSize()
                    .glassSource(glass, zIndex = 1f)
                    .onFocusChanged { input.gridFocused = it.hasFocus }
                    .semantics { testTagsAsResourceId = true },
        ) {
            val grid = state().grid
            item(key = "shelf", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                Spacer(Modifier.fillMaxWidth().height(shelfBottom - ContentTop))
            }
            item(key = "dock", span = { GridItemSpan(maxLineSpan) }, contentType = "dock") {
                Dock(
                    glass = glass,
                    focus = dockFocus,
                    firstTile = firstDockTile,
                    input = input,
                    dock = state().dock,
                    grid = grid,
                    layout = layout,
                    shelfLayout = shelfLayout,
                    rowTileWidth = rowTileWidth,
                    onFocus = onFocus,
                    onClick = onClick,
                    modifier = Modifier.fadeUnderShelf(gridState, "dock", shelfBottom),
                )
            }
            itemsIndexed(grid, key = { _, app -> app.key }, contentType = { _, _ -> "tile" }) { index, app ->
                AppTile(
                    app = app,
                    accent = shelfLayout.focusAccent(app.accent, inDock = false),
                    onFocus = onFocus,
                    onClick = onClick,
                    input = input,
                    tileMenu = layout.tileMenu,
                    modifier =
                        Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = TvTheme.motion.placementSpec)
                            .fadeUnderShelf(gridState, app.key, shelfBottom)
                            .towardLibrary(
                                input,
                                layout,
                                grid,
                                last = index == grid.lastIndex,
                                row = index / layout.columns,
                                column =
                                    index % layout.columns,
                            ),
                )
            }
            if (layout.libraryPill) {
                item(key = LIBRARY_FOCUS, span = { GridItemSpan(maxLineSpan) }, contentType = "library") {
                    LibraryPill(glass, input, state, layout, onOpenLibrary)
                }
            }
        }
    }
}

/**
 * Moves focus to [target], a tile key or [SETTINGS_FOCUS], [LIBRARY_FOCUS] or [LIBRARY_BUTTON_FOCUS]. The tile may be off
 * screen, or about to be built in the other container by the list that just changed.
 */
private suspend fun focusOn(
    target: String,
    gridState: LazyGridState,
    state: () -> HomeUiState,
    input: HomeInput,
) {
    val grid = state().grid
    val inGrid = grid.indexOfFirst { it.key == target }
    val shown = gridState.layoutInfo.visibleItemsInfo
    when {
        inGrid >= 0 -> if (shown.none { it.key == target }) gridState.scrollToItem(inGrid + GRID_ITEMS_BEFORE)
        target == LIBRARY_FOCUS -> if (shown.none { it.key == LIBRARY_FOCUS }) gridState.scrollToItem(GRID_ITEMS_BEFORE + grid.size)
        shown.none { it.key == "dock" } -> gridState.scrollToItem(0)
    }
    withFrameNanos { }
    withFrameNanos { }
    input.focus(target)
}

/**
 * The App library pill after the last row. Only Up leaves it, to the tile in the last row under the column that had
 * focus last.
 */
@Composable
private fun LibraryPill(
    glass: GlassState,
    input: HomeInput,
    state: () -> HomeUiState,
    layout: HomeLayout,
    onOpen: () -> Unit,
) {
    val ink = rememberUpdatedState(TvTheme.colors.onSurface)
    Box(modifier = Modifier.fillMaxWidth().height(LibraryRowHeight), contentAlignment = Alignment.Center) {
        GlassIconButton(
            glass = glass,
            contentDescription = stringResource(R.string.library_entry),
            onClick = onOpen,
            modifier =
                Modifier
                    .testTag("library:pill")
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        pillUp(input, state(), layout)?.let { up = it }
                    }.focusRequester(input.requester(LIBRARY_FOCUS))
                    .onFocusChanged { input.pillFocused = it.isFocused },
            label = { BasicText(stringResource(R.string.library_entry), style = TvTheme.typography.pillLabel, maxLines = 1) },
        ) { focus -> AppsIcon(ink, focus) }
    }
}

private fun pillUp(
    input: HomeInput,
    state: HomeUiState,
    layout: HomeLayout,
): FocusRequester? {
    val key = pillUpKey(state.dock, state.grid, layout.columns, layout.singleRow, state.focusedKey) ?: return null
    return input.requester(key)
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
    val motion = TvTheme.motion
    val reveal = remember(text) { Animatable(if (motion.animations) 0f else 1f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, motion.springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
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
    input: HomeInput,
    dock: List<AppEntry>,
    grid: List<AppEntry>,
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
                            input = input,
                            tileMenu = layout.tileMenu,
                            modifier =
                                Modifier
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = TvTheme.motion.placementSpec)
                                    .width(rowTileWidth)
                                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                                    .towardLibrary(input, layout, grid, last = index == dock.lastIndex, row = -1, column = index),
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
                            input = input,
                            tileMenu = layout.tileMenu,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .slideToIndex(index, TileGap)
                                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                                    .stopAtDockEnds(
                                        first = index == 0,
                                        last = index == dock.lastIndex,
                                        input,
                                        layout,
                                        grid,
                                        column = index,
                                    ),
                        )
                    }
                }
                // Slots with no app stay empty, so the tiles keep the width of a column.
                repeat(layout.columns - dock.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The dock is a row of its own: Left and Right stop at its first and last tile instead of jumping to a wider grid tile.
 * With the library pill, Right from the last tile of a dock with no grid below goes to the pill, and Down from a dock tile
 * with no tile below it goes to the grid's last tile.
 */
private fun Modifier.stopAtDockEnds(
    first: Boolean,
    last: Boolean,
    input: HomeInput,
    layout: HomeLayout,
    grid: List<AppEntry>,
    column: Int,
): Modifier {
    val down = libraryDown(input, layout, grid, row = -1, column = column)
    return if (!first && !last && down == null) {
        this
    } else {
        focusProperties {
            if (first) left = FocusRequester.Cancel
            if (last) right = if (layout.libraryPill && grid.isEmpty()) input.requester(LIBRARY_FOCUS) else FocusRequester.Cancel
            if (down != null) this.down = down
        }
    }
}

/** The tile Down from [row] ([row] -1 is the dock) reaches when the row below it is short and the library pill follows. */
private fun libraryDown(
    input: HomeInput,
    layout: HomeLayout,
    grid: List<AppEntry>,
    row: Int,
    column: Int,
): FocusRequester? {
    if (!layout.libraryPill) return null
    return gridDownOverride(row, column, grid.size, layout.columns)?.let { input.requester(grid[it].key) }
}

/**
 * Keys toward the library pill from a tile of the grid or the dock row: Right from the last tile, and Down to the last
 * tile from a tile whose cell below is empty, so the pill, which spans the row below, does not capture it.
 */
private fun Modifier.towardLibrary(
    input: HomeInput,
    layout: HomeLayout,
    grid: List<AppEntry>,
    last: Boolean,
    row: Int,
    column: Int,
): Modifier {
    if (!layout.libraryPill) return this
    val rightTo = if (last && (row >= 0 || grid.isEmpty())) input.requester(LIBRARY_FOCUS) else null
    val downTo = libraryDown(input, layout, grid, row, column)
    if (rightTo == null && downTo == null) return this
    return focusProperties {
        if (rightTo != null) right = rightTo
        if (downTo != null) down = downTo
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    accent: Color,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    input: HomeInput,
    tileMenu: Boolean,
    modifier: Modifier = Modifier,
) {
    val key = app.key
    val reordering by input.reordering
    val requester = remember(input, key) { input.requester(key) }
    FocusSurface(
        onClick = { onClick(key) },
        onFocused = { onFocus(key) },
        shape = TileShape,
        accent = accent,
        modifier =
            modifier
                .then(if (reordering) Modifier.reorderLook(key, input) else Modifier)
                .aspectRatio(TILE_ASPECT)
                .testTag("tile:$key")
                .focusRequester(requester)
                .holdOk(
                    input.hold,
                    onHold = { bounds ->
                        input.anchor.value = bounds
                        input.actions.onTileMenu(key, true)
                    },
                    onMenuKey =
                        if (tileMenu) {
                            { bounds ->
                                input.anchor.value = bounds
                                input.actions.onTileMenu(key, false)
                            }
                        } else {
                            null
                        },
                ),
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

@Preview(name = "Home 1080p glass 4 columns", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenFourColumnsPreview() {
    HomePreview(GlassKind.Refractive, columns = 4)
}

@Preview(name = "Home 1080p tile menu", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenTileMenuPreview() {
    HomePreview(
        GlassKind.Refractive,
        menu = HomeMenu("example.movies", listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo)),
        menuAnchor = Rect(left = 110f, top = 520f, right = 380f, bottom = 672f),
    )
}

@Preview(name = "Home 1080p inputs", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenInputsPreview() {
    HomePreview(
        GlassKind.Refractive,
        inputs = PreviewInputs,
        inputsOpen = true,
        menuAnchor = Rect(left = 1358f, top = 56f, right = 1438f, bottom = 136f),
    )
}

@Preview(name = "Home 1080p reorder", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenReorderPreview() {
    val others = PreviewNames.map { "example.${it.lowercase()}" } - "example.music"
    HomePreview(
        GlassKind.Refractive,
        reorder = ReorderView(carried = "example.music", dimmed = others.toSet(), hint = ReorderHint.Reorder),
        focused = "example.music",
    )
}

@Preview(name = "Home 1080p library pill", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenLibraryPillPreview() {
    HomePreview(GlassKind.Refractive, focused = LIBRARY_FOCUS, appCount = 9, libraryPill = true)
}

@Preview(name = "Home 1080p Spanish", device = "spec:width=1920px,height=1080px,dpi=320", locale = "es-rES", showBackground = true)
@Composable
private fun HomeScreenSpanishPreview() {
    HomePreview(GlassKind.Refractive)
}

@Preview(name = "Home 1080p reorder Spanish", device = "spec:width=1920px,height=1080px,dpi=320", locale = "es-rES", showBackground = true)
@Composable
private fun HomeScreenReorderSpanishPreview() {
    val others = PreviewNames.map { "example.${it.lowercase()}" } - "example.music"
    HomePreview(
        GlassKind.Refractive,
        reorder = ReorderView(carried = "example.music", dimmed = others.toSet(), hint = ReorderHint.DockChooseApp),
        focused = "example.music",
    )
}

@Preview(name = "Home 1080p flat", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenFlatPreview() {
    HomePreview(GlassKind.Flat)
}

private val PreviewNames =
    listOf("Movies", "Music", "Games", "Photos", "Weather", "Browser", "Files", "Radio", "Clock", "Maps", "Notes", "Podcasts")
private val PreviewAccents = listOf(0xFFE53935, 0xFFE91E63, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1)

/** Four HDMI ports, the second with a CEC device on it, one of them empty, and Live TV. */
private val PreviewInputs =
    InputsMenu(
        available = true,
        rows =
            listOf(
                InputRow.Port("HDMI 1", InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput("tv/.Hdmi/HW1")),
                InputRow.Port("Apple TV \u00B7 HDMI 2", InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput("tv/.Cec/HDMI2")),
                InputRow.Port("HDMI 3", InputIcon.Hdmi, InputConnection.Disconnected, InputTarget.TvInput("tv/.Hdmi/HW3")),
                InputRow.Port("HDMI 4", InputIcon.Hdmi, InputConnection.Connected, InputTarget.TvInput("tv/.Hdmi/HW4")),
                InputRow.Separator(),
                InputRow.LiveTv,
            ),
    )

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
private fun HomePreview(
    glassKind: GlassKind,
    menu: HomeMenu? = null,
    menuAnchor: Rect = Rect.Zero,
    reorder: ReorderView? = null,
    focused: String = "example.movies",
    appCount: Int = PreviewNames.size,
    libraryPill: Boolean = false,
    inputs: InputsMenu = InputsMenu(available = true),
    inputsOpen: Boolean = false,
    columns: Int = 6,
) {
    TvTheme(glassKind = glassKind) {
        val apps =
            PreviewNames.take(appCount).mapIndexed { i, name ->
                val id = "example.${name.lowercase()}"
                AppEntry(key = id, packageName = id, label = name, accent = Color(PreviewAccents[i % PreviewAccents.size]))
            }
        val layout = HomeLayout(columns = columns, rowsBelowDock = 0, singleRow = false, fillDock = true, libraryPill = libraryPill)
        val locale = LocalConfiguration.current.locales[0]
        val header =
            HeaderStyle(hour24 = false, datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), greetingName = "Sam")
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
                focused,
                locale,
                header,
                network,
                wallpaper = wallpaper,
                cinematic = true,
                lastOpened = mapOf("example.movies" to yesterday),
                menu = menu,
                reorder = reorder,
                focusRequest = FocusRequest(focused, 1),
                inputs = inputs,
                inputsOpen = inputsOpen,
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
            menuAnchor = menuAnchor,
        )
    }
}
