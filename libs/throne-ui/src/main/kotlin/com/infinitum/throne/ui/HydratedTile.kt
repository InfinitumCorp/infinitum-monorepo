package com.infinitum.throne.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
            .drawBehind { drawRaisedShadow() }
            .clip(HydratedShape)
            .background(Color.White)
            .border(0.75.dp, Color.Black.copy(alpha = 0.07f), HydratedShape)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.55f),
                        0.42f to Color.Transparent,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            Image(
                bitmap = glyph,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.55f),
            )
        }
    }
}

private fun DrawScope.drawRaisedShadow() {
    val corner = size.minDimension * 0.22f
    val ambientBlur = 12.dp.toPx()
    val ambientSteps = 8
    for (step in ambientSteps downTo 1) {
        val spread = ambientBlur * step / ambientSteps
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.10f / ambientSteps),
            topLeft = Offset(-spread, -spread * 0.25f),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius(corner + spread, corner + spread),
        )
    }
    val keyBlur = 6.dp.toPx()
    val keyDrop = 3.5.dp.toPx()
    val keySteps = 6
    for (step in keySteps downTo 1) {
        val spread = keyBlur * step / keySteps
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f / keySteps),
            topLeft = Offset(-spread * 0.35f, keyDrop),
            size = Size(size.width + spread * 0.7f, size.height + spread * 0.7f),
            cornerRadius = CornerRadius(corner + spread * 0.35f, corner + spread * 0.35f),
        )
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
