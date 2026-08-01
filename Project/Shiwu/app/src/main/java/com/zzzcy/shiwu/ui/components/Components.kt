package com.zzzcy.shiwu.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentStrong
import com.zzzcy.shiwu.ui.theme.Bg
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Ink2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

/** 主屏 / 副标题 应用顶栏 */
@Composable
fun AppHeader(
    eyebrow: String,
    title: String,
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = eyebrow.uppercase(),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = Fg
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action -> action() }
        }
    }
}

/** 顶栏方形图标按钮（描边圆角） */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Fg2,
            modifier = Modifier.size(17.dp)
        )
    }
}

/** 概览大卡（首页 / 愿望单顶部） */
@Composable
fun OverviewCard(
    label: String,
    value: String,
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    val brush = if (dark) {
        Brush.verticalGradient(listOf(Fg, Ink2))
    } else {
        Brush.verticalGradient(listOf(Accent, AccentStrong))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (dark) 14.dp else 10.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (dark) Color(0x6614120C) else Accent.copy(alpha = 0.45f)
            )
            .background(brush, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Column {
            Text(
                text = label,
                color = OnAccent.copy(alpha = 0.65f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "¥",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = OnAccent.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 3.dp, bottom = 3.dp)
                )
                Text(
                    text = value,
                    fontFamily = NumFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = OnAccent
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OnAccent.copy(alpha = 0.18f))
            )
            Spacer(Modifier.height(10.dp))
            Row {
                stats.forEachIndexed { index, (k, v) ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = k,
                            color = OnAccent.copy(alpha = 0.6f),
                            fontSize = 10.5.sp,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = v,
                            fontFamily = NumFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = OnAccent
                        )
                    }
                }
            }
        }
    }
}

/** 区块标题（如「全部物品 · 共 24 件」） */
@Composable
fun SectionHead(
    title: String,
    meta: AnnotatedString? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Fg
        )
        if (meta != null) {
            Text(
                text = meta,
                fontSize = 11.sp,
                color = Muted,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/** 筛选 / 分类胶囊 */
@Composable
fun PillChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(Fg)
                } else {
                    Modifier.background(Color.Transparent).border(1.dp, Border, shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (active) Bg else Muted,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.2.sp
        )
    }
}

enum class DockTab(val label: String) {
    HOME("首页"),
    WISHLIST("愿望"),
    STATS("统计"),
    PROFILE("我的")
}

/** 底部悬浮 Dock */
@Composable
fun Dock(
    selected: DockTab,
    modifier: Modifier = Modifier,
    onSelect: (DockTab) -> Unit = {}
) {
    val tabs = listOf(
        DockTab.HOME,
        DockTab.WISHLIST,
        DockTab.STATS,
        DockTab.PROFILE
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(62.dp)
            .shadow(12.dp, RoundedCornerShape(26.dp), spotColor = Color(0x2E14120C))
            .background(Surface.copy(alpha = 0.94f), RoundedCornerShape(26.dp))
            .border(1.dp, Border, RoundedCornerShape(26.dp))
    ) {
        val dockWidth = maxWidth
        // 内容区与指示条共用同一坐标系（含 6dp 内边距），保证三者精确对齐
        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(tab) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 占位保持图标位置稳定（真实指示条为上方滑动动画）
                        Spacer(Modifier.size(width = 16.dp, height = 2.5.dp))
                        Spacer(Modifier.height(3.dp))
                        Icon(
                            imageVector = when (tab) {
                                DockTab.HOME -> Icons.Outlined.Home
                                DockTab.WISHLIST -> if (active) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                DockTab.STATS -> Icons.Outlined.BarChart
                                DockTab.PROFILE -> Icons.Outlined.Person
                            },
                            contentDescription = tab.label,
                            tint = if (active) Accent else Muted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 9.5.sp,
                            color = if (active) Accent else Muted,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            // 顶部激活指示条：随选中标签平滑滑动
            val slotWidth = (dockWidth - 12.dp) / 4
            val indicatorOffset by animateDpAsState(
                targetValue = slotWidth * (selected.ordinal + 0.5f) - (dockWidth - 12.dp) / 2,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "dockIndicator"
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = indicatorOffset)
                    .size(width = 16.dp, height = 2.5.dp)
                    .background(Accent, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** 悬浮新增按钮 */
@Composable
fun Fab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(12.dp, CircleShape, spotColor = Accent.copy(alpha = 0.55f))
            .background(Accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "新增",
            tint = OnAccent,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 统一的白色圆角卡片 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Surface, shape)
            .border(1.dp, Border, shape),
        content = { content() }
    )
}

/** 数字文本（等宽） */
@Composable
fun NumText(
    text: String,
    style: TextStyle,
    color: Color = Fg
) {
    Text(
        text = text,
        fontFamily = NumFamily,
        color = color,
        style = style,
        textAlign = TextAlign.Start
    )
}

@Composable
fun MutedSmall(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 10.5.sp,
        color = color,
        letterSpacing = 0.2.sp
    )
}

/** 虚线圆角描边（用于表单照片位 / 删除按钮 / 注释块） */
fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp = 10.dp,
    strokeWidth: Dp = 1.5.dp,
    dash: FloatArray = floatArrayOf(8f, 6f)
): Modifier = drawBehind {
    val inset = strokeWidth.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(dash)
        )
    )
}
