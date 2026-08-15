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
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

@Composable
fun LocationManagementScreen(onClose: () -> Unit) {
    val locations by ShiwuRepository.locations.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var addInput by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
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
                    text = "存放地点管理",
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${locations.size} 个地点",
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
                locations.forEach { location ->
                    val usage = ShiwuRepository.locationUsage(location)
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
                                    imageVector = Icons.Outlined.Place,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = location,
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
                                        renameTarget = location
                                        renameInput = location
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
                                    .clickable { deleteTarget = location }
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
                    tint = OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "新建存放地点",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent
                )
            }
        }

        if (showAdd) {
            LocationDialog(
                title = "新建存放地点",
                value = addInput,
                onValueChange = { addInput = it },
                onConfirm = {
                    val name = addInput.trim()
                    if (name.isNotBlank() && ShiwuRepository.addLocation(name)) {
                        addInput = ""
                        showAdd = false
                    }
                },
                onDismiss = { showAdd = false }
            )
        }

        renameTarget?.let { target ->
            LocationDialog(
                title = "重命名存放地点",
                value = renameInput,
                onValueChange = { renameInput = it },
                onConfirm = {
                    ShiwuRepository.renameLocation(target, renameInput)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除存放地点") },
                text = { Text("删除「$target」后，使用该地点的物品将变为「未设置」。") },
                confirmButton = {
                    TextButton(onClick = {
                        ShiwuRepository.deleteLocation(target)
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
private fun LocationDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
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
            TextButton(onClick = onConfirm) { Text("确定", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
