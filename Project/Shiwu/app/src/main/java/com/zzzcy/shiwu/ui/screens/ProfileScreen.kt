package com.zzzcy.shiwu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Upload
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.Wish
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
import com.zzzcy.shiwu.util.PhotoUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    items: List<Item>,
    wishes: List<Wish>,
    onOpenTrash: () -> Unit
) {
    val context = LocalContext.current
    val userName by ShiwuRepository.userName.collectAsState()
    var showEditName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    val activeCount = items.count { !it.deleted }
    val trashCount = items.count { it.deleted }
    val wishCount = wishes.count { !it.purchased }
    val backupInfo = remember(items, wishes) { ShiwuRepository.backupInfo() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) { null }
            if (text != null) {
                val count = ShiwuRepository.importJson(text)
                Toast.makeText(context, "导入成功：$count 条记录", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "导入失败：文件无法读取", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportData() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "shiwu_export_${System.currentTimeMillis()}.json")
        f.writeText(ShiwuRepository.exportJson())
        val uri = androidx.core.content.FileProvider.getUriForFile(context, PhotoUtils.FILE_PROVIDER_AUTHORITY, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "导出数据"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            eyebrow = "Me",
            title = "我的",
            actions = emptyList()
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            ProfileHero(
                name = userName,
                memberDays = ShiwuRepository.memberDays,
                itemCount = activeCount,
                wishCount = wishCount,
                trashCount = trashCount,
                onClick = {
                    nameInput = userName
                    showEditName = true
                }
            )

            Spacer(Modifier.height(8.dp))

            MenuGroup(
                rows = listOf(
                    MenuRowData(
                        "导入物品",
                        "从 JSON / CSV 备份恢复",
                        Icons.Outlined.Download,
                        Accent,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }
                    ),
                    MenuRowData(
                        "导出数据",
                        "JSON 完整备份，可分享",
                        Icons.Outlined.Upload,
                        Accent,
                        onClick = ::exportData
                    ),
                    MenuRowData(
                        "自动备份",
                        "本地快照 · 点击立即备份",
                        Icons.Outlined.CloudSync,
                        null,
                        badge = "已开启",
                        onClick = {
                            ShiwuRepository.createBackup()
                            Toast.makeText(context, "备份完成", Toast.LENGTH_SHORT).show()
                        }
                    ),
                    MenuRowData(
                        "回收站",
                        "$trashCount 件 · 可恢复或彻底删除",
                        Icons.Outlined.Delete,
                        Warn,
                        badge = "$trashCount",
                        onClick = onOpenTrash
                    )
                )
            )

            StorageCard(
                count = backupInfo.count,
                sizeBytes = backupInfo.totalSizeBytes,
                lastAt = backupInfo.lastBackupAt
            )

            MenuGroup(
                modifier = Modifier.padding(top = 12.dp),
                rows = listOf(
                    MenuRowData("分类管理", "数码 / 服饰 / 阅读 / 家居 …", Icons.Outlined.GridView),
                    MenuRowData("外观主题", "当前：暖白 · 跟随系统", Icons.Outlined.DarkMode),
                    MenuRowData(
                        "关于拾物",
                        "v${appVersion()} · Made with care",
                        Icons.Outlined.Info
                    )
                )
            )
        }

        Spacer(Modifier.height(120.dp))
    }

    if (showEditName) {
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text("修改昵称") },
            text = {
                Column {
                    Text(
                        text = "昵称将显示在个人页，头像取昵称首字",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 16) nameInput = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = Fg),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Bg2, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ShiwuRepository.updateUserName(nameInput)
                    showEditName = false
                }) { Text("保存", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showEditName = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
    }
}

@Composable
private fun ProfileHero(
    name: String,
    memberDays: Int,
    itemCount: Int,
    wishCount: Int,
    trashCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
                text = name.firstOrNull()?.toString() ?: "拾",
                fontFamily = SerifFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnAccent
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = name,
                fontFamily = SerifFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "使用 拾物 已 $memberDays 天",
                fontSize = 11.5.sp,
                color = Muted,
                letterSpacing = 0.2.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "修改昵称",
                    tint = Muted2,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "点击修改昵称",
                    fontSize = 10.5.sp,
                    color = Muted2,
                    letterSpacing = 0.2.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                "$itemCount" to "物品",
                "$wishCount" to "心愿",
                "$trashCount" to "回收"
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
    val iconColor: Color? = null,
    val badge: String? = null,
    val onClick: (() -> Unit)? = null
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
                        .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
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
private fun StorageCard(count: Int, sizeBytes: Long, lastAt: Long?) {
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
                    text = "$count 份快照 · ${formatSize(sizeBytes)}",
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
                        .fillMaxWidth((count % 10).coerceAtMost(10) / 10f)
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
                    text = if (lastAt != null) "上次备份 ${formatTime(lastAt)}" else "暂无备份",
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

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
