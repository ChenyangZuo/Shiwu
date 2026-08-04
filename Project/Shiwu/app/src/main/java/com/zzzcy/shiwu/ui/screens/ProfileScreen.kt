package com.zzzcy.shiwu.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.zzzcy.shiwu.util.TransferZip
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    items: List<Item>,
    wishes: List<Wish>,
    onOpenTrash: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTheme: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val userName by ShiwuRepository.userName.collectAsState()
    val themeMode by ShiwuRepository.themeMode.collectAsState()
    var showEditName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val activeCount = items.count { !it.deleted }
    val trashCount = items.count { it.deleted }
    val wishCount = wishes.count { !it.purchased }

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    fun shareFile(f: File, mime: String, title: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, PhotoUtils.FILE_PROVIDER_AUTHORITY, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    fun exportJson() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "shiwu_export_${System.currentTimeMillis()}.json")
        f.writeText(ShiwuRepository.exportJson())
        shareFile(f, "application/json", "导出 JSON")
    }

    fun exportCsv() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "shiwu_items_${System.currentTimeMillis()}.csv")
        f.writeText(ShiwuRepository.exportCsv())
        shareFile(f, "text/csv", "导出 CSV")
    }

    fun exportZip() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "shiwu_export_${System.currentTimeMillis()}.zip")
        val ok = TransferZip.buildExportZip(ShiwuRepository.exportCsv(), ShiwuRepository.exportPhotoFiles(), f)
        if (ok) {
            shareFile(f, "application/zip", "导出 CSV + 照片")
        } else {
            Toast.makeText(context, "导出失败：无法打包 ZIP", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTemplate() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "shiwu_import_template.csv")
        f.writeText(ShiwuRepository.csvTemplate())
        shareFile(f, "text/csv", "CSV 导入模板")
    }

    fun doImport(uri: Uri) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
        if (bytes == null) {
            Toast.makeText(context, "导入失败：文件无法读取", Toast.LENGTH_SHORT).show()
            return
        }
        val isZip = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val isJson = bytes.firstOrNull() == '{'.code.toByte()
        val count = when {
            isZip -> {
                val result = TransferZip.importZip(context, uri)
                if (result != null) ShiwuRepository.importCsv(result.first, result.second) else -1
            }
            isJson -> ShiwuRepository.importJson(bytes.toString(Charsets.UTF_8))
            else -> ShiwuRepository.importCsv(bytes.toString(Charsets.UTF_8))
        }
        if (count >= 0) {
            val suffix = if (isZip) "（含照片）" else ""
            Toast.makeText(context, "导入成功：$count 条记录$suffix", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "导入失败：ZIP 文件无法解析", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(::doImport) }

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
                        "JSON / CSV / ZIP · 导入前可查看模板",
                        Icons.Outlined.Download,
                        Accent,
                        onClick = { showImportDialog = true }
                    ),
                    MenuRowData(
                        "导出数据",
                        "CSV / CSV+照片 ZIP / JSON",
                        Icons.Outlined.Upload,
                        Accent,
                        onClick = { showExportDialog = true }
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

            MenuGroup(
                modifier = Modifier.padding(top = 12.dp),
                rows = listOf(
                    MenuRowData(
                        "分类管理",
                        "数码 / 服饰 / 阅读 / 家居 …",
                        Icons.Outlined.GridView,
                        onClick = onOpenCategories
                    ),
                    MenuRowData(
                        "外观主题",
                        themeLabel(themeMode),
                        Icons.Outlined.DarkMode,
                        onClick = onOpenTheme
                    ),
                    MenuRowData(
                        "关于时物",
                        "v${appVersion()} · Made with care",
                        Icons.Outlined.Info,
                        onClick = { showAboutDialog = true }
                    ),
                    MenuRowData(
                        "分享时物",
                        "扫码查看 GitHub 仓库",
                        Icons.Outlined.Share,
                        onClick = { showShareDialog = true }
                    )
                )
            )
        }

        Spacer(Modifier.height(120.dp))
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入物品") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "支持三种格式：\n· JSON：完整备份（含心愿单）\n· CSV：物品文字信息\n· ZIP：CSV + photos/ 照片文件夹\n\n建议先查看模板了解 CSV 列格式。",
                        fontSize = 12.sp,
                        color = Muted,
                        lineHeight = 18.sp
                    )
                    ImportOption("查看 CSV 模板", "生成一份带示例行的模板文件") {
                        shareTemplate()
                    }
                    ImportOption("选择文件导入", "JSON / CSV / ZIP") {
                        showImportDialog = false
                        importLauncher.launch(arrayOf("*/*"))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportOption("CSV（纯文字）", "当前清单 · Excel / WPS 可直接打开") {
                        showExportDialog = false
                        exportCsv()
                    }
                    ImportOption("CSV + 照片（ZIP）", "当前清单 · 内含 items.csv 与 photos/ 照片") {
                        showExportDialog = false
                        exportZip()
                    }
                    ImportOption("JSON 完整备份", "含物品、心愿单、分类") {
                        showExportDialog = false
                        exportJson()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("取消") }
            }
        )
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
                        textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
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

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于时物") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "「物有来处，时有所记。」",
                        fontFamily = SerifFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                    Text(
                        text = "「时物」是一款本地优先的物品管理应用：记录物品的价格、购入与退役、保质期与日均成本，也收留那些尚未入手的愿望。数据留在设备本地，重要决定由你掌控。",
                        fontSize = 12.sp,
                        color = Fg,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "· 多清单：物品与心愿单按清单组织，一键切换\n" +
                            "· 保质期守护：三选二自动推算，临期黄框、过期红框\n" +
                            "· 心愿清单：记录优先级与攒钱进度，购买后一键转正\n" +
                            "· 统计洞察：总价值、分类占比、日均成本、临期排行\n" +
                            "· 数据自主：CSV / JSON 导入导出，照片随行\n" +
                            "· 清单级保护：密码与指纹鉴权",
                        fontSize = 12.sp,
                        color = Muted,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "v${appVersion()} · Kotlin / Jetpack Compose / Room",
                        fontSize = 11.sp,
                        color = Muted2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("分享时物") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val qr = ShareQrBitmap(GITHUB_URL, 512)
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qr != null) {
                            Image(
                                bitmap = qr,
                                contentDescription = "GitHub 仓库二维码",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text(
                        text = GITHUB_URL,
                        fontSize = 11.sp,
                        color = Muted,
                        letterSpacing = 0.2.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(GITHUB_URL))
                    Toast.makeText(context, "仓库链接已复制", Toast.LENGTH_SHORT).show()
                }) { Text("复制链接") }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ImportOption(title: String, desc: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Bg2)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fg
        )
        Text(
            text = desc,
            fontSize = 11.sp,
            color = Muted,
            letterSpacing = 0.2.sp
        )
    }
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "当前：暖白"
    "dark" -> "当前：深色"
    else -> "当前：跟随系统"
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

/** GitHub 仓库地址（分享二维码指向此处） */
private const val GITHUB_URL = "https://github.com/ChenyangZuo/Shiwu"

/** 在后台线程生成指向仓库的二维码位图 */
@Composable
private fun ShareQrBitmap(url: String, size: Int): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.Default) {
            try {
                val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
                val ink = 0xFF1B1B19.toInt()
                val white = 0xFFFFFFFF.toInt()
                val pixels = IntArray(size * size) { i ->
                    if (matrix[i % size, i / size]) ink else white
                }
                Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    return bitmap
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
                text = "使用 时物 已 $memberDays 天",
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
