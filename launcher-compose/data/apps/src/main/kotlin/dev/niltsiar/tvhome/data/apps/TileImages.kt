package dev.niltsiar.tvhome.data.apps

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DESIGN_WIDTH_DP = 180f
private const val MIN_WIDTH_DP = 80f
private const val FOCUS_SCALE = 1.10f
private const val FOCUS_ROOM_DP = 12f
private const val ICON_FILL = 0.7f

/**
 * The width a tile needs for a grid of [columns] across [innerWidthPx]: the cell, less room for the focus zoom, up to
 * the design size and not under the smallest size. Banners are decoded at this size.
 */
internal fun tileWidthPx(
    innerWidthPx: Int,
    columns: Int,
    density: Float,
): Int {
    val design = (DESIGN_WIDTH_DP * density).roundToInt()
    if (innerWidthPx <= 0 || columns <= 0) return design
    val fit = ((innerWidthPx / columns - FOCUS_ROOM_DP * density) / FOCUS_SCALE).toInt()
    return max((MIN_WIDTH_DP * density).roundToInt(), min(design, fit))
}

/**
 * Some apps declare a small square image as their banner. Cropped over a 16:9 tile it is a blur, so it counts as
 * no banner. A drawable with no size (a colour, a shape) still fills the tile.
 */
internal fun isBannerShaped(
    width: Int,
    height: Int,
): Boolean = width <= 0 || height <= 0 || width >= height * 1.4f

/** What one tile shows: its [image] (null when the app has no art) and the [accent] colour picked from the art, as ARGB. */
internal class TileArt(
    val image: ImageBitmap?,
    val accent: Int,
)

/** Draws the artwork of one tile: the app's banner, or its icon. */
internal class TileImages(
    private val context: Context,
    private val widthPx: Int,
    private val heightPx: Int,
) {
    fun load(
        activity: ActivityInfo,
        icon: () -> Drawable?,
    ): TileArt {
        val banner = banner(activity)
        if (banner != null) {
            return TileArt(banner.toBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).asImageBitmap(), accentOf(banner))
        }
        val drawable = icon() ?: return TileArt(null, NO_ART_ACCENT)
        return TileArt(drawable.centeredOnTile().asImageBitmap(), accentOf(drawable))
    }

    /** The art the cinematic shelf softens: the tile's banner, else its icon. */
    fun artwork(
        activity: ActivityInfo,
        icon: () -> Drawable?,
    ): Drawable? = banner(activity) ?: icon()

    private fun accentOf(art: Drawable): Int {
        val pixels = IntArray(VIVID_SIDE * VIVID_SIDE)
        art.toBitmap(VIVID_SIDE, VIVID_SIDE, Bitmap.Config.ARGB_8888).getPixels(pixels, 0, VIVID_SIDE, 0, 0, VIVID_SIDE, VIVID_SIDE)
        return vividColor(pixels)
    }

    private fun banner(activity: ActivityInfo): Drawable? {
        val pm = context.packageManager
        val id = activity.banner.takeIf { it != 0 } ?: activity.applicationInfo.banner
        var banner: Drawable? = null
        if (id != 0) {
            try {
                // At least xhdpi detail, rather than downsampling artwork before the focus zoom.
                val density = max(DisplayMetrics.DENSITY_XHIGH, context.resources.displayMetrics.densityDpi)
                banner = pm.getResourcesForApplication(activity.applicationInfo).getDrawableForDensity(id, density, null)
            } catch (_: PackageManager.NameNotFoundException) {
            } catch (_: Resources.NotFoundException) {
            }
        }
        banner = banner ?: activity.loadBanner(pm)
        return banner?.takeIf { isBannerShaped(it.intrinsicWidth, it.intrinsicHeight) }
    }

    /** A square icon would stretch on a 16:9 tile, so draw it at tile height in the middle. */
    private fun Drawable.centeredOnTile(): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val side = (heightPx * ICON_FILL).toInt()
        val left = (widthPx - side) / 2
        val top = (heightPx - side) / 2
        setBounds(left, top, left + side, top + side)
        draw(Canvas(bitmap))
        return bitmap
    }
}
