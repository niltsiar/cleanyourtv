package dev.niltsiar.tvhome.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.Medallion
import dev.niltsiar.tvhome.core.designsystem.MedallionShape
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputTile

/**
 * What an input tile shows now. [target] is what OK switches to, null while the TV does not list the input. [label] is the
 * name its Inputs row reads now, or the name it was pinned under while it is not listed. [connection] is the row's state,
 * null while it is not listed.
 */
@Immutable
public data class InputTileLive(
    val target: InputTarget?,
    val label: String,
    val connection: InputConnection?,
)

/** The Inputs row a pinned [tile] stands for now: the row of its own id, else the first row on the port it was pinned on, else none. */
internal fun resolvePin(
    tile: InputTile,
    rows: List<InputRow>,
): InputRow.Port? {
    val ports = rows.filterIsInstance<InputRow.Port>()
    val own = InputTarget.TvInput(tile.id)
    return ports.firstOrNull { it.target == own } ?: tile.port?.let { port -> ports.firstOrNull { it.port == port } }
}

/** Each input tile of [apps] by key, resolved against the Inputs [rows]. */
internal fun inputTilesLive(
    apps: List<AppEntry>,
    rows: List<InputRow>,
): Map<String, InputTileLive> =
    buildMap {
        for (app in apps) {
            val tile = app.input ?: continue
            val row = resolvePin(tile, rows)
            put(app.key, InputTileLive(row?.target, row?.label ?: app.label, row?.connection))
        }
    }

/** The line under the title for this input: Connected, No device while it is not listed or has no device, none for no state. */
@StringRes
internal fun InputTileLive.status(): Int? =
    when (connection) {
        null, InputConnection.Disconnected -> R.string.input_not_connected
        InputConnection.Connected -> R.string.input_connected
        InputConnection.Standby, InputConnection.Unknown -> null
    }

/** Whether the dot is lit (connected), idle (in standby, without a device or while not listed), or absent without a state. */
private fun InputTileLive.lit(): Boolean? =
    when (connection) {
        InputConnection.Connected -> true
        null, InputConnection.Standby, InputConnection.Disconnected -> false
        InputConnection.Unknown -> null
    }

/** The outline of each kind of port's medallion. */
private fun InputIcon.medallion(): MedallionShape =
    when (this) {
        InputIcon.Hdmi -> MedallionShape.Pentagon
        InputIcon.Av -> MedallionShape.Clover
        InputIcon.LiveTv -> MedallionShape.Cookie
    }

private val DotSize = 10.dp
private val DotRing = 2.dp
private val MedallionSize = 48.dp
private val DotInset = 10.dp
private val GlyphSize = 28.dp

/** The tile [app]'s live state as [HomeInput] holds it; a change of another tile's state does not reach its readers. */
@Composable
internal fun rememberInputTileLive(
    input: HomeInput,
    app: AppEntry,
): State<InputTileLive?> = remember(input, app.key) { derivedStateOf { input.inputTiles.value[app.key] } }

/** The name an input tile shows: its row's name now, else the name it was pinned under. */
@Composable
internal fun rememberInputTileLabel(
    input: HomeInput,
    app: AppEntry,
): State<String> {
    val live = rememberInputTileLive(input, app)
    return remember(live, app.label) { derivedStateOf { live.value?.label ?: app.label } }
}

/**
 * An input tile's face: its row's glyph, its name with [showName], and a dot in the top end corner for its state. The dot is
 * drawn, so a change of state recomposes nothing; a change of name recomposes this face only.
 */
@Composable
internal fun InputTileFace(
    app: AppEntry,
    tile: InputTile,
    input: HomeInput,
    showName: Boolean,
) {
    val live = rememberInputTileLive(input, app)
    val label = rememberInputTileLabel(input, app).value
    val labelStyle = TvTheme.typography.tileLabel
    val painter = painterResource(tile.icon.drawable())
    val colours = TvTheme.lookColors
    val lookState = TvTheme.lookState
    val medallion = remember(tile.icon) { Medallion(tile.icon.medallion()) }
    val density = LocalDensity.current
    val idleRing = remember(density) { Stroke(with(density) { DotRing.toPx() }) }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("input-tile:${app.key}")
            .semantics { contentDescription = label }
            .drawWithContent {
                drawContent()
                val lit = live.value?.lit() ?: return@drawWithContent
                val radius = DotSize.toPx() / 2f
                val inset = DotInset.toPx()
                val centre = Offset(size.width - inset - radius, inset + radius)
                when {
                    lit -> {
                        drawCircle(colours.success(), radius = radius, center = centre)
                    }

                    lookState.look == Look.Expressive -> {
                        drawCircle(colours.idle(), radius = radius - idleRing.width / 2f, center = centre, style = idleRing)
                    }

                    else -> {
                        drawCircle(colours.idle(), radius = radius, center = centre)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(
                Modifier.size(GlyphSize).drawBehind {
                    val side = MedallionSize.toPx()
                    val fill = colours.medallion()
                    if (fill.alpha > 0f) {
                        translate((size.width - side) / 2f, (size.height - side) / 2f) { drawPath(medallion.path(side), fill) }
                    }
                    with(painter) { draw(size, colorFilter = ColorFilter.tint(colours.medallionGlyph())) }
                },
            )
            if (showName) {
                TrimmedEllipsisText(
                    label,
                    labelStyle,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
                    color = colours.tileLabel,
                )
            }
        }
    }
}
