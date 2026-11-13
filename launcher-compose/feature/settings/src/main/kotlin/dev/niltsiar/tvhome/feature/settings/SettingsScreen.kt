package dev.niltsiar.tvhome.feature.settings

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.FocusPill
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.SCROLL_CUE_ALPHA
import dev.niltsiar.tvhome.core.designsystem.SCROLL_CUE_PILL_ALPHA
import dev.niltsiar.tvhome.core.designsystem.ScrollsClearOfEdge
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.drawScrollDownCue
import dev.niltsiar.tvhome.core.designsystem.fadeScrolledEdges
import dev.niltsiar.tvhome.core.designsystem.menuRowShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelMaxWidth = 920.dp
private val PanelMargin = 32.dp
private val PanelEdge = 20.dp
private val PanelSide = 28.dp
private val PanelRise = 40.dp
private const val PANEL_START_SCALE = 0.94f
private val TitleTop = 24.dp
private val TitleBottom = 8.dp
internal val RowMinHeight = 52.dp
private val RowSide = 16.dp
private val ChipHeight = 40.dp
private val ChipMinWidth = 64.dp
private val ChipPadding = 18.dp
private val ChipGap = 8.dp
private val HeadingTop = 22.dp
private val HeadingRuleGap = 6.dp
private val ValueMaxWidth = 320.dp
private val ChevronWidth = 10.dp
private val ChevronHeight = 16.dp

/** A focused row keeps this much of the list clear past it: the next row, or its group's heading. It is more than the edge fade. */
private val ScrollClear = 64.dp
private const val ROW_FOCUS_MS = 120

private const val LABEL_IDLE_ALPHA = 0.78f
private const val HEADING_ALPHA = 0.65f
private const val RULE_ALPHA = 0.12f
private const val SELECTED_FILL_ALPHA = 0.16f
private const val SELECTED_RIM_ALPHA = 0.42f
private const val FOCUSED_ROW_ALPHA = 0.10f

private const val NOTICE_MS = 2_000L
private const val NOTICE_FADE_MS = 200
private val NoticeHeight = 36.dp

private const val WALLPAPER_STOP = "wallpaper"
private val NoPickerArt = PickerArt()
private const val OPEN_ON_START_STOP = "open_on_start"

/** One thing the list shows, top to bottom. The [Focusable] ones are what Up and Down move between, in this order. */
private sealed interface PageItem {
    data class Heading(
        @StringRes val id: Int,
    ) : PageItem
}

private sealed interface Focusable : PageItem {
    val stop: String
}

private data object ReorderItem : Focusable {
    override val stop = "reorder"
}

private data class SettingItem(
    val row: SettingsRow,
) : Focusable {
    override val stop = "setting:${row.id.name}"
}

private data class WallpaperItem(
    val value: OptionLabel,
) : Focusable {
    override val stop = WALLPAPER_STOP
}

private data class OpenOnStartItem(
    val value: OptionLabel,
) : Focusable {
    override val stop = OPEN_ON_START_STOP
}

private data object FollowThemeItem : Focusable {
    override val stop = "follow_theme"
}

private data object DefaultHomeItem : Focusable {
    override val stop = "default_home"
}

private data object TvSettingsItem : Focusable {
    override val stop = "tv_settings"
}

/** Java's order: the Wallpaper row follows the Clock row, the Open on start row follows the first section's rows. */
private fun pageItems(ui: SettingsUiState): List<PageItem> =
    buildList {
        if (ui.reorder) add(ReorderItem)
        for ((position, section) in ui.sections.withIndex()) {
            add(PageItem.Heading(section.heading))
            for (row in section.rows) {
                add(SettingItem(row))
                if (row.id == SettingsRowId.ClockFormat) ui.wallpaper?.let { add(WallpaperItem(it)) }
            }
            if (position == 0) ui.openOnStart?.let { add(OpenOnStartItem(it)) }
        }
        add(PageItem.Heading(R.string.settings_group_system))
        add(FollowThemeItem)
        add(DefaultHomeItem)
        add(TvSettingsItem)
    }

