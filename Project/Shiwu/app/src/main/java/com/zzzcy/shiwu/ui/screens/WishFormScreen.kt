package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.Wish
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.components.dashedBorder
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

private val priorities = listOf("HIGH" to "高", "MEDIUM" to "中", "DREAM" to "梦想")

@Composable
fun WishFormScreen(
    editingWish: Wish?,
    onClose: () -> Unit
) {
    var name by remember(editingWish?.id) { mutableStateOf(editingWish?.name ?: "") }
    var category by remember(editingWish?.id) { mutableStateOf(editingWish?.category ?: "数码") }
    var priceText by remember(editingWish?.id) {
        mutableStateOf(if (editingWish != null && editingWish.price > 0) editingWish.price.toString() else "")
    }
    var priority by remember(editingWish?.id) { mutableStateOf(editingWish?.priority ?: "MEDIUM") }
    var savedText by remember(editingWish?.id) {
        mutableStateOf(if (editingWish != null && editingWish.savedAmount > 0) editingWish.savedAmount.toString() else "")
    }
    var note by remember(editingWish?.id) { mutableStateOf(editingWish?.note ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }

    fun save() {
        val price = priceText.toDoubleOrNull()
        when {
            name.isBlank() -> error = "请输入心愿名称"
            price == null || price <= 0 -> error = "请输入有效的预计价格（> 0）"
            else -> {
                error = null
                if (editingWish == null) {
                    ShiwuRepository.addWish(
                        name = name.trim(),
                        category = category,
                        price = price,
                        priority = priority,
                        savedAmount = savedText.toDoubleOrNull() ?: 0.0,
                        note = note.trim()
                    )
                } else {
                    ShiwuRepository.updateWish(
                        editingWish.copy(
                            name = name.trim(),
                            category = category,
                            price = price,
                            priority = priority,
                            savedAmount = savedText.toDoubleOrNull() ?: 0.0,
                            note = note.trim()
                        )
                    )
                }
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeOverlayTaps()
            .shadow(18.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
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
                    text = if (editingWish == null) "新增心愿" else "编辑心愿",
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
                    .imePadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FormTextField(
                    label = "名称",
                    required = true,
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "例:MacBook Pro 14\" M4 Pro"
                )
                CategoryField(
                    selected = category,
                    onSelect = { category = it },
                    onCreateNew = { showNewCategory = true }
                )
                FormTextField(
                    label = "预计价格",
                    required = true,
                    value = priceText,
                    onValueChange = { input ->
                        if (input.length <= 10 && (input.isEmpty() || input.matches(Regex("[0-9]*\\.?[0-9]{0,2}")))) {
                            priceText = input
                        }
                    },
                    placeholder = "0.00",
                    unit = "¥",
                    keyboardType = KeyboardType.Decimal
                )
                Column {
                    FieldLabel("优先级")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        priorities.forEach { (key, label) ->
                            PillChip(label, active = priority == key) { priority = key }
                        }
                    }
                }
                FormTextField(
                    label = "已攒金额",
                    required = false,
                    value = savedText,
                    onValueChange = { input ->
                        if (input.length <= 10 && (input.isEmpty() || input.matches(Regex("[0-9]*\\.?[0-9]{0,2}")))) {
                            savedText = input
                        }
                    },
                    placeholder = "0.00（选填）",
                    unit = "¥",
                    keyboardType = KeyboardType.Decimal
                )
                FormTextField(
                    label = "备注",
                    required = false,
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "选填：预计时间、购买渠道等"
                )
                if (error != null) {
                    Text(text = error ?: "", fontSize = 11.sp, color = Danger)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Surface.copy(alpha = 0f), Surface),
                        startY = 0f
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Accent.copy(alpha = 0.5f))
                    .background(Accent, RoundedCornerShape(14.dp))
                    .clickable { save() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (editingWish == null) "添加心愿" else "保存修改",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent,
                    letterSpacing = 0.5.sp
                )
            }
            if (editingWish != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .dashedBorder(Danger, cornerRadius = 14.dp)
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "删除心愿",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Danger
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除心愿") },
                text = { Text("确定删除「${name}」？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        editingWish?.let { ShiwuRepository.deleteWish(it.id) }
                        showDeleteConfirm = false
                        onClose()
                    }) { Text("删除", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                }
            )
        }

        if (showNewCategory) {
            AlertDialog(
                onDismissRequest = { showNewCategory = false },
                title = { Text("新建分类") },
                text = {
                    BasicTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Bg2, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = newCategoryText.trim()
                        if (trimmed.isNotBlank()) {
                            ShiwuRepository.addCategory(trimmed)
                            category = trimmed
                            newCategoryText = ""
                            showNewCategory = false
                        }
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCategory = false }) { Text("取消") }
                }
            )
        }
    }
}
