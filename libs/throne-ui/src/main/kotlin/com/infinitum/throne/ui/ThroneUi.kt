package com.infinitum.throne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ThronePaper = Color(0xFFFFFFFF)
val ThroneInk = Color(0xFF161616)
val ThroneMuted = Color(0xFF5C5C5C)
val ThroneLine = Color(0xFFE4E4E4)

private val ThroneColors = lightColorScheme(
    primary = ThroneInk,
    onPrimary = ThronePaper,
    primaryContainer = ThronePaper,
    onPrimaryContainer = ThroneInk,
    secondary = ThroneInk,
    onSecondary = ThronePaper,
    secondaryContainer = ThronePaper,
    onSecondaryContainer = ThroneInk,
    tertiary = ThroneInk,
    onTertiary = ThronePaper,
    background = ThronePaper,
    onBackground = ThroneInk,
    surface = ThronePaper,
    onSurface = ThroneInk,
    surfaceVariant = ThronePaper,
    onSurfaceVariant = ThroneMuted,
    outline = ThroneLine,
    outlineVariant = ThroneLine,
    error = ThroneInk,
    onError = ThronePaper,
    errorContainer = ThronePaper,
    onErrorContainer = ThroneInk,
    inverseSurface = ThroneInk,
    inverseOnSurface = ThronePaper,
    scrim = ThroneInk,
)

private val BaseType = Typography()

/** Liberation Sans, the OFL stand-in for the site stack Helvetica Neue / Helvetica / Arial. */
private val BrandSans = FontFamily(
    Font(R.font.liberation_sans, FontWeight.Normal),
    Font(R.font.liberation_sans_bold, FontWeight.Bold),
)
private val Sans = TextStyle(fontFamily = BrandSans)

private val ThroneType = BaseType.copy(
    headlineLarge = BaseType.headlineLarge.merge(Sans).copy(fontWeight = FontWeight.Light, letterSpacing = 0.4.sp),
    headlineSmall = BaseType.headlineSmall.merge(Sans).copy(fontWeight = FontWeight.Light, letterSpacing = 0.2.sp),
    titleMedium = BaseType.titleMedium.merge(Sans).copy(fontWeight = FontWeight.Light, letterSpacing = 2.4.sp),
    bodyLarge = BaseType.bodyLarge.merge(Sans).copy(fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = BaseType.bodyMedium.merge(Sans).copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = BaseType.bodySmall.merge(Sans).copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = BaseType.labelLarge.merge(Sans).copy(fontWeight = FontWeight.Normal, letterSpacing = 1.sp),
    labelSmall = BaseType.labelSmall.merge(Sans).copy(fontWeight = FontWeight.Normal, letterSpacing = 1.4.sp),
)

@Composable
fun ThroneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ThroneColors, typography = ThroneType) {
        Surface(color = ThronePaper, contentColor = ThroneInk, content = content)
    }
}

@Composable
fun LatticeMark(modifier: Modifier = Modifier, color: Color = ThroneInk) {
    Canvas(modifier.size(28.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter)
        fun diamond(inset: Float): Path = Path().apply {
            moveTo(size.width / 2f, size.height * inset)
            lineTo(size.width * (1f - inset), size.height / 2f)
            lineTo(size.width / 2f, size.height * (1f - inset))
            lineTo(size.width * inset, size.height / 2f)
            close()
        }
        drawPath(diamond(0.08f), color, style = stroke)
        drawPath(diamond(0.28f), color, style = stroke)
    }
}

@Composable
fun ThroneSection(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = ThroneInk,
        modifier = modifier.padding(top = 28.dp, bottom = 12.dp),
    )
}

@Composable
fun ThroneCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ThroneLine)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

@Composable
fun ThroneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) ThroneInk else ThroneLine),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ThronePaper,
            contentColor = ThroneInk,
            disabledContainerColor = ThronePaper,
            disabledContentColor = ThroneMuted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun throneFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ThroneInk,
    unfocusedBorderColor = ThroneLine,
    cursorColor = ThroneInk,
    focusedTextColor = ThroneInk,
    unfocusedTextColor = ThroneInk,
    focusedPlaceholderColor = ThroneMuted,
    unfocusedPlaceholderColor = ThroneMuted,
    focusedLabelColor = ThroneMuted,
    unfocusedLabelColor = ThroneMuted,
    focusedContainerColor = ThronePaper,
    unfocusedContainerColor = ThronePaper,
)

@Composable
fun Wordmark(edition: String, kicker: String) {
    Column {
        LatticeMark()
        Text(
            text = "INFINITUM",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "THRONE][OS™",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (kicker.isNotEmpty()) {
            Text(
                text = kicker,
                style = MaterialTheme.typography.bodyMedium,
                color = ThroneMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "Edition $edition. Debug build. Not a certified release.",
            style = MaterialTheme.typography.bodySmall,
            color = ThroneMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