/** The stops by key, in the order of [keys]: each one's requester, and the requesters above and below it. */
private fun focusStops(keys: List<String>): Map<String, FocusStop> {
    val entries = keys.map { FocusRequester() }
    return keys.withIndex().associate { (at, key) ->
        key to
            FocusStop(entries[at], entries.getOrNull(at - 1) ?: FocusRequester.Cancel, entries.getOrNull(at + 1) ?: FocusRequester.Cancel)
    }
}

/** A short message in the page's title band, in place of a system toast: [show] puts one there for 2 s, in place of any still showing. */
@Stable
public class PageNotice {
    internal var shown: Shown? by mutableStateOf(null)
        private set

    public fun show(
        @StringRes text: Int,
    ) {
        shown = Shown(text)
    }

    internal fun clear(of: Shown) {
        if (shown === of) shown = null
    }

    /** One showing of a message: the same text shown again is a new one, with its own 2 s. */
    internal class Shown(
        @StringRes val text: Int,
    )
}

/**
 * Launcher settings as one panel. With [SettingsUiState.reorder] the first row is Reorder apps, which has focus when the screen
 * opens and runs [onReorder]; otherwise the first row's selected option has focus. The setting rows follow in groups, each
 * listing its options side by side, with the Wallpaper row after Clock and the Open on start row after the first group when the
 * state has them. Their pickers open over the panel. Follow theme, Choose default home ([onChooseDefaultHome]) and TV system
 * settings ([onOpenTvSettings]) end the list. OK on an option stores it, unless it is the selected one. A [notice] shows at the
 * end of the title band. The pickers show [art] beside their entries.
 * Up and Down move between rows, onto the selected option of the next one; Left and Right move between a row's options and
 * stop at its ends. Focus cannot leave the screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun SettingsScreen(
    state: () -> SettingsUiState,
    actions: SettingsActions,
    onChooseDefaultHome: () -> Unit,
    onOpenTvSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onReorder: () -> Unit = {},
    notice: PageNotice = remember { PageNotice() },
    art: PickerArt = NoPickerArt,
) {
    val reveal = rememberReveal()
    val ui = state()
    val items = remember(ui) { pageItems(ui) }
    val keys = items.filterIsInstance<Focusable>().map { it.stop }
    val stops = remember(keys) { focusStops(keys) }
    val currentStops by rememberUpdatedState(stops)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentStops.getValue(keys.first()).entry.requestFocus()
    }
    val pickerStop =
        if (ui.picker != null) {
            OPEN_ON_START_STOP
        } else if (ui.wallpaperPicker != null) {
            WALLPAPER_STOP
        } else {
            null
        }
    var returnTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pickerStop) {
        if (pickerStop != null) {
            returnTo = pickerStop
        } else {
            returnTo?.let {
                withFrameNanos { }
                currentStops.getValue(it).entry.requestFocus()
            }
        }
    }
    val scroll = rememberScrollState()
    // The launcher does not opt into right-to-left layouts, so a language written right to left sets the direction here.
    val rtl = LocalConfiguration.current.layoutDirection == View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .testTag("settings")
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            OverlaySurface(
                modifier =
                    Modifier
                        .padding(horizontal = PanelMargin, vertical = PanelEdge)
                        .testTag("settings:panel")
                        .widthIn(max = PanelMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .revealed(reveal),
            ) {
                val ink = TvTheme.colors.onSurface
                val cueInk = ink.copy(alpha = SCROLL_CUE_ALPHA)
                val cuePill = ink.copy(alpha = SCROLL_CUE_PILL_ALPHA)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PanelTitle(stringResource(R.string.settings_title), "settings:title", Modifier.weight(1f))
                        notice.shown?.let { NoticePill(it, notice::clear) }
                    }
                    ScrollsClearOfEdge(ScrollClear) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("settings:list")
                                .drawWithContent {
                                    drawContent()
                                    if (scroll.canScrollForward) drawScrollDownCue(size.height - PanelSide.toPx() / 2f, cueInk, cuePill)
                                }.fadeScrolledEdges(scroll)
                                .verticalScroll(scroll)
                                .padding(start = PanelSide, end = PanelSide, bottom = PanelSide),
                        ) {
                            for (item in items) {
                                when (item) {
                                    is ReorderItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.settings_reorder),
                                            selected = false,
                                            onClick = onReorder,
                                            tag = "settings:reorder",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }

                                    is PageItem.Heading -> {
                                        Heading(item.id)
                                    }

                                    is SettingItem -> {
                                        key(item.row.id) { SettingRow(item.row, actions.onPick, stops.getValue(item.stop)) }
                                    }

                                    is WallpaperItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.wallpaper_label),
                                            value = item.value.text(),
                                            selected = false,
                                            onClick = actions.onOpenWallpaperPicker,
                                            tag = "settings:wallpaper",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }

                                    is OpenOnStartItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.open_on_start_label),
                                            value = item.value.text(),
                                            selected = false,
                                            onClick = actions.onOpenPicker,
                                            tag = "settings:open_on_start",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }

                                    is FollowThemeItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.settings_follow_theme),
                                            selected = ui.followsTheme,
                                            onClick = actions.onFollowTheme,
                                            tag = "settings:follow_theme",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }

                                    is DefaultHomeItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.home_settings),
                                            selected = false,
                                            onClick = onChooseDefaultHome,
                                            tag = "settings:default_home",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }

                                    is TvSettingsItem -> {
                                        ActionRow(
                                            label = stringResource(R.string.settings_tv_settings),
                                            selected = false,
                                            onClick = onOpenTvSettings,
                                            tag = "settings:tv_settings",
                                            stop = stops.getValue(item.stop),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            OverlayHost(open = { pickerStop != null }) {
                ui.picker?.let { picker ->
                    val entries = remember(picker, ui.backdrop) { startEntries(picker.apps, ui.backdrop) }
                    PickerPanel(
                        R.string.open_on_start_label,
                        entries,
                        picker.selected,
                        art.app,
                        actions.onPickStartApp,
                        actions.onClosePicker,
                    )
                }
                ui.wallpaperPicker?.let { picker ->
                    val entries = remember(picker, ui.backdrop) { wallpaperEntries(picker, ui.backdrop) }
                    PickerPanel(
                        R.string.wallpaper_label,
                        entries,
                        picker.selected,
                        art.wallpaper,
                        actions.onPickWallpaper,
                        actions.onClosePicker,
                    )
                }
            }
        }
    }
}

/** The settings panel's entrance: it rises, grows and fades in with a spring, at once with animations off. */
@Composable
internal fun rememberReveal(): Animatable<Float, AnimationVector1D> {
    val motion = TvTheme.motion
    val reveal = remember { Animatable(if (motion.animations) 0f else 1f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, motion.springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    return reveal
}

internal fun Modifier.revealed(reveal: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = reveal.value.coerceIn(0f, 1f)
        translationY = (1f - reveal.value) * PanelRise.toPx()
        val scale = PANEL_START_SCALE + (1f - PANEL_START_SCALE) * reveal.value
        scaleX = scale
        scaleY = scale
    }

@Composable
internal fun PanelTitle(
    text: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.shelfTitle.copy(fontSize = 30.sp) }
    BasicText(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = PanelSide + RowSide, end = PanelSide, top = TitleTop, bottom = TitleBottom)
                .testTag(tag),
    )
}

