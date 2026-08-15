package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.Category
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.categoryIconKeys
import com.zzzcy.shiwu.ui.components.iconForKey
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

@Composable
fun CategoryManagementScreen(onClose: () -> Unit) {
    val categories by ShiwuRepository.categories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var addInput by remember { mutableStateOf("") }
    var addIcon by remember { mutableStateOf("shopping") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameIcon by remember { mutableStateOf("shopping") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "分类管理",
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${categories.size} 个分类",
                    fontFamily = NumFamily,
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val usage = ShiwuRepository.categoryUsage(category.name)
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(AccentSoft, RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconForKey(category.icon),
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Fg
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$usage 件使用中",
                                    fontSize = 10.5.sp,
                                    color = Muted
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "重命名",
                                tint = Muted2,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        renameTarget = category.name
                                        renameInput = category.name
                                        renameIcon = category.icon
                                    }
                                    .padding(10.dp)
                            )
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = Danger,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteTarget = category.name }
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(46.dp)
                .background(Accent, RoundedCornerShape(14.dp))
                .clickable { showAdd = true },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = com.zzzcy.shiwu.ui.theme.OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "新建分类",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.zzzcy.shiwu.ui.theme.OnAccent
                )
            }
        }

        if (showAdd) {
            CategoryDialog(
                title = "新建分类",
                value = addInput,
                onValueChange = { addInput = it },
                iconKey = addIcon,
                onIconChange = { addIcon = it },
                onConfirm = {
                    if (addInput.trim().isNotBlank()) {
                        val name = addInput.trim()
                        ShiwuRepository.addCategory(name)
                        ShiwuRepository.updateCategoryIcon(name, addIcon)
                        addInput = ""
                        addIcon = "shopping"
                        showAdd = false
                    }
                },
                onDismiss = { showAdd = false }
            )
        }

        renameTarget?.let { target ->
            CategoryDialog(
                title = "重命名分类",
                value = renameInput,
                onValueChange = { renameInput = it },
                iconKey = renameIcon,
                onIconChange = { renameIcon = it },
                onConfirm = {
                    val newName = renameInput.trim()
                    ShiwuRepository.renameCategory(target, newName)
                    ShiwuRepository.updateCategoryIcon(newName, renameIcon)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }

        deleteTarget?.let { target ->
            val usage = ShiwuRepository.categoryUsage(target)
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除分类") },
                text = {
                    Text(
                        if (usage > 0) {
                            "「$target」下有 $usage 件物品/心愿，删除后将自动移到其他分类。"
                        } else {
                            "确定删除分类「$target」？"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ShiwuRepository.deleteCategory(target)
                        deleteTarget = null
                    }) { Text("删除", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    iconKey: String,
    onIconChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= 12) onValueChange(it) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Bg2, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "图标",
                    fontSize = 11.sp,
                    color = Muted
                )
                Spacer(Modifier.height(6.dp))
                IconPicker(selected = iconKey, onSelect = onIconChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确定", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryIconKeys.forEach { key ->
            val active = key == selected
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) AccentSoft else Bg2)
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) Accent else Border,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForKey(key),
                    contentDescription = null,
                    tint = if (active) Accent else Muted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
