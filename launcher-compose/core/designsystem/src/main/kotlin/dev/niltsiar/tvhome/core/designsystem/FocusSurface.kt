package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.TilePainter
import dev.niltsiar.tvhome.core.designsystem.expressive.drawExpressiveTile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much a focused [FocusSurface] grows by default. */
public const val TILE_FOCUSED_SCALE: Float = 1.08f
private val RingWidth = 3.dp
private val HeavyRingWidth = 4.dp
private val RingGap = 3.dp

/** From a surface's edge to its ring's outer edge: the gap and the ring, which a wider ring shares out differently. */
internal val RingBand = RingGap + RingWidth
private val HairlineWidth = 1.dp
private val OuterStrokeWidth = 2.dp
private val InnerStrokeClear = 1.dp
private val GlowElevation = 16.dp

/** How much of its size a pressed surface gives up at the bottom of its dip. */
private const val PRESS_DIP = 0.04f

/** How much wider a pressed ring grows into the gap inside it. */
private val PressRingGrowth = 1.dp

/**
 * How a focused [FocusSurface] is lit, from glass_style. [slab] gives it the lit top and, with a [FocusSurface] `plate`, the
 * plate; [glowStrength] scales the colour of its elevation glow, 0 leaving it out. [ringWidth] is the ring's width: a wider
 * ring takes the room from the gap inside it, so its outer edge and [FocusRingReach] stay put. [halo] draws a glow in the
 * ring's colour past the keyline ([FocusHaloElement]), which ends before the edge of a [clipsHalo] container.
 * The ring and its keyline are drawn in every style.
 */
public enum class FocusStyle(
    public val slab: Boolean,
    internal val glowStrength: Float,
    internal val ringWidth: Dp,
    internal val halo: Boolean,
) {
    Glass(slab = true, glowStrength = 1f, ringWidth = RingWidth, halo = false),
    Rim(slab = false, glowStrength = 0f, ringWidth = HeavyRingWidth, halo = false),
    Halo(slab = false, glowStrength = 0f, ringWidth = RingWidth, halo = true),
}

/** How far the ring, with its hairline and dark stroke, reaches past a focused surface's edge, before the surface grows. */
public val FocusRingReach: Dp = RingGap + RingWidth + HairlineWidth + OuterStrokeWidth

/**
 * A D-pad target, for tiles and cards: scales up and gets a lit top, a ring with a dark hairline outside it and a
 * glow while focused, and a 2 dp stroke outside the hairline, so the ring holds on a bright wallpaper; how it
 * moves is the theme's [Motion]. Only the content is clipped to [shape]; the ring floats outside it and its corners are
 * concentric to the shape's, so a parent must leave the grown surface plus 9dp times [focusedScale] free around it or it
 * clips the ring. [onFocused] fires on gaining focus. [interactionSource] receives
 * the press and focus events, for content that reacts to them. [accent] colours the glow; unspecified takes the
 * theme's. [TvTheme.focusStyle] says which of the lit top, plate and glow it gets, and how wide the ring is. [ring]
 * colours the ring, and a halo glow, instead of the theme's. [ringBlends] ([ringBlendsWith]) puts a light stroke across
 * the gap but its inner 1 dp, between the dark gap and the ring. [plate], when specified, is the colour of a plate behind the surface, out to the ring's inner edge with corners concentric to the shape's: it fades in and grows as focus arrives, and a
 * translucent surface shows it. [glow] turns the glow off, a halo glow too: the elevation shadow also
 * shows through a translucent surface, as a dark patch. The animation starts in the focus callback and is read in the layer and draw
 * lambdas, so a focus change neither recomposes nor relays out. [focusedScale] is the size it grows to. The growth is
 * drawn, not a layer scale, so the surface's bounds stay put while it grows and accessibility has nothing to update.
 * [growthPivot], given the surface's height in pixels, says how far under its top the growth is anchored, in the middle
 * of its width; without it the surface grows about its centre. [focus] is its focus progress, for a parent that orders
 * its children by it ([popsOver]). [press] is the progress of a press ([pressDip]), 0 at rest: the surface draws up to 4% under
 * its size, with [Motion.animated] only, and its ring moves toward [TvElementColors.ringPressed] and grows wider into the gap inside it, its outer edge
 * where it was. While OK is held on a surface whose [modifier] has [holdOk], an arc in the ring's colour fills the gap and
 * the ring clockwise from the top centre, whole as the menu opens ([HoldArcElement]), with [Motion.animations] only; in
 * [Look.Expressive], while springs run, the held tile charges instead: it tightens, its capsule corner pinches and a wave
 * runs round its band. While [keepsShape] says so, as while its menu is open, an Expressive surface without focus keeps its
 * content clipped to its focused shape, and lets go of it with the focus fade once it no longer says so.
 *
 * In [Look.Expressive] focus shows as a band of the ring's colour, or the house primary without one, between a gap in the
 * house surface colour and a dark keyline, all inside [FocusRingReach], with no glow; a press moves the band toward
 * [TvElementColors.ringPressed] as it does the ring. [TvTheme] says the band's shape
 * ([dev.niltsiar.tvhome.core.config.ExpressiveTile]). The look and the shape are read in the draw phase.
 */
