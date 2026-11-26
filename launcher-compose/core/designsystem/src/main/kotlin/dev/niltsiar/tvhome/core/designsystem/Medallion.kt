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

/** The large shapes the Expressive screensaver turns through behind the clock, one a minute, each a Material 3 Expressive shape. */
public enum class ScreensaverShape {
    Cookie,
    Clover,
    Burst,
    Puffy,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ScreensaverShape.polygon(): RoundedPolygon =
    when (this) {
        ScreensaverShape.Cookie -> MaterialShapes.Cookie12Sided
        ScreensaverShape.Clover -> MaterialShapes.Clover4Leaf
        ScreensaverShape.Burst -> MaterialShapes.SoftBurst
        ScreensaverShape.Puffy -> MaterialShapes.Puffy
    }

/**
 * The path of a medallion's shape in a square of a given side, from its top left corner; built when the side changes and
 * kept, so drawing it each frame allocates nothing.
 */
public class Medallion private constructor(
    private val polygon: () -> RoundedPolygon,
) {
    public constructor(shape: MedallionShape) : this({ shape.polygon() })
    public constructor(shape: ScreensaverShape) : this({ shape.polygon() })

    private var side = -1f
    private val path = Path()

    /** The outline in a square [side] pixels wide. */
    public fun path(side: Float): Path {
        if (side != this.side) {
            this.side = side
            path.reset()
            path.addPath(polygon().toPath().asComposePath())
            path.transform(Matrix().apply { scale(side, side) })
        }
        return path
    }
}
