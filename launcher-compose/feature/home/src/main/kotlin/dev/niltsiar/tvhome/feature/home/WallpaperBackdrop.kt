package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlin.math.max
import kotlin.math.roundToInt

private const val PICTURE_FADE_MS = 700
private const val CAPTION_OUT_MS = 250
private const val CAPTION_IN_MS = 350
private const val SKY_MID_STOP = 0.55f
private const val SKY_GLOW_Y = 0.80f
private const val SKY_GLOW_RADIUS = 0.55f
private const val MAX_ALPHA = 255f
private val CaptionMaxWidth = 520.dp
private const val TOP_SCRIM_REACH = 0.45f
private const val LEFT_SCRIM_REACH = 0.55f

/**
 * The hero block's scrim: [HERO_SCRIM_ALPHA] black from the screen's left edge to the end of the block's longest line, gone
 * [HeroScrimReach] past it; feathered [HeroScrimFeather] above and below the block, and held [HeroScrimBelow] past its
 * bottom: the subtitle line ends 12 dp above that edge (22 dp with the cinematic shelf), so 24 dp of full strength lie under it.
 */
private val HeroScrimReach = 120.dp
private val HeroScrimFeather = 48.dp
private val HeroScrimBelow = 12.dp
private const val HERO_SCRIM_ALPHA = 0.60f

/** The width of the hero block's box, from the screen margin, whose picture the scrim's brightness is read from. */
internal val HeroSampleWidth = 480.dp

/** A pixel of the picture under the hero block is bright above this relative luminance, and the block gets its scrim from this share of them. */
internal const val HERO_BRIGHT_LUMINANCE = 0.5f
internal const val HERO_BRIGHT_SHARE = 0.12f

/**
 * What is behind Home, drawn in the draw phase only: the picture, the time-of-day sky, then the scrim that keeps the
 * white text readable on any picture ([drawHomeScrim], which the chrome's frost bakes too, with the fixed strength). The
 * scrim's strength follows [headerLightness] unless the theme says header_scrim off. A new picture cross-fades
 * in over the old one with no recomposition per frame. These parts sit in one offscreen layer that the GPU redraws only
 * when they change, so a frame costs one full-screen fill for them instead of one per part. The [cinematic] shelf's art,
 * glow and scrim come last, outside that layer, as they animate on each dock move.
 */
@Composable
internal fun WallpaperBackdrop(
    wallpaper: () -> Wallpaper,
    layout: WallpaperLayout,
    modifier: Modifier = Modifier,
    cinematic: CinematicBackdrop? = null,
    headerLightness: () -> Float = { 0f },
) {
    val picture by remember(wallpaper) { derivedStateOf { wallpaper().picture } }
    val sky by remember(wallpaper) { derivedStateOf { wallpaper().sky } }
    val fade = remember { PictureFade(picture) }
    LaunchedEffect(picture, layout.crossfade) { fade.show(picture, if (layout.crossfade) PICTURE_FADE_MS else 0) }
    val skyAlpha = layout.skyIntensity / 100f
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    cinematic?.draw(this)
                },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawBehind {
                    fade.draw(this)
                    if (skyAlpha > 0f) sky?.let { drawSky(it, skyAlpha) }
                    fade.drawScrim(this, if (layout.headerScrim == HeaderScrim.Auto) scrimFor(headerLightness()) else ScrimStrength.Fixed)
                },
        )
    }
}

/**
 * The picture on screen and the one it replaces, and the scrim over them, which fades in with a first picture. They
 * draw in the draw phase; only [progress] moves while fading.
 */
private class PictureFade(
    initial: ImageBitmap?,
) {
    private var from: ImageBitmap? = null
    private var to by mutableStateOf(initial)
    private val progress = Animatable(1f)

    suspend fun show(
        next: ImageBitmap?,
        millis: Int,
    ) {
        if (next === to) return
        from = to
        progress.snapTo(if (millis > 0) 0f else 1f)
        to = next
        if (millis > 0) progress.animateTo(1f, tween(millis, easing = LinearEasing))
    }

    fun draw(scope: DrawScope) =
        with(scope) {
            val t = progress.value
            val next = to
            val previous = from.takeIf { t < 1f }
            previous?.let { drawCover(it, 1f) }
            if (next != null) drawCover(next, t)
        }

    fun drawScrim(
        scope: DrawScope,
        strength: ScrimStrength,
    ) {
        if (to == null) return
        val t = progress.value
        scope.drawHomeScrim(strength, alpha = if (from == null || t >= 1f) t else 1f)
    }
}

