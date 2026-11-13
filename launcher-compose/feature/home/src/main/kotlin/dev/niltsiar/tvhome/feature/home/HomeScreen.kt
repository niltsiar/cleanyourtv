package dev.niltsiar.tvhome.feature.home

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.DockEdgeLight
import dev.niltsiar.tvhome.core.designsystem.DockSurface
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.GlassIconButton
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.KeyChip
import dev.niltsiar.tvhome.core.designsystem.KeyChipHeight
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.Spotlight
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.clipsHalo
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.designsystem.popsOver
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.designsystem.ringBlendsWith
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

internal val ScreenMargin = 40.dp
internal val TileGap = 24.dp

/**
 * The gap between a tile and its name's pill, for a tile that grows about its bottom edge: its ring then reaches
 * [FocusRingReach] x 1.08 = 9.72 dp under it, and the pill, which never moves, stays [NameRingClearance] clear of it.
 * [nameGap] adds to it for a tile too tall for that.
 */
private val TileNameGap = 14.dp
private val NameRingClearance = 4.dp

/**
 * How far under the top of a tile [tileHeight] dp high its focus growth, grown [scale] times, is anchored: its bottom edge, or
 * higher when the grown ring would reach within [NameRingClearance] of the [TileGap] over the tile (the row above's pills, the
 * dock panel's rim).
 */
internal fun namePivot(
    tileHeight: Float,
    scale: Float,
): Float {
    if (scale <= 1f) return tileHeight
    val reach = TileGap.value - NameRingClearance.value - FocusRingReach.value * scale
    return minOf(tileHeight, reach / (scale - 1f))
}

/**
 * The gap under a tile [tileHeight] dp high to its pill: [TileNameGap], and the tile's growth under its bottom edge past it.
 * A tile that grows [scale] times and pops to [peak] has the gap for its growth and ring at the peak, [NameRingClearance]
 * clear of the pill.
 */
internal fun nameGap(
    tileHeight: Float,
    scale: Float,
    peak: Float = scale,
): Float =
    maxOf(TileNameGap.value, FocusRingReach.value * peak + NameRingClearance.value) +
        (peak - 1f).coerceAtLeast(0f) * (tileHeight - namePivot(tileHeight, scale))

/**
 * The room over a tile that grows [scale] times about [pivot] dp under its top: the [TileGap], or, when it pops to [peak],
 * its growth and ring at the peak and [NameRingClearance] if that is more.
 */
internal fun roomOver(
    pivot: Float,
    scale: Float,
    peak: Float,
): Float =
    if (peak <= scale) {
        TileGap.value
    } else {
        maxOf(TileGap.value, pivot * (peak - 1f) + FocusRingReach.value * peak + NameRingClearance.value)
    }

/** The least the space under a pill is of its [nameGap], so that the pill reads as the name of the tile over it. */
private const val NAME_BELOW_RATIO = 1.5f

/**
 * What a tile [tileHeight] dp high adds under its pill, so that the space to what follows (a [TileGap] away) is
 * [NAME_BELOW_RATIO] times its gap, and at least the [roomOver] of a tile as high in the row under it.
 */
internal fun nameBelow(
    tileHeight: Float,
    scale: Float,
    peak: Float = scale,
): Float =
    maxOf(
        0f,
        NAME_BELOW_RATIO * nameGap(tileHeight, scale, peak) - TileGap.value,
        roomOver(namePivot(tileHeight, scale), scale, peak) - TileGap.value,
    )

/** One line of a tile's name and the padding of its pill. */
private val NameLine = 20.dp
private val NamePillAcross = 6.dp
private val NamePillDown = 2.dp

/** The radius of every name pill: half the height of a pill of one line. */
private val NamePillRadius = (NameLine + NamePillDown * 2) / 2

/** The room always_show_names adds under a tile that grows about its bottom edge: the gap and a pill of one line. */
internal val TileNameRoom = TileNameGap + NameLine + NamePillDown * 2

/** How far the name of another launcher's banner stands from the banner's bottom and left edges. */
private val NameOnBannerInset = 8.dp

/** The ink and pill of a name under a tile, focused or not: the hero title already names the focused app. */
private val NameInk = Color.White.copy(alpha = 0.85f)
private val NamePill = Color.Black.copy(alpha = 0.55f)

internal val TopBarTop = 20.dp
internal val TopBarHeight = 56.dp
internal val ContentTop = TopBarTop + TopBarHeight + 20.dp
private val CaptionTop = TopBarTop + TopBarHeight + 8.dp
private val TileCorner = 12.dp
private val TileShape = RoundedCornerShape(TileCorner)
private val LightTileEdgeWidth = 1.dp
private val LightTileEdge = Color.Black.copy(alpha = 0.75f)

/** The edge of a dock tile on the light glass dock (dock_glass), 1.5 px wide, so a white tile holds on it. */
private const val GLASS_TILE_EDGE_PX = 1.5f
private val GlassTileEdge = Color.Black.copy(alpha = 0.18f)

private val DockShape = RoundedCornerShape(32.dp)
private val GridBottom = 48.dp

/** The space between the end of the Inputs list and the dock's panel. */
private val InputsTrayClearance = 24.dp
private val LibraryRowHeight = 84.dp

/** The least padding at the sides of a dock panel that has fewer tiles than the row holds. */
private val DockShrunkSide = 28.dp

/** From the outer edge of a focused end dock tile's ring to the dock panel's wall, at least. */
private val DockRingWallClear = 12.dp

/** The dock panel's padding: [side] left and right of the tiles, [vertical] above and below them. */
@Immutable
internal data class DockPadding(
    val side: Dp,
    val vertical: Dp = TileGap,
)

/**
 * The padding of a dock panel whose focused end tile's ring needs [ringSide] to the wall and [vertical] over and under the
 * tiles. A [shrunk] panel (fewer tiles than the row holds) keeps at least [DockShrunkSide]. With [even] the top and bottom
 * take the side's padding, or [vertical] if that is more, and so does the side.
 */
internal fun dockPadding(
    ringSide: Dp,
    shrunk: Boolean,
    even: Boolean,
    vertical: Dp = TileGap,
): DockPadding {
    val side = if (shrunk) maxOf(DockShrunkSide, ringSide) else ringSide
    if (!even) return DockPadding(side, vertical)
    val all = maxOf(side, vertical)
    return DockPadding(all, all)
}

/** The width of a tile of the dock's row, from the [gridWidth] of [columns] tiles and the gaps between them. */
private fun dockRowTileWidth(
    gridWidth: Dp,
    columns: Int,
): Dp = (gridWidth - TileGap * (columns - 1)) / columns

/** The padding a focused end tile of the dock row, grown [scale] times, needs: its ring's reach and [DockRingWallClear]. */
private fun Density.dockRingSide(
    rowTileWidth: Dp,
    scale: Float,
): Dp = ringReach(rowTileWidth.toPx(), scale).toDp() + DockRingWallClear

/** Whether the dock panel is drawn only as wide as its [dockSize] tiles: it holds fewer than the row has room for. */
private fun DockLook.shrinks(
    layout: HomeLayout,
    dockSize: Int,
): Boolean = band && !layout.singleRow && dockSize < layout.columns

/** From the shelf's bottom edge up to the line under which a row at the top of the grid counts as fully visible. */
private val ShelfToFade = 16.dp

/** From the bottom of the band's text to the shelf's bottom edge, with 12 dp between the text and the grid's cutoff. */
private val TitleToShelfBottom = 12.dp + ShelfToFade
private val HeroAscent = 36.sp
private val ChipHeight = KeyChipHeight
private val CinematicShelfHeight = CinematicTextHeight + TitleToShelfBottom

/** A 48 sp title line (its ascent and descent), then the meta line or the key chips. */
private val ShelfHeight = 52.dp + MetaGap + ChipHeight + TitleToShelfBottom

/** The alpha of the grid rows with focus on the library pill: the grid is not a ghost above it. */
private const val LAST_ROW_PILL_ALPHA = 0.85f

/** How far the title and its line move down while the grid is up, so its capitals stand 36 dp under the top bar. */
private val TitleGridDrop = 16.dp

/** A dock scrolled up for the grid fades out over the last of this before it reaches the shelf's bottom edge. */
private val DockFadeOut = 28.dp
private const val MENU_SCRIM = 0.5f
private const val BUTTON_SPOT_RING_ALPHA = 0.48f
private const val LIBRARY_SCRIM = 0.78f
private const val BAND_IN_MS = 160
private const val BAND_OUT_MS = 120
private const val LIBRARY_SWAP_MS = 150

/** How long the hero block takes to fade out as a tile menu opens, and back in as it closes. */
private const val HERO_MENU_MS = 120

/** The row the dock is in; rows count from 0 at the first grid row. */
private const val DOCK_ROW = -1
private val ReorderRing = Color(0xFFFF6B4A)

/** The grid items before the first grid tile: the shelf spacer and the dock. */
internal const val GRID_ITEMS_BEFORE = 2