@Composable
public fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvTheme.shapes.tile,
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    glow: Boolean = true,
    accent: Color = Color.Unspecified,
    focusedScale: Float = TILE_FOCUSED_SCALE,
    ring: ColorProducer? = null,
    ringBlends: Boolean = false,
    plate: Color = Color.Unspecified,
    growthPivot: ((Float) -> Float)? = null,
    focus: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    press: Animatable<Float, AnimationVector1D>? = null,
    keepsShape: (() -> Boolean)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val dips = press != null && motion.animated
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(focusedScale)
    val style = TvTheme.focusStyle
    val colours = TvTheme.elementColors
    val glowing = glow && style.glowStrength > 0f
    val halo = glow && style.halo
    val glowColor = accent.takeOrElse { TvTheme.colors.accent }.let { it.copy(alpha = it.alpha * style.glowStrength) }
    val plateColor = if (style.slab) plate else Color.Unspecified
    val plateFill =
        remember(plateColor, colours) {
            if (plateColor.isUnspecified) {
                null
            } else {
                Brush.verticalGradient(
                    listOf(plateColor.copy(alpha = colours.plateTopAlpha), plateColor.copy(alpha = colours.plateBottomAlpha)),
                )
            }
        }
    val gloss = remember(colours) { Brush.verticalGradient(listOf(colours.tileGloss, Color.Transparent)) }
    val lookState = TvTheme.lookState
    val painter = remember { TilePainter() }
    val tileOutline = remember(shape, lookState, painter, focus) { TileOutline(shape, lookState, painter, focus) }
    val kept = if (keepsShape != null) keptShape(keepsShape, motion) else null
    val band =
        remember(ring, lookState, colours) {
            ring ?: ColorProducer { if (lookState.look == Look.Expressive) lookState.scheme(dark = true).primary else colours.ring }
        }
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    if (lookState.look == Look.Expressive) {
                        // The band and its keyline show the focus; no glow, so no outline to follow per frame.
                        shadowElevation = 0f
                        this.shape = RectangleShape
                    } else {
                        shadowElevation = if (glowing) GlowElevation.toPx() * focus.value.coerceIn(0f, 1f) else 0f
                        this.shape = ScaledShape(shape, (1f + (grownScale - 1f) * focus.value) * dipOf(press, dips), growthPivot)
                    }
                    ambientShadowColor = glowColor
                    spotShadowColor = glowColor
                }.then(
                    if (halo) {
                        FocusHaloElement(focus, grownScale, growthPivot, tileOutline, band, colours, press, dips, lookState, painter)
                    } else {
                        Modifier
                    },
                ).then(
                    if (motion.animations) {
                        HoldArcElement(
                            focus,
                            grownScale,
                            growthPivot,
                            shape,
                            band,
                            colours,
                            press,
                            dips,
                            lookState,
                            painter,
                            motion.animated,
                        )
                    } else {
                        Modifier
                    },
                ).drawWithContent {
                    scale(
                        (1f + (grownScale - 1f) * focus.value) * dipOf(press, dips),
                        Offset(
                            size.width / 2f,
                            growthPivot?.invoke(size.height) ?: (size.height / 2f),
                        ),
                    ) {
                        val progress = focus.value.coerceIn(0f, 1f)
                        // The ring shows whole from the first frame of focus; it fades only on the way out.
                        val ringAlpha = if (focus.targetValue > 0f) 1f else progress
                        val pressed = press?.value ?: 0f
                        if (lookState.look == Look.Expressive) {
                            val rest = band()
                            this@drawWithContent.drawExpressiveTile(
                                painter = painter,
                                tile = lookState.tile,
                                style = style,
                                base = shape,
                                progress = progress,
                                ringAlpha = ringAlpha,
                                band = if (pressed > 0f) lerp(rest, colours.ringPressed, colours.ringPressedMix * pressed) else rest,
                                gap = lookState.scheme(dark = true).surface,
                                keyline = colours.ringKeyline,
                                plate = plateColor,
                                plateAlpha = colours.plateTopAlpha,
                            )
                            return@scale
                        }
                        val outline = if (ringAlpha > 0f) shape.createOutline(size, layoutDirection, this) else null
                        val width = style.ringWidth.toPx()
                        val gap = RingBand.toPx() - width
                        val lit = width + PressRingGrowth.toPx() * pressed
                        val base = ring?.invoke() ?: colours.ring
                        val ringColour = if (pressed > 0f) lerp(base, colours.ringPressed, colours.ringPressedMix * pressed) else base
                        if (outline != null && plateFill != null) {
                            val behind = outline.grownBy(gap)
                            drawOutline(behind, plateFill, alpha = progress)
                            drawOutline(behind, plateColor, alpha = progress * colours.plateEdgeAlpha, style = Stroke(HairlineWidth.toPx()))
                        }
                        this@drawWithContent.drawContent()
                        if (outline != null) {
                            if (style.slab) drawOutline(outline, gloss, alpha = progress)
                            if (ringBlends) {
                                val inner = gap - InnerStrokeClear.toPx()
                                drawOutline(
                                    outline.grownBy(gap - inner / 2f),
                                    colours.ringBlendStroke,
                                    alpha = ringAlpha,
                                    style = Stroke(inner),
                                )
                            }
                            drawOutline(
                                outline.grownBy(gap + width - lit / 2f),
                                ringColour,
                                alpha = ringAlpha,
                                style = Stroke(lit),
                            )
                            val hairline = HairlineWidth.toPx()
                            drawOutline(
                                outline.grownBy(gap + width + hairline / 2f),
                                colours.ringKeyline,
                                alpha = ringAlpha,
                                style = Stroke(hairline),
                            )
                            val stroke = OuterStrokeWidth.toPx()
                            drawOutline(
                                outline.grownBy(gap + width + hairline + stroke / 2f),
                                colours.ringOuterStroke,
                                alpha = ringAlpha,
                                style = Stroke(stroke),
                            )
                        }
                    }
                }.graphicsLayer {
                    this.shape =
                        if (lookState.look == Look.Expressive) {
                            painter.clip(shape, lookState.tile, maxOf(focus.value, kept?.value ?: 0f), size, this)
                        } else {
                            shape
                        }
                    clip = true
                }.onFocusChanged {
                    val target = if (it.isFocused) 1f else 0f
                    if (focus.targetValue != target) {
                        // Undispatched, its first frame is the one that shows the new focus, not the frame after.
                        val spec = motion.focusSpec(it.isFocused, focus.value)
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, spec) }
                    }
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}

