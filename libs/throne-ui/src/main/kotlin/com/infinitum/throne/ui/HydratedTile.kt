package com.infinitum.throne.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** About 22% of the tile side, the usual phone-icon corner. */
private val HydratedShape = RoundedCornerShape(22)

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
    colorIcon: Boolean = false,
) {
    val context = LocalContext.current
    val mark = remember(productId) {
        if (colorIcon) {
            null
        } else {
            productId?.let { ProductMarks.bitmap(context, it, 512) }?.let(::cropToInk)
        }
    }
    val glyph = when {
        colorIcon -> fallback
        mark != null -> mark
        else -> fallback?.let(::cropToInk)
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = HydratedShape,
                ambientColor = Color(0x1A000000),
                spotColor = Color.Transparent,
            )
            .shadow(
                elevation = 2.dp,
                shape = HydratedShape,
                ambientColor = Color.Transparent,
                spotColor = Color(0x2A000000),
            )
            .clip(HydratedShape)
            .background(Color.White)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.42f),
                        0.38f to Color.Transparent,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            val wide = !colorIcon && glyph.width > glyph.height * 1.6f
            Image(
                bitmap = glyph,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = when {
                    colorIcon -> Modifier.fillMaxSize(0.86f)
                    wide -> Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(glyph.width.toFloat() / glyph.height.toFloat())
                    else -> Modifier.fillMaxSize(0.60f)
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
