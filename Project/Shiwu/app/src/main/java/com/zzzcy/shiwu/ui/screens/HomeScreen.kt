package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.MockData
import com.zzzcy.shiwu.data.MockItem
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.OverviewCard
import com.zzzcy.shiwu.ui.components.SectionHead
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

@Composable
fun HomeScreen(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Shiwu",
            title = "我的物品",
            actions = listOf(
                { HeaderIconButton(Icons.Outlined.Search, "搜索") },
                { HeaderIconButton(Icons.Outlined.Share, "分享") }
            )
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            OverviewCard(
                label = "总价值 · CNY",
                value = "48,562",
                stats = listOf(
                    "物品总数" to "24件",
                    "日均总成本" to "¥32.4",
                    "本月新增" to "3件"
                )
            )

            SearchBar()

            SectionHead(
                title = "全部物品",
                meta = buildAnnotatedString {
                    append("共 ")
                    withStyle(SpanStyle(fontFamily = NumFamily, fontWeight = FontWeight.SemiBold, color = Fg2)) {
                        append("24")
                    }
                    append(" 件")
                }
            )

            MockData.items.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        ItemCard(item, Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SearchBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp, end = 4.dp)
            .height(38.dp)
            .background(Bg2, RoundedCornerShape(50))
            .border(1.dp, Border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "搜索物品、品牌或标签",
            fontSize = 12.5.sp,
            color = Muted,
            letterSpacing = 0.2.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "⌘ K",
            fontFamily = NumFamily,
            fontSize = 10.sp,
            color = Muted2,
            modifier = Modifier
                .background(Surface, RoundedCornerShape(5.dp))
                .border(1.dp, Border, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ItemCard(item: MockItem, modifier: Modifier = Modifier) {
    CardSurface(modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(item.gradient.first, item.gradient.second))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.category,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NumFamily,
                    color = Fg,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = OnAccent.copy(alpha = 0.95f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = item.daysText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = NumFamily,
                    color = OnAccent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0x8C14120C), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Fg,
                lineHeight = 15.sp,
                maxLines = 2,
                modifier = Modifier.height(30.dp)
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        fontFamily = SerifFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Muted,
                        modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)
                    )
                    Text(
                        text = item.price,
                        fontFamily = NumFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.dayCost,
                        fontFamily = NumFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                    Text(
                        text = "/天",
                        fontSize = 10.5.sp,
                        color = Muted
                    )
                }
            }
        }
    }
}
