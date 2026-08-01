package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CurrencyYuan
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.ui.components.AppHeader
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Accent2
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Warn

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Me",
            title = "我的",
            actions = listOf(
                { HeaderIconButton(Icons.Outlined.Settings, "设置") }
            )
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            ProfileHero()

            Spacer(Modifier.height(8.dp))

            MenuGroup(
                rows = listOf(
                    MenuRowData(
                        "导入物品",
                        "从 CSV / Excel / 旧版备份",
                        Icons.Outlined.Download,
                        Accent
                    ),
                    MenuRowData(
                        "导出数据",
                        "CSV / JSON / PDF 报表",
                        Icons.Outlined.Upload,
                        Accent
                    ),
                    MenuRowData(
                        "自动备份",
                        "本地快照 · 每日导出 JSON",
                        Icons.Outlined.CloudSync,
                        null,
                        badge = "已开启"
                    ),
                    MenuRowData(
                        "回收站",
                        "12 件 · 30 天后自动清除",
                        Icons.Outlined.Delete,
                        Warn,
                        badge = "12"
                    )
                )
            )

            StorageCard()

            MenuGroup(
                modifier = Modifier.padding(top = 12.dp),
                rows = listOf(
                    MenuRowData(
                        "货币与单位",
                        "人民币 · CNY",
                        Icons.Outlined.CurrencyYuan
                    ),
                    MenuRowData(
                        "分类管理",
                        "数码 / 服饰 / 阅读 / 家居 …",
                        Icons.Outlined.GridView
                    ),
                    MenuRowData(
                        "外观主题",
                        "当前：暖白 · 跟随系统",
                        Icons.Outlined.DarkMode
                    ),
                    MenuRowData(
                        "关于拾物",
                        "v0.1.0 · Made with care",
                        Icons.Outlined.Info
                    )
                )
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ProfileHero() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Brush.linearGradient(listOf(Accent, Accent2)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "林",
                fontFamily = SerifFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnAccent
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "林川",
                fontFamily = SerifFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "使用 拾物 已 287 天",
                fontSize = 11.5.sp,
                color = Muted,
                letterSpacing = 0.2.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                "24" to "物品",
                "5" to "心愿",
                "12" to "回收"
            ).forEach { (v, k) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = v,
                        fontFamily = NumFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = k,
                        fontSize = 9.5.sp,
                        color = Muted,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

private data class MenuRowData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color? = null,
    val badge: String? = null
)

@Composable
private fun MenuGroup(
    rows: List<MenuRowData>,
    modifier: Modifier = Modifier
) {
    CardSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                when (row.iconColor) {
                                    Accent -> AccentSoft
                                    Warn -> Warn.copy(alpha = 0.10f)
                                    else -> Bg2
                                },
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            tint = row.iconColor ?: Fg2,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Fg
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = row.subtitle,
                            fontSize = 10.5.sp,
                            color = Muted,
                            letterSpacing = 0.2.sp
                        )
                    }
                    if (row.badge != null) {
                        Text(
                            text = row.badge,
                            fontFamily = NumFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier
                                .background(AccentSoft, RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = Muted2,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (index < rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(Border)
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageCard() {
    CardSurface(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "本地备份",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Fg
                )
                Text(
                    text = "3 / 10 份快照 · 42.8 MB",
                    fontFamily = NumFamily,
                    fontSize = 11.sp,
                    color = Muted,
                    letterSpacing = 0.4.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Bg2, RoundedCornerShape(50))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.30f)
                        .height(6.dp)
                        .background(Brush.horizontalGradient(listOf(Accent, Accent2)), RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "上次备份 12 分钟前",
                    fontSize = 10.5.sp,
                    color = Muted,
                    letterSpacing = 0.2.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "仅保存在本机",
                        fontSize = 10.5.sp,
                        color = Accent
                    )
                }
            }
        }
    }
}
