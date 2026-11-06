package dev.niltsiar.tvhome.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.FocusInk
import dev.niltsiar.tvhome.core.designsystem.FocusPill
import dev.niltsiar.tvhome.core.designsystem.GlassDropdown
import dev.niltsiar.tvhome.core.designsystem.MenuDivider
import dev.niltsiar.tvhome.core.designsystem.MenuDividerHeight
import dev.niltsiar.tvhome.core.designsystem.MenuRowSpacing
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.menuRowShape
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.visible

private val MinListWidth = 150.dp
private val MaxListWidth = 360.dp
private val RowHeight = 46.dp
private val RowShape = menuRowShape()
private const val ICON_ALPHA = 0.9f
private const val NO_DEVICE_ALPHA = 0.5f
private val CapsuleHeight = 24.dp
private val DotSize = 6.dp
private val Connected = Color(0xFF34C759)

/** The "Connected" capsule of an idle row, on the dark surface and on the light one. */
private val CapsuleFillOnDark = Color(0x2934C759)
private val CapsuleRimOnDark = Color(0x6634C759)
private val CapsuleTextOnDark = Color(0xFF6EE787)
private val CapsuleFillOnLight = Color(0x2E34C759)
private val CapsuleRimOnLight = Color(0x8034C759)
private val CapsuleTextOnLight = Color(0xFF1B7A35)

/** On the focused row, whose fill is white or black, the capsule is a solid green pill with white text. */
private val CapsuleSolid = Color(0xFF146C2E)
private const val PULSE_MS = 1800
private const val HALO_ALPHA = 0.4f
private const val HALO_GROWTH = 1.8f

/**
 * The TV's input list hanging under the Inputs button at [anchor], its right edge on the top bar's: as wide as the button,
 * or 150 dp, and wider for a long name up to 360 dp. It ends at or above [bottomLimit], between two rows, and scrolls when
 * its rows are taller. Each
 * [InputRow.Port] row shows a green capsule while its port is connected, its dot ringed
 * by a pulse unless [animated] is off. "More inputs" shows the rows [rows] folds. The Live TV row comes last.
 * Focus opens on the first connected port, as the TV does not say which input is active, else on the first row.
 * Rows are tagged `input:<n>` by position, the list `menu`.
 */
@Composable
internal fun InputsMenu(
    rows: () -> List<InputRow>,
    anchor: State<Rect>,
    bottomLimit: Dp,
    animated: Boolean,
    onPick: (InputTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown by remember { derivedStateOf { rows().visible(expanded) } }
    val first = remember { FocusRequester() }
    val afterMore = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            // The first row that was folded exists once the list has laid out again.
            withFrameNanos { }
            runCatching { afterMore.requestFocus() }
        }
    }
    val pulse = rememberPulse(animated)
    val density = LocalDensity.current
    val buttonWidth = with(density) { anchor.value.width.toDp() }
    val clusterRight = LocalWindowInfo.current.containerSize.width - with(density) { ScreenMargin.toPx() }
    val endAtCluster = remember(anchor, clusterRight) { derivedStateOf { anchor.value.copy(right = clusterRight) } }
    val minWidth = maxOf(buttonWidth, MinListWidth)
    val openRow =
        remember(shown) {
            val connected = shown.indexOfFirst { it is InputRow.Port && it.connection == InputConnection.Connected }
            if (connected >= 0) connected else shown.indexOfFirst { it !is InputRow.Separator }
        }
    val firstFolded = remember(shown) { shown.indexOfFirst { it is InputRow.Port && it.folded } }
    val childHeights = remember(shown) { rowHeights(shown) }
    GlassDropdown(
        anchor = endAtCluster,
        alignEnd = true,
        firstFocus = first,
        modifier = modifier,
        minWidth = minWidth,
        maxWidth = maxOf(minWidth, MaxListWidth),
        edgeMargin = ScreenMargin,
        bottomLimit = bottomLimit,
        childHeights = childHeights,
    ) {
        shown.forEachIndexed { index, row ->
            val focus =
                when {
                    expanded && index == firstFolded -> Modifier.focusRequester(afterMore)
                    index == openRow -> Modifier.focusRequester(first)
                    else -> Modifier
                }
            when (row) {
                is InputRow.Port -> {
                    InputItem(row.icon.drawable(), row.label, row.connection, pulse, "input:$index", { onPick(row.target) }, focus)
                }

                InputRow.Panel -> {
                    InputItem(R.drawable.ic_input_hdmi, stringResource(R.string.inputs), InputConnection.Unknown, pulse, "input:$index", {
                        onPick(InputTarget.SourcePanel)
                    }, focus)
                }

                InputRow.LiveTv -> {
                    InputItem(
                        R.drawable.ic_input_livetv,
                        stringResource(
                            R.string.live_tv,
                        ),
                        InputConnection.Unknown,
                        pulse,
                        "input:$index",
                        {
                            onPick(InputTarget.LiveTv)
                        },
                        focus,
                    )
                }

                InputRow.More -> {
                    InputItem(
                        R.drawable.ic_input_hdmi,
                        stringResource(
                            R.string.more_inputs,
                        ),
                        InputConnection.Unknown,
                        pulse,
                        "input:more",
                        {
                            expanded =
                                true
                        },
                        focus,
                    )
                }

                is InputRow.Separator -> {
                    MenuDivider()
                }
            }
        }
    }
}

