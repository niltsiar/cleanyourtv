package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/** The outlines a small medallion behind a glyph takes, each a Material 3 Expressive shape, so a kind reads by its outline. */
public enum class MedallionShape {
    Pentagon,
    Clover,
    Cookie,

    /** A nine-sided seal, the mark of a chosen item. */
    Seal,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MedallionShape.polygon(): RoundedPolygon =
    when (this) {
        MedallionShape.Pentagon -> MaterialShapes.Pentagon
        MedallionShape.Clover -> MaterialShapes.Clover4Leaf
        MedallionShape.Cookie -> MaterialShapes.Cookie4Sided
        MedallionShape.Seal -> MaterialShapes.Cookie9Sided
    }

/**
 * The path of a [shape] medallion in a square of a given side, from its top left corner; built when the side changes and
 * kept, so drawing it each frame allocates nothing.
 */
public class Medallion(
    private val shape: MedallionShape,
) {
    private var side = -1f
    private val path = Path()

    /** The outline in a square [side] pixels wide. */
    public fun path(side: Float): Path {
        if (side != this.side) {
            this.side = side
            path.reset()
            path.addPath(shape.polygon().toPath().asComposePath())
            path.transform(Matrix().apply { scale(side, side) })
        }
        return path
    }
}
