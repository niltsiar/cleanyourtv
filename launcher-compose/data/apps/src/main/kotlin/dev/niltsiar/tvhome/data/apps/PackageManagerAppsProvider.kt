package dev.niltsiar.tvhome.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Apps that declare LEANBACK_LAUNCHER or LAUNCHER, once per package, without this app.
 * [apps] emits the labels first, then the same list with banners or icons decoded to [tileWidthPx] x [tileHeightPx].
 */
public class PackageManagerAppsProvider(
    private val context: Context,
    private val tileWidthPx: Int,
    private val tileHeightPx: Int,
) {
    public val apps: Flow<List<AppEntry>> =
        flow {
            val resolved = queryLaunchers()
            emit(resolved.map { AppEntry(packageName = it.packageName, label = it.label) })
            emit(resolved.map { AppEntry(packageName = it.packageName, label = it.label, image = tileImage(it.info)) })
        }.flowOn(Dispatchers.IO)

    private class Launcher(
        val packageName: String,
        val label: String,
        val info: ResolveInfo,
    )

    private fun queryLaunchers(): List<Launcher> {
        val pm = context.packageManager
        // LEANBACK first so a TV banner wins over a phone icon when a package has both.
        val found =
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category ->
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)
                }
        return found
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { Launcher(it.activityInfo.packageName, it.loadLabel(pm).toString(), it) }
            .sortedBy { it.label.lowercase() }
    }

    private fun tileImage(info: ResolveInfo): ImageBitmap? {
        val pm = context.packageManager
        val banner = info.activityInfo.loadBanner(pm)
        return if (banner != null) {
            banner.toBitmap(tileWidthPx, tileHeightPx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val icon = info.loadIcon(pm) ?: return null
            icon.centeredOnTile().asImageBitmap()
        }
    }

    /** A square icon would stretch on a 16:9 tile, so draw it at tile height in the middle. */
    private fun Drawable.centeredOnTile(): Bitmap {
        val bitmap = createBitmap(tileWidthPx, tileHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val side = (tileHeightPx * ICON_FILL).toInt()
        val left = (tileWidthPx - side) / 2
        val top = (tileHeightPx - side) / 2
        setBounds(left, top, left + side, top + side)
        draw(canvas)
        return bitmap
    }

    private companion object {
        const val ICON_FILL = 0.7f
    }
}