/** The [shown] message as a pill level with the title, which fades in, stays 2 s, fades out and then is [gone]. */
@Composable
private fun NoticePill(
    shown: PageNotice.Shown,
    gone: (PageNotice.Shown) -> Unit,
) {
    val motion = TvTheme.motion
    val typography = TvTheme.typography
    val ink = TvTheme.colors.onSurface
    val style = remember(typography, ink) { typography.pillLabel.copy(fontSize = 16.sp, color = ink) }
    val alpha = remember(shown) { Animatable(0f) }
    LaunchedEffect(shown) {
        alpha.animateTo(1f, motion.tweenSpec(NOTICE_FADE_MS))
        delay(NOTICE_MS)
        alpha.animateTo(0f, motion.tweenSpec(NOTICE_FADE_MS))
        gone(shown)
    }
    Box(
        Modifier
            .padding(top = TitleTop, bottom = TitleBottom, end = PanelSide)
            .graphicsLayer { this.alpha = alpha.value }
            .height(NoticeHeight)
            .background(ink.copy(alpha = SELECTED_FILL_ALPHA), CircleShape)
            .border(1.dp, ink.copy(alpha = SELECTED_RIM_ALPHA), CircleShape)
            .testTag("settings:notice"),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(shown.text), style = style, maxLines = 1, modifier = Modifier.padding(horizontal = ChipPadding))
    }
}

