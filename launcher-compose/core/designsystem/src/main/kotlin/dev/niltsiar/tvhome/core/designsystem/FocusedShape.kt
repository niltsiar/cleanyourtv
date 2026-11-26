package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.cinchArtRadius
import dev.niltsiar.tvhome.core.designsystem.expressive.cinchOutline
import dev.niltsiar.tvhome.core.designsystem.expressive.restRadius
import kotlin.math.abs
import kotlin.math.min

/**
 * The outline of a focused tile's art, for what is drawn round it: in Glass the tile's own shape; for the capsule a full
 * capsule, for the cinch a rounded rectangle whose corners stay inside its band. [bandOutline] is the outer
 * edge of its focus band, and [sideReach] how far past the tile's left and right edges, at mid height, that edge lies.
 * Each is built once per size and tile and kept, so reading them in draw allocates nothing after the first frame.
 */
@Stable
public class FocusedShape internal constructor(
    private val look: LookState,
) {
    private var builtTile: ExpressiveTile? = null
    private var builtSize = Size.Unspecified
    private var builtDensity = 0f
    private var outline: Outline? = null
    private var band: Outline? = null
    private var reach = 0f

    /** [base] as a focused tile shows it; read its outline in draw. */
    public fun of(base: Shape): Shape =
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline = outline(base, size, layoutDirection, density)
        }

    /** The outline of a focused tile of [base] shape and [size]. */
    public fun outline(
        base: Shape,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (look.look != Look.Expressive) return base.createOutline(size, layoutDirection, density)
        build(base, size, density)
        return checkNotNull(outline)
    }

    /**
     * The outer edge of the focus band of a focused tile of [base] shape and [size], in Expressive; in Glass, the tile's
     * outline, as Glass draws nothing that needs it.
     */
    public fun bandOutline(
        base: Shape,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (look.look != Look.Expressive) return base.createOutline(size, layoutDirection, density)
        build(base, size, density)
        return checkNotNull(band)
    }

    /** How far past the left and right edges of a focused tile of [base] shape and [size], at mid height, its band ends. */
    public fun sideReach(
        base: Shape,
        size: Size,
        density: Density,
    ): Float {
        if (look.look != Look.Expressive) return with(density) { FocusRingReach.toPx() }
        build(base, size, density)
        return reach
    }

    private fun build(
        base: Shape,
        size: Size,
        density: Density,
    ) {
        val tile = look.tile
        if (tile == builtTile && size == builtSize && density.density == builtDensity) return
        val ringReach = with(density) { FocusRingReach.toPx() }
        when (tile) {
            ExpressiveTile.Capsule -> {
                val radius = min(size.width, size.height) / 2f
                outline = Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                band =
                    Outline.Rounded(
                        RoundRect(
                            -ringReach,
                            -ringReach,
                            size.width + ringReach,
                            size.height + ringReach,
                            CornerRadius(radius + ringReach),
                        ),
                    )
                reach = ringReach
            }

            ExpressiveTile.Cinch -> {
                val art = density.cinchArtRadius(size.width, size.height, density.restRadius(base, size))
                val edge = density.cinchOutline(size.width, size.height, ringReach)
                outline = Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(art)))
                band = Outline.Generic(edge)
                reach = -leftAtMiddle(edge, size)
            }
        }
        builtTile = tile
        builtSize = size
        builtDensity = density.density
    }
}

/** Remembers a [FocusedShape] that follows [TvTheme.lookState]. */
@Composable
public fun rememberFocusedShape(): FocusedShape {
    val look = TvTheme.lookState
    return remember(look) { FocusedShape(look) }
}

private const val OUTLINE_SAMPLES = 720

/** The x of [path]'s left side where it crosses the middle height of a tile of [size], from samples along it. */
private fun leftAtMiddle(
    path: Path,
    size: Size,
): Float {
    val measure = PathMeasure().apply { setPath(path, true) }
    val middle = size.height / 2f
    var best = 0f
    var bestDistance = Float.MAX_VALUE
    var previous = measure.getPosition(0f)
    for (i in 1..OUTLINE_SAMPLES) {
        val next = measure.getPosition(measure.length * i / OUTLINE_SAMPLES)
        val crosses = (previous.y - middle) * (next.y - middle) <= 0f && previous.y != next.y
        if (crosses && previous.x < size.width / 2f) {
            val x = previous.x + (next.x - previous.x) * (middle - previous.y) / (next.y - previous.y)
            val distance = abs(previous.y - middle)
            if (distance < bestDistance) {
                best = x
                bestDistance = distance
            }
        }
        previous = next
    }
    return best
}
