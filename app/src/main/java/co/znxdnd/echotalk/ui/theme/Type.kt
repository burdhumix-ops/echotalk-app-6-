package co.znxdnd.echotalk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = getCustomTypography(FontFamily.Default, 1.0f)

/**
 * Creates scaled, curated Material 3 typography adhering to strict customization boundaries.
 * Prevents broken layouts by using proportional scalings.
 */
fun getCustomTypography(
    fontFamily: FontFamily = FontFamily.Default,
    scale: Float = 1.0f
): Typography {
    val s = scale.coerceIn(0.85f, 1.25f)
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (32 * s).sp,
            lineHeight = (40 * s).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (28 * s).sp,
            lineHeight = (36 * s).sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * s).sp,
            lineHeight = (28 * s).sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * s).sp,
            lineHeight = (24 * s).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * s).sp,
            lineHeight = (24 * s).sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * s).sp,
            lineHeight = (20 * s).sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s).sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14 * s).sp,
            lineHeight = (20 * s).sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * s).sp,
            lineHeight = (16 * s).sp
        )
    )
}
