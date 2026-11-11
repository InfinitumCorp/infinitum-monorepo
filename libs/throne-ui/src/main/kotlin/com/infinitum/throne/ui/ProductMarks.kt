package com.infinitum.throne.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Official marks taken from live product sites on 10 Nov 2026.
 * Products with no published logo are absent here.
 */
object ProductMarks {
    fun resId(productId: String): Int? = when (productId) {
        "fabric" -> R.drawable.ic_mark_fabric
        "architect" -> R.drawable.ic_mark_architect
        "scalvs" -> R.drawable.ic_mark_scalvs
        "cumulus-mail" -> R.drawable.ic_mark_mail
        "veil-vpn" -> R.drawable.ic_mark_veil_vpn
        "veil-browser" -> R.drawable.ic_mark_veil
        "cumulus-cloud" -> R.drawable.ic_mark_cloud
        "cumulus-id" -> R.drawable.ic_mark_spectre
        "metatron" -> R.drawable.ic_mark_metatron
        "hrvst" -> R.drawable.ic_mark_hrvst
        "eidetic" -> R.drawable.ic_mark_eidetic
        "conjure" -> R.drawable.ic_mark_conjure
        "nether" -> R.drawable.ic_mark_nether
        "corporation", "defense", "academy", "aether" -> R.drawable.ic_mark_lattice
        else -> null
    }

    fun bitmap(context: Context, productId: String, sizePx: Int): ImageBitmap? {
        val id = resId(productId) ?: return null
        val drawable = context.getDrawable(id) ?: return null
        val sourceW = drawable.intrinsicWidth.coerceAtLeast(1)
        val sourceH = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = sizePx.toFloat() / maxOf(sourceW, sourceH)
        val width = (sourceW * scale).toInt().coerceAtLeast(1)
        val height = (sourceH * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