/** 1 while [keeps] says so, falling to 0 with the focus fade of [motion] once it no longer does. */
@Composable
private fun keptShape(
    keeps: () -> Boolean,
    motion: Motion,
): Animatable<Float, AnimationVector1D> {
    val kept = remember { Animatable(0f) }
    LaunchedEffect(keeps, motion) {
        snapshotFlow { keeps() }.collect {
            if (it || !motion.animations) kept.snapTo(if (it) 1f else 0f) else kept.animateTo(0f, motion.focusSpec(false, kept.value))
        }
    }
    return kept
}

/** The share of its size a surface draws at for [press]: under 1 while it dips, which only it does when it [dips]. */
internal fun dipOf(
    press: Animatable<Float, AnimationVector1D>?,
    dips: Boolean,
): Float = if (dips && press != null) 1f - PRESS_DIP * press.value else 1f

/**
 * Runs one press of a [FocusSurface] that takes this as its `press`: down and back, each with [Motion.pressDipSpec], or
 * with animations off to full at once and back after [Motion.PRESS_DIP_MS] twice.
 */
public suspend fun Animatable<Float, AnimationVector1D>.pressDip(motion: Motion) {
    if (motion.animations) {
        animateTo(1f, motion.pressDipSpec())
        animateTo(0f, motion.pressDipSpec())
    } else {
        snapTo(1f)
        delay(2L * Motion.PRESS_DIP_MS)
        snapTo(0f)
    }
}

