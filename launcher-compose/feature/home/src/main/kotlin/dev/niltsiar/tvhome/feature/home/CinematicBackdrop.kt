package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val ART_IN_MS = 420
private const val ART_FIRST_IN_MS = 600
private const val HIDE_MS = 300
private const val GLOW_FIRST_IN_MS = 600
private const val SCRIM_W = 480
private const val SCRIM_H = 270
private val ScrimColor = Color(0xFF060406)
private val NoArtGlow = Color(0xFF444444)

/**
 * The cinematic shelf's part of the backdrop, drawn between the picture and the UI by [draw] in the draw phase:
 * the focused app's softened art stretched over the screen, the glow in its colour from the top corner away from the
 * text, and the scrim that keeps the text and the dock readable. Alpha is applied while drawing, never through a layer.
 * From one app to the next the art cross-fades and the glow changes colour, both in one [cinematicTween].
 * [show] and [hide] run the fades; one coroutine at a time may call them, and a new call continues from what is on screen.
 */
@Stable
internal class CinematicBackdrop(
    private val layout: ShelfLayout,
    private val direction: LayoutDirection,
) {
    private var art by mutableStateOf<ImageBitmap?>(null)
    private var artKey: String? = null

    /** The art that fades out under [art] while [artMix] goes to 1. */
    private var fromArt by mutableStateOf<ImageBitmap?>(null)
    private var fromKey: String? = null
    private val artAlpha = Animatable(0f)
    private val artMix = Animatable(1f)
    private val glowColor = Animatable(NoArtGlow, Color.VectorConverter(NoArtGlow.colorSpace))
    private val glowAlpha = Animatable(0f)
    private var scrim by mutableStateOf<ImageBitmap?>(null)
    private var glowBrush: Brush? = null
    private var glowBrushColor = Color.Unspecified
    private var glowBrushSize = Size.Zero

    /** Builds the scrim picture; the first frames draw without it. */
    suspend fun prepareScrim() {
        if (layout.scrimAlpha > 0f && scrim == null) scrim = withContext(Dispatchers.Default) { buildScrim(direction) }
    }

    fun draw(scope: DrawScope) =
        with(scope) {
            val alpha = artAlpha.value
            if (alpha > 0f) {
                val mix = artMix.value
                val from = fromArt
                if (from != null && mix < 1f) drawArt(from, alpha * (1f - mix))
                art?.let { if (mix > 0f) drawArt(it, alpha * mix) }
            }
            val glow = glowAlpha.value
            if (glow > 0f) drawRect(glowBrush(glowColor.value, size), alpha = glow)
            scrim?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = layout.scrimAlpha) }
        }

    private fun DrawScope.drawArt(
        image: ImageBitmap,
        alpha: Float,
    ) = drawImage(
        image,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )

    /** Focus is on the dock tile [key]: its [accent] colours the glow, and [loadArt] gives the art to fade in. */
    suspend fun show(
        key: String,
        accent: Color,
        loadArt: suspend () -> ImageBitmap?,
    ) {
        coroutineScope {
            if (layout.ambientGlow) launch { showGlow(accent.takeIf { it.isSpecified } ?: NoArtGlow) }
            if (layout.artAlpha > 0f) showArt(key, loadArt)
        }
    }

    /** Focus left the dock: the art and the glow fade out. */
    suspend fun hide() {
        coroutineScope {
            launch { fadeTo(glowAlpha, 0f, HIDE_MS) }
            launch { fadeTo(artAlpha, 0f, HIDE_MS) }
        }
    }

    private suspend fun showArt(
        key: String,
        loadArt: suspend () -> ImageBitmap?,
    ) {
        val back = key == artKey
        if (!back && key == fromKey && artMix.value < 1f) {
            // Back to the art still fading out: the mix runs the other way from where it is.
            fromArt = art.also { art = fromArt }
            fromKey = artKey.also { artKey = fromKey }
            artMix.snapTo(1f - artMix.value)
        } else if (!back) {
            val next = loadArt()
            val blend = layout.animated && artAlpha.value > 0f && art != null
            fromArt = if (blend) art else null
            fromKey = if (blend) artKey else null
            art = next
            artKey = key
            artMix.snapTo(if (blend) 0f else 1f)
        }
        coroutineScope {
            if (artAlpha.value < layout.artAlpha) launch { fadeTo(artAlpha, layout.artAlpha, if (back) ART_IN_MS else ART_FIRST_IN_MS) }
            if (artMix.value < 1f) {
                artMix.animateTo(1f, cinematicTween())
                fromArt = null
                fromKey = null
            }
        }
    }

    private suspend fun showGlow(color: Color) {
        coroutineScope {
            val shown = glowAlpha.value > 0f
            if (glowAlpha.value < 1f) launch { fadeTo(glowAlpha, 1f, GLOW_FIRST_IN_MS) }
            if (shown && layout.animated && !layout.glowSnap) glowColor.animateTo(color, cinematicTween()) else glowColor.snapTo(color)
        }
    }

    private suspend fun fadeTo(
        alpha: Animatable<Float, *>,
        target: Float,
        millis: Int,
    ) {
        if (layout.animated && millis > 0) alpha.animateTo(target, tween(millis, easing = LinearEasing)) else alpha.snapTo(target)
    }

    private fun glowBrush(
        color: Color,
        size: Size,
    ): Brush {
        glowBrush?.let { if (color == glowBrushColor && size == glowBrushSize) return it }
        val brush =
            Brush.radialGradient(
                0f to color.copy(alpha = GLOW_CORE),
                GLOW_MID_STOP to color.copy(alpha = GLOW_MID),
                1f to color.copy(alpha = 0f),
                center = Offset(size.width * (if (direction == LayoutDirection.Rtl) GLOW_X_RTL else GLOW_X), size.height * GLOW_Y),
                radius = size.width * GLOW_RADIUS,
            )
        glowBrush = brush
        glowBrushColor = color
        glowBrushSize = size
        return brush
    }
}

