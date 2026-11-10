package com.infinitum.throne.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.infinitum.throne.core.home.GlyphPixels

object IconGlyphs {
    fun toGlyph(drawable: Drawable, sizePx: Int): ImageBitmap {
        val mono = monochrome(drawable)
        if (mono != null) {
            val painted = draw(mono, sizePx, forceInk = true)
            if (!isBlank(painted)) return blackInk(painted)
            painted.recycle()
        }
        val source = (drawable as? AdaptiveIconDrawable)?.foreground ?: drawable
        val raw = draw(source, sizePx, forceInk = false)
        val pixels = IntArray(sizePx * sizePx)
        raw.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        raw.recycle()
        val ink = GlyphPixels.desaturateThreshold(pixels, sizePx, sizePx)
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        out.setPixels(ink, 0, sizePx, 0, 0, sizePx, sizePx)
        return blackInk(out)
    }

    /** Pure black ink. Themed-icon fallbacks stay black, never a gray tint. */
    private fun blackInk(bitmap: Bitmap): ImageBitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val alpha = pixels[index] ushr 24
            if (alpha != 0) pixels[index] = alpha shl 24
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap.asImageBitmap()
    }

    private fun monochrome(drawable: Drawable): Drawable? {
        if (Build.VERSION.SDK_INT < 33) return null
        return (drawable as? AdaptiveIconDrawable)?.monochrome
    }

    private fun draw(drawable: Drawable, sizePx: Int, forceInk: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        if (forceInk) {
            copy.colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
        }
        copy.setBounds(0, 0, sizePx, sizePx)
        copy.draw(canvas)
        return bitmap
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.all { it ushr 24 == 0 }
    }
}
