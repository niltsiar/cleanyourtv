package dev.niltsiar.tvhome.feature.clock

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

private val PreviewFace = ClockFace(hours = "9", minutes = "41", amPm = "PM", date = "WEDNESDAY  4 NOVEMBER")

@Preview(name = "Clock 1080p", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun ClockPreview() {
    ClockSceneContent(
        PreviewFace,
        ClockSceneState(Random(7), initialFade = 1f, initialSpot = Spot(0.3f, 0.4f)),
        glowing = true,
        picture = null,
    )
}

@Preview(name = "Clock 1080p wallpaper", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun ClockWallpaperPreview() {
    ClockSceneContent(
        PreviewFace,
        ClockSceneState(Random(7), initialFade = 1f, initialSpot = Spot(0.55f, 0.5f)),
        glowing = false,
        picture = previewPicture(),
    )
}

/** A dusk gradient, darkened the way clock_wallpaper_dim darkens a picture. */
private fun previewPicture(): ImageBitmap {
    val bitmap = ImageBitmap(960, 540)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(960f, 540f)) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2A6B), Color(0xFFB4506E), Color(0xFFF2A65A))))
        drawCircle(Color(0xFFFFD79A), radius = 90f, center = Offset(700f, 330f))
        drawRect(Color.Black.copy(alpha = 0.5f))
    }
    return bitmap
}
