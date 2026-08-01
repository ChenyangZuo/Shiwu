package com.zzzcy.shiwu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 完整色板（浅色/深色共用同一结构） */
data class ShiwuPalette(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val fg: Color,
    val fg2: Color,
    val muted: Color,
    val muted2: Color,
    val border: Color,
    val border2: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentStrong: Color,
    val accent2: Color,
    val warn: Color,
    val warnDeep: Color,
    val danger: Color,
    val dangerDeep: Color,
    val chipBg: Color,
    val chipFg: Color,
    val onAccent: Color,
    val earthDeep: Color,
    val ink2: Color
)

/** 暖白（浅色）主题 */
val LightPalette = ShiwuPalette(
    bg = Color(0xFFF4F1EB),
    bg2 = Color(0xFFEFEBE2),
    surface = Color(0xFFFFFFFF),
    fg = Color(0xFF1B1B19),
    fg2 = Color(0xFF3A3A35),
    muted = Color(0xFF7A766B),
    muted2 = Color(0xFFA8A39A),
    border = Color(0xFFE2DCCF),
    border2 = Color(0xFFD5CDB8),
    accent = Color(0xFF1E5F4F),
    accentSoft = Color(0xFFE8F0EC),
    accentStrong = Color(0xFF174739),
    accent2 = Color(0xFF2D7A6E),
    warn = Color(0xFFB85C1E),
    warnDeep = Color(0xFF7A3D12),
    danger = Color(0xFFA8362C),
    dangerDeep = Color(0xFF5C1F18),
    chipBg = Color(0xFFF0EAD8),
    chipFg = Color(0xFF5C5232),
    onAccent = Color(0xFFF7F3E7),
    earthDeep = Color(0xFF3A3220),
    ink2 = Color(0xFF2D2D29)
)

/** 深色主题（暖黑） */
val DarkPalette = ShiwuPalette(
    bg = Color(0xFF1A1916),
    bg2 = Color(0xFF24231E),
    surface = Color(0xFF2B2A24),
    fg = Color(0xFFF1EDE1),
    fg2 = Color(0xFFCBC4B2),
    muted = Color(0xFF9A9385),
    muted2 = Color(0xFF6D675A),
    border = Color(0xFF3B382F),
    border2 = Color(0xFF4B473C),
    accent = Color(0xFF47A184),
    accentSoft = Color(0xFF1E3A30),
    accentStrong = Color(0xFF357F68),
    accent2 = Color(0xFF5FB6A2),
    warn = Color(0xFFD9924E),
    warnDeep = Color(0xFFA76B36),
    danger = Color(0xFFD9705F),
    dangerDeep = Color(0xFF8E463B),
    chipBg = Color(0xFF393323),
    chipFg = Color(0xFFC9B887),
    onAccent = Color(0xFFEAF3EE),
    earthDeep = Color(0xFF433B2C),
    ink2 = Color(0xFF312D22)
)

val LocalShiwuPalette = staticCompositionLocalOf { LightPalette }

// 兼容旧用法：读取当前主题色板（只能在 Composable 上下文中读取）
val Bg: Color @Composable get() = LocalShiwuPalette.current.bg
val Bg2: Color @Composable get() = LocalShiwuPalette.current.bg2
val Surface: Color @Composable get() = LocalShiwuPalette.current.surface
val Fg: Color @Composable get() = LocalShiwuPalette.current.fg
val Fg2: Color @Composable get() = LocalShiwuPalette.current.fg2
val Muted: Color @Composable get() = LocalShiwuPalette.current.muted
val Muted2: Color @Composable get() = LocalShiwuPalette.current.muted2
val Border: Color @Composable get() = LocalShiwuPalette.current.border
val Border2: Color @Composable get() = LocalShiwuPalette.current.border2
val Accent: Color @Composable get() = LocalShiwuPalette.current.accent
val AccentSoft: Color @Composable get() = LocalShiwuPalette.current.accentSoft
val AccentStrong: Color @Composable get() = LocalShiwuPalette.current.accentStrong
val Accent2: Color @Composable get() = LocalShiwuPalette.current.accent2
val Warn: Color @Composable get() = LocalShiwuPalette.current.warn
val WarnDeep: Color @Composable get() = LocalShiwuPalette.current.warnDeep
val Danger: Color @Composable get() = LocalShiwuPalette.current.danger
val DangerDeep: Color @Composable get() = LocalShiwuPalette.current.dangerDeep
val ChipBg: Color @Composable get() = LocalShiwuPalette.current.chipBg
val ChipFg: Color @Composable get() = LocalShiwuPalette.current.chipFg
val OnAccent: Color @Composable get() = LocalShiwuPalette.current.onAccent
val EarthDeep: Color @Composable get() = LocalShiwuPalette.current.earthDeep
val Ink2: Color @Composable get() = LocalShiwuPalette.current.ink2