/** The picture scaled to cover the draw area and centred, as ContentScale.Crop would. */
private fun DrawScope.drawCover(
    image: ImageBitmap,
    alpha: Float,
) {
    val scale = max(size.width / image.width, size.height / image.height)
    val sourceWidth = (size.width / scale).roundToInt().coerceIn(1, image.width)
    val sourceHeight = (size.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - sourceWidth) / 2, (image.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

/**
 * Whether the hero block (the title and its line) gets a local scrim and a pill under its subtitle: the theme leaves
 * header_scrim on auto, and the picture under the block is bright ([GlassState.heroBrightShare] at [HERO_BRIGHT_SHARE] or more).
 */
@Composable
internal fun rememberHeroScrimOn(
    glass: GlassState,
    headerScrim: HeaderScrim,
): State<Boolean> =
    remember(glass, headerScrim) { derivedStateOf { headerScrim == HeaderScrim.Auto && glass.heroBrightShare >= HERO_BRIGHT_SHARE } }

/**
 * A soft scrim behind the hero block while [on], over the [boxHeight] of the block, [HeroScrimBelow] more, and a feather
 * above and below it. It runs from the screen's left edge over [ScreenMargin] and the block's text, [blockWidth] pixels
 * wide, and fades out [HeroScrimReach] past it.
 * It sits in one cached offscreen layer that [shift] moves in the layer phase, so a move draws nothing again.
 */
@Composable
internal fun HeroScrim(
    on: State<Boolean>,
    boxHeight: Dp,
    blockWidth: () -> Int,
    shift: GraphicsLayerScope.() -> Float,
) {
    if (!on.value) return
    val width = with(LocalDensity.current) { ScreenMargin + blockWidth().toDp() + HeroScrimReach }
    Box(
        Modifier
            .padding(top = ContentTop - HeroScrimFeather)
            .size(width, boxHeight + HeroScrimBelow + HeroScrimFeather * 2)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                translationY = shift()
            }.drawBehind {
                val edge = HeroScrimFeather.toPx() / size.height
                val strong = Color.Black.copy(alpha = HERO_SCRIM_ALPHA)
                drawRect(Brush.horizontalGradient(0f to strong, 1f - HeroScrimReach.toPx() / size.width to strong, 1f to Color.Transparent))
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/** Darkens the top and the left of the picture, where the top bar and the titles sit. */
internal fun DrawScope.drawHomeScrim(
    strength: ScrimStrength = ScrimStrength.Fixed,
    alpha: Float = 1f,
) {
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = strength.top), TOP_SCRIM_REACH to Color.Transparent), alpha = alpha)
    drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = strength.left), LEFT_SCRIM_REACH to Color.Transparent), alpha = alpha)
}

/** Night blues to a warm horizon, with a glow that travels with the sun, over everything drawn before it. */
internal fun DrawScope.drawSky(
    sky: SkyPalette,
    alpha: Float,
) {
    drawRect(
        brush = Brush.verticalGradient(0f to Color(sky.top), SKY_MID_STOP to Color(sky.mid), 1f to Color(sky.bottom)),
        alpha = alpha,
    )
    if (sky.sun > 0) {
        val glow = Color(sky.glow)
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = sky.sun / MAX_ALPHA), glow.copy(alpha = 0f)),
                    center = Offset(size.width * sky.sunX, size.height * SKY_GLOW_Y),
                    radius = size.width * SKY_GLOW_RADIUS,
                ),
            alpha = alpha,
        )
    }
}

/** The picture's name, one line. A new name fades the old one out and itself in; no name fades it out. */
@Composable
internal fun WallpaperCaption(
    caption: () -> String?,
    crossfade: Boolean,
    modifier: Modifier = Modifier,
) {
    val target by remember(caption) { derivedStateOf(caption) }
    var shown by remember { mutableStateOf(target) }
    val alpha = remember { Animatable(if (target == null) 0f else 1f) }
    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        alpha.animateTo(0f, tween(if (crossfade) CAPTION_OUT_MS else 0))
        shown = target
        if (target != null) alpha.animateTo(1f, tween(if (crossfade) CAPTION_IN_MS else 0))
    }
    val text = shown ?: return
    val density = LocalDensity.current.density
    val base = TvTheme.typography.caption
    val style = remember(base, density) { base.copy(shadow = Shadow(Color(0xCC000000), Offset(0f, density), 6 * density)) }
    BasicText(
        text = text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .widthIn(max = CaptionMaxWidth)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.alpha = alpha.value
                },
    )
}
