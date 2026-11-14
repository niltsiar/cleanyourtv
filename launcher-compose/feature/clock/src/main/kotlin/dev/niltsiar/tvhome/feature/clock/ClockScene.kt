package dev.niltsiar.tvhome.feature.clock

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlin.random.Random

/** How far the colon dims at the bottom of a breath, and how long a breath lasts. */
private const val COLON_LOW = 0.25f
private const val BREATH_MS = 1_800

/**
 * The clock screensaver, for the activity and the system dream alike: a large clock and date on black that drift to a
 * new spot every minute, over a slow glow, or over [picture] (Home's wallpaper, already darkened) in place of the glow.
 * [glow] false leaves plain black behind the clock, e.g. for an OLED. A null [face] shows nothing until the TV knows the
 * time.
 *
 * It runs for hours, so what moves is read in the layout and draw phases only: the clock's opacity and the colon's breath
 * through colour producers, its spot in the placement, the glow in one drawn radial gradient. No recomposition, no layers.
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
    val glowing = glow && picture == null
    LaunchedEffect(state, glowing) { state.run(cycleGlow = glowing) }
    ClockSceneContent(face, state, glowing, picture, modifier)
}

@Composable
internal fun ClockSceneContent(
    face: ClockFace?,
    state: ClockSceneState,
    glowing: Boolean,
    picture: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (picture != null) {
            Image(bitmap = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        SpotLayout(state, glowing) {
            if (face != null) ClockText(face, state)
        }
    }
}

/** Places its one child at the state's spot, with the glow centred behind it. */
@Composable
private fun SpotLayout(
    state: ClockSceneState,
    glowing: Boolean,
    content: @Composable () -> Unit,
) {
    // Written while placing, read while drawing: the glow needs the clock's size, and layout always runs before draw.
    val clockSize = remember { IntArray(2) }
    val glowModifier =
        if (glowing) {
            Modifier.drawBehind {
                val center = IntSize(size.width.toInt(), size.height.toInt()).centerOf(IntSize(clockSize[0], clockSize[1]), state.spot)
                drawRect(glowBrush(state.glow, center, size.width))
            }
        } else {
            Modifier
        }
    Layout(content = content, modifier = Modifier.fillMaxSize().then(glowModifier)) { measurables, constraints ->
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

/** The time over the date. The hours, colon and minutes read left to right in a right-to-left layout too; the rest mirrors. */
@Composable
private fun ClockText(
    face: ClockFace,
    state: ClockSceneState,
) {
    val typography = TvTheme.typography
    val breath = rememberColonBreath()
    Column {
        Row {
            val onBaseline = Modifier.alignByBaseline()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(onBaseline) {
                    val digitsOnBaseline = Modifier.alignByBaseline()
                    FadingText(face.hours, typography.screensaverClock, state, digitsOnBaseline)
                    FadingText(":", typography.screensaverClock, state, digitsOnBaseline, breath)
                    FadingText(face.minutes, typography.screensaverClock, state, digitsOnBaseline)
                }
            }
            if (face.amPm.isNotEmpty()) {
                FadingText(face.amPm, typography.screensaverAmPm, state, onBaseline.padding(start = 14.dp))
            }
        }
        FadingText(face.date, typography.screensaverDate, state, Modifier.padding(start = 10.dp, top = 6.dp))
    }
}

/**
 * The colon's opacity, from full to [COLON_LOW] and back, as a state that only draw lambdas read. It stays at full
 * when the theme's motion does not run endless loops.
 */
@Composable
internal fun rememberColonBreath(): State<Float> {
    if (!TvTheme.motion.animated) return remember { mutableStateOf(1f) }
    return rememberInfiniteTransition(label = "colon").animateFloat(
        initialValue = 1f,
        targetValue = COLON_LOW,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
}

/** Text whose opacity follows the scene's fade, and [breath] where given, in the draw phase. */
@Composable
private fun FadingText(
    text: String,
    style: TextStyle,
    state: ClockSceneState,
    modifier: Modifier = Modifier,
    breath: State<Float>? = null,
) {
    BasicText(text, modifier = modifier, style = style, color = { style.color.withFade(state.fade.value * (breath?.value ?: 1f)) })
}