private const val GLOW_CORE = 0xA6 / 255f
private const val GLOW_MID = 0x47 / 255f
private const val GLOW_MID_STOP = 0.5f
private const val GLOW_X = 0.80f
private const val GLOW_X_RTL = 0.20f
private const val GLOW_Y = 0.06f
private const val GLOW_RADIUS = 0.78f

/** The scrim as a small picture (480x270), so its strength is the alpha it is drawn with: one texture, no layer. */
private fun buildScrim(direction: LayoutDirection): ImageBitmap {
    val image = ImageBitmap(SCRIM_W, SCRIM_H)
    val rtl = direction == LayoutDirection.Rtl
    val side =
        Brush.horizontalGradient(
            0f to Color(0xD1060406),
            0.42f to Color(0x59060406),
            0.7f to Color(0x00060406),
            startX = if (rtl) SCRIM_W.toFloat() else 0f,
            endX = if (rtl) 0f else SCRIM_W.toFloat(),
        )
    val bottom =
        Brush.verticalGradient(
            0f to ScrimColor.copy(alpha = 0f),
            0.28f to ScrimColor.copy(alpha = 0f),
            0.58f to Color(0xDB060406),
            0.76f to ScrimColor,
        )
    CanvasDrawScope().draw(Density(1f), direction, Canvas(image), Size(SCRIM_W.toFloat(), SCRIM_H.toFloat())) {
        drawRect(side)
        drawRect(bottom)
    }
    return image
}

private data class ShelfFocus(
    val key: String,
    val accent: Color,
)

/**
 * The cinematic backdrop for [layout], or null without cinematic_home. It follows [shelf]: art is loaded with [heroArt]
 * off the main thread, and the focus moving off the dock fades it out.
 */
@Composable
internal fun rememberCinematicBackdrop(
    layout: ShelfLayout,
    shelf: () -> Shelf?,
    heroArt: suspend (String) -> ImageBitmap?,
): CinematicBackdrop? {
    if (!layout.cinematic) return null
    val direction = LocalLayoutDirection.current
    val backdrop = remember(layout, direction) { CinematicBackdrop(layout, direction) }
    val currentShelf by rememberUpdatedState(shelf)
    val currentHeroArt by rememberUpdatedState(heroArt)
    LaunchedEffect(backdrop) { backdrop.prepareScrim() }
    LaunchedEffect(backdrop) {
        snapshotFlow { currentShelf()?.let { ShelfFocus(it.key, it.accent) } }
            .distinctUntilChanged()
            .collectLatest { focus ->
                if (focus == null) backdrop.hide() else backdrop.show(focus.key, focus.accent) { currentHeroArt(focus.key) }
            }
    }
    return backdrop
}