/**
 * When focus pops ([Motion.pops]), draws this item over its siblings while it gains focus, and over the ones at rest while
 * it loses it, so that no neighbour drawn after it covers the ring of a pop. [focus] is the focus progress of the
 * [FocusSurface] in it. The order is read in the placement phase: a focus move does not recompose.
 */
@Composable
public fun Modifier.popsOver(focus: Animatable<Float, AnimationVector1D>): Modifier {
    if (!TvTheme.motion.pops) return this
    val order =
        remember(focus) {
            derivedStateOf {
                when {
                    focus.targetValue > 0f -> 2f
                    focus.value > 0f -> 1f
                    else -> 0f
                }
            }
        }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = order.value) }
    }
}

/**
 * This outline pushed out by [delta] on every side, with each corner radius grown by [delta] too, so the new curve is
 * concentric with the old one. A shape that is not a rounded rectangle grows as its bounds, with corners of [delta].
 */
internal fun Outline.grownBy(delta: Float): Outline.Rounded =
    when (this) {
        is Outline.Rounded -> {
            val r = roundRect
            Outline.Rounded(
                RoundRect(
                    left = r.left - delta,
                    top = r.top - delta,
                    right = r.right + delta,
                    bottom = r.bottom + delta,
                    topLeftCornerRadius = r.topLeftCornerRadius.grownBy(delta),
                    topRightCornerRadius = r.topRightCornerRadius.grownBy(delta),
                    bottomRightCornerRadius = r.bottomRightCornerRadius.grownBy(delta),
                    bottomLeftCornerRadius = r.bottomLeftCornerRadius.grownBy(delta),
                ),
            )
        }

        else -> {
            Outline.Rounded(RoundRect(bounds.inflate(delta), CornerRadius(delta)))
        }
    }

private fun CornerRadius.grownBy(delta: Float): CornerRadius = CornerRadius(x + delta, y + delta)

/** [base] grown [scale] times about its centre, or about [pivot]'s height under its top, as the drawn growth shows it, so the glow under it grows with it. */
private data class ScaledShape(
    val base: Shape,
    val scale: Float,
    val pivot: ((Float) -> Float)?,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = base.createOutline(size, layoutDirection, density)
        if (scale == 1f) return outline
        val cx = size.width / 2f
        val cy = pivot?.invoke(size.height) ?: (size.height / 2f)
        if (outline is Outline.Rounded) {
            val r = outline.roundRect
            return Outline.Rounded(
                RoundRect(
                    left = cx + (r.left - cx) * scale,
                    top = cy + (r.top - cy) * scale,
                    right = cx + (r.right - cx) * scale,
                    bottom = cy + (r.bottom - cy) * scale,
                    topLeftCornerRadius = r.topLeftCornerRadius * scale,
                    topRightCornerRadius = r.topRightCornerRadius * scale,
                    bottomRightCornerRadius = r.bottomRightCornerRadius * scale,
                    bottomLeftCornerRadius = r.bottomLeftCornerRadius * scale,
                ),
            )
        }
        val matrix =
            Matrix().apply {
                translate(cx, cy)
                scale(scale, scale)
                translate(-cx, -cy)
            }
        return Outline.Generic(
            Path().apply {
                addOutline(outline)
                transform(matrix)
            },
        )
    }
}

/**
 * The outline a focused surface's halo follows: [base], or in [Look.Expressive] with the capsule the rounded step its
 * corners have reached at [focus]'s progress, pinched by a held OK's charge.
 */
internal class TileOutline(
    private val base: Shape,
    private val look: LookState,
    private val painter: TilePainter,
    private val focus: Animatable<Float, AnimationVector1D>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        if (look.look == Look.Expressive) {
            painter.clip(base, look.tile, focus.value, size, density).createOutline(size, layoutDirection, density)
        } else {
            base.createOutline(size, layoutDirection, density)
        }
}
