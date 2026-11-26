package dev.niltsiar.tvhome.core.designsystem

import android.graphics.Rect
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The look flip of a page that takes [Modifier.lookFlip]: [flip] takes a picture of the page as it shows, at half its size,
 * then switches the look, and once the new look arrives reveals it through a [FlipHole] that grows from the focused element
 * until it covers the page; then the picture goes. The flip owns no focus node and takes no keys. Below Android 9, with the
 * modifier off, before the page has drawn, or when the picture cannot be made, the switch is a cut.
 */
@Stable
public class LookFlip internal constructor(
    private val scope: CoroutineScope,
) {
    /** How many pictures of the page were taken. */
    public var captures: Int = 0
        private set

    /** True while a picture of the page is held. */
    public val holdsPicture: Boolean get() = picture != null

    internal var picture: ImageBitmap? by mutableStateOf(null)
        private set
    internal val progress = Animatable(0f)
    internal var origin = Offset.Zero
        private set
    internal var node: LookFlipNode? = null
    private var run: Job? = null

    /**
     * Takes the picture and then calls [change], which switches the look. [arrival] waits for the new look and returns its
     * [Motion.flipSpec]; when it has not returned within [ARRIVAL_MS], the picture goes without a flip. A flip already
     * running ends, and the new picture shows the page as it is at that moment, the old picture with it.
     */
    public fun flip(
        change: () -> Unit,
        arrival: suspend () -> FiniteAnimationSpec<Float>,
    ) {
        val node = node?.takeIf { it.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P }
        if (node == null) {
            change()
            return
        }
        run?.cancel()
        run =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val shot =
                    try {
                        node.capture()
                    } finally {
                        change()
                    }
                if (shot == null) {
                    picture = null
                    return@launch
                }
                captures++
                // The new picture first: it shows what the screen shows, so the old hole over it changes nothing until the
                // snap, which waits for the replaced flip to end.
                picture = shot
                progress.snapTo(0f)
                origin = node.focusedCentre()
                try {
                    withTimeoutOrNull(ARRIVAL_MS) { arrival() }?.let { progress.animateTo(1f, it) }
                } finally {
                    // A flip that a newer one replaced leaves the newer picture alone.
                    if (picture === shot) picture = null
                }
            }
    }

    /** Ends a running flip and lets go of the picture: the page shows as it is. */
    public fun release() {
        run?.cancel()
        run = null
        picture = null
    }

    public companion object {
        /** How long a flip waits for the new look before it lets go of the picture, in milliseconds. */
        public const val ARRIVAL_MS: Long = 1_000
    }
}

/** A [LookFlip] that lives as long as the composition that remembers it. */
@Composable
public fun rememberLookFlip(): LookFlip {
    val scope = rememberCoroutineScope()
    return remember(scope) { LookFlip(scope) }
}

/**
 * The page that [flip] takes pictures of and draws its flip over. While [enabled] is false and no flip runs, the page draws as
 * it would without the modifier and no picture is taken.
 */
public fun Modifier.lookFlip(
    flip: LookFlip,
    enabled: Boolean,
): Modifier = this then LookFlipElement(flip, enabled)

private data class LookFlipElement(
    val flip: LookFlip,
    val enabled: Boolean,
) : ModifierNodeElement<LookFlipNode>() {
    override fun create() = LookFlipNode(flip, enabled)

    override fun update(node: LookFlipNode) {
        if (node.flip !== flip) {
            node.flip.release()
            node.flip.node = null
            node.flip = flip
            flip.node = node
        }
        node.enabled = enabled
        node.invalidateDraw()
    }
}

internal class LookFlipNode(
    var flip: LookFlip,
    var enabled: Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private lateinit var page: GraphicsLayer
    private lateinit var shot: GraphicsLayer
    internal val hole = FlipHole()

    override fun onAttach() {
        page = requireGraphicsContext().createGraphicsLayer()
        shot = requireGraphicsContext().createGraphicsLayer()
        flip.node = this
    }

    override fun onDetach() {
        flip.release()
        flip.node = null
        hole.drop()
        requireGraphicsContext().releaseGraphicsLayer(shot)
        requireGraphicsContext().releaseGraphicsLayer(page)
    }

    override fun ContentDrawScope.draw() {
        val picture = flip.picture
        if (picture == null) hole.drop()
        if (!enabled && picture == null) {
            drawContent()
            return
        }
        page.record {
            this@draw.drawContent()
            if (picture != null) with(hole) { drawFlip(picture, flip.origin, flip.progress.value) }
        }
        drawLayer(page)
    }

    /** The page as it last drew, at half its size; null before it has drawn, or when the picture cannot be made. */
    suspend fun capture(): ImageBitmap? {
        val size = page.size
        if (size.width < 2 || size.height < 2) return null
        shot.record(requireDensity(), requireLayoutDirection(), IntSize(size.width / 2, size.height / 2)) {
            scale(0.5f, pivot = Offset.Zero) { drawLayer(page) }
        }
        return try {
            shot.toImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            // A hardware bitmap the graphics memory cannot hold comes back as null, which fails here.
            null
        }
    }

    /** The centre of the focused element, on the page; without one, of the window's Compose view. */
    fun focusedCentre(): Offset {
        val focused = Rect()
        // The Compose view answers with the bounds of the focused node, in its own coordinates.
        currentValueOf(LocalView).getFocusedRect(focused)
        val coordinates = requireLayoutCoordinates()
        return coordinates.localPositionOf(coordinates.findRootCoordinates(), Offset(focused.exactCenterX(), focused.exactCenterY()))
    }
}
