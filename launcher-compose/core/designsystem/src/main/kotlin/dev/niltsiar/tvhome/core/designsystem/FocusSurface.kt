package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** How much a focused [FocusSurface] grows by default. */
public const val TILE_FOCUSED_SCALE: Float = 1.08f
private val RingWidth = 3.dp
private val RingGap = 3.dp
private val HairlineWidth = 1.dp
private val Hairline = Color(0x73000000)
private val OuterStrokeWidth = 2.dp
private val OuterStroke = Color.Black.copy(alpha = 0.55f)
private val InnerStrokeWidth = 2.dp
private val InnerStroke = Color.White.copy(alpha = 0.90f)
private val GlowElevation = 16.dp
private val Gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))
private const val PLATE_TOP_ALPHA = 0.22f
private const val PLATE_BOTTOM_ALPHA = 0.06f
private const val PLATE_STROKE_ALPHA = 0.3f

/**
 * How a focused [FocusSurface] is lit, from glass_style. [slab] gives it the lit top and, with a [FocusSurface] `plate`, the
 * plate; [glowStrength] scales the colour of its glow, 0 leaving it out. The ring and its keyline are drawn in every style.
 */
public enum class FocusStyle(
    public val slab: Boolean,
    internal val glowStrength: Float,
) {
    Glass(slab = true, glowStrength = 1f),
    Rim(slab = false, glowStrength = 0f),
    Halo(slab = false, glowStrength = 0.5f),
}

/** How far the ring, with its hairline and dark stroke, reaches past a focused surface's edge, before the surface grows. */
public val FocusRingReach: Dp = RingGap + RingWidth + HairlineWidth + OuterStrokeWidth

/**
 * A D-pad target, for tiles and cards: scales up and gets a lit top, a white ring with a dark hairline outside it and a
 * glow while focused, and a 2 dp black stroke at 0.55 outside the hairline, so the ring holds on a bright wallpaper; how it
 * moves is the theme's [Motion]. Only the content is clipped to [shape]; the ring floats outside it and its corners are
 * concentric to the shape's, so a parent must leave the grown surface plus 9dp times [focusedScale] free around it or it
 * clips the ring. [onFocused] fires on gaining focus. [interactionSource] receives
 * the press and focus events, for content that reacts to them. [accent] colours the glow; unspecified takes the
 * theme's. [TvTheme.focusStyle] says which of the lit top, plate and glow it gets. [ring] colours the ring instead of
 * white. [ringBlends] ([ringBlendsWith]) puts a 2 dp white stroke at 0.90 in the outer part of the gap, between the dark
 * gap and the ring. [plate], when specified, is the colour of a plate behind the surface, out to the ring's inner edge with corners concentric to the shape's: it fades and grows with the ring, as one cue, and a
 * translucent surface shows it. [glow] turns the glow off: the elevation shadow also shows through
 * a translucent surface, as a dark patch. The animation starts in the focus callback and is read in the layer and draw
 * lambdas, so a focus change neither recomposes nor relays out. [focusedScale] is the size it grows to. The growth is
 * drawn, not a layer scale, so the surface's bounds stay put while it grows and accessibility has nothing to update.
 * [growthPivot], given the surface's height in pixels, says how far under its top the growth is anchored, in the middle
 * of its width; without it the surface grows about its centre.
 */
@Composable
public fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    glow: Boolean = true,
    accent: Color = Color.Unspecified,
    focusedScale: Float = TILE_FOCUSED_SCALE,
    ring: ColorProducer? = null,
    ringBlends: Boolean = false,
    plate: Color = Color.Unspecified,
    growthPivot: ((Float) -> Float)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(focusedScale)
    val style = TvTheme.focusStyle
    val glowing = glow && style.glowStrength > 0f
    val glowColor = accent.takeOrElse { TvTheme.colors.accent }.let { it.copy(alpha = it.alpha * style.glowStrength) }
    val plateColor = if (style.slab) plate else Color.Unspecified
    val plateFill =
        remember(plateColor) {
            if (plateColor.isUnspecified) {
                null
            } else {
                Brush.verticalGradient(
                    listOf(plateColor.copy(alpha = PLATE_TOP_ALPHA), plateColor.copy(alpha = PLATE_BOTTOM_ALPHA)),
                )
            }
        }
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    shadowElevation = if (glowing) GlowElevation.toPx() * focus.value.coerceIn(0f, 1f) else 0f
                    this.shape = ScaledShape(shape, 1f + (grownScale - 1f) * focus.value, growthPivot)
                    ambientShadowColor = glowColor
                    spotShadowColor = glowColor
                }.drawWithContent {
                    scale(
                        1f + (grownScale - 1f) * focus.value,
                        Offset(
                            size.width / 2f,
                            growthPivot?.invoke(size.height) ?: (size.height / 2f),
                        ),
                    ) {
                        val progress = focus.value.coerceIn(0f, 1f)
                        val outline = if (progress > 0f) shape.createOutline(size, layoutDirection, this) else null
                        val width = RingWidth.toPx()
                        val gap = RingGap.toPx()
                        if (outline != null && plateFill != null) {
                            val behind = outline.grownBy(gap)
                            drawOutline(behind, plateFill, alpha = progress)
                            drawOutline(behind, plateColor, alpha = progress * PLATE_STROKE_ALPHA, style = Stroke(HairlineWidth.toPx()))
                        }
                        this@drawWithContent.drawContent()
                        if (outline != null) {
                            if (style.slab) drawOutline(outline, Gloss, alpha = progress)
                            if (ringBlends) {
                                val inner = InnerStrokeWidth.toPx()
                                drawOutline(outline.grownBy(gap - inner / 2f), InnerStroke, alpha = progress, style = Stroke(inner))
                            }
                            drawOutline(
                                outline.grownBy(gap + width / 2f),
                                ring?.invoke() ?: Color.White,
                                alpha = progress,
                                style = Stroke(width),
                            )
                            val hairline = HairlineWidth.toPx()
                            drawOutline(outline.grownBy(gap + width + hairline / 2f), Hairline, alpha = progress, style = Stroke(hairline))
                            val stroke = OuterStrokeWidth.toPx()
                            drawOutline(
                                outline.grownBy(gap + width + hairline + stroke / 2f),
                                OuterStroke,
                                alpha = progress,
                                style = Stroke(stroke),
                            )
                        }
                    }
                }.clip(shape)
                .onFocusChanged {
                    val target = if (it.isFocused) 1f else 0f
                    if (focus.targetValue != target) {
                        // Undispatched, its first frame is the one that shows the new focus, not the frame after.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, motion.focusSpec) }
                    }
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}

/**
 * This outline pushed out by [delta] on every side, with each corner radius grown by [delta] too, so the new curve is
 * concentric with the old one. A shape that is not a rounded rectangle grows as its bounds, with corners of [delta].
 */
internal fun Outline.grownBy(delta: Float): Outline =
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
