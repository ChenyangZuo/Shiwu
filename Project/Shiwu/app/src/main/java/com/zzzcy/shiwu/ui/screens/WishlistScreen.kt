package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.MockData
import com.zzzcy.shiwu.data.MockWish
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.OverviewCard
import com.zzzcy.shiwu.ui.components.SectionHead
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.ChipBg
import com.zzzcy.shiwu.ui.theme.ChipFg
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Warn

@Composable
fun WishlistScreen(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Wishlist",
            title = "愿望清单",
            actions = listOf(
                { HeaderIconButton(Icons.Outlined.Tune, "筛选") },
                { HeaderIconButton(Icons.AutoMirrored.Outlined.Sort, "排序") }
            )
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            OverviewCard(
                label = "预计总费用 · 待购",
                value = "73,296",
                stats = listOf(
                    "心愿数" to "5件",
                    "已攒预算" to "¥12,500",
                    "完成度" to "17%"
                ),
                dark = true
            )

            SectionHead(
                title = "心愿列表",
                meta = buildAnnotatedString {
                    append("按 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Fg2)) {
                        append("优先级")
                    }
                    append(" 排序")
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MockData.wishes.forEach { wish ->
                    WishRow(wish)
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun WishRow(wish: MockWish) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(wish.gradient.first, wish.gradient.second))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = wish.icon,
                    contentDescription = null,
                    tint = OnAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = wish.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wish.category,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = NumFamily,
                        color = ChipFg,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier
                            .background(ChipBg, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = wish.note,
                        fontSize = 10.5.sp,
                        color = Muted,
                        letterSpacing = 0.2.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                wish.priority?.let { priority ->
                    PriorityBadge(priority)
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        fontFamily = SerifFamily,
                        fontSize = 10.5.sp,
                        color = Muted,
                        modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)
                    )
                    Text(
                        text = wish.price,
                        fontFamily = NumFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(text: String) {
    val color = if (text == "DREAM") Danger else Warn
    Text(
        text = "★ $text",
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = NumFamily,
        color = color,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
