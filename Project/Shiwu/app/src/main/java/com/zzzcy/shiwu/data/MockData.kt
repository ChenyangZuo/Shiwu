package com.zzzcy.shiwu.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Accent2
import com.zzzcy.shiwu.ui.theme.AccentStrong
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.ChipFg
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.DangerDeep
import com.zzzcy.shiwu.ui.theme.EarthDeep
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Warn
import com.zzzcy.shiwu.ui.theme.WarnDeep

/**
 * 第一版仅展示界面：所有数据均为写死的示例占位。
 */
data class MockItem(
    val name: String,
    val category: String,
    val daysText: String,
    val price: String,
    val dayCost: String,
    val gradient: Pair<Color, Color>,
    val icon: ImageVector
)

data class MockWish(
    val name: String,
    val category: String,
    val note: String,
    val price: String,
    val gradient: Pair<Color, Color>,
    val icon: ImageVector,
    val priority: String? = null
)

data class CategoryShare(
    val name: String,
    val value: String,
    val percent: String,
    val fraction: Float,
    val color: Color
)

data class TopCost(
    val name: String,
    val cost: String,
    val barFraction: Float
)

object MockData {
    val items = listOf(
        MockItem(
            name = "Sony WH-1000XM5 无线降噪耳机",
            category = "数码",
            daysText = "124天",
            price = "2,899",
            dayCost = "¥23.4",
            gradient = Fg2 to Fg,
            icon = Icons.Outlined.Headphones
        ),
        MockItem(
            name = "Kindle Paperwhite 11代",
            category = "阅读",
            daysText = "287天",
            price = "1,098",
            dayCost = "¥3.8",
            gradient = ChipFg to EarthDeep,
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ),
        MockItem(
            name = "iPad Air M2 11 寸 256G",
            category = "数码",
            daysText = "56天",
            price = "4,799",
            dayCost = "¥85.7",
            gradient = Muted to Fg2,
            icon = Icons.Outlined.TabletAndroid
        ),
        MockItem(
            name = "The North Face 冲锋衣",
            category = "服饰",
            daysText = "412天",
            price = "1,890",
            dayCost = "¥4.6",
            gradient = Accent2 to Accent,
            icon = Icons.Outlined.Checkroom
        )
    )

    val wishes = listOf(
        MockWish(
            name = "MacBook Pro 14\" M4 Pro",
            category = "数码",
            note = "预计 11月",
            price = "14,999",
            gradient = Fg2 to Fg,
            icon = Icons.Outlined.Laptop,
            priority = "HIGH"
        ),
        MockWish(
            name = "DJI Pocket 3 全能套装",
            category = "影像",
            note = "已攒 ¥3,200",
            price = "4,499",
            gradient = Accent to AccentStrong,
            icon = Icons.Outlined.Videocam
        ),
        MockWish(
            name = "一加 13 16+512G",
            category = "数码",
            note = "等待发布",
            price = "4,499",
            gradient = Warn to WarnDeep,
            icon = Icons.Outlined.PhoneAndroid
        ),
        MockWish(
            name = "飞天茅台 53° 500ml",
            category = "收藏",
            note = "节日备礼",
            price = "2,499",
            gradient = ChipFg to EarthDeep,
            icon = Icons.Outlined.Liquor
        ),
        MockWish(
            name = "徕卡 Q3 43 紧凑全幅",
            category = "影像",
            note = "长期目标",
            price = "46,800",
            gradient = Danger to DangerDeep,
            icon = Icons.Outlined.CameraAlt,
            priority = "DREAM"
        )
    )

    val categoryShares = listOf(
        CategoryShare("数码", "¥21,400", "44%", 0.44f, Accent),
        CategoryShare("服饰", "¥9,800", "20%", 0.20f, Warn),
        CategoryShare("家居", "¥7,162", "15%", 0.15f, Accent2),
        CategoryShare("阅读", "¥6,200", "13%", 0.13f, ChipFg),
        CategoryShare("其他", "¥4,000", "8%", 0.08f, Border2)
    )

    val topCosts = listOf(
        TopCost("iPad Air M2 11 寸", "¥85.7", 1.0f),
        TopCost("Sony WH-1000XM5", "¥23.4", 0.27f),
        TopCost("The North Face 冲锋衣", "¥4.6", 0.06f)
    )
}
