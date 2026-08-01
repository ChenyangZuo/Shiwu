package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.components.dashedBorder
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

/** 新增 / 编辑 物品表单（第一版为静态展示） */
@Composable
fun ItemFormScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部拖拽条 + 导航
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Border2, RoundedCornerShape(50))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeaderIconButton(Icons.Outlined.Close, "取消", onClick = onClose)
                Text(
                    text = "新增物品",
                    fontFamily = SerifFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.size(36.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 150.dp)
            ) {
                PhotoField()
                TextField(label = "名称", required = "必填", placeholder = "例:Sony WH-1000XM5 无线降噪耳机", focused = true)
                CategoryField()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextField(label = "价格", required = "必填", unit = "¥", placeholder = "0.00")
                    }
                    Column(Modifier.weight(1f)) {
                        TextField(label = "购入日期", value = "2026-08-01", withCalendar = true)
                    }
                }
                CalcHint()
                EditModeNotes()
            }
        }

        // 底部 CTA（上缘渐变遮罩）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Surface.copy(alpha = 0f), Surface),
                        startY = 0f
                    )
                )
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Accent.copy(alpha = 0.5f))
                    .background(Accent, RoundedCornerShape(14.dp))
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "添加物品",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .dashedBorder(Danger, cornerRadius = 14.dp)
                    .clickable(onClick = {}),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "删除物品",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Danger
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "仅编辑模式",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = Danger.copy(alpha = 0.8f),
                    modifier = Modifier
                        .border(1.dp, Danger.copy(alpha = 0.8f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(label: String, hint: String?, color: Color = Muted) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            letterSpacing = 0.6.sp
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 11.sp,
                color = if (color == Danger) Danger else Muted2,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
private fun PhotoField() {
    Column {
        FieldLabel("照片", "选填 · 最多 6 张")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .dashedBorder(Border2, cornerRadius = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "添加",
                        fontSize = 9.5.sp,
                        color = Muted,
                        letterSpacing = 0.6.sp
                    )
                }
            }
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Bg2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun TextField(
    label: String,
    required: String? = null,
    placeholder: String? = null,
    value: String? = null,
    unit: String? = null,
    focused: Boolean = false,
    withCalendar: Boolean = false
) {
    Column {
        FieldLabel(label, required?.let { "必填" }, color = if (required != null) Danger else Muted)
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .then(
                    if (focused) {
                        Modifier
                            .shadow(6.dp, shape, spotColor = Accent.copy(alpha = 0.12f), clip = false)
                            .border(1.dp, Accent, shape)
                    } else {
                        Modifier.border(1.dp, Border, shape)
                    }
                )
                .background(Surface, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (unit != null) {
                Text(
                    text = unit,
                    fontFamily = SerifFamily,
                    fontSize = 14.sp,
                    color = Muted
                )
                Spacer(Modifier.width(8.dp))
            }
            if (placeholder != null) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    color = Muted2,
                    maxLines = 1
                )
            } else if (value != null) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontFamily = NumFamily,
                    color = Fg
                )
            }
            if (withCalendar) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Muted2,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryField() {
    Column {
        FieldLabel("分类", "必选", color = Danger)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("数码", active = true)
            PillChip("服饰", active = false)
            PillChip("阅读", active = false)
            PillChip("家居", active = false)
            PillChip("收藏", active = false)
            PillChip("+ 新建", active = false)
        }
    }
}

@Composable
private fun CalcHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "保存后自动计算:",
            fontSize = 10.5.sp,
            color = Accent,
            letterSpacing = 0.2.sp
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "日均成本 = 价格 ÷ 持有天数",
            fontFamily = NumFamily,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Accent
        )
    }
}

@Composable
private fun EditModeNotes() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg2.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .dashedBorder(Border2, cornerRadius = 14.dp)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "复用逻辑 · 编辑已有物品时",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fg2,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(Modifier.height(7.dp))
        EditNoteRow("标题按钮", "「新增物品 / 添加物品」→「编辑物品 / 保存修改」")
        EditNoteRow("数据预填", "载入当前值,照片位显示已有图片")
        EditNoteRow("同一校验", "名称必填、价格 ≥ 0,两种模式共用")
        EditNoteRow("危险操作", "底部出现「删除物品」(二次确认后移入回收站)")
    }
}

@Composable
private fun EditNoteRow(key: String, value: String) {
    Row(Modifier.padding(vertical = 2.5.dp)) {
        Text(
            text = key,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = Fg2,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            color = Muted,
            lineHeight = 16.sp
        )
    }
}
