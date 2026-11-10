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
        "architect" -> R.drawable.ic_mark_architect
        "scalvs" -> R.drawable.ic_mark_scalvs
        "cumulus-mail" -> R.drawable.ic_mark_mail
        "veil-vpn" -> R.drawable.ic_mark_veil_vpn
        "veil-browser" -> R.drawable.ic_mark_veil
        "cumulus-cloud" -> R.drawable.ic_mark_cloud
        "cumulus-id" -> R.drawable.ic_mark_spectre
        "corporation", "defense", "academy", "aether" -> R.drawable.ic_mark_lattice
        else -> null
    }

    fun bitmap(context: Context, productId: String, sizePx: Int): ImageBitmap? {
        val id = resId(productId) ?: return null
        val drawable = context.getDrawable(id) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
