package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme

/** 40 x 3 px on a 1920 x 1080 screen, which is 20 x 3 dp at the TV's 2x density, with a 1 px edge. */
internal val HabitMarkWidth = 20.dp
internal val HabitMarkHeight = 3.dp
private val HabitMarkEdgeWidth = 0.5.dp
internal val HabitMarkFill = Color(0xFFF2F4F8)
private val HabitMarkEdge = Color.Black.copy(alpha = 0.35f)
private const val HABIT_FADE_MS = 400

/**
 * The mark under the tile of the app [habit] names, if that is [packageName]: a bar centred in the [TileGap] under the
 * modifier's bounds, midway between the focus rings of the tile above and of the tile below. It fades over
 * [HABIT_FADE_MS] when the habit changes, and jumps with reduced motion. Nothing is read in composition: the habit is
 * followed in an effect and the fade is read in the draw lambda.
 */
@Composable
internal fun Modifier.habitMark(
    habit: State<String?>?,
    packageName: String,
): Modifier {
    if (habit == null) return this
    val animated = TvTheme.motion.animated
    val fade = remember(packageName) { Animatable(if (Snapshot.withoutReadObservation { habit.value } == packageName) 1f else 0f) }
    LaunchedEffect(habit, packageName, animated) {
        snapshotFlow { habit.value == packageName }.collect { usual ->
            val target = if (usual) 1f else 0f
            if (animated) fade.animateTo(target, tween(HABIT_FADE_MS)) else fade.snapTo(target)
        }
    }
    return drawBehind {
        val alpha = fade.value
        if (alpha <= 0f) return@drawBehind
        val size = Size(HabitMarkWidth.toPx(), HabitMarkHeight.toPx())
        val at = Offset((this.size.width - size.width) / 2f, this.size.height + (TileGap.toPx() - size.height) / 2f)
        drawRoundRect(HabitMarkFill, at, size, CornerRadius(size.height / 2f), alpha = alpha)
        val edge = HabitMarkEdgeWidth.toPx()
        val inner = Size(size.width - edge, size.height - edge)
        drawRoundRect(
            HabitMarkEdge,
            Offset(at.x + edge / 2f, at.y + edge / 2f),
            inner,
            CornerRadius(inner.height / 2f),
            style = Stroke(edge),
            alpha = alpha,
        )
    }
}
