package com.zcq100.s3browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** 设计稿里的色板：浅灰白底、墨色文字、一个深橙强调色。 */
object Palette {
    val Ground = Color(0xFFF4F5F2)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF16181A)
    val TextSecondary = Color(0xFF3E4246)
    val Muted = Color(0xFF5C6066)
    val Faint = Color(0xFF8A8E93)
    val Border = Color(0xFFE3E5DF)
    val BorderStrong = Color(0xFFD9DBD5)
    val Chip = Color(0xFFECEEE9)
    val Track = Color(0xFFE6E8E3)
    val SwitchOff = Color(0xFFB4B8B0)
    val Accent = Color(0xFFC2410C)
    val Danger = Color(0xFFB91C1C)
    val DangerSoft = Color(0xFFFCA5A5)
    val WarnBg = Color(0xFFFDEBDD)
    val WarnFg = Color(0xFF9A3412)
    val Success = Color(0xFF15803D)
}

val Mono = FontFamily.Monospace

private val scheme = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Ink,
    onSecondary = Color.White,
    background = Palette.Ground,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Chip,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.Chip,
    surfaceContainerLowest = Palette.Surface,
    outline = Palette.BorderStrong,
    outlineVariant = Palette.Border,
    error = Palette.Danger,
    onError = Color.White,
)

@Composable
fun S3BrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
