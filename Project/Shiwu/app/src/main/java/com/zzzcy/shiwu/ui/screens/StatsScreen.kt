package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.MockData
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Accent2
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.ChipFg
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.Surface

@Composable
fun StatsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Stats",
            title = "统计",
            actions = listOf(
                { HeaderIconButton(Icons.Outlined.FileUpload, "导出报表") }
            )
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PillChip("本月", active = false)
                PillChip("近半年", active = true)
                PillChip("全年", active = false)
            }

            TrendPanel()
            CategoryPanel()
            TopCostPanel()
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun TrendPanel() {
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
                        text = "¥48,562",
                        fontFamily = NumFamily,
                        fontSize = 11.sp,
                        color = Muted,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "+27.2%",
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
            TrendChart()
        }
    }
}

/** 总价值趋势折线图（按设计稿 312×108 坐标等比绘制） */
@Composable
private fun TrendChart() {
    val points = listOf(
        8f to 82.8f,
        67.2f to 71.4f,
        126.4f to 61.2f,
        185.6f to 45f,
        244.8f to 36f,
        304f to 20.4f
    )
    val labels = listOf("3月", "4月", "5月", "6月", "7月", "8月")
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val sx = size.width / 312f
            val sy = size.height / 108f
            val scaled = { p: Pair<Float, Float> -> Offset(p.first * sx, p.second * sy) }

            // 网格线
            listOf(36f, 68f, 100f).forEachIndexed { i, y ->
                drawLine(
                    color = Border.copy(alpha = if (i == 2) 0.8f else 0.45f),
                    start = Offset(0f, y * sy),
                    end = Offset(size.width, y * sy),
                    strokeWidth = 1f
                )
            }

            // 面积渐变
            val area = Path().apply {
                moveTo(scaled(points[0]).x, scaled(points[0]).y)
                points.drop(1).forEach { lineTo(scaled(it).x, scaled(it).y) }
                lineTo(scaled(points.last()).x, size.height)
                lineTo(scaled(points.first()).x, size.height)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(Accent.copy(alpha = 0.18f), Accent.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height
                )
            )

            // 折线
            val line = Path().apply {
                moveTo(scaled(points[0]).x, scaled(points[0]).y)
                points.drop(1).forEach { lineTo(scaled(it).x, scaled(it).y) }
            }
            drawPath(
                path = line,
                color = Accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 末端圆点
            val last = scaled(points.last())
            drawCircle(color = Surface, radius = 6.dp.toPx(), center = last)
            drawCircle(color = Accent, radius = 3.5.dp.toPx(), center = last)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
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
private fun CategoryPanel() {
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
                    text = "5 个分类",
                    fontFamily = NumFamily,
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart()
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MockData.categoryShares.forEach { share ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(share.color, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = share.name,
                                fontSize = 11.sp,
                                color = Fg2,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = share.value,
                                fontFamily = NumFamily,
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = share.percent,
                                fontFamily = NumFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Fg,
                                modifier = Modifier.width(30.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart() {
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Bg2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            var start = -90f
            MockData.categoryShares.forEach { share ->
                val sweep = share.fraction * 360f
                drawArc(
                    color = share.color,
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
                text = "24",
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
private fun TopCostPanel() {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "日均成本 TOP 3",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg
                )
                Text(
                    text = "按当前持有",
                    fontFamily = NumFamily,
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            Spacer(Modifier.height(4.dp))
            MockData.topCosts.forEachIndexed { index, top ->
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
                                if (index == 0) Accent else Bg2,
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontFamily = NumFamily,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (index == 0) OnAccent else Muted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = top.name,
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
                                    .fillMaxWidth(top.barFraction)
                                    .height(4.dp)
                                    .background(Accent.copy(alpha = 0.8f), RoundedCornerShape(50))
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = top.cost,
                        fontFamily = NumFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                    Text(
                        text = "/天",
                        fontSize = 9.5.sp,
                        color = Muted
                    )
                }
            }
        }
    }
}
