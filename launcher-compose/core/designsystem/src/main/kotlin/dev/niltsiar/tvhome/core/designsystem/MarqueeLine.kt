package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** basicMarquee's defaults: the wait before each pass, the speed, and how many passes it makes. */
private const val MARQUEE_DELAY_MS = 1_200L
private val MarqueeVelocity = 30.dp
private const val MARQUEE_PASSES = 3

/** The space between the end of the text and its next copy, as a part of the box's width, as basicMarquee leaves it. */
private const val MARQUEE_GAP = 1f / 3f

/**
 * [text] on one line, centred in the height of the box [modifier] gives it, scrolled sideways as basicMarquee scrolls it while
 * [scrolling] is true, and not drawn while it is false; each time [scrolling] turns true it starts again from the start. It
 * is drawn, not laid out as text, and [scrolling] is read in the draw phase and in an effect, so a change of it neither
 * recomposes nor relays out: lay it over a still copy that hides while it scrolls and gives the text to accessibility.
 */
@Composable
public fun MarqueeLine(
    text: String,
    style: TextStyle,
    scrolling: () -> Boolean,
    modifier: Modifier = Modifier,
    color: ColorProducer? = null,
) {
    val measurer = rememberTextMeasurer()
    val line =
        remember(text, style, measurer) { measurer.measure(text, style.copy(textAlign = TextAlign.Start), maxLines = 1, softWrap = false) }
    val offset = remember { Animatable(0f) }
    var boxWidth by remember { mutableIntStateOf(0) }
    val speed = with(LocalDensity.current) { MarqueeVelocity.toPx() } / 1_000f
    LaunchedEffect(offset, line, speed) {
        snapshotFlow { scrolling() }.collectLatest { on ->
            offset.snapTo(0f)
            if (!on) return@collectLatest
            repeat(MARQUEE_PASSES) {
                delay(MARQUEE_DELAY_MS)
                val distance = line.size.width + boxWidth * MARQUEE_GAP
                offset.animateTo(distance, tween((distance / speed).toInt(), easing = LinearEasing))
                offset.snapTo(0f)
            }
        }
    }
    Spacer(
        modifier
            .onSizeChanged { boxWidth = it.width }
            .clipToBounds()
            .drawBehind {
                if (!scrolling()) return@drawBehind
                val top = (size.height - line.size.height) / 2f
                val tint = color?.invoke() ?: Color.Unspecified
                drawText(line, tint, Offset(-offset.value, top))
                drawText(line, tint, Offset(line.size.width + size.width * MARQUEE_GAP - offset.value, top))
            },
    )
}
