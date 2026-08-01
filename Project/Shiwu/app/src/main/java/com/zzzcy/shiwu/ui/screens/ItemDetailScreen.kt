package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.formatDate
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Fg2
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val item = ShiwuRepository.itemById(itemId) ?: run {
        onBack()
        return
    }
    var showRetirePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "物品详情",
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.weight(1f))
                HeaderIconButton(Icons.Outlined.Edit, "编辑", onClick = onEdit)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
            ) {
                if (item.photos.isNotEmpty()) {
                    PhotoStrip(item.photos)
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = item.name,
                    fontFamily = SerifFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NumFamily,
                        color = Fg2,
                        modifier = Modifier
                            .background(Color(0xFFF0EAD8), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (item.retired) {
                        Text(
                            text = "已退役",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Muted,
                            modifier = Modifier
                                .background(Color(0xFFEFEBE2), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    } else {
                        Text(
                            text = "使用中",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier
                                .background(AccentSoft, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStat("价格", "¥${"%,.0f".format(Locale.US, item.price)}", Modifier.weight(1f))
                    DetailStat("购入日期", item.purchaseDate.formatDate(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStat(
                        if (item.retired) "服役天数" else "持有天数",
                        "${item.serviceDays} 天",
                        Modifier.weight(1f)
                    )
                    DetailStat(
                        if (item.retired) "服役日均成本" else "日均成本",
                        "¥${"%.2f".format(item.dayCost)}",
                        Modifier.weight(1f)
                    )
                }
                if (item.retired && item.retirementDate != null) {
                    Spacer(Modifier.height(10.dp))
                    DetailStat("退役日期", item.retirementDate.formatDate(), Modifier.fillMaxWidth())
                }
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    DetailStat("备注", item.note, Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(Accent, RoundedCornerShape(14.dp))
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "编辑物品",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = com.zzzcy.shiwu.ui.theme.OnAccent
                    )
                }
                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(if (item.retired) AccentSoft else Color.Transparent, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showRetirePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.retired) Icons.Outlined.Undo else Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = if (item.retired) Accent else Fg2,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (item.retired) "恢复使用" else "标记退役",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.retired) Accent else Fg2
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .border(1.5.dp, Danger, RoundedCornerShape(14.dp))
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "删除物品",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Danger
                        )
                    }
                }
            }
        }

        if (showRetirePicker) {
            if (item.retired) {
                AlertDialog(
                    onDismissRequest = { showRetirePicker = false },
                    title = { Text("恢复使用") },
                    text = { Text("将「${item.name}」恢复为使用中状态？") },
                    confirmButton = {
                        TextButton(onClick = {
                            ShiwuRepository.unretireItem(item.id)
                            showRetirePicker = false
                        }) { Text("恢复", color = Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRetirePicker = false }) { Text("取消") }
                    }
                )
            } else {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showRetirePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                ShiwuRepository.retireItem(item.id, date.toEpochDay())
                            }
                            showRetirePicker = false
                        }) { Text("确认退役", color = Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRetirePicker = false }) { Text("取消") }
                    }
                ) {
                    DatePicker(state = pickerState)
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除物品") },
                text = { Text("「${item.name}」将移入回收站，可稍后恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        ShiwuRepository.softDeleteItem(item.id)
                        showDeleteConfirm = false
                        onBack()
                    }) { Text("移入回收站", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        photos.take(4).forEach { path ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEFEBE2))
            ) {
                val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF7F4EE), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = Muted,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = NumFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fg
        )
    }
}
