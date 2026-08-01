package com.zzzcy.shiwu.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

/** 清单切换面板：切换 / 新建 / 重命名 / 删除 */
@Composable
fun ListSwitcherScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lists by ShiwuRepository.lists.collectAsState()
    val currentId by ShiwuRepository.currentListId.collectAsState()

    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<InventoryList?>(null) }
    var deleteTarget by remember { mutableStateOf<InventoryList?>(null) }
    var draft by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Muted, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "切换清单",
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
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lists.forEach { list ->
                    ListRow(
                        name = list.name,
                        itemCount = ShiwuRepository.listItemCount(list.id),
                        wishCount = ShiwuRepository.listWishCount(list.id),
                        active = list.id == currentId,
                        onClick = {
                            ShiwuRepository.setCurrentListId(list.id)
                            onClose()
                        },
                        onRename = {
                            draft = list.name
                            renameTarget = list
                        },
                        onDelete = { deleteTarget = list }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent)
                    .clickable { showNew = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "新建清单",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent
                )
            }
        }
    }

    if (showNew || renameTarget != null) {
        AlertDialog(
            onDismissRequest = {
                showNew = false
                renameTarget = null
            },
            title = { Text(if (renameTarget != null) "重命名清单" else "新建清单") },
            text = {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 16) draft = it },
                    textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Bg2, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameTarget != null) {
                        ShiwuRepository.renameList(renameTarget!!.id, draft)
                    } else {
                        val id = ShiwuRepository.addList(draft)
                        ShiwuRepository.setCurrentListId(id)
                    }
                    showNew = false
                    renameTarget = null
                }) { Text("确定", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNew = false
                    renameTarget = null
                }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除清单") },
            text = {
                Text(
                    text = "删除「${target.name}」？其中物品 ${ShiwuRepository.listItemCount(target.id)} 件将移入回收站，心愿 ${ShiwuRepository.listWishCount(target.id)} 个将被删除，此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = ShiwuRepository.deleteList(target.id)
                    if (!ok) {
                        Toast.makeText(context, "至少保留一份清单", Toast.LENGTH_SHORT).show()
                    }
                    deleteTarget = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ListRow(
    name: String,
    itemCount: Int,
    wishCount: Int,
    active: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "当前清单",
                            tint = Accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "物品 $itemCount · 心愿 $wishCount",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "重命名", tint = Muted, modifier = Modifier.size(17.dp))
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Danger, modifier = Modifier.size(17.dp))
            }
        }
    }
}
