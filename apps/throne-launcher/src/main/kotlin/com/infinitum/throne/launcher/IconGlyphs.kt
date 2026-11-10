package com.infinitum.throne.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.infinitum.throne.core.home.GlyphPixels

object IconGlyphs {
    fun monogram(name: String): ImageBitmap {
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
        }
        val mark = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(mark, size / 2f, baseline, paint)
        return bitmap.asImageBitmap()
    }

    fun toGlyph(drawable: Drawable, sizePx: Int): ImageBitmap {
        val mono = monochrome(drawable)
        if (mono != null) {
            val painted = draw(mono, sizePx, forceInk = true)
            if (!isBlank(painted)) return painted.asImageBitmap()
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
        return out.asImageBitmap()
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
