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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.FocusPill
import dev.niltsiar.tvhome.core.designsystem.MenuDivider
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.ScrollsClearOfEdge
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.fadeScrolledEdges
import dev.niltsiar.tvhome.core.designsystem.menuRowShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private val PanelMaxWidth = 920.dp
private val PanelMargin = 32.dp
private val PanelEdge = 20.dp
private val PanelSide = 28.dp
private val PanelRise = 40.dp
private const val PANEL_START_SCALE = 0.94f
private val TitleTop = 24.dp
private val TitleBottom = 8.dp
private val RowMinHeight = 52.dp
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

/** Follow theme, Choose default home and TV system settings, after the rows. */
private const val BUTTON_COUNT = 3
private const val LABEL_IDLE_ALPHA = 0.78f
private const val HEADING_ALPHA = 0.65f
private const val RULE_ALPHA = 0.12f
private const val SELECTED_FILL_ALPHA = 0.16f
private const val SELECTED_RIM_ALPHA = 0.42f

/**
 * Launcher settings as one panel: every row lists its options side by side and OK stores the focused one at once.
 * Up and Down move between rows, onto the selected option of the next one; Left and Right move between a row's options
 * and stop at its ends. Focus cannot leave the screen. [onChooseDefaultHome] opens the system's home picker and
 * [onOpenTvSettings] the TV's own settings. With
 * [SettingsUiState.reorder] the first row is Reorder apps, which has focus when the screen opens, and [onReorder] runs when it is picked.
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
) {
    val reveal = rememberReveal()
    val ui = state()
    val rowCount = ui.sections.sumOf { it.rows.size }
    val lead = if (ui.reorder) 1 else 0
    val startRows = if (ui.openOnStart != null) 1 else 0
    val wallpaperRows = if (ui.wallpaper != null) 1 else 0
    val firstRows =
        ui.sections
            .firstOrNull()
            ?.rows
            .orEmpty()
    // Java's order: the Wallpaper row follows the Clock row, the Open on start row follows the first section's rows.
    val wallpaperAt = lead + firstRows.indexOfFirst { it.id == SettingsRowId.ClockFormat } + 1
    val startAt = lead + firstRows.size + wallpaperRows
    // The Reorder row if there is one, then one requester per row, the Wallpaper row and the Open on start row, then the three
    // buttons: each sits on the option a row gets focus on, and links the rows.
    val firstButton = lead + rowCount + startRows + wallpaperRows
    val entries = remember(firstButton) { List(firstButton + BUTTON_COUNT) { FocusRequester() } }
    val currentEntries by rememberUpdatedState(entries)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentEntries.first().requestFocus()
    }
    val pickerRow =
        if (ui.picker != null) {
            startAt
        } else if (ui.wallpaperPicker != null) {
            wallpaperAt
        } else {
            -1
        }
    var returnTo by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pickerRow) {
        if (pickerRow >= 0) {
            returnTo = pickerRow
        } else if (returnTo >= 0) {
            withFrameNanos { }
            entries[returnTo].requestFocus()
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
                Column(Modifier.fillMaxSize()) {
                    PanelTitle(stringResource(R.string.settings_title), "settings:title")
                    ScrollsClearOfEdge(ScrollClear) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("settings:list")
                                .fadeScrolledEdges(scroll)
                                .verticalScroll(scroll)
                                .padding(start = PanelSide, end = PanelSide, bottom = PanelSide),
                        ) {
                            if (ui.reorder) {
                                ActionRow(
                                    label = stringResource(R.string.settings_reorder),
                                    selected = false,
                                    onClick = onReorder,
                                    tag = "settings:reorder",
                                    entry = entries[0],
                                    upEntry = entries.above(0),
                                    downEntry = entries.below(0),
                                )
                                Spacer(Modifier.height(ChipGap))
                            }
                            var index = lead
                            for ((position, section) in ui.sections.withIndex()) {
                                section.heading?.let { Heading(it) }
                                for (row in section.rows) {
                                    val at = index++
                                    key(row.id) { SettingRow(row, actions.onPick, entries[at], entries.above(at), entries.below(at)) }
                                    if (row.id == SettingsRowId.ClockFormat) {
                                        ui.wallpaper?.let { wallpaper ->
                                            val wallpaperIndex = index++
                                            ActionRow(
                                                label = stringResource(R.string.wallpaper_label),
                                                value = wallpaper.value.text(),
                                                selected = false,
                                                onClick = actions.onOpenWallpaperPicker,
                                                tag = "settings:wallpaper",
                                                entry = entries[wallpaperIndex],
                                                upEntry = entries.above(wallpaperIndex),
                                                downEntry = entries.below(wallpaperIndex),
                                            )
                                        }
                                    }
                                }
                                if (position == 0) {
                                    ui.openOnStart?.let { start ->
                                        val at = index++
                                        ActionRow(
                                            label = stringResource(R.string.open_on_start_label),
                                            value = start.value.text(),
                                            selected = false,
                                            onClick = actions.onOpenPicker,
                                            tag = "settings:open_on_start",
                                            entry = entries[at],
                                            upEntry = entries.above(at),
                                            downEntry = entries.below(at),
                                        )
                                    }
                                }
                            }
                            MenuDivider(Modifier.padding(vertical = 10.dp))
                            ActionRow(
                                label = stringResource(R.string.settings_follow_theme),
                                selected = ui.followsTheme,
                                onClick = actions.onFollowTheme,
                                tag = "settings:follow_theme",
                                entry = entries[firstButton],
                                upEntry = entries.above(firstButton),
                                downEntry = entries.below(firstButton),
                            )
                            Spacer(Modifier.height(ChipGap))
                            ActionRow(
                                label = stringResource(R.string.home_settings),
                                selected = false,
                                onClick = onChooseDefaultHome,
                                tag = "settings:default_home",
                                entry = entries[firstButton + 1],
                                upEntry = entries.above(firstButton + 1),
                                downEntry = entries.below(firstButton + 1),
                            )
                            Spacer(Modifier.height(ChipGap))
                            ActionRow(
                                label = stringResource(R.string.settings_tv_settings),
                                selected = false,
                                onClick = onOpenTvSettings,
                                tag = "settings:tv_settings",
                                entry = entries[firstButton + 2],
                                upEntry = entries.above(firstButton + 2),
                                downEntry = entries.below(firstButton + 2),
                            )
                        }
                    }
                }
            }
            OverlayHost(open = { pickerRow >= 0 }) {
                ui.picker?.let { picker ->
                    val labels =
                        remember(picker) {
                            listOf<OptionLabel>(OptionLabel.Res(R.string.settings_off)) +
                                picker.apps.map { OptionLabel.Text(it.label) }
                        }
                    PickerPanel(R.string.open_on_start_label, labels, picker.selected, actions.onPickStartApp, actions.onClosePicker)
                }
                ui.wallpaperPicker?.let {
                    PickerPanel(R.string.wallpaper_label, it.entries, it.selected, actions.onPickWallpaper, actions.onClosePicker)
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

private fun List<FocusRequester>.above(index: Int) = getOrNull(index - 1) ?: FocusRequester.Cancel

private fun List<FocusRequester>.below(index: Int) = getOrNull(index + 1) ?: FocusRequester.Cancel

@Composable
internal fun PanelTitle(
    text: String,
    tag: String,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.shelfTitle.copy(fontSize = 30.sp) }
    BasicText(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PanelSide + RowSide, end = PanelSide, top = TitleTop, bottom = TitleBottom)
                .testTag(tag),
    )
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
 * A row: its label, which brightens while an option of the row has focus, and its options. The row's [entry] is on its
 * selected option (the first one when none is), [upEntry] and [downEntry] are the entries of the rows around it.
 */
