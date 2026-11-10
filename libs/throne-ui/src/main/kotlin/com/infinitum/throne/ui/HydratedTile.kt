package com.infinitum.throne.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** iOS-style continuous corner. n = 5 is the usual phone-icon squircle. */
object IosSquircle : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(squircle(size, exponent = 5f))
    }
}

private fun squircle(size: Size, exponent: Float): Path {
    val path = Path()
    val rx = size.width / 2f
    val ry = size.height / 2f
    val steps = 72
    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * (2f * PI.toFloat())
        val ct = cos(t)
        val st = sin(t)
        val x = rx + rx * sign(ct) * abs(ct).pow(2f / exponent)
        val y = ry + ry * sign(st) * abs(st).pow(2f / exponent)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * White rounded tile. The mark sits centered, solid, and uncropped.
 * FABRIC++ is the black wordmark ++ ™. VEIL VPN is the black wordmark V | ™.
 */
@Composable
fun HydratedTile(
    productId: String?,
    modifier: Modifier = Modifier,
    fallback: ImageBitmap? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val mark = remember(productId) {
        productId?.let { ProductMarks.bitmap(context, it, 512) }?.let(::cropToInk)
    }
    val glyph = mark ?: fallback?.let(::cropToInk)
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = IosSquircle,
                ambientColor = Color(0x24000000),
                spotColor = Color(0x14000000),
            )
            .clip(IosSquircle)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            val wide = glyph.width > glyph.height * 1.6f
            Image(
                bitmap = glyph,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = if (wide) {
                    Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(glyph.width.toFloat() / glyph.height.toFloat())
                } else {
                    Modifier.fillMaxSize(0.60f)
                },
            )
        }
    }
}

/** Drops transparent and near-white margins so the black mark can fill the tile. */
private fun cropToInk(image: ImageBitmap): ImageBitmap {
    val source = image.asAndroidBitmap()
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (!isInk(pixels[row + x])) continue
            if (x < left) left = x
            if (y < top) top = y
            if (x > right) right = x
            if (y > bottom) bottom = y
        }
    }
    if (right < left || bottom < top) return image
    val cropped = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    return cropped.asImageBitmap()
}

private fun isInk(pixel: Int): Boolean {
    val alpha = pixel ushr 24
    if (alpha < 24) return false
    val red = (pixel shr 16) and 0xFF
    val green = (pixel shr 8) and 0xFF
    val blue = pixel and 0xFF
    val light = (red * 299 + green * 587 + blue * 114) / 1000
    return light < 236
}
