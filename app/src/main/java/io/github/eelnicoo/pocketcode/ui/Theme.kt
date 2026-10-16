package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Extra colour roles the Claude look needs beyond Material's scheme. */
@Immutable
data class ClaudeColors(
    val dark: Boolean,
    val accent: Color,
    val background: Color,
    val drawer: Color,
    val composer: Color,
    val userBubble: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val codeBg: Color,
    val codeHeader: Color,
    val codeText: Color,
    val selected: Color,
    val danger: Color,
    val success: Color,
    // syntax highlighting
    val synKeyword: Color,
    val synString: Color,
    val synComment: Color,
    val synNumber: Color,
    val synType: Color,
    val synFunction: Color,
)

// Palettes taken from Claude's own warm neutrals.
private val DarkClaude = ClaudeColors(
    dark = true,
    accent = Color(0xFFD97757),
    background = Color(0xFF262624),
    drawer = Color(0xFF1F1E1D),
    composer = Color(0xFF30302E),
    userBubble = Color(0xFF141413),
    border = Color(0xFF3E3E3A),
    text = Color(0xFFF5F4EF),
    textSecondary = Color(0xFFC2C0B6),
    textTertiary = Color(0xFF9A9890),
    codeBg = Color(0xFF1A1A18),
    codeHeader = Color(0xFF232321),
    codeText = Color(0xFFE6E4DC),
    selected = Color(0xFF34332F),
    danger = Color(0xFFE5705B),
    success = Color(0xFF7FB37A),
    synKeyword = Color(0xFFE59A79),
    synString = Color(0xFFB5C98E),
    synComment = Color(0xFF85837B),
    synNumber = Color(0xFFD8B26A),
    synType = Color(0xFF8DB7D6),
    synFunction = Color(0xFFD4A4E0),
)

private val LightClaude = ClaudeColors(
    dark = false,
    accent = Color(0xFFC96442),
    background = Color(0xFFFAF9F5),
    drawer = Color(0xFFF3F1EA),
    composer = Color(0xFFFFFFFF),
    userBubble = Color(0xFFF0EEE6),
    border = Color(0xFFE3E0D5),
    text = Color(0xFF1F1E1D),
    textSecondary = Color(0xFF55544F),
    textTertiary = Color(0xFF85837B),
    codeBg = Color(0xFFF5F4EE),
    codeHeader = Color(0xFFECEAE2),
    codeText = Color(0xFF2B2A27),
    selected = Color(0xFFE9E6DC),
    danger = Color(0xFFC0432C),
    success = Color(0xFF4F8A4B),
    synKeyword = Color(0xFFB0472A),
    synString = Color(0xFF52752B),
    synComment = Color(0xFF8D8B83),
    synNumber = Color(0xFF9A6B12),
    synType = Color(0xFF2F6690),
    synFunction = Color(0xFF7D3C98),
)

val LocalClaude = staticCompositionLocalOf { DarkClaude }
/** Font family for Claude's replies: serif by default, like the Claude app. */
val LocalReplyFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

private fun materialScheme(c: ClaudeColors) = if (c.dark) {
    darkColorScheme(
        primary = c.accent, onPrimary = Color.White,
        primaryContainer = Color(0xFF4A2B20), onPrimaryContainer = Color(0xFFF2C4B2),
        secondary = c.accent, onSecondary = Color.White,
        background = c.background, onBackground = c.text,
        surface = c.background, onSurface = c.text,
        surfaceVariant = c.composer, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.composer, surfaceContainerHigh = c.composer, surfaceContainerHighest = c.selected,
        surfaceContainerLow = c.drawer, surfaceContainerLowest = c.userBubble,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, errorContainer = Color(0xFF4A2420), onErrorContainer = Color(0xFFF2B5AB),
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = Color.White,
        primaryContainer = Color(0xFFF6DDD1), onPrimaryContainer = Color(0xFF4A2314),
        secondary = c.accent, onSecondary = Color.White,
        background = c.background, onBackground = c.text,
        surface = c.background, onSurface = c.text,
        surfaceVariant = c.userBubble, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.composer, surfaceContainerHigh = c.composer, surfaceContainerHighest = c.selected,
        surfaceContainerLow = c.drawer, surfaceContainerLowest = Color.White,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, errorContainer = Color(0xFFF7D9D2), onErrorContainer = Color(0xFF6E241A),
    )
}

private val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Monospace style for code. */
val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

/**
 * @param themeMode "system", "light" or "dark".
 * @param chatFont "serif" or "sans".
 */
@Composable
fun PocketTheme(themeMode: String = "system", chatFont: String = "serif", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val claude = if (dark) DarkClaude else LightClaude
    val replyFont = if (chatFont == "sans") FontFamily.Default else FontFamily.Serif
    CompositionLocalProvider(LocalClaude provides claude, LocalReplyFont provides replyFont) {
        MaterialTheme(colorScheme = materialScheme(claude), typography = AppTypography, content = content)
    }
}
