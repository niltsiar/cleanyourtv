package dev.niltsiar.tvhome.feature.clock

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlin.random.Random

/** How far the colon dims at the bottom of a breath, and how long a breath lasts. */
private const val COLON_LOW = 0.25f
private const val BREATH_MS = 1_800

/**
 * The clock screensaver, for the activity and the system dream alike: a large clock and date on black that drift to a
 * new spot every minute, over a slow glow, or over [picture] (Home's wallpaper, already darkened) in place of the glow.
 * In Expressive the glow is a large shape that changes with each move and holds the [picture] where there is one.
 * [glow] false leaves plain black behind the clock, e.g. for an OLED. A null [face] shows nothing until the TV knows the
 * time.
 *
 * It runs for hours, so what moves is read in the layout and draw phases only: the clock's opacity and the colon's breath
 * through colour producers, its spot in the placement, the glow in one drawn radial gradient, Expressive's shape in one
 * cached path ([MinuteShapes]). No recomposition, no layers.
 */
@Composable
public fun ClockScene(
    face: ClockFace?,
    glow: Boolean,
    picture: ImageBitmap?,
    modifier: Modifier = Modifier,
    random: Random = Random.Default,
) {
    val state = remember(random) { ClockSceneState(random) }
    val expressive = TvTheme.lookState.look == Look.Expressive
    // The Expressive glow is one house colour, so it has no colours to turn through; its shape turns instead.
    val cycles = glow && picture == null && !expressive
    val shapes = if (glow && expressive) TvTheme.motion.shapeChange else null
    LaunchedEffect(state, cycles, shapes) { state.run(cycleGlow = cycles, shapes = shapes) }
    ClockSceneContent(face, state, glow, picture, modifier)
}

/**
 * The scene of [state]. With [glow], Glass draws a radial glow behind the clock where no [picture] shows, and Expressive a
 * large shape, filled with the [picture] when there is one and black round it.
 */
@Composable
internal fun ClockSceneContent(
    face: ClockFace?,
    state: ClockSceneState,
    glow: Boolean,
    picture: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val colours = TvTheme.lookColors
    val shaped = glow && TvTheme.lookState.look == Look.Expressive
    Box(modifier.fillMaxSize().drawBehind { drawRect(colours.screensaverGround()) }) {
        if (picture != null && !shaped) {
            Image(bitmap = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val behind =
            when {
                shaped -> Behind.Shape
                glow && picture == null -> Behind.Glow
                else -> Behind.Nothing
            }
        SpotLayout(state, behind, picture) {
            if (face != null) ClockText(face, state)
        }
    }
}

/** What the scene draws behind the clock. */
private enum class Behind { Nothing, Glow, Shape }

/** Places its one child at the state's spot, with the glow or the shape centred behind it. */
@Composable
private fun SpotLayout(
    state: ClockSceneState,
    behind: Behind,
    picture: ImageBitmap?,
    content: @Composable () -> Unit,
) {
    // Written while placing, read while drawing: the glow needs the clock's size, and layout always runs before draw.
    val clockSize = remember { IntArray(2) }
    val colours = TvTheme.lookColors
    val shapes = remember { MinuteShapes() }
    val behindModifier =
        when (behind) {
            Behind.Nothing -> {
                Modifier
            }

            Behind.Glow -> {
                Modifier.drawBehind {
                    drawRect(glowBrush(colours.screensaverGlow(state.glow), clockCenter(clockSize, state.spot), size.width))
                }
            }

            Behind.Shape -> {
                Modifier.drawBehind {
                    with(shapes) { drawShape(state.turn, state.change.value, clockCenter(clockSize, state.spot), colours, picture) }
                }
            }
        }
    Layout(content = content, modifier = Modifier.fillMaxSize().then(behindModifier)) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable != null) {
                clockSize[0] = placeable.width
                clockSize[1] = placeable.height
                placeable.place(
                    IntSize(constraints.maxWidth, constraints.maxHeight).cornerAt(IntSize(placeable.width, placeable.height), state.spot),
                )
            }
        }
    }
}

/** The centre of a clock of [clockSize], width then height, placed at [spot] in this scope. */
private fun DrawScope.clockCenter(
    clockSize: IntArray,
    spot: Spot,
): Offset = IntSize(size.width.toInt(), size.height.toInt()).centerOf(IntSize(clockSize[0], clockSize[1]), spot)

/** The time over the date. The hours, colon and minutes read left to right in a right-to-left layout too; the rest mirrors. */
@Composable
private fun ClockText(
    face: ClockFace,
    state: ClockSceneState,
) {
    val typography = TvTheme.typography
    val colours = TvTheme.lookColors
    val breath = rememberColonBreath()
    Column {
        Row {
            val onBaseline = Modifier.alignByBaseline()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(onBaseline) {
                    val digitsOnBaseline = Modifier.alignByBaseline()
                    FadingText(
                        face.hours,
                        typography.screensaverClock,
                        colours.screensaverTime,
                        state,
                        digitsOnBaseline.testTag("clock:hours"),
                    )
                    FadingText(
                        ":",
                        typography.screensaverClock,
                        colours.screensaverTime,
                        state,
                        digitsOnBaseline.testTag("clock:colon"),
                        breath,
                    )
                    FadingText(
                        face.minutes,
                        typography.screensaverClock,
                        colours.screensaverTime,
                        state,
                        digitsOnBaseline.testTag("clock:minutes"),
                    )
                }
            }
            if (face.amPm.isNotEmpty()) {
                FadingText(
                    face.amPm,
                    typography.screensaverAmPm,
                    colours.screensaverAmPm,
                    state,
                    onBaseline.padding(start = 14.dp).testTag("clock:am_pm"),
                )
            }
        }
        FadingText(
            face.date,
            typography.screensaverDate,
            colours.screensaverDate,
            state,
            Modifier.padding(start = 10.dp, top = 6.dp).testTag("clock:date"),
        )
    }
}

/**
 * The colon's opacity, from full to [COLON_LOW] and back, as a state that only draw lambdas read. It stays at full
 * when the theme's motion does not run endless loops, and in Expressive, which has none.
 */
@Composable
internal fun rememberColonBreath(): State<Float> {
    val motion = TvTheme.motion
    if (!motion.animated || motion.look == Look.Expressive) return remember { mutableStateOf(1f) }
    return rememberInfiniteTransition(label = "colon").animateFloat(
        initialValue = 1f,
        targetValue = COLON_LOW,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
}

/** Text in [colour] whose opacity follows the scene's fade, and [breath] where given, in the draw phase. */
@Composable
private fun FadingText(
    text: String,
    style: TextStyle,
    colour: ColorProducer,
    state: ClockSceneState,
    modifier: Modifier = Modifier,
    breath: State<Float>? = null,
) {
    BasicText(text, modifier = modifier, style = style, color = { colour().withFade(state.fade.value * (breath?.value ?: 1f)) })
}
