package com.zzzcy.shiwu.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Accent2
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.ChipFg
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.Surface
import com.zzzcy.shiwu.ui.theme.Warn
import com.zzzcy.shiwu.data.formatPrice

private val chartColors: List<Color>
    @androidx.compose.runtime.Composable get() = listOf(Accent, Warn, Accent2, ChipFg, Border2)

@Composable
fun StatsScreen(items: List<Item>) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(6) } // 1=本月, 6=近半年, 12=全年
    val trend = remember(items, range) { ShiwuRepository.trendPoints(range) }
    val shares = remember(items) { ShiwuRepository.categoryShares() }
    val totalValue = items.filter { !it.deleted }.sumOf { it.price }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Stats",
            title = "统计",
            actions = listOf(
                {
                    HeaderIconButton(Icons.Outlined.FileUpload, "导出报表") {
                        Toast.makeText(context, "该功能正在赶来的路上", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PillChip("本月", active = range == 1) { range = 1 }
                PillChip("近半年", active = range == 6) { range = 6 }
                PillChip("全年", active = range == 12) { range = 12 }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TrendPanel(trend, totalValue)
                CategoryPanel(shares, items.count { !it.deleted })
                TopCostPanel(items.filter { !it.deleted })
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun TrendPanel(trend: List<Pair<String, Double>>, totalValue: Double) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总价值趋势",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "¥${totalValue.formatPrice()}",
                        fontFamily = NumFamily,
                        fontSize = 11.sp,
                        color = Muted,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "当前",
                        fontFamily = NumFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        modifier = Modifier
                            .background(AccentSoft, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TrendChart(trend)
        }
    }
}

@Composable
private fun TrendChart(data: List<Pair<String, Double>>) {
    val maxV = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val border = Border
    val accent = Accent
    val surface = Surface
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val stepX = size.width / (data.size - 1).coerceAtLeast(1)
            val y0 = size.height * 0.92f
            val yMax = size.height * 0.12f
            val points = data.mapIndexed { i, (_, v) ->
                Offset(i * stepX, y0 - (v / maxV).toFloat() * (y0 - yMax))
            }

            // 网格
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                val y = size.height * f
                drawLine(
                    color = border.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            if (points.size >= 2) {
                val area = Path().apply {
                    moveTo(points.first().x, y0)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, y0)
                    close()
                }
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0f)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path = line, color = accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                val last = points.last()
                drawCircle(color = surface, radius = 6.dp.toPx(), center = last)
                drawCircle(color = accent, radius = 3.5.dp.toPx(), center = last)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    fontFamily = NumFamily,
                    fontSize = 9.5.sp,
                    color = Muted,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryPanel(shares: List<Triple<String, Int, Double>>, totalCount: Int) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "分类占比",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg
                )
                Text(
                    text = "${shares.size} 个分类",
                    fontFamily = NumFamily,
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            Spacer(Modifier.height(12.dp))
            if (shares.isEmpty()) {
                Text(
                    text = "暂无数据",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(shares, totalCount)
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        shares.forEachIndexed { index, share ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(chartColors[index % chartColors.size], RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = share.first,
                                    fontSize = 11.sp,
                                    color = Fg2,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${share.second} 件",
                                    fontFamily = NumFamily,
                                    fontSize = 10.5.sp,
                                    color = Muted
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${(share.third * 100).toInt()}%",
                                    fontFamily = NumFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Fg,
                                    modifier = Modifier.width(30.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(shares: List<Triple<String, Int, Double>>, totalCount: Int) {
    val colors = chartColors
    val bg2 = Bg2
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = bg2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            var start = -90f
            shares.forEachIndexed { index, share ->
                val sweep = (share.third * 360f).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start,
                    sweepAngle = sweep - 0.5f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$totalCount",
                fontFamily = NumFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fg
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "件物品",
                fontSize = 9.sp,
                color = Muted,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TopCostPanel(items: List<Item>) {
    var expanded by remember { mutableStateOf(false) }
    val ranked = items.sortedByDescending { it.dayCost }
    val maxCost = ranked.maxOfOrNull { it.dayCost } ?: 1.0
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "日均成本排名",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "共 ${ranked.size} 件" else "TOP 3 · 按当前持有",
                        fontFamily = NumFamily,
                        fontSize = 11.sp,
                        color = Muted
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (ranked.isEmpty()) {
                Text(
                    text = "暂无数据",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                ranked.take(3).forEachIndexed { index, item ->
                    CostRankRow(item, index + 1, maxCost)
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ranked.drop(3).forEachIndexed { index, item ->
                            CostRankRow(item, index + 4, maxCost)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRankRow(item: Item, rank: Int, maxCost: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (rank == 1) Accent else Bg2,
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                fontFamily = NumFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (rank == 1) OnAccent else Muted
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Fg,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Bg2, RoundedCornerShape(50))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((item.dayCost / maxCost).toFloat())
                        .height(4.dp)
                        .background(Accent.copy(alpha = 0.8f), RoundedCornerShape(50))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "¥${"%.1f".format(item.dayCost)}",
            fontFamily = NumFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fg
        )
        Text(text = "/天", fontSize = 9.5.sp, color = Muted)
    }
}
