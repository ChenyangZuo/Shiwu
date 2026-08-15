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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrashScreen(onClose: () -> Unit) {
    val items by ShiwuRepository.items.collectAsState()
    val trashed = items.filter { it.deleted }.sortedByDescending { it.deletedAt ?: 0L }
    var purgeTarget by remember { mutableStateOf<Item?>(null) }

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
                    text = "回收站",
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${trashed.size} 件",
                    fontFamily = NumFamily,
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            if (trashed.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "回收站是空的",
                        fontFamily = SerifFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fg
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "删除的物品会先到这里，可随时恢复或彻底删除",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trashed.forEach { item ->
                        CardSurface(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Fg,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "删除于 ${item.deletedAt?.let(::formatTime) ?: "-"} · 来自「${ShiwuRepository.listName(item.listId)}」",
                                        fontSize = 10.5.sp,
                                        color = Muted
                                    )
                                }
                                TextButton(onClick = { ShiwuRepository.restoreItem(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.height(16.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text("恢复", color = Accent)
                                }
                                TextButton(onClick = { purgeTarget = item }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        tint = Danger,
                                        modifier = Modifier.height(16.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text("彻底删除", color = Danger)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(100.dp))
                }
            }
        }

        purgeTarget?.let { item ->
            AlertDialog(
                onDismissRequest = { purgeTarget = null },
                title = { Text("彻底删除") },
                text = { Text("「${item.name}」将被永久删除，无法恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        ShiwuRepository.purgeItem(item.id)
                        purgeTarget = null
                    }) { Text("彻底删除", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { purgeTarget = null }) { Text("取消") }
                }
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
