package com.creativeworkbench.writer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    secondary = Color(0xFF6F6253),
    onSecondary = Color(0xFFFFF8EF),
    secondaryContainer = Color(0xFFEBE0CE),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper2,
    onSurface = Ink,
    // 不补这些角色，M3 会回落到默认的紫色中性色（底部导航、对话框、动作面板都会变紫）。
    surfaceDim = Color(0xFFE3DACB),
    surfaceBright = Color(0xFFFFFBF4),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFAF4EB),
    surfaceContainer = Color(0xFFF6F0E6),
    surfaceContainerHigh = Color(0xFFF0E9DC),
    surfaceContainerHighest = Color(0xFFECE4D7),
    surfaceTint = Accent,
    surfaceVariant = Color(0xFFECE4D7),
    onSurfaceVariant = Color(0xFF695F54),
    outline = Color(0xFFE2D7C7),
    outlineVariant = Color(0xFFD8CCBA),
    inverseSurface = Color(0xFF322D27),
    inverseOnSurface = Color(0xFFF4EEE4),
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color(0xFF2A1610),
    primaryContainer = NightSoft,
    onPrimaryContainer = NightInk,
    secondary = Color(0xFFC6B59C),
    onSecondary = Color(0xFF2A2219),
    secondaryContainer = Color(0xFF3A3128),
    onSecondaryContainer = NightInk,
    background = NightBg,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceDim = Color(0xFF141110),
    surfaceBright = Color(0xFF3B342C),
    surfaceContainerLowest = Color(0xFF141110),
    surfaceContainerLow = Color(0xFF1E1A16),
    surfaceContainer = Color(0xFF221E19),
    surfaceContainerHigh = Color(0xFF2A251F),
    surfaceContainerHighest = Color(0xFF332D26),
    surfaceTint = NightAccent,
    surfaceVariant = Color(0xFF2A251F),
    onSurfaceVariant = Color(0xFFB3A594),
    outline = Color(0xFF3A332B),
    outlineVariant = Color(0xFF4A4238),
    inverseSurface = Color(0xFFD8CFC2),
    inverseOnSurface = Color(0xFF2A251F)
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
    val colorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 应用内切深浅色不会改系统 uiMode，窗口底色和状态栏图标得手动跟着走，
            // 否则深色主题下顶上会留一条米色，图标也会是深色看不见。
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
