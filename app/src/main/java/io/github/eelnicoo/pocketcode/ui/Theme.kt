package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Claude-inspired warm palette. */
object Palette {
    val Clay = Color(0xFFD97757)       // signature coral/clay
    val ClayDeep = Color(0xFFC25A3A)
    val ClaySoft = Color(0xFFE8A88F)

    // dark
    val DarkBg = Color(0xFF1A1917)
    val DarkSurface = Color(0xFF242321)
    val DarkSurface2 = Color(0xFF2E2D2A)
    val DarkElevated = Color(0xFF34332F)
    val DarkOutline = Color(0xFF474540)
    val DarkText = Color(0xFFEDEAE2)
    val DarkTextDim = Color(0xFFADA99E)

    // light
    val LightBg = Color(0xFFFAF9F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurface2 = Color(0xFFF1EFE8)
    val LightElevated = Color(0xFFEDEBE3)
    val LightOutline = Color(0xFFD9D5C9)
    val LightText = Color(0xFF26251F)
    val LightTextDim = Color(0xFF6B6A61)

    val Error = Color(0xFFE5705B)
    val Success = Color(0xFF6FA66B)
}

private val DarkColors = darkColorScheme(
    primary = Palette.Clay,
    onPrimary = Color(0xFF2A1712),
    primaryContainer = Color(0xFF45271C),
    onPrimaryContainer = Palette.ClaySoft,
    secondary = Palette.ClaySoft,
    onSecondary = Color(0xFF2A1712),
    background = Palette.DarkBg,
    onBackground = Palette.DarkText,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkText,
    surfaceVariant = Palette.DarkSurface2,
    onSurfaceVariant = Palette.DarkTextDim,
    surfaceContainerHigh = Palette.DarkElevated,
    outline = Palette.DarkOutline,
    outlineVariant = Palette.DarkOutline,
    error = Palette.Error,
    errorContainer = Color(0xFF4A2420),
    onErrorContainer = Color(0xFFF2B5AB),
)

private val LightColors = lightColorScheme(
    primary = Palette.ClayDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DDD1),
    onPrimaryContainer = Color(0xFF4A2314),
    secondary = Palette.ClayDeep,
    onSecondary = Color(0xFFFFFFFF),
    background = Palette.LightBg,
    onBackground = Palette.LightText,
    surface = Palette.LightSurface,
    onSurface = Palette.LightText,
    surfaceVariant = Palette.LightSurface2,
    onSurfaceVariant = Palette.LightTextDim,
    surfaceContainerHigh = Palette.LightElevated,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutline,
    error = Palette.Error,
    errorContainer = Color(0xFFF7D9D2),
    onErrorContainer = Color(0xFF6E241A),
)

private val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
}

/** Monospace style for code. */
val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
