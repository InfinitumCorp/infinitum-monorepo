package com.infinitum.throne.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    val mark = productId?.let(ProductMarks::resId)
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
        when {
            mark != null -> Image(
                painter = painterResource(mark),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = ContentScale.Fit,
            )
            fallback != null -> Image(
                bitmap = fallback,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(13.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