@Composable
private fun Heading(
    @StringRes id: Int,
) {
    val typography = TvTheme.typography
    val ink = TvTheme.colors.onSurface
    val style = remember(typography, ink) { typography.chipKey.copy(fontSize = 15.sp, color = ink.copy(alpha = HEADING_ALPHA)) }
    Column(Modifier.fillMaxWidth().padding(top = HeadingTop)) {
        BasicText(
            stringResource(id),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = RowSide).testTag("settings:heading"),
        )
        Spacer(Modifier.height(HeadingRuleGap))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(1.dp)
                .background(ink.copy(alpha = RULE_ALPHA)),
        )
    }
}

/**
 * A row: its label and its options, on a faint pill as wide as an action row's while an option of the row has focus, when
 * the label brightens too. The [stop]'s entry is on the selected option (the first one when none is); its up and down are
 * the entries of the rows around it.
 */
@Composable
private fun SettingRow(
    row: SettingsRow,
    onPick: (SettingsRowId, Int) -> Unit,
    stop: FocusStop,
) {
    val motion = TvTheme.motion
    val typography = TvTheme.typography
    val ink = TvTheme.colors.onSurface
    val labelStyle = remember(typography) { typography.pillLabel.copy(fontSize = 18.sp) }
    val idle = remember(ink) { ink.copy(alpha = LABEL_IDLE_ALPHA) }
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pill = remember(ink) { ink.copy(alpha = FOCUSED_ROW_ALPHA) }
    val shape = remember { menuRowShape() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    val shown = focus.value.coerceIn(0f, 1f)
                    if (shown > 0f) drawOutline(outline, pill, alpha = shown)
                }
            }.padding(horizontal = RowSide)
            .testTag("setting:${row.id.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(row.label),
            style = labelStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = ColorProducer { lerp(idle, ink, focus.value.coerceIn(0f, 1f)) },
            modifier = Modifier.weight(1f).padding(end = RowSide),
        )
        Row(
            Modifier
                .onFocusChanged {
                    val target = if (it.hasFocus) 1f else 0f
                    if (focus.targetValue != target) {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, motion.tweenSpec(ROW_FOCUS_MS)) }
                    }
                }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val entryOption = row.selected.coerceAtLeast(0)
            row.options.forEachIndexed { index, option ->
                OptionChip(
                    label = option.text(),
                    selected = index == row.selected,
                    onClick = { onPick(row.id, index) },
                    modifier =
                        Modifier
                            .testTag("option:${row.id.name}:$index")
                            .then(if (index == entryOption) Modifier.focusRequester(stop.entry) else Modifier)
                            .focusProperties {
                                up = stop.up
                                down = stop.down
                            },
                )
            }
        }
    }
}

@Composable
internal fun OptionLabel.text(): String =
    when (this) {
        is OptionLabel.Text -> text
        is OptionLabel.Res -> stringResource(id)
    }

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.pillLabel.copy(fontSize = 18.sp) }
    val rest = TvTheme.colors.onSurface
    FocusPill(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.height(ChipHeight).widthIn(min = ChipMinWidth).semantics { this.selected = selected },
        background = { if (selected) SelectedLook(rest, CircleShape) },
    ) { ink ->
        BasicText(
            label,
            style = style,
            maxLines = 1,
            color = if (selected) ink.content else ink.secondary,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = ChipPadding),
        )
    }
}

