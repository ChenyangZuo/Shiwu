package com.zzzcy.shiwu.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.defaultIconKeyForCategory

/** 全部可选的图标 key（分类管理中展示给用户选择） */
val categoryIconKeys = listOf(
    "devices", "videocam", "menu_book", "checkroom", "chair", "star",
    "sports", "palette", "restaurant", "car", "business", "shopping"
)

/** 图标 key → 矢量图标 */
fun iconForKey(key: String): ImageVector = when (key) {
    "devices" -> Icons.Outlined.Devices
    "videocam" -> Icons.Outlined.Videocam
    "menu_book" -> Icons.Outlined.MenuBook
    "checkroom" -> Icons.Outlined.Checkroom
    "chair" -> Icons.Outlined.Chair
    "star" -> Icons.Outlined.Star
    "sports" -> Icons.Outlined.SportsBasketball
    "palette" -> Icons.Outlined.Palette
    "restaurant" -> Icons.Outlined.Restaurant
    "car" -> Icons.Outlined.DirectionsCar
    "business" -> Icons.Outlined.BusinessCenter
    else -> Icons.Outlined.ShoppingBag
}

/** 分类当前的展示图标（优先读用户配置，未配置回退默认映射；随分类配置实时刷新） */
@Composable
fun categoryIcon(category: String): ImageVector {
    val categories by ShiwuRepository.categories.collectAsState()
    val key = categories.firstOrNull { it.name == category }?.icon ?: defaultIconKeyForCategory(category)
    return iconForKey(key)
}
