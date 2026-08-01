package com.zzzcy.shiwu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题模式 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = LightPalette.onAccent,
    primaryContainer = LightPalette.accentSoft,
    onPrimaryContainer = LightPalette.accentStrong,
    secondary = LightPalette.accent2,
    onSecondary = LightPalette.onAccent,
    background = LightPalette.bg,
    onBackground = LightPalette.fg,
    surface = LightPalette.surface,
    onSurface = LightPalette.fg,
    surfaceVariant = LightPalette.bg2,
    onSurfaceVariant = LightPalette.fg2,
    outline = LightPalette.border2,
    outlineVariant = LightPalette.border,
    error = LightPalette.danger,
    onError = LightPalette.onAccent
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.onAccent,
    primaryContainer = DarkPalette.accentSoft,
    onPrimaryContainer = DarkPalette.accent,
    secondary = DarkPalette.accent2,
    onSecondary = DarkPalette.onAccent,
    background = DarkPalette.bg,
    onBackground = DarkPalette.fg,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.fg,
    surfaceVariant = DarkPalette.bg2,
    onSurfaceVariant = DarkPalette.fg2,
    outline = DarkPalette.border2,
    outlineVariant = DarkPalette.border,
    error = DarkPalette.danger,
    onError = DarkPalette.onAccent
)

@Composable
fun ShiwuTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    // 状态栏/导航栏图标颜色跟随 App 实际主题（而非手机系统深色模式）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalShiwuPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography,
            content = content
        )
    }
}
