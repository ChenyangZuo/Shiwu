package com.zzzcy.shiwu.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.categoryIcon
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.OverviewCard
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.components.RetiredFilter
import com.zzzcy.shiwu.ui.components.SectionHead
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.util.PhotoUtils
import com.zzzcy.shiwu.data.formatPrice

@Composable
fun HomeScreen(
    items: List<Item>,
    query: String,
    onQueryChange: (String) -> Unit,
    retiredFilter: RetiredFilter,
    onFilterChange: (RetiredFilter) -> Unit,
    listName: String,
    onListClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val stats = ShiwuRepository.homeStats()
    // 按购入时间倒序：最新购入的排前面，越早买的越靠后
    val active = items.filter { !it.deleted }.sortedByDescending { it.purchaseDate }
    val filtered = active.filter { item ->
        val matchQuery = query.isBlank() ||
            item.name.contains(query.trim(), ignoreCase = true) ||
            item.category.contains(query.trim(), ignoreCase = true)
        val matchRetired = when (retiredFilter) {
            RetiredFilter.ALL -> true
            RetiredFilter.ACTIVE -> !item.retired
            RetiredFilter.RETIRED -> item.retired
        }
        matchQuery && matchRetired
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            AppHeader(
                eyebrow = "Shiwu",
                title = listName,
                titleClick = onListClick,
                horizontalPadding = 4.dp,
                actions = listOf(
                    {
                        HeaderIconButton(Icons.Outlined.Share, "分享") {
                            val text = buildString {
                                append("我的物品清单 · ${active.size} 件\n\n")
                                active.forEach { append("• ${it.name}  ¥${it.price.formatPrice()}\n") }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "分享物品清单"))
                        }
                    }
                )
            )
        }

        item(span = { GridItemSpan(2) }) {
            OverviewCard(
                label = "总价值 · CNY",
                value = stats.totalValue.formatPrice(),
                stats = listOf(
                    "物品总数" to "${stats.itemCount}件",
                    "日均总成本" to "¥${"%.1f".format(stats.avgDayCost)}",
                    "本月新增" to "${stats.monthNew}件"
                )
            )
        }

        item(span = { GridItemSpan(2) }) {
            SearchBar(query = query, onQueryChange = onQueryChange)
        }

        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RetiredFilter.entries.forEach { f ->
                    PillChip(f.label, active = retiredFilter == f) { onFilterChange(f) }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            SectionHead(
                title = "全部物品",
                meta = buildAnnotatedString {
                    append("共 ")
                    withStyle(SpanStyle(fontFamily = NumFamily, fontWeight = FontWeight.SemiBold, color = Fg2)) {
                        append("${filtered.size}")
                    }
                    append(" 件")
                }
            )
        }

        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState(onAddClick)
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp)
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
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = Fg, lineHeight = 16.sp),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (query.isBlank()) {
                Text(
                    text = "搜索物品、品牌或标签",
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = Muted,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "这里空空如也",
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fg
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点击右下角 + 录入第一件物品",
            fontSize = 12.sp,
            color = Muted
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Accent)
                .clickable(onClick = onAddClick)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = "新增物品",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnAccent
            )
        }
    }
}

@Composable
private fun ItemCard(item: Item, modifier: Modifier = Modifier) {
    CardSurface(modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            val firstPhoto = remember(item.photos.firstOrNull()) {
                item.photos.firstOrNull()?.let { PhotoUtils.decodeSampledBitmap(it, 640, 480)?.asImageBitmap() }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (item.retired) Fg2.copy(alpha = 0.75f) else Color(0xFF3A3A35),
                                if (item.retired) Muted else Color(0xFF1B1B19)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (firstPhoto != null) {
                    Image(
                        bitmap = firstPhoto,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon(item.category),
                        contentDescription = null,
                        tint = Color(0xFF1B1B19),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = item.category,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NumFamily,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF1B1B19)
                    )
                }
                Text(
                    text = if (item.retired) "服役 ${item.serviceDays}天" else "${item.serviceDays}天",
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
                if (item.retired) {
                    Text(
                        text = "已退役",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Color(0xFF1B1B19)
                    )
                }
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
                        color = Muted,
                        modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)
                    )
                    Text(
                        text = item.price.formatPrice(),
                        fontFamily = NumFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥${"%.1f".format(item.dayCost)}",
                        fontFamily = NumFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                    Text(text = "/天", fontSize = 10.5.sp, color = Muted)
                }
            }
        }
    }
}