@Composable
private fun SettingRow(
    row: SettingsRow,
    onPick: (SettingsRowId, Int) -> Unit,
    entry: FocusRequester,
    upEntry: FocusRequester,
    downEntry: FocusRequester,
) {
    val motion = TvTheme.motion
    val typography = TvTheme.typography
    val ink = TvTheme.colors.onSurface
    val labelStyle = remember(typography) { typography.pillLabel.copy(fontSize = 18.sp) }
    val idle = remember(ink) { ink.copy(alpha = LABEL_IDLE_ALPHA) }
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = RowSide)
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
                            .then(if (index == entryOption) Modifier.focusRequester(entry) else Modifier)
                            .focusProperties {
                                up = upEntry
                                down = downEntry
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

/**
 * A full-width button of the panel; a [selected] one is marked with a check. With a [value] the row reads like the others: the
 * label on the start side, the value and a chevron towards the end side, and one description of both for a screen reader.
 * [entry] is where focus arrives, [upEntry] and [downEntry] the entries of the rows around it, which a list that moves focus
 * itself leaves at their defaults.
 */
@Composable
internal fun ActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    entry: FocusRequester? = null,
    upEntry: FocusRequester = FocusRequester.Default,
    downEntry: FocusRequester = FocusRequester.Default,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.pillLabel.copy(fontSize = 18.sp) }
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
                .height(RowMinHeight)
                .testTag(tag)
                .semantics {
                    this.selected = selected
                    if (value != null) contentDescription = "$label, $value"
                }.then(if (entry != null) Modifier.focusRequester(entry) else Modifier)
                .focusProperties {
                    up = upEntry
                    down = downEntry
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        background = { if (selected) SelectedLook(rest, shape) },
    ) { ink ->
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = RowSide),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (value == null) ink.content else ColorProducer { lerp(idle, focusedInk, ink.progress()) },
                modifier = Modifier.weight(1f),
            )
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
