package dev.niltsiar.tvhome.service.keys

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/** [this] window's root drawn on the native canvas, at the size its window laid it out. */
internal fun View.drawn(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
