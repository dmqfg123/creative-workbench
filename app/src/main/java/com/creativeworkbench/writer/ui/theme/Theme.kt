package com.creativeworkbench.writer.ui.theme

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
import com.creativeworkbench.writer.core.settings.AppSettings
import com.creativeworkbench.writer.core.settings.ThemeMode

private val Paper = Color(0xFFF4EEE4)
private val Paper2 = Color(0xFFFCF7EF)
private val Ink = Color(0xFF2E2A25)
private val Accent = Color(0xFF9C5842)
private val AccentSoft = Color(0xFFF0DDD4)
private val NightBg = Color(0xFF1B1815)
private val NightCard = Color(0xFF211D19)
private val NightInk = Color(0xFFD8CFC2)
private val NightAccent = Color(0xFFC98B74)
private val NightSoft = Color(0xFF3A2A24)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFAF3),
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper2,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE4D7),
    onSurfaceVariant = Color(0xFF695F54),
    outline = Color(0xFFE2D7C7)
)

private val DarkColors = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color(0xFF2A1610),
    primaryContainer = NightSoft,
    onPrimaryContainer = NightInk,
    background = NightBg,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF2A251F),
    onSurfaceVariant = Color(0xFF9F9384),
    outline = Color(0xFF3A332B)
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 31.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.5.sp)
)

@Composable
fun CreativeWorkbenchTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
