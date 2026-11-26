package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** The colours of a [FocusPill]'s text and icons, which follow its focus. Read them in a draw lambda or as a text colour. */
@Stable
public class FocusInk internal constructor(
    /** Labels and icons. */
    public val content: ColorProducer,
    /** Any other text. */
    public val secondary: ColorProducer,
    /** How far the pill has filled, from 0 to 1, for content that changes with it. */
    public val progress: () -> Float,
    /** Labels and icons on a full fill: the look's colour for text on its pill fill. */
    public val filled: ColorProducer,
)

/**
 * A D-pad target for rows and buttons: while focused it fills with [TvSurfaces.pillFill] over [background], and the
 * [FocusInk] its content gets turns from the theme's text colours to the pill's. It grows to [focusedScale] and casts a
 * shadow of [shadowElevation] in [TvElementColors.pillShadow] while idle too, about [transformOrigin]. As with
 * [FocusSurface], the animation starts in the focus callback and is read in the layer and draw lambdas only, so a focus
 * change neither recomposes nor relays out. In [Look.Expressive] the fill is the house primary, its text the house
 * on-primary, and it casts no shadow; the look is read in the draw phase. There, while OK is held on a pill whose
 * [modifier] has [holdOk], a wave runs round it ([PillChargeElement]), with [Motion.animations] only.
 */
@Composable
public fun FocusPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvTheme.shapes.pill,
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    focusedScale: Float = 1f,
    shadowElevation: Dp = 0.dp,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.(ink: FocusInk) -> Unit,
) {
    val motion = TvTheme.motion
    val surfaces = TvTheme.surfaces
    val rest = TvTheme.lookColors
    val shadow = TvTheme.elementColors.pillShadow
    val lookState = TvTheme.lookState
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(focusedScale)
    val ink =
        remember(surfaces, rest, lookState) {
            val filled = ColorProducer { lookState.pillContent(surfaces) }
            FocusInk(
                content = { lerp(rest.text(), filled(), focus.value.coerceIn(0f, 1f)) },
                secondary = {
                    val expressive = lookState.look == Look.Expressive
                    lerp(rest.textMuted(), if (expressive) filled() else surfaces.pillSecondary, focus.value.coerceIn(0f, 1f))
                },
                progress = { focus.value.coerceIn(0f, 1f) },
                filled = filled,
            )
        }
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val scale = 1f + (grownScale - 1f) * focus.value
                    scaleX = scale
                    scaleY = scale
                    this.transformOrigin = transformOrigin
                    if (shadowElevation > 0.dp && lookState.look != Look.Expressive) {
                        this.shadowElevation = shadowElevation.toPx()
                        this.shape = shape
                        ambientShadowColor = shadow
                        spotShadowColor = shadow
                    }
                }.then(if (motion.animations) PillChargeElement(shape, lookState, surfaces.dark, motion.animated) else Modifier)
                .clip(shape)
                .onFocusChanged {
                    val target = if (it.isFocused) 1f else 0f
                    if (focus.targetValue != target) {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, motion.pillFillSpec()) }
                    }
                    if (it.isFocused) onFocused()
                }.clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
    ) {
        background()
        Spacer(
            Modifier.matchParentSize().drawBehind {
                val shown = focus.value.coerceIn(0f, 1f)
                if (shown > 0f) drawRect(lookState.pillFill(surfaces), alpha = shown)
            },
        )
        content(ink)
    }
}

/** The fill of a focused [FocusPill] on [surfaces]: Glass's, or the house primary in [Look.Expressive]. Read it in draw. */
internal fun LookState.pillFill(surfaces: TvSurfaces): Color =
    if (look == Look.Expressive) scheme(surfaces.dark).primary else surfaces.pillFill

/** The text on [pillFill]. Read it in draw. */
internal fun LookState.pillContent(surfaces: TvSurfaces): Color =
    if (look == Look.Expressive) scheme(surfaces.dark).onPrimary else surfaces.pillContent

/** A warning's text on [surfaces]: Glass's, or the house error in [Look.Expressive]. Read it in draw. */
internal fun LookState.warnInk(surfaces: TvSurfaces): Color = if (look == Look.Expressive) scheme(surfaces.dark).error else surfaces.warnInk
