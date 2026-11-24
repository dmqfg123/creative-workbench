package com.creativeworkbench.writer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.creativeworkbench.writer.core.settings.AppSettings
import com.creativeworkbench.writer.core.settings.ThemeMode

/**
 * 视觉基线：暖纸 / 暖炭，陶土色作唯一强调色。
 * 个人工具，不做商业化视觉：没有渐变促销、没有红点、没有强对比。
 * 所有正文与次要文字按 WCAG AA（4.5:1）反推过颜色。
 */
private object Palette {
    // 日间
    val Paper = Color(0xFFF7F2E8)
    val Card = Color(0xFFFFFCF6)
    val CardHigh = Color(0xFFF5EFE3)
    val Ink = Color(0xFF241F19)
    val InkMuted = Color(0xFF6C6257)
    val Line = Color(0xFFDCCFBB)
    val Clay = Color(0xFFAF4B2B)
    val ClaySoft = Color(0xFFF6E3D8)
    val ClayDeep = Color(0xFF4A2317)

    // 夜间
    val NightBg = Color(0xFF14110D)
    val NightCard = Color(0xFF1D1913)
    val NightCardHigh = Color(0xFF292319)
    val NightInk = Color(0xFFEFE7D8)
    val NightMuted = Color(0xFFB2A491)
    val NightLine = Color(0xFF3A3228)
    val NightClay = Color(0xFFE2906B)
    val NightClaySoft = Color(0xFF4A2C1F)
}

private val LightColors = lightColorScheme(
    primary = Palette.Clay,
    onPrimary = Color(0xFFFFF8F1),
    primaryContainer = Palette.ClaySoft,
    onPrimaryContainer = Palette.ClayDeep,
    secondary = Color(0xFF6E5C4B),
    onSecondary = Color(0xFFFFF8EF),
    secondaryContainer = Color(0xFFEDE1D1),
    onSecondaryContainer = Color(0xFF3A2E23),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceDim = Color(0xFFE7DECF),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceContainerLowest = Color(0xFFFFFEFA),
    surfaceContainerLow = Color(0xFFFBF6EC),
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.CardHigh,
    surfaceContainerHighest = Color(0xFFEAE1D2),
    surfaceTint = Palette.Clay,
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Palette.InkMuted,
    outline = Palette.Line,
    outlineVariant = Color(0xFFD6C9B4),
    inverseSurface = Color(0xFF302A23),
    inverseOnSurface = Palette.Paper,
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Palette.NightClay,
    onPrimary = Color(0xFF33190F),
    primaryContainer = Palette.NightClaySoft,
    onPrimaryContainer = Color(0xFFF6DCCB),
    secondary = Color(0xFFC9B49B),
    onSecondary = Color(0xFF33291D),
    secondaryContainer = Color(0xFF3A3126),
    onSecondaryContainer = Palette.NightInk,
    background = Palette.NightBg,
    onBackground = Palette.NightInk,
    surface = Palette.NightCard,
    onSurface = Palette.NightInk,
    surfaceDim = Color(0xFF100D09),
    surfaceBright = Color(0xFF3B3327),
    surfaceContainerLowest = Color(0xFF100D09),
    surfaceContainerLow = Color(0xFF181410),
    surfaceContainer = Palette.NightBg,
    surfaceContainerHigh = Palette.NightCardHigh,
    surfaceContainerHighest = Color(0xFF322B20),
    surfaceTint = Palette.NightClay,
    surfaceVariant = Color(0xFF2A251D),
    onSurfaceVariant = Palette.NightMuted,
    outline = Palette.NightLine,
    outlineVariant = Color(0xFF4A4133),
    inverseSurface = Palette.NightInk,
    inverseOnSurface = Color(0xFF241F19),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 17.sp,
        lineHeight = 31.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
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
            // 应用内切深浅色不会改系统 uiMode，窗口底色和状态栏图标手动跟。
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