/**
 * The Home screen. [state] is read lazily, inside the parts that show each field. The grid reads the dock and grid
 * lists through states that ignore equal lists, outside its items, so a focus move recomposes no grid item while the
 * shelf label updates.
 * The shelf title stays under the top bar; rows fade out as they scroll up to it.
 * [homePresses] counts Home key presses that reach Home while it shows; each one scrolls to the top and focuses the first dock tile.
 * [layout] is the theme's: dock size and grid columns, rows that must fit below the dock, one sideways row.
 * [topBar] is the theme's top bar: which buttons show and how.
 * [wallpaperLayout] is the theme's say over the picture behind Home, which [HomeUiState.wallpaper] supplies.
 * [shelfLayout] is the theme's cinematic shelf and focus colours; [heroArt] gives it the softened art of a tile.
 * [dockLook] is the theme's dock panel: its tint, its glass and the light of the focused tile on its rim.
 * The cinematic shelf makes the shelf taller.
 * [actions] carry the tile menu, the Settings menu and the reorder and edit-dock modes to the state. Holding OK on a tile
 * or the Settings button, or MENU on a tile, opens its menu; in a mode OK and the arrows move tiles. An open menu hangs from
 * the item that opened it, and from [menuAnchor] (in the screen's coordinates) until one does.
 * [glass] is what glass surfaces draw from; the host shares it with the screens it shows over Home. The library pill
 * after the last row ([HomeLayout.libraryPill]) and the top bar's library button call [onOpenLibrary]; when
 * [libraryOpen] goes false again, focus returns to the one that opened it (the pill for any other way in).
 * OK on the Inputs button hangs the TV's input list under it; Back closes the list and returns focus to the button.
 * Each rise of [inputsRequests] (the system asked for the input list) opens the list.
 * The menus, the input list and [overlays] (the host's, such as the App library, open while [libraryOpen]) sit in one
 * [OverlayHost] over everything else on Home. The tile a menu acts on stays undimmed under it, and so does the top-bar button
 * that opened the Settings menu or the input list. While the library is open
 * every layer of Home above the wallpaper fades out, so only the wallpaper and the library's scrim are behind its panel.
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
    dockLook: DockLook = DockLook(),
    heroArt: suspend (String) -> ImageBitmap? = { null },
    onOpenSettings: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
    onOpenWifiSettings: () -> Unit = {},
    actions: HomeMenuActions = HomeMenuActions(),
    menuAnchor: Rect = Rect.Zero,
    glass: GlassState = rememberGlassState(),
    libraryOpen: () -> Boolean = { false },
    onOpenLibrary: () -> Unit = {},
    inputsRequests: () -> Int = { 0 },
    overlays: @Composable () -> Unit = {},
) {
    val dockFocus = remember { FocusRequester() }
    val currentState = rememberUpdatedState(state)
    val currentActions = rememberUpdatedState(actions)
    val currentOpenLibrary = rememberUpdatedState(onOpenLibrary)
    val shelfHeight = if (shelfLayout.cinematic) CinematicShelfHeight else ShelfHeight
    val shelfBottom = ContentTop + shelfHeight
    val dockPadding = rememberDockPadding(currentState, layout, dockLook)
    val dim = rememberFocusDim(currentState, shelfBottom, layout, dockPadding)
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val input =
        remember {
            HomeInput(
                currentActions,
                groupShift = dim::lowPx,
                carried = derivedStateOf { currentState.value().reorder?.carried },
                dimmed = derivedStateOf { currentState.value().reorder?.dimmed ?: emptySet() },
                reordering = derivedStateOf { currentState.value().reorder != null },
                canMoveLeft = derivedStateOf { currentState.value().reorder?.canMoveLeft == true },
                canMoveRight = derivedStateOf { currentState.value().reorder?.canMoveRight == true },
                openLibrary = currentOpenLibrary,
                inputOffered = derivedStateOf { currentState.value().inputOffer != null },
                habit = if (layout.habitMark) derivedStateOf { currentState.value().habit } else null,
                faded = if (layout.unusedFade) derivedStateOf { currentState.value().faded } else null,
            ).also { it.anchor.value = menuAnchor }
        }
    val menu by remember { derivedStateOf { currentState.value().menu } }
    val inputsOpen by remember { derivedStateOf { currentState.value().inputsOpen } }
    val menuDockTile by remember {
        derivedStateOf {
            currentState.value().let { shown -> shown.menu?.tileKey?.let { key -> shown.dock.firstOrNull { it.key == key }?.label } }
        }
    }
    val menuPlace =
        remember {
            derivedStateOf {
                val at = input.anchor.value
                if (currentState.value().menu?.tileKey != null) at.translate(0f, dim.lowPx()) else at
            }
        }
    val backOpen by remember {
        derivedStateOf { currentState.value().let { it.menu != null || it.reorder != null || it.inputsOpen } }
    }
    BackHandler(enabled = backOpen) { currentActions.value.onBack() }
    LaunchedEffect(Unit) {
        var handled = 0
        snapshotFlow(inputsRequests).collect { requests ->
            if (requests <= handled) return@collect
            handled = requests
            input.openInputs()
        }
    }
    val reordering = { state().reorder != null }
    val backdrop = rememberCinematicBackdrop(shelfLayout, shelf = { state().shelf }, heroArt = heroArt, reordering = reordering)
    val titleShift: GraphicsLayerScope.() -> Float = { dim.titleShiftPx(TitleGridDrop.toPx()) }
    val heroSource by remember(state, layout.dockTitle) {
        derivedStateOf { heroSource(layout.dockTitle, state().reorder?.hint, input.pillFocused, input.headerControl(state())) }
    }
    val heroScrim = rememberHeroScrim(glass, wallpaperLayout.headerScrim)
    val scrimWanted by remember(shelfLayout.cinematic) { derivedStateOf { shelfLayout.cinematic || heroSource != HeroSource.Quiet } }
    val scrimFade = rememberHeroScrimFade(source = { heroSource }, wanted = { scrimWanted })
    val scrimLit = { heroScrim.value.darkening > 0f && scrimWanted }
    val shelfExtent = remember { TitleExtent() }
    val bandExtent = remember { TitleExtent() }
    val shelfModifier =
        Modifier
            .padding(top = ContentTop, start = ScreenMargin)
            .height(shelfHeight - TitleToShelfBottom)
            .graphicsLayer { translationY = titleShift() }
    val band = rememberBandFade(libraryOpen, tileMenuOpen = { currentState.value().menu?.tileKey != null })
    val gridState = rememberLazyGridState()
    FocusDimEffects(dim, currentState, input, layout, libraryOpen, gridState, shelfBottom - ShelfToFade, dockLook.band)
    val scope = rememberCoroutineScope()
    val edge = remember(dockLook.edgeLight) { if (dockLook.edgeLight) DockEdgeLight(scope) else null }
    if (edge != null) DockEdgeEffect(edge, currentState, input, layout, libraryOpen)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(wallpaperLayout.background)
                .onPreviewKeyEvent { input.onPreview(it, currentState.value()) }
                .focusProperties {
                    // Focus coming back from outside (the window regains it, or the focused tile was removed) arrives with
                    // a direction, and the nearest target that way is a top-bar button; it goes to the dock instead.
                    onEnter = {
                        // Before the apps arrive the nearest target is a top-bar button, which would show focus until the dock exists.
                        if (currentState.value().awaitingApps) {
                            cancelFocusChange()
                        } else if (requestedFocusDirection != FocusDirection.Enter) {
                            try {
                                // An overlay that just closed gives focus back to what it hung from, not to the dock on the way.
                                val owner = input.overlayOwner
                                if (owner == null || !input.focus(owner)) dockFocus.requestFocus()
                            } catch (e: IllegalStateException) {
                                // The dock is scrolled out of the grid: the search goes on as usual.
                            }
                        }
                    }
                }.focusGroup(),
    ) {
        WallpaperBackdrop(
            wallpaper = { state().wallpaper },
            layout = wallpaperLayout,
            cinematic = backdrop,
            headerLightness = { glass.headerLightness },
        )
        ChromeFrostEffect(
            glass,
            wallpaper = { state().wallpaper },
            skyIntensity = wallpaperLayout.skyIntensity,
            dock = dim.band,
            hero = dim.hero,
            dockGlass = dockLook.band && dockLook.glass,
        )
        // Every layer above the wallpaper draws at the fade's alpha, so none draws while the App library's panel is up.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = band.layers.value
            },
        ) {
            HeroScrim(
                look = heroScrim,
                boxHeight = shelfHeight - TitleToShelfBottom,
                shelf = shelfExtent,
                band = bandExtent,
                shift = titleShift,
                fade = { scrimFade.value },
            )
            HomeContent(
                glass,
                dockFocus,
                gridState,
                input,
                dim,
                state,
                layout,
                shelfLayout,
                dockLook,
                dockPadding,
                edge,
                shelfBottom,
                onFocus,
                onClick,
                homePresses,
                libraryOpen,
                onOpenLibrary,
            )
            if (shelfLayout.cinematic) {
                CinematicShelf(
                    shelf = { state().takeIf { it.reorder == null && !input.pillFocused && input.headerControl(it) == null }?.shelf },
                    layout = shelfLayout,
                    modifier = shelfModifier,
                    titleAlpha = { band.title.value },
                    detailAlpha = { band.detail.value },
                    pill = scrimLit,
                    extent = shelfExtent,
                )
            }
            HeroTitle(
                state,
                input,
                { heroSource },
                shelfLayout.cinematic,
                band,
                shelfModifier,
                pill = scrimLit,
                extent = bandExtent,
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
                onOpenWifiSettings = onOpenWifiSettings,
                onOpenLibrary = {
                    input.fromTopBar = true
                    onOpenLibrary()
                },
                input = input,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        OverlayHost(
            open = { currentState.value().let { it.menu != null || it.inputsOpen } || libraryOpen() },
            strength = { if (libraryOpen()) LIBRARY_SCRIM else MENU_SCRIM },
            spotlight = {
                val shown = currentState.value()
                val key = shown.menu?.tileKey
                when {
                    key != null -> {
                        val inDock = shown.dock.firstOrNull { it.key == key }
                        val tile = inDock ?: shown.grid.firstOrNull { it.key == key }
                        val ring = tile?.let { shelfLayout.ringColour(it.accent, inDock = inDock != null, edge = it.edge) } ?: Color.White
                        Spotlight(input.anchor.value, TileShape, ring)
                    }

                    shown.menu != null -> {
                        buttonSpotlight(input.settingsBounds.value)
                    }

                    shown.inputsOpen -> {
                        buttonSpotlight(input.inputsBounds.value)
                    }

                    else -> {
                        null
                    }
                }
            },
            spotlightOffset = dim::lowPx,
        ) {
            menu?.let { open ->
                key(open) {
                    MenuOverlay(
                        open.rows,
                        menuPlace,
                        tileMenu = open.tileKey != null,
                        bandBottom = shelfBottom - ShelfToFade,
                        onPick = input.actions.onMenuPick,
                        dockTile = menuDockTile,
                        dockPadding = dockPadding.vertical,
                    )
                }
            }
            if (inputsOpen) {
                InputsMenu(
                    rows = { state().inputs.rows },
                    anchor = input.inputsBounds,
                    bottomLimit = windowHeight * dim.band.top - InputsTrayClearance,
                    animated = topBar.animated,
                    onPick = input.actions.onInputPick,
                )
            }
            overlays()
        }
    }
}

/** The spotlight on a top-bar button at [bounds]; none while the button is not on screen (its bounds are empty). */
private fun buttonSpotlight(bounds: Rect): Spotlight? =
    if (bounds.isEmpty) null else Spotlight(bounds, CircleShape, ringAlpha = BUTTON_SPOT_RING_ALPHA, followsOffset = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    glass: GlassState,
    dockFocus: FocusRequester,
    gridState: LazyGridState,
    input: HomeInput,
    dim: FocusDim,
    state: () -> HomeUiState,
    layout: HomeLayout,
    shelfLayout: ShelfLayout,
    dockLook: DockLook,
    dockPadding: DockPadding,
    edge: DockEdgeLight?,
    shelfBottom: Dp,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    homePresses: () -> Int,
    libraryOpen: () -> Boolean,
    onOpenLibrary: () -> Unit,
) {
    val density = LocalDensity.current
    // The focused tile rests the panel's top padding inside the dock panel, so at rest the panel's top edge is the shelf's bottom edge.
    val pivotPx = with(density) { (shelfBottom + dockPadding.vertical).toPx() }
    val pivotSpec = remember(pivotPx, dim) { PivotBringIntoViewSpec(pivotPx, dim::lowPx) }
    val shelfBottomPx = with(density) { shelfBottom.toPx() }
    val window = LocalWindowInfo.current.containerSize
    val gridWidth = with(density) { window.width.toDp() } - ScreenMargin * 2
    val share = gridShare(layout, gridWidth, with(density) { window.height.toDp() }, shelfBottom, dockPadding.vertical)
    val startMargin = gridStart(gridWidth, share)
    val endMargin = ScreenMargin + gridWidth * (1f - share) - (startMargin - ScreenMargin)
    // The pill ends the grid: scrolled to the end it stands whole above the bottom fade, not a bottom margin higher.
    val pillAfterGrid = layout.libraryPill && !layout.singleRow
    val bottomMargin = if (pillAfterGrid) with(density) { (settleOf(window.height.toFloat()) + 1f).toDp() } else GridBottom
    val rowTileWidth = dockRowTileWidth(gridWidth, layout.columns)
    val firstDockTile = remember { FocusRequester() }
    val dockRow = rememberLazyListState()
    // Read here and not in the grid's content: a state the items read, even one that ignores equal lists, recomposes
    // every visible item on any HomeUiState change. Equal lists also keep the grid's item provider.
    val dock = remember(state) { derivedStateOf(structuralEqualityPolicy()) { state().dock } }.value
    val grid = remember(state) { derivedStateOf(structuralEqualityPolicy()) { state().grid } }.value
    val reordering = input.reordering.value
    val currentLayout = rememberUpdatedState(layout)
    LaunchedEffect(gridState) {
        snapshotFlow { state().focusRequest }.filterNotNull().collect { request ->
            focusOn(request.target, gridState, dockRow, currentLayout.value.singleRow, state, input)
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { state().dock.isNotEmpty() }.distinctUntilChanged().filter { it }.collect {
            // The grid attaches its items during layout, after this effect starts.
            withFrameNanos { }
            val shown = state()
            val target = shown.focusedKey?.takeIf { key -> (shown.dock + shown.grid).any { it.key == key } } ?: shown.dock.first().key
            focusOn(target, gridState, dockRow, currentLayout.value.singleRow, state, input)
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { overlayOwnerOf(state(), input, libraryOpen()) }.collectLatest { owner ->
            if (owner == null) {
                // Same wait as focusOn: the redirect runs before then. An overlay that opens meanwhile cancels the clear.
                withFrameNanos { }
                withFrameNanos { }
            }
            input.overlayOwner = owner
        }
    }
    LaunchedEffect(gridState) {
        var wasOpen = false
        snapshotFlow(libraryOpen).collect { open ->
            if (wasOpen && !open) {
                val target = if (input.fromTopBar) LIBRARY_BUTTON_FOCUS else LIBRARY_FOCUS
                input.fromTopBar = false
                focusOn(target, gridState, dockRow, currentLayout.value.singleRow, state, input)
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
                input.travel.reset()
                firstDockTile.requestFocus()
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(layout.columns),
            contentPadding = PaddingValues(start = startMargin, end = endMargin, top = ContentTop, bottom = bottomMargin),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (layout.scrollCue) {
                            Modifier.moreCue(
                                gridState,
                                dim::lowPx,
                                dim.fold::value,
                                layout.columns,
                                (grid.size + layout.columns - 1) / layout.columns,
                                layout.showNames,
                            )
                        } else {
                            Modifier
                        },
                    ).gridEdges(gridState, dim::lowPx, cutoff = shelfBottom - ShelfToFade, names = layout.showNames)
                    .onFocusChanged {
                        input.gridFocused = it.hasFocus
                        if (!it.hasFocus) input.travel.reset()
                    }.semantics { testTagsAsResourceId = true },
        ) {
            item(key = "shelf", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                // The grid's spacing follows it, so the dock rests at the shelf's bottom edge, where the pivot puts it.
                Spacer(Modifier.fillMaxWidth().height(shelfBottom - ContentTop - TileGap))
            }
            item(key = "dock", span = { GridItemSpan(maxLineSpan) }, contentType = "dock") {
                Dock(
                    glass = glass,
                    focus = dockFocus,
                    firstTile = firstDockTile,
                    row = dockRow,
                    input = input,
                    dock = dock,
                    grid = grid,
                    reordering = reordering,
                    layout = layout,
                    shelfLayout = shelfLayout,
                    look = dockLook,
                    padding = dockPadding,
                    edge = edge,
                    shade = remember(dim) { { dim.shade.value } },
                    rowTileWidth = rowTileWidth,
                    onFocus = onFocus,
                    onClick = onClick,
                    onOpenLibrary = onOpenLibrary,
                    modifier = remember(dim, gridState, shelfBottomPx) { Modifier.dockLayer(dim, gridState, shelfBottomPx) },
                )
            }
            itemsIndexed(grid, key = { _, app -> app.key }, contentType = { _, _ -> "tile" }) { index, app ->
                val toward =
                    remember(input, layout, grid, index, dim) {
                        Modifier
                            .gridRowLayer(index / layout.columns, dim)
                            .towardLibrary(
                                input,
                                layout,
                                grid,
                                last = index == grid.lastIndex,
                                row = index / layout.columns,
                                column = index % layout.columns,
                            )
                    }
                AppTile(
                    app = app,
                    accent = shelfLayout.focusAccent(app.accent, inDock = false),
                    ring = shelfLayout.ringColour(app.accent, inDock = false, edge = app.edge),
                    onFocus = onFocus,
                    onClick = onClick,
                    input = input,
                    reordering = reordering,
                    tileMenu = layout.tileMenu,
                    showName = layout.showNames,
                    fadeUnused = true,
                    modifier =
                        Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = TvTheme.motion.placementSpec)
                            .then(toward),
                )
            }
            if (layout.libraryPill && !layout.singleRow) {
                item(key = LIBRARY_FOCUS, span = { GridItemSpan(maxLineSpan) }, contentType = "library") {
                    LibraryPill(
                        glass,
                        input,
                        onOpenLibrary,
                        Modifier.fillMaxWidth().height(LibraryRowHeight).graphicsLayer { translationY = dim.lowPx() },
                        up = { pillUp(input, state(), layout) },
                    )
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
    dockRow: LazyListState,
    singleRow: Boolean,
    state: () -> HomeUiState,
    input: HomeInput,
) {
    val grid = state().grid
    val inGrid = grid.indexOfFirst { it.key == target }
    val shown = gridState.layoutInfo.visibleItemsInfo
    val inDock = state().dock.indexOfFirst { it.key == target }
    val pillInRow = target == LIBRARY_FOCUS && singleRow
    when {
        inGrid >= 0 -> {
            if (shown.none { it.key == target }) gridState.scrollToItem(inGrid + GRID_ITEMS_BEFORE)
        }

        singleRow && inDock >= 0 -> {
            if (shown.none { it.key == "dock" }) gridState.scrollToItem(0)
            if (dockRow.layoutInfo.visibleItemsInfo.none { it.key == target }) dockRow.scrollToItem(inDock)
        }

        pillInRow -> {
            if (shown.none { it.key == "dock" }) gridState.scrollToItem(0)
            if (dockRow.layoutInfo.visibleItemsInfo.none { it.key == LIBRARY_FOCUS }) dockRow.scrollToItem(state().dock.size)
        }

        target == LIBRARY_FOCUS -> {
            if (shown.none { it.key == LIBRARY_FOCUS }) gridState.scrollToItem(GRID_ITEMS_BEFORE + grid.size)
        }

        shown.none { it.key == "dock" } -> {
            gridState.scrollToItem(0)
        }
    }
    withFrameNanos { }
    withFrameNanos { }
    input.focus(target)
}

/** The focus target the open overlay hangs from, or null while none is open. */
private fun overlayOwnerOf(
    state: HomeUiState,
    input: HomeInput,
    libraryOpen: Boolean,
): String? =
    when {
        state.menu != null -> state.menu.tileKey ?: SETTINGS_FOCUS
        state.inputsOpen -> INPUTS_FOCUS
        libraryOpen -> if (input.fromTopBar) LIBRARY_BUTTON_FOCUS else LIBRARY_FOCUS
        else -> null
    }

/**
 * The App library pill, in [modifier]'s box: after the last row, on the grid's start edge, which it grows from on focus,
 * where only Up leaves it, to the tile [up] names; or, [inRow], centred, the last item of the single dock row, where Left walks back along the row and Up
 * leaves as a tile's does.
 */
@Composable
private fun LibraryPill(
    glass: GlassState,
    input: HomeInput,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    inRow: Boolean = false,
    up: () -> FocusRequester? = { null },
) {
    Box(modifier = modifier, contentAlignment = if (inRow) Alignment.Center else Alignment.CenterStart) {
        GlassIconButton(
            glass = glass,
            contentDescription = stringResource(R.string.library_entry),
            onClick = onOpen,
            transformOrigin = if (inRow) TransformOrigin.Center else TransformOrigin(0f, 0.5f),
            modifier =
                Modifier
                    .testTag("library:pill")
                    .focusProperties {
                        if (!inRow) left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        up()?.let { this.up = it }
                    }.focusRequester(input.requester(LIBRARY_FOCUS))
                    .onFocusChanged {
                        input.pillFocused = it.isFocused
                        if (it.isFocused) input.travel.reset()
                    },
            label = { ink ->
                BasicText(stringResource(R.string.library_entry), style = TvTheme.typography.pillLabel, maxLines = 1, color = ink.content)
            },
        ) { focus, ink -> AppsIcon(ink, focus) }
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

/**
 * The row focus is on, animated: [DOCK_ROW] for the dock, the grid row of a grid tile or the row after the last for the
 * library pill, and [DOCK_ROW] while focus is outside the grid. [low] is 1 while the band and the dock sit [lowering]
 * pixels lower (focus in the dock or on the top bar) and 0 in the grid layout; the grid's items, the band and the pill draw that far down. Read them in layer lambdas only.
 */
@Stable
private class FocusDim(
    private val lowering: State<Float>,
    private val dockBand: State<Rect>,
    private val heroBand: State<Rect>,
) {
    /** The dock panel in dock focus, as fractions of the window: where the frost's lightness is read. */
    val band: Rect get() = dockBand.value

    /** The hero block's box with the group low, as fractions of the window: where its scrim's brightness is read. */
    val hero: Rect get() = heroBand.value

    val row = Animatable(DOCK_ROW.toFloat())
    val low = Animatable(1f)

    /** The first grid row that [hiddenFromRow] finds cut by the bottom edge, animated; rows from it down draw at alpha 0. */
    val fold = Animatable(Float.MAX_VALUE)

    /** The first grid row that clears the title block, animated; rows before it draw at alpha 0. */
    val head = Animatable(0f)

    /** The alpha every grid row draws at: [LAST_ROW_PILL_ALPHA] with focus on the library pill, else 1. */
    val pill = Animatable(1f)

    /** The dock's alpha: [POPUP_DOCK_ALPHA] while a top-bar popup is open, [TOP_BAR_DOCK_ALPHA] while focus is in the top bar of a dock with no panel, else 1. */
    val bar = Animatable(1f)

    /** The alpha of the black layer over the dock panel while focus is in the top bar: [TOP_BAR_DOCK_SHADE], else 0. */
    val shade = Animatable(0f)

    /** The dock's alpha: 0 while focus is on a grid row or the library pill, 1 with it in the dock or outside the grid. */
    fun dockAlpha(): Float = 1f - (row.value - DOCK_ROW).coerceIn(0f, 1f)

    /** The alpha of grid [row] for the title block: 0 before [head], 1 from it down, times [pill]. */
    fun gridAlphaOf(row: Int): Float = (row - head.value + 1f).coerceIn(0f, 1f) * pill.value

    /** The alpha of grid [row] for the bottom edge: 1 above [fold], 0 from it down. */
    fun fitsAbove(row: Int): Float = (fold.value - row).coerceIn(0f, 1f)

    /** How far down the group is drawn now, in pixels. */
    fun lowPx(): Float = low.value * lowering.value

    /**
     * How far down the hero block is drawn now: with the group, and [drop] more while it is up. With nothing to lower
     * the block never moves with the group, so it takes all of [drop].
     */
    fun titleShiftPx(drop: Float): Float = lowPx() + if (lowering.value == 0f) drop else drop * (1f - low.value)
}

/** How long the group takes to move, and the damping of its spring when focus_overshoot is on. */
private const val GROUP_MS = 320
private const val GROUP_OVERSHOOT_DAMPING = 0.7f

/**
 * The dock panel's padding in this window, from the width of the dock row's tiles, which sets the room a focused tile's
 * ring needs: at the tile's peak size, past its grown size with focus_overshoot.
 */
@Composable
private fun rememberDockPadding(
    state: State<() -> HomeUiState>,
    layout: HomeLayout,
    look: DockLook,
): DockPadding {
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val shrunk by remember(state, layout, look) { derivedStateOf { look.shrinks(layout, state.value().dock.size) } }
    val scale = TvTheme.motion.focusScale(TILE_FOCUSED_SCALE)
    val peak = TvTheme.motion.focusPeakScale(TILE_FOCUSED_SCALE)
    val tileWidth = dockRowTileWidth(with(density) { windowWidth.toDp() } - ScreenMargin * 2, layout.columns)
    val tileHeight = tileWidth.value / TILE_ASPECT
    val pivot = if (layout.showNames) namePivot(tileHeight, scale) else tileHeight / 2f
    return dockPadding(density.dockRingSide(tileWidth, peak), shrunk, look.evenPadding, roomOver(pivot, scale, peak).dp)
}

@Composable
private fun rememberFocusDim(
    state: State<() -> HomeUiState>,
    shelfBottom: Dp,
    layout: HomeLayout,
    dockPadding: DockPadding,
): FocusDim {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val gridCount by remember(state) { derivedStateOf { state.value().grid.size } }
    val scale = TvTheme.motion.focusScale(TILE_FOCUSED_SCALE)
    val peak = TvTheme.motion.focusPeakScale(TILE_FOCUSED_SCALE)
    val (lowering, band, hero) =
        with(density) {
            val width = window.width.toDp()
            val height = window.height.toDp()
            val gridWidth = width - ScreenMargin * 2
            val share = gridShare(layout, gridWidth, height, shelfBottom, dockPadding.vertical)
            val tileWidth = (gridWidth * share - TileGap * (layout.columns - 1)) / layout.columns
            val tileHeight = tileWidth / TILE_ASPECT
            val gridRows = if (layout.singleRow) 0 else (gridCount + layout.columns - 1) / layout.columns
            val lowered =
                if (layout.singleRow) {
                    0.dp
                } else {
                    dockLowering(
                        windowHeight = height.value,
                        shelfBottom = shelfBottom.value,
                        tileHeight = tileHeight.value,
                        gap = TileGap.value,
                        dockPadding = dockPadding.vertical.value,
                        gridRows = gridRows,
                        rowsBelowDock = layout.rowsBelowDock,
                        bottomMargin = GridBottom.value,
                        nameRoom = layout.nameRoom(tileHeight.value, scale, peak),
                    ).dp
                }
            val start = gridStart(gridWidth, share)
            val edge = (start - TileGap) / width
            val end = (start + gridWidth * share + TileGap) / width
            val top = shelfBottom + lowered
            val heroTop = ContentTop + lowered
            Triple(
                lowered.toPx(),
                Rect(
                    edge,
                    top / height,
                    end,
                    (top + tileHeight + dockPadding.vertical * 2 + layout.nameRoom(tileHeight.value, scale, peak).dp) / height,
                ),
                Rect(
                    ScreenMargin / width,
                    heroTop / height,
                    (ScreenMargin + HeroSampleWidth) / width,
                    (shelfBottom - TitleToShelfBottom + lowered) / height,
                ),
            )
        }
    val currentLowering = rememberUpdatedState(lowering)
    val currentBand = rememberUpdatedState(band)
    val currentHero = rememberUpdatedState(hero)
    return remember { FocusDim(currentLowering, currentBand, currentHero) }
}

/**
 * The share of the grid's width its tiles use, so that [HomeLayout.rowsBelowDock] rows fit under a dock whose panel starts at
 * [shelfBottom] and pads its tiles [dockPadding] above and below. The names under the tiles do not change it.
 */
internal fun gridShare(
    layout: HomeLayout,
    gridWidth: Dp,
    windowHeight: Dp,
    shelfBottom: Dp,
    dockPadding: Dp = TileGap,
): Float {
    if (layout.singleRow) return 1f
    return tileWidthShare(
        gridWidth.value,
        (windowHeight - shelfBottom - GridBottom).value,
        layout.columns,
        layout.rowsBelowDock,
        TileGap.value,
        dockPadding.value,
    )
}

/** Where the dock and the grid start: centred between the margins when [share] shrinks them. */
private fun gridStart(
    gridWidth: Dp,
    share: Float,
): Dp = ScreenMargin + gridWidth * ((1f - share) / 2f)

/** The room the name of a tile [tileHeight] dp high takes under it, in dp; 0 with the names off. */
private fun HomeLayout.nameRoom(
    tileHeight: Float,
    scale: Float,
    peak: Float,
): Float =
    if (showNames) {
        nameGap(tileHeight, scale, peak) + (NameLine + NamePillDown * 2).value + nameBelow(tileHeight, scale, peak)
    } else {
        0f
    }

/** The row focus is on, as [FocusDim.row] counts it. */
private fun focusRowOf(
    state: HomeUiState,
    input: HomeInput,
    layout: HomeLayout,
): Int {
    val inGrid = state.grid.indexOfFirst { it.key == state.focusedKey }
    return when {
        !input.gridFocused || layout.singleRow -> DOCK_ROW
        input.pillFocused -> (state.grid.size + layout.columns - 1) / layout.columns
        inGrid >= 0 -> inGrid / layout.columns
        else -> DOCK_ROW
    }
}

/**
 * Whether [dim]'s group goes low for the focus now: with focus on a dock tile or a top-bar button or while a dock tile's
 * menu is open, and up with focus on a grid tile, the library pill or a grid tile's menu. Null while focus is in the
 * Inputs list or the Settings menu, or nowhere for a moment, so the group stays where it is.
 */
private fun groupLowFor(
    state: HomeUiState,
    input: HomeInput,
    layout: HomeLayout,
): Boolean? =
    when {
        state.menu?.tileKey != null -> state.dock.any { it.key == state.menu.tileKey }
        input.topBarFocused -> true
        input.pillFocused -> false
        input.gridFocused -> focusRowOf(state, input, layout) == DOCK_ROW
        else -> null
    }

/**
 * Runs [dim]: its row follows focus in [ROW_DIM_MS], and its group goes low or up as [groupLowFor] says, in
 * [GROUP_MS], at once without motion. It stays where it is while the App library is open. Its [FocusDim.fold] follows the
 * [grid]'s bottom edge in [ROW_DIM_MS], and its [FocusDim.bar] a top-bar popup in [BAND_IN_MS] and [BAND_OUT_MS], or focus in the top bar
 * in [TOP_BAR_DOCK_MS].
 */
@Composable
private fun FocusDimEffects(
    dim: FocusDim,
    state: State<() -> HomeUiState>,
    input: HomeInput,
    layout: HomeLayout,
    libraryOpen: () -> Boolean,
    grid: LazyGridState,
    cutoff: Dp,
    dockPanel: Boolean,
) {
    val motion = TvTheme.motion
    val currentLibraryOpen = rememberUpdatedState(libraryOpen)
    val density = LocalDensity.current
    val windowHeight =
        LocalWindowInfo.current.containerSize.height
            .toFloat()
    val solidTop = with(density) { cutoff.toPx() } + settleOf(windowHeight)
    val minVisible = with(density) { GridRowMinVisible.toPx() }
    LaunchedEffect(state, input, layout, motion) {
        snapshotFlow { focusRowOf(state.value(), input, layout).toFloat() }
            .collectLatest { dim.row.animateTo(it, motion.tweenSpec(ROW_DIM_MS)) }
    }
    LaunchedEffect(input, motion) {
        snapshotFlow { if (input.pillFocused) LAST_ROW_PILL_ALPHA else 1f }
            .collectLatest { dim.pill.animateTo(it, motion.tweenSpec(ROW_DIM_MS)) }
    }
    LaunchedEffect(grid, layout, dim, motion, solidTop) {
        var first = true
        snapshotFlow { dim.rowsBehindTitle(grid.layoutInfo, layout.columns, solidTop).toFloat() }.collectLatest { target ->
            if (first) dim.head.snapTo(target) else dim.head.animateTo(target, motion.tweenSpec(ROW_DIM_MS))
            first = false
        }
    }
    LaunchedEffect(state, input, layout, motion) {
        var low = true
        snapshotFlow {
            val shown = state.value()
            if (!currentLibraryOpen.value()) groupLowFor(shown, input, layout)?.let { low = it }
            low
        }.collectLatest { toLow ->
            val target = if (toLow) 1f else 0f
            when {
                !motion.animated -> dim.low.snapTo(target)
                motion.focusOvershoot -> dim.low.animateTo(target, spring(GROUP_OVERSHOOT_DAMPING, Spring.StiffnessMediumLow))
                else -> dim.low.animateTo(target, tween(GROUP_MS, easing = FastOutSlowInEasing))
            }
        }
    }
    LaunchedEffect(state, grid, layout, dim, motion, minVisible) {
        var first = true
        snapshotFlow {
            val rows = (state.value().grid.size + layout.columns - 1) / layout.columns
            minOf(dim.hiddenFromRow(grid.layoutInfo, layout.columns, minVisible, layout.showNames), rows).toFloat()
        }.collectLatest { target ->
            if (first) dim.fold.snapTo(target) else dim.fold.animateTo(target, motion.tweenSpec(ROW_DIM_MS))
            first = false
        }
    }
    LaunchedEffect(state, input, dim, motion, dockPanel) {
        var popup = false
        snapshotFlow {
            val popupOpen = state.value().let { it.inputsOpen || (it.menu != null && it.menu.tileKey == null) }
            when {
                popupOpen -> DockDim(alpha = POPUP_DOCK_ALPHA)
                input.topBarFocused -> if (dockPanel) DockDim(shade = TOP_BAR_DOCK_SHADE) else DockDim(alpha = TOP_BAR_DOCK_ALPHA)
                else -> DockDim()
            }
        }.collectLatest { dock ->
            val millis =
                when {
                    dock.alpha == POPUP_DOCK_ALPHA -> BAND_IN_MS
                    popup -> BAND_OUT_MS
                    else -> TOP_BAR_DOCK_MS
                }
            popup = dock.alpha == POPUP_DOCK_ALPHA
            coroutineScope {
                launch { dim.bar.animateTo(dock.alpha, motion.tweenSpec(millis)) }
                launch { dim.shade.animateTo(dock.shade, motion.tweenSpec(millis)) }
            }
        }
    }
}

/** Shows [edge] while focus is on a dock tile, and hides it for anything else: the top bar, a menu, the grid, the library. */
@Composable
private fun DockEdgeEffect(
    edge: DockEdgeLight,
    state: State<() -> HomeUiState>,
    input: HomeInput,
    layout: HomeLayout,
    libraryOpen: () -> Boolean,
) {
    val currentLibraryOpen = rememberUpdatedState(libraryOpen)
    LaunchedEffect(edge, state, input, layout) {
        snapshotFlow {
            val shown = state.value()
            input.gridFocused &&
                !input.pillFocused &&
                shown.menu == null &&
                !shown.inputsOpen &&
                !currentLibraryOpen.value() &&
                focusRowOf(shown, input, layout) == DOCK_ROW
        }.collectLatest { edge.show(it) }
    }
}

private const val ROW_DIM_MS = 160

/** The dock's alpha under a top-bar popup, so that the popup is what looks active. */
private const val POPUP_DOCK_ALPHA = 0.55f

/**
 * How the dock dims while focus is in the top bar, and how long it takes to get there and back: a black layer over its panel,
 * which darkens a light tray on any picture, or without a panel its alpha.
 */
internal const val TOP_BAR_DOCK_SHADE = 0.25f
private const val TOP_BAR_DOCK_ALPHA = 0.75f
private const val TOP_BAR_DOCK_MS = 150

/** The dock's alpha and the alpha of the black layer over its panel that one cause of dimming asks for. */
private data class DockDim(
    val alpha: Float = 1f,
    val shade: Float = 0f,
)

/**
 * The first grid row whose tile shows under [minVisible] pixels (or under its whole height, if that is less) above the end of the
 * bottom fade ([bottomFade]), where nothing is drawn, or [Int.MAX_VALUE].
 */
private fun FocusDim.hiddenFromRow(
    info: LazyGridLayoutInfo,
    columns: Int,
    minVisible: Float,
    names: Boolean,
): Int {
    val shift = lowPx() - info.viewportStartOffset
    val end = bottomFade(info, shift, info.viewportSize.height.toFloat(), minVisible, names).end
    var first = Int.MAX_VALUE
    for (i in info.visibleItemsInfo.indices) {
        val item = info.visibleItemsInfo[i]
        val index = item.index - GRID_ITEMS_BEFORE
        if (index < 0 || item.key == LIBRARY_FOCUS) continue
        if (!item.drawsAbove(end, item.offset.y + shift, minVisible)) first = minOf(first, index / columns)
    }
    return first
}

/**
 * How many grid rows, counting from the first, start above [solidTop] (pixels from the top): they sit behind the title
 * block or are scrolling under it, and a row that starts under it, above the focused one or not, shows.
 */
private fun FocusDim.rowsBehindTitle(
    info: LazyGridLayoutInfo,
    columns: Int,
    solidTop: Float,
): Int {
    val shift = lowPx() - info.viewportStartOffset
    var rows = 0
    for (i in info.visibleItemsInfo.indices) {
        val item = info.visibleItemsInfo[i]
        val index = item.index - GRID_ITEMS_BEFORE
        if (index < 0 || item.key == LIBRARY_FOCUS) continue
        if (item.offset.y + shift < solidTop) rows = maxOf(rows, index / columns + 1)
    }
    return rows
}

/** Draws the item of grid [row] at the alpha [dim] gives it, as far down as [dim]'s group is. */
private fun Modifier.gridRowLayer(
    row: Int,
    dim: FocusDim,
): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = dim.gridAlphaOf(row) * dim.fitsAbove(row)
        translationY = dim.lowPx()
    }

/**
 * The dock's layer: hidden by [FocusDim.dockAlpha] with focus in the grid, drawn as far down as [dim]'s group is, and faded
 * out over the [DockFadeOut] before its bottom edge scrolls up to [shelfBottomPx], so a dock scrolled up for the grid leaves nothing in
 * the grid's top fade.
 */
private fun Modifier.dockLayer(
    dim: FocusDim,
    grid: LazyGridState,
    shelfBottomPx: Float,
): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        val info = grid.layoutInfo
        val dock = info.visibleItemsInfo.firstOrNull { it.key == "dock" }
        val shown =
            if (dock == null) {
                0f
            } else {
                val bottom = dock.offset.y - info.viewportStartOffset + dock.size.height + dim.lowPx()
                ((bottom - shelfBottomPx) / DockFadeOut.toPx()).coerceIn(0f, 1f)
            }
        alpha = dim.dockAlpha() * shown * dim.bar.value
        translationY = dim.lowPx()
    }

/** How Home shows: whole, or gone under the App library, whose panel would cut through it. */
private enum class BandLook(
    val title: Float,
    val detail: Float,
    val layers: Float,
) {
    Whole(1f, 1f, 1f),
    NoHero(0f, 0f, 1f),
    Gone(0f, 0f, 0f),
}

/**
 * Home's fades: the band's title, the meta line, the page dots and the key chips under it, and [layers], every layer of Home
 * above the wallpaper. Read them in layer lambdas only.
 */
@Stable
private class BandFade {
    val title = Animatable(1f)
    val detail = Animatable(1f)
    val layers = Animatable(1f)
}

/**
 * The fades, following the App library: out with its scrim in [BAND_IN_MS], back in [BAND_OUT_MS]. While a tile menu is
 * [tileMenuOpen] the hero block (title, subtitle and dots) is out and the layers stay, in [HERO_MENU_MS] both ways.
 */
@Composable
private fun rememberBandFade(
    libraryOpen: () -> Boolean,
    tileMenuOpen: () -> Boolean,
): BandFade {
    val motion = TvTheme.motion
    val band = remember { BandFade() }
    val currentLibraryOpen = rememberUpdatedState(libraryOpen)
    val currentTileMenuOpen = rememberUpdatedState(tileMenuOpen)
    LaunchedEffect(motion) {
        var shown = BandLook.Whole
        snapshotFlow {
            when {
                currentLibraryOpen.value() -> BandLook.Gone
                currentTileMenuOpen.value() -> BandLook.NoHero
                else -> BandLook.Whole
            }
        }.collectLatest { look ->
            val millis =
                when {
                    look == BandLook.NoHero || shown == BandLook.NoHero -> HERO_MENU_MS
                    shown == BandLook.Gone && look != BandLook.Gone -> BAND_OUT_MS
                    else -> BAND_IN_MS
                }
            val spec = motion.tweenSpec<Float>(millis)
            shown = look
            coroutineScope {
                launch { band.title.animateTo(look.title, spec) }
                launch { band.layers.animateTo(look.layers, spec) }
                band.detail.animateTo(look.detail, spec)
            }
        }
    }
    return band
}

private fun HeroSource.quick(): Boolean = this is HeroSource.Bar || this == HeroSource.Library

/**
 * The hero scrim's fade, 0 or 1 once settled: it follows [wanted] in the time the band's line takes to swap
 * ([LIBRARY_SWAP_MS] when either line is a chrome control's, else [CINEMATIC_MS]), and jumps with animations off or reduced
 * motion, as the line does. Read it in layer lambdas only.
 */
@Composable
internal fun rememberHeroScrimFade(
    source: () -> HeroSource,
    wanted: () -> Boolean,
): State<Float> {
    val motion = TvTheme.motion
    val fade = remember { Animatable(if (wanted()) 1f else 0f) }
    val currentSource = rememberUpdatedState(source)
    val currentWanted = rememberUpdatedState(wanted)
    LaunchedEffect(motion) {
        var shown = currentSource.value()
        snapshotFlow { currentSource.value() to currentWanted.value() }.collectLatest { (now, on) ->
            val target = if (on) 1f else 0f
            val millis = if (shown.quick() || now.quick()) LIBRARY_SWAP_MS else CINEMATIC_MS
            shown = now
            if (motion.animated) fade.animateTo(target, motion.tweenSpec(millis)) else fade.snapTo(target)
        }
    }
    return fade.asState()
}

/** Which line the band over the grid shows, in order of precedence; [Quiet] is the empty line of dock_title false. */
internal sealed interface HeroSource {
    data class Mode(
        val hint: ReorderHint,
    ) : HeroSource

    data object Library : HeroSource

    data class Bar(
        val control: BarControl,
    ) : HeroSource

    data object Tile : HeroSource

    data object Quiet : HeroSource
}

/** The band's line: reorder or dock-edit mode, then the library pill, then a top-bar control, then the focused tile when [dockTitle] allows it. */
internal fun heroSource(
    dockTitle: Boolean,
    hint: ReorderHint?,
    pillFocused: Boolean,
    control: BarControl?,
): HeroSource =
    when {
        hint != null -> HeroSource.Mode(hint)
        pillFocused -> HeroSource.Library
        control != null -> HeroSource.Bar(control)
        dockTitle -> HeroSource.Tile
        else -> HeroSource.Quiet
    }

/**
 * What the band over the grid says when the cinematic shelf does not. A [meta] of a space keeps the line's place;
 * [opened] puts when Home last opened the tile there instead, [never] says Home never opened it, and [usual] adds that the tile is
 * the app of this time of day. [quick] swaps in [LIBRARY_SWAP_MS].
 */
@Immutable
private data class HeroLine(
    val title: String,
    val meta: String = " ",
    val opened: OpenedAt? = null,
    val never: Boolean = false,
    val usual: Boolean = false,
    val hint: ReorderHint? = null,
    /** The dock place the app to choose goes to, named in the hint. */
    val slot: DockSlot? = null,
    val quick: Boolean = false,
    /** The title names a chrome control (a top-bar button or the library pill), which gets a smaller title than an app. */
    val chrome: Boolean = false,
)

/**
 * The band over the grid: the focused tile's name and when Home last opened it; on the library pill the library's
 * name and size; in reorder mode the name of the tile that moves, in edit-dock mode the mode's name, and the keys. A new
 * line cross-fades with the old one as the cinematic shelf's does ([TitleSlots]). The meta line keeps its slot when empty, so the title stands where the
 * [cinematic] shelf's does. It draws at the [band]'s fades.
 */
@Composable
private fun HeroTitle(
    state: () -> HomeUiState,
    input: HomeInput,
    source: () -> HeroSource,
    cinematic: Boolean,
    band: BandFade,
    modifier: Modifier = Modifier,
    pill: () -> Boolean = { false },
    extent: TitleExtent = TitleExtent(),
) {
    val count by remember(state) { derivedStateOf { state().libraryCount } }
    val inputs by remember(state) {
        derivedStateOf {
            state().let { home ->
                home.inputOffer?.let { InputsSummary.One(it.label) } ?: home.inputs.rows.connectedSummary()
            }
        }
    }
    val link by remember(state) { derivedStateOf { state().network.link } }
    val launcherSettings by remember(state) { derivedStateOf { state().config.launcherSettingsButton } }
    val focusedLabel by remember(state) { derivedStateOf { state().focusedLabel } }
    val dockSlot by remember(state) { derivedStateOf { state().reorder?.slot } }
    val tile by remember(state) {
        derivedStateOf {
            val home = state()
            if (cinematic &&
                home.shelf != null
            ) {
                HeroLine("")
            } else {
                HeroLine(home.focusedLabel, opened = home.focusedOpened, never = home.focusedNeverOpened, usual = home.focusedUsual)
            }
        }
    }
    val line =
        when (val chosen = source()) {
            is HeroSource.Mode -> {
                HeroLine(
                    if (chosen.hint == ReorderHint.Reorder) focusedLabel else stringResource(R.string.dock_edit),
                    hint = chosen.hint,
                    slot = dockSlot,
                )
            }

            HeroSource.Library -> {
                HeroLine(stringResource(R.string.library_entry), libraryCountText(count), quick = true, chrome = true)
            }

            is HeroSource.Bar -> {
                barLine(chosen.control, inputs, link, launcherSettings).copy(chrome = true)
            }

            HeroSource.Tile -> {
                tile
            }

            HeroSource.Quiet -> {
                HeroLine("")
            }
        }
    val animated = TvTheme.motion.animated
    val rise = with(LocalDensity.current) { TitleRise.roundToPx() }
    val titleStyle = TvTheme.typography.heroTitle.withTitleShadow(pill())
    val chromeTitleStyle = TvTheme.typography.chromeTitle.withTitleShadow(pill())
    val metaStyle = TvTheme.typography.cinematicMeta.withSubtitleShadow()
    val detail = (if (cinematic) ShelfDetailHeight else MetaGap + ChipHeight) - MetaGap
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        TitleSlots(
            line,
            animated,
            rise,
            // A new time for the same tile, as the clock moves on, replaces the old one without a cross-fade.
            key = { it.copy(opened = null, never = false, usual = false) },
            inMillis = { from, to -> if (from.quick || to.quick) LIBRARY_SWAP_MS else CINEMATIC_MS },
            extent = extent,
        ) { shown, live ->
            Column {
                BasicText(
                    text = if (live) shown.title else "",
                    style = if (shown.chrome) chromeTitleStyle else titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .testTag(if (live) "shelf:title" else "")
                            .lineBetween(HeroAscent, TitleDescent)
                            .drawnAt { band.title.value },
                )
                Box(Modifier.padding(start = MetaIndent, top = MetaGap).height(detail).drawnAt { band.detail.value }) {
                    when (val keys = shown.hint?.takeIf { live }) {
                        ReorderHint.Reorder -> {
                            KeyChips(input.carried)
                        }

                        null -> {
                            val opened = shown.opened
                            val meta =
                                when {
                                    !live -> AnnotatedString("")
                                    opened != null || shown.usual || shown.never -> lastOpenedLine(opened, shown.usual, shown.never)
                                    else -> AnnotatedString(shown.meta)
                                }
                            BasicText(
                                meta,
                                style = metaStyle,
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .testTag(if (live) "shelf:title-meta" else "")
                                        .height(MetaSlot)
                                        .subtitlePill { pill() && meta.isNotBlank() },
                            )
                        }

                        ReorderHint.DockChooseApp -> {
                            SlotChips(shown.slot, metaStyle, pill)
                        }

                        ReorderHint.DockChoosePlace, ReorderHint.DockPlaceForApp -> {
                            val text = if (keys == ReorderHint.DockChoosePlace) R.string.dock_edit_hint else R.string.dock_edit_pick_place
                            BasicText(
                                stringResource(text),
                                style = metaStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("reorder:hint").subtitlePill(pill),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What the band says on the top bar: the name of the focused [control] and one line on its state; a button that has none shows its name. */
@Composable
private fun barLine(
    control: BarControl,
    inputs: InputsSummary,
    link: NetworkLink,
    launcherSettings: Boolean,
): HeroLine =
    when (control) {
        BarControl.Inputs -> {
            val meta =
                when (inputs) {
                    InputsSummary.None -> stringResource(R.string.inputs_none)
                    is InputsSummary.One -> "${inputs.label} \u00B7 ${stringResource(R.string.input_connected)}"
                    is InputsSummary.Many -> pluralStringResource(R.plurals.inputs_connected, inputs.count, inputs.count)
                }
            HeroLine(stringResource(R.string.inputs), meta, quick = true)
        }

        BarControl.Network -> {
            HeroLine(
                stringResource(if (link == NetworkLink.Ethernet) R.string.ethernet else R.string.wifi),
                stringResource(if (link == NetworkLink.Offline) R.string.offline else R.string.network_connected),
                quick = true,
            )
        }

        BarControl.Settings -> {
            if (launcherSettings) {
                HeroLine(stringResource(R.string.launcher_settings), stringResource(R.string.launcher_settings_summary), quick = true)
            } else {
                HeroLine(stringResource(R.string.settings), stringResource(R.string.settings_summary), quick = true)
            }
        }

        is BarControl.Named -> {
            HeroLine(control.label, quick = true)
        }
    }

@Composable
private fun libraryCountText(count: Int?): String = count?.let { pluralStringResource(R.plurals.library_app_count, it, it) } ?: " "

/**
 * The keys of reorder mode that do something now, one chip per action. With nothing [carried], OK picks a tile up and Back
 * finishes the mode. With one carried, the arrows move it and OK and Back, on one chip, put it down.
 */
@Composable
private fun KeyChips(carried: State<String?>) {
    val carrying by remember(carried) { derivedStateOf { carried.value != null } }
    Row(Modifier.testTag("reorder:hint"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (carrying) {
            KeyChip(
                stringResource(R.string.reorder_key_arrows),
                stringResource(R.string.reorder_move),
                Modifier.testTag("reorder:chip:arrows"),
            )
            KeyChip(
                listOf(stringResource(R.string.reorder_key_ok), stringResource(R.string.reorder_key_back)),
                stringResource(R.string.reorder_drop),
                Modifier.testTag("reorder:chip:drop"),
            )
        } else {
            KeyChip(stringResource(R.string.reorder_key_ok), stringResource(R.string.reorder_pick_up), Modifier.testTag("reorder:chip:ok"))
            KeyChip(
                stringResource(R.string.reorder_key_back),
                stringResource(R.string.reorder_finish),
                Modifier.testTag("reorder:chip:back"),
            )
        }
    }
}

/**
 * The app choice step of edit-dock mode: which dock place of how many, and the app in it now, when its name is known; then
 * the keys, OK to put the focused app there and Back to choose another place.
 */
@Composable
private fun SlotChips(
    slot: DockSlot?,
    style: TextStyle,
    pill: () -> Boolean,
) {
    Row(
        Modifier.testTag("reorder:hint"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (slot != null) {
            BasicText(
                stringResource(R.string.dock_edit_slot, slot.number, slot.count, slot.app),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag("reorder:slot").subtitlePill(pill),
            )
        }
        KeyChip(stringResource(R.string.reorder_key_ok), stringResource(R.string.dock_edit_put_here), Modifier.testTag("reorder:chip:put"))
        KeyChip(
            stringResource(R.string.reorder_key_back),
            stringResource(R.string.dock_edit_other_place),
            Modifier.testTag("reorder:chip:other"),
        )
    }
}

/** Measures the content [by] wider on each side than the room it gets, and centres it over that room. */
private fun Modifier.outset(by: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = by.roundToPx() * 2
        val width = constraints.maxWidth + extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    glass: GlassState,
    focus: FocusRequester,
    firstTile: FocusRequester,
    row: LazyListState,
    input: HomeInput,
    dock: List<AppEntry>,
    grid: List<AppEntry>,
    reordering: Boolean,
    layout: HomeLayout,
    shelfLayout: ShelfLayout,
    look: DockLook,
    padding: DockPadding,
    edge: DockEdgeLight?,
    shade: () -> Float,
    rowTileWidth: Dp,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One gap wider on each side than the grid. The padding inside leaves a focused end tile's ring DockRingWallClear from
    // the wall, so the tiles are a little narrower than the grid's columns. With fewer tiles than the row holds the panel
    // is as wide as they are, centred.
    val shrunk = look.shrinks(layout, dock.size)
    val width = if (shrunk) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()
    DockPanel(glass, look, edge, shade, modifier.outset(TileGap).then(width).testTag("dock")) {
        if (layout.singleRow) {
            // The grid's pivot scroll is vertical; this row scrolls sideways the usual way.
            CompositionLocalProvider(LocalBringIntoViewSpec provides RevealBringIntoViewSpec) {
                LazyRow(
                    state = row,
                    modifier =
                        Modifier
                            .focusRequester(focus)
                            .focusRestorer()
                            .upToOffer(input, layout)
                            .clipsHalo(),
                    contentPadding = PaddingValues(horizontal = padding.side, vertical = padding.vertical),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    itemsIndexed(dock, key = { _, app -> app.key }) { index, app ->
                        val toward =
                            remember(input, layout, grid, index, dock.lastIndex) {
                                Modifier.towardLibrary(input, layout, grid, last = index == dock.lastIndex, row = -1, column = index)
                            }
                        AppTile(
                            app = app,
                            accent = shelfLayout.focusAccent(app.accent, inDock = true),
                            ring = shelfLayout.ringColour(app.accent, inDock = true, edge = app.edge),
                            onFocus = onFocus,
                            onClick = onClick,
                            input = input,
                            reordering = reordering,
                            tileMenu = layout.tileMenu,
                            showName = layout.showNames,
                            edge = edge,
                            glassEdge = look.band && look.glass,
                            modifier =
                                Modifier
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = TvTheme.motion.placementSpec)
                                    .width(rowTileWidth)
                                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                                    .then(toward),
                        )
                    }
                    if (layout.libraryPill) {
                        item(key = LIBRARY_FOCUS, contentType = "library") {
                            LibraryPill(
                                glass,
                                input,
                                onOpenLibrary,
                                Modifier.width(rowTileWidth).aspectRatio(TILE_ASPECT),
                                inRow = true,
                            )
                        }
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
                        .upToOffer(input, layout)
                        .padding(horizontal = padding.side, vertical = padding.vertical),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                dock.forEachIndexed { index, app ->
                    key(app.key) {
                        val ends =
                            remember(input, layout, grid, index, dock.lastIndex) {
                                Modifier.stopAtDockEnds(
                                    first = index == 0,
                                    last = index == dock.lastIndex,
                                    input,
                                    layout,
                                    grid,
                                    column = index,
                                )
                            }
                        AppTile(
                            app = app,
                            accent = shelfLayout.focusAccent(app.accent, inDock = true),
                            ring = shelfLayout.ringColour(app.accent, inDock = true, edge = app.edge),
                            onFocus = onFocus,
                            onClick = onClick,
                            input = input,
                            reordering = reordering,
                            tileMenu = layout.tileMenu,
                            showName = layout.showNames,
                            edge = edge,
                            glassEdge = look.band && look.glass,
                            modifier =
                                (if (shrunk) Modifier.width(rowTileWidth) else Modifier.weight(1f))
                                    .slideToIndex(index, TileGap)
                                    .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                                    .then(ends),
                        )
                    }
                }
                // Slots with no app stay empty, so the tiles keep the width of a column.
                if (!shrunk) repeat(layout.columns - dock.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** With input_arrival, Up from a dock tile goes to the Inputs button while it offers a device. Read when focus moves. */
private fun Modifier.upToOffer(
    input: HomeInput,
    layout: HomeLayout,
): Modifier =
    if (!layout.inputArrival) {
        this
    } else {
        focusProperties { if (input.inputOffered.value) up = input.requester(INPUTS_FOCUS) }
    }

/** The dock's panel, under a black layer of alpha [shade], or with the theme's dock_band off nothing but the room it takes. */
@Composable
private fun DockPanel(
    glass: GlassState,
    look: DockLook,
    edge: DockEdgeLight?,
    shade: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (look.band) {
        val dark = TvTheme.surfaces.dark
        val tint = remember(look, dark, glass) { { look.tint(dark, glass.dockLightness) } }
        val gloss = remember(look, dark, glass) { { look.gloss(dark, glass.dockLightness) } }
        DockSurface(
            glass,
            tint,
            DockShape,
            modifier,
            edge,
            outerHairline = look.light(dark),
            gloss = gloss,
            shade = shade,
            content = content,
        )
    } else {
        Box(modifier, content = content)
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
    val rightTo = if (last && !layout.singleRow && (row >= 0 || grid.isEmpty())) input.requester(LIBRARY_FOCUS) else null
    val downTo = libraryDown(input, layout, grid, row, column)
    if (rightTo == null && downTo == null) return this
    return focusProperties {
        if (rightTo != null) right = rightTo
        if (downTo != null) down = downTo
    }
}

/** Where a dock tile was laid out and whether it has focus, for the edge light that follows focus to it. */
private class Placed {
    var tile: LayoutCoordinates? = null
    var focused = false
}

private fun LayoutCoordinates.centreX(): Float = localToRoot(Offset(size.width / 2f, size.height / 2f)).x

@Composable
private fun AppTile(
    app: AppEntry,
    accent: Color,
    ring: Color,
    onFocus: (String) -> Unit,
    onClick: (String) -> Unit,
    input: HomeInput,
    reordering: Boolean,
    tileMenu: Boolean,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
    habitMark: Boolean = true,
    fadeUnused: Boolean = false,
    edge: DockEdgeLight? = null,
    glassEdge: Boolean = false,
    bannerName: Boolean = true,
    growthPivot: ((Float) -> Float)? = null,
    focus: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
) {
    val marked = (if (habitMark) modifier.habitMark(input.habit, app.packageName) else modifier).popsOver(focus)
    if (showName) {
        val scale = TvTheme.motion.focusScale(TILE_FOCUSED_SCALE)
        val peak = TvTheme.motion.focusPeakScale(TILE_FOCUSED_SCALE)
        val density = LocalDensity.current
        val pivot: (
            Float,
        ) -> Float = remember(density, scale) { { height -> with(density) { namePivot(height.toDp().value, scale).dp.toPx() } } }
        NameUnder(app, scale, peak, marked) {
            AppTile(
                app,
                accent,
                ring,
                onFocus,
                onClick,
                input,
                reordering,
                tileMenu,
                Modifier.fillMaxWidth(),
                habitMark = false,
                fadeUnused = fadeUnused,
                edge = edge,
                glassEdge = glassEdge,
                bannerName = false,
                growthPivot = pivot,
                focus = focus,
            )
        }
        return
    }
    val key = app.key
    val requester = remember(input, key) { input.requester(key) }
    val travel = input.travel
    val motion = TvTheme.motion
    val scope = rememberCoroutineScope()
    val placed = remember { Placed() }
    val currentRing by rememberUpdatedState(ring)
    val ringBlends = remember(ring, app.accent) { ringBlendsWith(ring, app.accent) }
    val ringColour =
        remember(input, key, ring) {
            ColorProducer { if (input.carried.value == key) ReorderRing else travel.colourFor(key, ring) }
        }
    FocusSurface(
        onClick = { onClick(key) },
        onFocused = {
            onFocus(key)
            travel.arrive(key, ring, motion, scope)
            val laidOut = placed.tile?.takeIf { it.isAttached }
            if (edge != null && laidOut != null) {
                edge.focus(laidOut.centreX()) { travel.colourFor(key, currentRing) }
            }
        },
        shape = TileShape,
        accent = accent,
        ring = ringColour,
        ringBlends = ringBlends,
        growthPivot = growthPivot,
        focus = focus,
        modifier =
            (if (fadeUnused) marked.unusedFade(input.faded, key) else marked)
                .then(if (reordering) Modifier.reorderLook(key, input, TileShape, growthPivot) else Modifier)
                .aspectRatio(TILE_ASPECT)
                .then(
                    if (edge != null) {
                        Modifier
                            .onGloballyPositioned {
                                placed.tile = it
                                if (placed.focused) edge.follow(it.centreX())
                            }.onFocusChanged { placed.focused = it.isFocused }
                    } else {
                        Modifier
                    },
                ).testTag("tile:$key")
                .then(if (app.image != null) Modifier.semantics { contentDescription = app.label } else Modifier)
                .focusRequester(requester)
                .holdOk(
                    input.hold,
                    onHold = { bounds ->
                        input.anchorTile(bounds)
                        input.actions.onTileMenu(key, true)
                    },
                    onMenuKey =
                        if (tileMenu) {
                            { bounds ->
                                input.anchorTile(bounds)
                                input.actions.onTileMenu(key, false)
                            }
                        } else {
                            null
                        },
                ),
    ) {
        // An image tile is one semantics node, which the benchmark reads as "art has landed"; a text tile adds its label.
        // The surface clips this box, so its fill is drawn: a shaped background would add a node.
        val surface = TvTheme.colors.surface
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(surface) }
                .then(
                    if (app.lightArt || glassEdge) {
                        Modifier.drawWithContent {
                            drawContent()
                            // The light tile's edge is darker and covers the glass dock's.
                            if (app.lightArt) {
                                drawTileEdge(LightTileEdge, LightTileEdgeWidth.toPx())
                            } else {
                                drawTileEdge(GlassTileEdge, GLASS_TILE_EDGE_PX)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val image = app.image
            if (image != null) {
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (app.iconArt) IconTileLabel(app.label)
                if (app.homeLauncher && bannerName && !app.iconArt) {
                    NameChip(app, Modifier.align(Alignment.BottomStart).padding(NameOnBannerInset))
                }
            } else {
                TrimmedEllipsisText(app.label, TvTheme.typography.tileLabel, maxLines = 2)
            }
        }
    }
}

/**
 * [tile] with [app]'s name under it, on one line, on a pill as wide as the text and no wider than the tile, which looks the same
 * with focus or without, but for a cut name, which scrolls while the tile has focus. The pill is [nameGap] under the tile and
 * does not move with focus.
 */
@Composable
private fun NameUnder(
    app: AppEntry,
    scale: Float,
    peak: Float,
    modifier: Modifier = Modifier,
    tile: @Composable () -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    val scrolling = remember(focused) { { focused.value } }
    Layout(
        content = {
            tile()
            NameChip(app, scrolling = scrolling)
        },
        modifier = modifier.onFocusChanged { focused.value = it.hasFocus },
    ) { measurables, constraints ->
        val art = measurables[0].measure(constraints)
        val name = measurables[1].measure(Constraints(maxWidth = art.width))
        val tileHeight = art.height.toDp().value
        val gap = nameGap(tileHeight, scale, peak).dp.roundToPx()
        val ringRoom = ceil((roomOver(namePivot(tileHeight, scale), scale, peak) - TileGap.value).dp.toPx()).toInt()
        // In whole pixels, so that rounding the two apart cannot leave the space under the pill short of its ratio.
        val below = maxOf(0, ceil(NAME_BELOW_RATIO * gap).toInt() - TileGap.roundToPx(), ringRoom)
        layout(art.width, art.height + gap + name.height + below) {
            art.place(0, 0)
            name.place((art.width - name.width) / 2, art.height + gap)
        }
    }
}

/** [app]'s name in a pill of one line, [NameLine] high; a cut name scrolls while [scrolling] says so. */
@Composable
private fun NameChip(
    app: AppEntry,
    modifier: Modifier = Modifier,
    scrolling: (() -> Boolean)? = null,
) {
    val density = LocalDensity.current
    val label = TvTheme.typography.tileLabel
    val style =
        remember(density, label) {
            label.copy(
                color = NameInk,
                textAlign = TextAlign.Center,
                lineHeight = with(density) { NameLine.toSp() },
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
        }
    Box(
        modifier
            .drawBehind { drawRoundRect(NamePill, cornerRadius = CornerRadius(NamePillRadius.toPx())) }
            .padding(horizontal = NamePillAcross, vertical = NamePillDown),
    ) {
        TrimmedEllipsisText(
            app.label,
            style,
            maxLines = 1,
            modifier = Modifier.testTag("name:${app.key}").clearAndSetSemantics {},
            scrolling = scrolling,
        )
    }
}

/** An edge [width] pixels wide drawn inside the tile's shape over its art: a light tile's, so it holds on any surface, or the glass dock's. */
private fun ContentDrawScope.drawTileEdge(
    color: Color,
    width: Float,
) {
    drawRoundRect(
        color,
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(TileCorner.toPx() - width / 2f),
        style = Stroke(width),
    )
}

/** The name over the bottom of a tile whose art is the app's icon, which names nothing; the tile's description says it already. */
@Composable
private fun BoxScope.IconTileLabel(label: String) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .drawBehind { drawRect(IconTileShade) }
                .clearAndSetSemantics {},
        contentAlignment = Alignment.BottomStart,
    ) {
        TrimmedEllipsisText(label, TvTheme.typography.tileCaption, maxLines = 1, modifier = Modifier.padding(10.dp))
    }
}

private val IconTileShade = Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))

@Preview(name = "Home 1080p glass", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenGlassPreview() {
    HomePreview()
}

@Preview(name = "Home 1080p glass 4 columns", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenFourColumnsPreview() {
    HomePreview(columns = 4)
}

@Preview(name = "Home 1080p tile menu", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenTileMenuPreview() {
    HomePreview(
        menu = HomeMenu("example.movies", listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo)),
        menuAnchor = Rect(left = 110f, top = 520f, right = 380f, bottom = 672f),
    )
}

@Preview(name = "Home 1080p inputs", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenInputsPreview() {
    HomePreview(
        inputs = PreviewInputs,
        inputsOpen = true,
    )
}

@Preview(name = "Home 1080p reorder", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenReorderPreview() {
    val others = PreviewNames.map { "example.${it.lowercase()}" } - "example.music"
    HomePreview(
        reorder = ReorderView(carried = "example.music", dimmed = others.toSet(), hint = ReorderHint.Reorder),
        focused = "example.music",
    )
}

@Preview(name = "Home 1080p library pill", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun HomeScreenLibraryPillPreview() {
    HomePreview(focused = LIBRARY_FOCUS, appCount = 9, libraryPill = true)
}

@Preview(name = "Home 1080p Spanish", device = "spec:width=1920px,height=1080px,dpi=320", locale = "es-rES", showBackground = true)
@Composable
private fun HomeScreenSpanishPreview() {
    HomePreview()
}

@Preview(name = "Home 1080p reorder Spanish", device = "spec:width=1920px,height=1080px,dpi=320", locale = "es-rES", showBackground = true)
@Composable
private fun HomeScreenReorderSpanishPreview() {
    val others = PreviewNames.map { "example.${it.lowercase()}" } - "example.music"
    HomePreview(
        reorder =
            ReorderView(
                carried = "example.music",
                dimmed = others.toSet(),
                hint = ReorderHint.DockChooseApp,
                slot = DockSlot(2, 6, "Music"),
            ),
        focused = "example.music",
    )
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
    TvTheme {
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
        val wallpaper =
            Wallpaper(
                picture = ImageBitmap.imageResource(R.drawable.wallpaper),
                caption = "Sunrise and aurora over northern Europe · NASA, Expedition 43",
            )
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
