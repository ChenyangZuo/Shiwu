package com.zzzcy.shiwu.ui.screens

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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.Wish
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.OverviewCard
import com.zzzcy.shiwu.ui.components.SectionHead
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
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
import java.util.Locale

@Composable
fun WishlistScreen(
    wishes: List<Wish>,
    onWishClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onPurchased: (Long) -> Unit
) {
    val list = wishes.filter { !it.purchased }
    val total = list.sumOf { it.price }
    val saved = list.sumOf { it.savedAmount }
    val completion = if (total > 0) (saved / total * 100).toInt() else 0
    var purchaseWish by remember { mutableStateOf<Wish?>(null) }

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
                value = "%,.0f".format(Locale.US, total),
                stats = listOf(
                    "心愿数" to "${list.size}件",
                    "已攒预算" to "¥${"%,.0f".format(Locale.US, saved)}",
                    "完成度" to "$completion%"
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

            if (list.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "心愿清单为空",
                        fontFamily = SerifFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "点击右下角 + 添加第一个心愿",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { wish ->
                        WishRow(
                            wish = wish,
                            onClick = { onWishClick(wish.id) },
                            onPurchase = { purchaseWish = wish }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    purchaseWish?.let { wish ->
        AlertDialog(
            onDismissRequest = { purchaseWish = null },
            title = { Text("标记为已购买") },
            text = { Text("「${wish.name}」将转为物品并记入物品库。") },
            confirmButton = {
                TextButton(onClick = {
                    ShiwuRepository.markPurchased(wish.id)?.let { onPurchased(it) }
                    purchaseWish = null
                }) { Text("确认", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { purchaseWish = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WishRow(
    wish: Wish,
    onClick: () -> Unit,
    onPurchase: () -> Unit
) {
    val gradient = when (wish.priority) {
        "HIGH" -> Fg2 to Fg
        "DREAM" -> Danger to Color(0xFF5C1F18)
        else -> Accent to Color(0xFF174739)
    }
    val icon = when (wish.category) {
        "影像" -> Icons.Outlined.Videocam
        else -> Icons.Outlined.ShoppingBag
    }
    CardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
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
                        text = wish.note.ifBlank { priorityNote(wish.priority) },
                        fontSize = 10.5.sp,
                        color = Muted,
                        letterSpacing = 0.2.sp
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (wish.priority != "MEDIUM") {
                    PriorityBadge(wish.priority)
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
                        text = "%,.0f".format(Locale.US, wish.price),
                        fontFamily = NumFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AccentSoft)
                    .clickable(onClick = onPurchase),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "标记已购买",
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun priorityNote(priority: String): String = when (priority) {
    "HIGH" -> "高优先级"
    "DREAM" -> "长期目标"
    else -> "待定"
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
