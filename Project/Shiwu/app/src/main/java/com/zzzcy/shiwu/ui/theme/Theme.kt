package com.zzzcy.shiwu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 设计稿为固定暖白主题，不做动态取色 / 深色切换
private val ShiwuColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentStrong,
    secondary = Accent2,
    onSecondary = OnAccent,
    background = Bg,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Bg2,
    onSurfaceVariant = Fg2,
    outline = Border2,
    outlineVariant = Border,
    error = Danger,
    onError = OnAccent
)

@Composable
fun ShiwuTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShiwuColorScheme,
        typography = Typography,
        content = content
    )
}
