package com.imagipoint.watch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ImagiPoint's "instrument" palette: a true-black OLED canvas, warm titanium greys,
 * and one measured solar accent. Colour carries state; it is never used as decoration.
 */
object IpColors {
    val Black = Color(0xFF000000)
    val Obsidian = Color(0xFF050506)
    val Graphite = Color(0xFF0A0B0C)
    val Surface = Color(0xFF101113)
    val SurfaceRaised = Color(0xFF191A1D)
    val SurfaceSoft = Color(0xFF141517)
    val Hairline = Color(0xFF282A2D)
    val HairlineSoft = Color(0xFF1C1E20)
    val TextPrimary = Color(0xFFF4F2EC)
    val TextSecondary = Color(0xFFA7A6A0)
    val TextTertiary = Color(0xFF6B6D6C)
    val Accent = Color(0xFFFFB45C)
    val AccentSoft = Color(0xFFE8A34F)
    val AccentDim = Color(0xFF2A1D0F)
    val Success = Color(0xFF8CD7AD)
    val Warning = Color(0xFFE8C47B)
    val Danger = Color(0xFFFF7D73)
    val Scrim = Color(0xE6000000)
}

private val colorScheme = darkColorScheme(
    primary = IpColors.Accent,
    onPrimary = IpColors.Black,
    primaryContainer = IpColors.AccentDim,
    onPrimaryContainer = IpColors.TextPrimary,
    secondary = IpColors.TextSecondary,
    onSecondary = IpColors.Black,
    background = IpColors.Black,
    onBackground = IpColors.TextPrimary,
    surface = IpColors.Surface,
    onSurface = IpColors.TextPrimary,
    surfaceVariant = IpColors.SurfaceRaised,
    onSurfaceVariant = IpColors.TextSecondary,
    outline = IpColors.Hairline,
    error = IpColors.Danger,
    onError = IpColors.Black,
)

private val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.9).sp,
        color = IpColors.TextPrimary,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.7).sp,
        color = IpColors.TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
        color = IpColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.25).sp,
        color = IpColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
        color = IpColors.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.05.sp,
        color = IpColors.TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = IpColors.TextSecondary,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = IpColors.TextSecondary,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = IpColors.TextSecondary,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.15.sp,
        color = IpColors.TextPrimary,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.7.sp,
        color = IpColors.TextTertiary,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        lineHeight = 11.sp,
        letterSpacing = 1.2.sp,
        color = IpColors.TextTertiary,
    ),
)

val IpTypography = typography

/** Numeric diagnostics use tabular, monospaced digits so values do not jitter. */
val IpNumericStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.2).sp,
    color = IpColors.TextPrimary,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ImagiPointTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
