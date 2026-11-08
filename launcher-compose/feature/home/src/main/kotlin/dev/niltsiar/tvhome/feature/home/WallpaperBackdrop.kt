package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import kotlin.math.pow
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
 * The hero block's scrim: black at its core alpha from the screen's left edge to the end of the block's longest line, gone
 * [HeroScrimReach] past it; [HeroScrimAbove] above the block, and held [HeroScrimBelow] past its bottom, then gone over
 * [HeroScrimFall]: the subtitle line ends 12 dp above that edge (22 dp with the cinematic shelf), so 24 dp of full strength
 * lie under it. Every edge falls off as a smoothstep, so none shows as a line.
 */
private val HeroScrimReach = 240.dp
private val HeroScrimAbove = 96.dp
private val HeroScrimBelow = 12.dp
private val HeroScrimFall = 120.dp
private const val FALL_STEPS = 16

/** The width of the hero block's box, from the screen margin, whose picture the scrim's brightness is read from. */
internal val HeroSampleWidth = 480.dp

/**
 * The sRGB brightness the hero scrim darkens a light picture to, behind the text: a grey of 0.38 has a relative luminance of
 * 0.12, where white text holds 6:1. The scrim never darkens a picture by more than [HERO_SCRIM_MAX_ALPHA].
 */
private const val HERO_SCRIM_TARGET_BRIGHTNESS = 0.38f
private const val HERO_SCRIM_MAX_ALPHA = 0.50f

/**
 * How much the hero block darkens a picture of mean relative luminance [luminance] under it, Home's left scrim included: none up
 * to a brightness of [HERO_SCRIM_TARGET_BRIGHTNESS] (luminance 0.12), then what takes the picture's brightness down to it, up
 * to [HERO_SCRIM_MAX_ALPHA].
 */
internal fun heroScrimDarkening(luminance: Float): Float {
    val brightness = if (luminance <= 0.0031308f) 12.92f * luminance else 1.055f * luminance.pow(1f / 2.4f) - 0.055f
    return (1f - HERO_SCRIM_TARGET_BRIGHTNESS / brightness).coerceIn(0f, HERO_SCRIM_MAX_ALPHA)
}

/**
 * The hero scrim's [darkening] under the title, and the [homeLeft] strength of Home's left scrim, which the hero scrim adds
 * only what is missing to: the block is [darkening] dark in all, wherever Home's scrim is weaker.
 */
@Immutable
internal data class HeroScrimLook(
    val darkening: Float,
    val homeLeft: Float,
)

/** Called each time the hero scrim's body is drawn into its layer; a test counts the calls. */
internal var onHeroBodyDraw: () -> Unit = {}

/** The alpha of the hero scrim at [x] pixels from the screen's left edge, when Home's left scrim reaches [reach] pixels. */
private fun HeroScrimLook.alphaAt(
    x: Float,
    reach: Float,
): Float {
    val home = homeLeft * (1f - x / reach).coerceAtLeast(0f)
    return (1f - (1f - darkening) / (1f - home)).coerceAtLeast(0f)
}

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
 * The hero block's scrim: [heroScrimDarkening] of [GlassState.heroLuminance], and Home's left strength under it; none unless
 * the theme leaves header_scrim on auto. The block gets its scrim, the pill under its subtitle and its tight title shadow
 * while the darkening is above 0.
 */
@Composable
internal fun rememberHeroScrim(
    glass: GlassState,
    headerScrim: HeaderScrim,
): State<HeroScrimLook> =
    remember(glass, headerScrim) {
        derivedStateOf {
            if (headerScrim == HeaderScrim.Auto) {
                HeroScrimLook(heroScrimDarkening(glass.heroLuminance), scrimFor(glass.headerLightness).left)
            } else {
                HeroScrimLook(0f, 0f)
            }
        }
    }