/** Where focus arrives in a row ([entry]) and the entries of the rows around it. */
internal class FocusStop(
    val entry: FocusRequester,
    val up: FocusRequester = FocusRequester.Default,
    val down: FocusRequester = FocusRequester.Default,
)

/**
 * A full-width button of the panel; a [selected] one is marked with a check. With a [value] the row reads like the others: the
 * label on the start side, the value and a chevron towards the end side, and one description of both for a screen reader.
 * A [detail] line in a smaller muted style goes under the label, and [leading] before both. Without a [stop] focus arrives
 * and moves by the defaults, as in a list that moves focus itself. The row is [RowMinHeight] tall, or [pillHeight] when that
 * is more, and its pill [pillHeight], centred, so that by default the pill keeps a margin to the rows around it, as an option does.
 */
@Composable
internal fun ActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    stop: FocusStop? = null,
    pillHeight: Dp = ChipHeight,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.pillLabel.copy(fontSize = 18.sp) }
    val detailStyle = remember(typography) { typography.pillLabel.copy(fontSize = 14.sp) }
    val rest = TvTheme.colors.onSurface
    val focusedInk = TvTheme.surfaces.pillContent
    val idle = remember(rest) { rest.copy(alpha = LABEL_IDLE_ALPHA) }
    val shape = remember { menuRowShape() }
    FocusPill(
        onClick = onClick,
        shape = shape,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = ((RowMinHeight - pillHeight) / 2).coerceAtLeast(0.dp))
                .height(pillHeight)
                .testTag(tag)
                .semantics {
                    this.selected = selected
                    if (value != null) contentDescription = "$label, $value"
                }.then(if (stop != null) Modifier.focusRequester(stop.entry) else Modifier)
                .focusProperties {
                    up = stop?.up ?: FocusRequester.Default
                    down = stop?.down ?: FocusRequester.Default
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
    ) { ink ->
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = RowSide),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(RowSide))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    label,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (value == null) ink.content else ColorProducer { lerp(idle, focusedInk, ink.progress()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (detail != null) {
                    BasicText(
                        detail,
                        style = detailStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ink.secondary,
                        modifier = Modifier.fillMaxWidth().testTag("$tag:detail"),
                    )
                }
            }
            if (value != null) {
                BasicText(
                    value,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = ink.secondary,
                    modifier = Modifier.widthIn(max = ValueMaxWidth).padding(start = RowSide),
                )
                Box(
                    Modifier
                        .padding(start = ChipGap)
                        .testTag("$tag:chevron")
                        .size(ChevronWidth, ChevronHeight)
                        .drawBehind { drawChevron(ink.secondary()) },
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = RowSide)
                    .size(CheckSize)
                    .drawBehind { drawCheck(ink.content()) },
            )
        }
    }
}

private val CheckSize = 20.dp
private val CheckStroke = 2.dp

@Composable
private fun BoxScope.SelectedLook(
    ink: Color,
    shape: Shape,
) {
    Box(
        Modifier
            .matchParentSize()
            .background(ink.copy(alpha = SELECTED_FILL_ALPHA), shape)
            .border(1.dp, ink.copy(alpha = SELECTED_RIM_ALPHA), shape),
    )
}

private fun DrawScope.drawCheck(color: Color) {
    val check =
        Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            lineTo(size.width * 0.40f, size.height * 0.82f)
            lineTo(size.width * 0.88f, size.height * 0.22f)
        }
    drawPath(check, color, style = Stroke(CheckStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A chevron that points to the end side, so it turns round in a right-to-left layout. */
private fun DrawScope.drawChevron(color: Color) {
    val flip = layoutDirection == LayoutDirection.Rtl

    fun x(fraction: Float) = size.width * if (flip) 1f - fraction else fraction
    val chevron =
        Path().apply {
            moveTo(x(0.15f), size.height * 0.1f)
            lineTo(x(0.85f), size.height * 0.5f)
            lineTo(x(0.15f), size.height * 0.9f)
        }
    drawPath(chevron, color, style = Stroke(CheckStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