/** The heights of the rows of [rows] for the list to end between, a divider counted with the row under it. */
private fun rowHeights(rows: List<InputRow>): List<Dp> {
    var divided = 0.dp
    return buildList {
        rows.forEach { row ->
            if (row is InputRow.Separator) {
                divided += MenuDividerHeight + MenuRowSpacing
            } else {
                add(RowHeight + divided)
                divided = 0.dp
            }
        }
    }
}

@DrawableRes
private fun InputIcon.drawable(): Int =
    when (this) {
        InputIcon.Hdmi -> R.drawable.ic_input_hdmi
        InputIcon.Av -> R.drawable.ic_input_av
        InputIcon.LiveTv -> R.drawable.ic_input_livetv
    }

/** 0 to 1 over and over, shared by every dot of the list. Read it in a draw lambda; a read in composition would recompose each frame. */
@Composable
private fun rememberPulse(animated: Boolean): State<Float> {
    if (!animated) return remember { mutableStateOf(0f) }
    return rememberInfiniteTransition(label = "inputPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
}

@Composable
private fun InputItem(
    @DrawableRes icon: Int,
    label: String,
    connection: InputConnection,
    pulse: State<Float>,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val noDevice = remember(typography) { typography.caption.copy(fontSize = 13.sp) }
    val painter = painterResource(icon)
    FocusPill(onClick = onClick, modifier = modifier.fillMaxWidth().height(RowHeight).testTag(tag), shape = RowShape) { ink ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.size(20.dp).drawBehind {
                    with(painter) { draw(size, alpha = ICON_ALPHA, colorFilter = ColorFilter.tint(ink.content())) }
                },
            )
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = typography.pillLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = ink.content)
            Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
            when (connection) {
                InputConnection.Connected -> {
                    ConnectedCapsule(ink, pulse)
                }

                InputConnection.Disconnected -> {
                    BasicText(
                        stringResource(R.string.input_not_connected),
                        style = noDevice,
                        maxLines = 1,
                        color = { ink.content().copy(alpha = NO_DEVICE_ALPHA) },
                    )
                }

                InputConnection.Unknown -> {
                    Unit
                }
            }
        }
    }
}

/**
 * "Connected" in a green capsule: faint on the idle row, in the colours of the surface behind it, and a solid [CapsuleSolid]
 * pill with white text as the row's [ink] fills.
 */
@Composable
private fun ConnectedCapsule(
    ink: FocusInk,
    pulse: State<Float>,
) {
    val dark = TvTheme.surfaces.dark
    val typography = TvTheme.typography
    val style = remember(typography) { typography.chipKey.copy(fontSize = 12.sp) }
    val idleLight = if (dark) 0f else 1f
    Row(
        modifier =
            Modifier
                .height(CapsuleHeight)
                .drawBehind {
                    val focus = ink.progress()
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(lerp(lerp(CapsuleFillOnDark, CapsuleFillOnLight, idleLight), CapsuleSolid, focus), cornerRadius = radius)
                    val rim = 1.dp.toPx()
                    drawRoundRect(
                        lerp(lerp(CapsuleRimOnDark, CapsuleRimOnLight, idleLight), CapsuleSolid, focus),
                        topLeft = Offset(rim / 2f, rim / 2f),
                        size = Size(size.width - rim, size.height - rim),
                        cornerRadius = CornerRadius(size.height / 2f - rim / 2f),
                        style = Stroke(rim),
                    )
                }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(pulse)
        Spacer(Modifier.width(6.dp))
        BasicText(
            stringResource(R.string.input_connected),
            style = style,
            maxLines = 1,
            color = { lerp(lerp(CapsuleTextOnDark, CapsuleTextOnLight, idleLight), Color.White, ink.progress()) },
        )
    }
}

/** A green dot with a ring that grows and fades once per [pulse] cycle; both are drawn, so a frame of the pulse recomposes nothing. */
@Composable
private fun PulsingDot(pulse: State<Float>) {
    Spacer(
        Modifier.size(DotSize).drawBehind {
            val radius = size.minDimension / 2f
            val p = pulse.value
            drawCircle(Connected.copy(alpha = HALO_ALPHA * (1f - p)), radius = radius * (1f + HALO_GROWTH * p))
            drawCircle(Connected, radius = radius)
        },
    )
}