/**
 * A soft scrim behind the hero block while [look] darkens it, over the [boxHeight] of the block, [HeroScrimBelow] more, and the
 * falls above and below it. It runs from the screen's left edge over [ScreenMargin] and the block's text, as wide as [shelf] and
 * [band] say they are drawn now, and fades out [HeroScrimReach] past it. Its alpha rises from the left as Home's left scrim
 * weakens. That width is read in the draw and layer phases, so it follows a title's cross-fade without recomposing. The part
 * under the text is a cached offscreen layer of the screen's width, clipped to the width when it is drawn into the box, and the
 * falloff is a cached offscreen layer of fixed width that the width moves in the layer phase, so a cross-fade rasterises neither
 * again and no layout bound depends on the width. [shift] moves both in the layer phase, and [fade] (0 to 1) is the alpha of
 * both, read there too.
 */
@Composable
internal fun HeroScrim(
    look: State<HeroScrimLook>,
    boxHeight: Dp,
    shelf: TitleExtent,
    band: TitleExtent,
    shift: GraphicsLayerScope.() -> Float,
    fade: () -> Float,
) {
    val scrim = look.value
    if (scrim.darkening <= 0f) return
    val reach = LEFT_SCRIM_REACH * LocalWindowInfo.current.containerSize.width
    val height = HeroScrimAbove + boxHeight + HeroScrimBelow + HeroScrimFall
    val solid: Density.() -> Float = { (ScreenMargin.toPx() + max(shelf.px(), band.px())).roundToInt().toFloat() }
    Box(
        Modifier
            .padding(top = ContentTop - HeroScrimAbove)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = fade()
                translationY = shift()
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { clipRect(right = solid()) { this@drawWithContent.drawContent() } }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val above = HeroScrimAbove.toPx()
                    val bottom = above + boxHeight.toPx() + HeroScrimBelow.toPx()
                    val across =
                        Brush.horizontalGradient(
                            colorStops =
                                Array(FALL_STEPS + 1) { step ->
                                    val fraction = step / FALL_STEPS.toFloat()
                                    fraction to Color.Black.copy(alpha = scrim.alphaAt(fraction * reach, reach))
                                },
                            startX = 0f,
                            endX = reach,
                        )
                    val mask = verticalMask(above / size.height, bottom / size.height)
                    onDrawBehind {
                        onHeroBodyDraw()
                        drawRect(across)
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                },
        )
        Box(
            Modifier
                .size(HeroScrimReach, height)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    translationX = solid()
                    alpha = scrim.alphaAt(solid(), reach)
                }.drawWithCache {
                    val above = HeroScrimAbove.toPx()
                    val bottom = above + boxHeight.toPx() + HeroScrimBelow.toPx()
                    val across = Brush.horizontalGradient(*smoothStops(0f, 1f, rising = false, Color.Black), startX = 0f, endX = size.width)
                    val mask = verticalMask(above / size.height, bottom / size.height)
                    onDrawBehind {
                        drawRect(across)
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                },
        )
    }
}

/** Black rising from clear to whole by [riseEnd] and falling from whole to clear from [fallStart] (fractions of the height). */
private fun verticalMask(
    riseEnd: Float,
    fallStart: Float,
): Brush =
    Brush.verticalGradient(
        *(
            smoothStops(0f, riseEnd, rising = true, Color.Black) +
                smoothStops(fallStart, 1f, rising = false, Color.Black)
        ),
    )

/**
 * The colour stops of a smoothstep between [from] and [to] (fractions of a gradient), from clear up to [color] or, with
 * [rising] false, from [color] down to clear. A gradient is linear between stops, so [FALL_STEPS] of them follow the curve.
 */
private fun smoothStops(
    from: Float,
    to: Float,
    rising: Boolean,
    color: Color,
): Array<Pair<Float, Color>> =
    Array(FALL_STEPS + 1) { step ->
        val t = step / FALL_STEPS.toFloat()
        val smooth = t * t * (3f - 2f * t)
        from + (to - from) * t to color.copy(alpha = color.alpha * if (rising) smooth else 1f - smooth)
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
