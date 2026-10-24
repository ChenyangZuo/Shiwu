package com.zzzcy.shiwu.ui.screens

import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.SyncConfigStore
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.WebDavSyncManager
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface
import com.zzzcy.shiwu.util.WebDavClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WebDavSyncScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val loadResult = remember { SyncConfigStore.loadResult(context) }
    val saved = loadResult.config
    var server by remember { mutableStateOf(saved?.server ?: "") }
    var username by remember { mutableStateOf(saved?.username ?: "") }
    var password by remember { mutableStateOf(saved?.password ?: "") }
    var testing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    val busy = testing || syncing
    var lastReport by remember { mutableStateOf<String?>(null) }
    var lastNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember {
        mutableStateOf<String?>(
            if (loadResult.decryptFailed) "已保存的密码无法解密（可能换机/恢复备份），请重新输入密码" else null
        )
    }
    var lastSync by remember { mutableStateOf(SyncConfigStore.lastSyncAt(context)) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun errorText(e: Throwable): String {
        val m = e.message ?: "同步失败"
        return when {
            m.contains("401") -> "账号或密码错误"
            m.contains("403") -> "服务器拒绝访问（无权限）"
            m.contains("404") -> "服务器路径不存在"
            m.contains("412") || m.contains("锁") -> "其他设备正在同步，请稍后重试"
            m.contains("507") -> "服务器存储空间不足"
            else -> m
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "WebDAV 同步",
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fg
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "NAS 配置",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Fg
                        )
                        SyncField("服务器地址", server, { server = it }, "http://192.168.1.100:5005")
                        SyncField("账号", username, { username = it }, "shiwu")
                        SyncField(
                            "密码",
                            password,
                            { password = it },
                            "WebDAV 密码",
                            password = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton("保存配置", accent = true, enabled = !busy) {
                                val url = server.trim()
                                if (url.isEmpty()) {
                                    toast("请填写服务器地址")
                                } else {
                                    val ok = SyncConfigStore.save(
                                        context,
                                        WebDavSyncManager.Config(url, username.trim(), password)
                                    )
                                    toast(if (ok) "配置已保存" else "配置保存失败：无法加密密码")
                                }
                            }
                            ActionButton(
                                text = if (testing) "连接中…" else "测试连接",
                                accent = false,
                                enabled = !busy
                            ) {
                                val url = server.trim()
                                if (url.isEmpty()) {
                                    toast("请填写服务器地址")
                                } else {
                                    testing = true
                                    scope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                val c = WebDavClient(url, username.trim(), password)
                                                c.mkcol(WebDavSyncManager.ROOT)
                                                c.propfind(WebDavSyncManager.ROOT)
                                            }
                                        }
                                        testing = false
                                        result.onSuccess { toast("连接成功") }
                                            .onFailure {
                                                Log.e("ShiwuSync", "测试连接失败", it)
                                                toast("连接失败：${errorText(it)}")
                                            }
                                    }
                                }
                            }
                        }
                    }
                }

                CardSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "同步",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Fg
                        )
                        Text(
                            text = if (lastSync > 0) {
                                "上次同步：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync))}"
                            } else {
                                "尚未同步过"
                            },
                            fontSize = 11.sp,
                            color = Muted
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionButton(
                                text = if (syncing) "同步中…" else "立即同步",
                                accent = true,
                                enabled = !busy && server.isNotBlank()
                            ) {
                                val url = server.trim()
                                syncing = true
                                error = null
                                lastReport = null
                                lastNotes = emptyList()
                                scope.launch {
                                    val result = runCatching {
                                        WebDavSyncManager.sync(
                                            context,
                                            WebDavSyncManager.Config(url, username.trim(), password)
                                        )
                                    }
                                    syncing = false
                                    result.onSuccess { report ->
                                        SyncConfigStore.markSynced(context)
                                        lastSync = SyncConfigStore.lastSyncAt(context)
                                        lastReport = report.summary()
                                        lastNotes = report.notes
                                        toast("同步完成：${report.summary()}")
                                    }.onFailure {
                                        Log.e("ShiwuSync", "同步失败", it)
                                        error = errorText(it)
                                        toast("同步失败：${errorText(it)}")
                                    }
                                }
                            }
                            if (testing || syncing) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Accent
                                )
                            }
                        }
                        if (error != null) {
                            Text(
                                text = error!!,
                                fontSize = 11.sp,
                                color = Danger
                            )
                        }
                        if (lastReport != null) {
                            Text(
                                text = lastReport!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Accent
                            )
                            lastNotes.forEach { note ->
                                Text(text = "· $note", fontSize = 10.5.sp, color = Muted)
                            }
                        }
                    }
                }

                if (isDebug) {
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "测试数据（仅调试版）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Fg
                            )
                            Text(
                                text = "批量生成物品 / 心愿，方便演练同步合并；不含照片。",
                                fontSize = 11.sp,
                                color = Muted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton("生成 30 件物品", accent = true, enabled = !busy) {
                                    ShiwuRepository.generateTestData(30, 0)
                                    toast("已生成 30 件物品")
                                }
                                ActionButton("生成 15 件心愿", accent = false, enabled = !busy) {
                                    ShiwuRepository.generateTestData(0, 15)
                                    toast("已生成 15 件心愿")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "说明：同步在局域网 WebDAV 服务器上保存一份数据快照（含照片）。\n" +
                        "离线期间的修改不会丢失；同一条记录两端都修改时，保留较新版本，输方会保存为「（冲突副本）」并在此显示。",
                    fontSize = 10.5.sp,
                    color = Muted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SyncField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .background(Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(fontSize = 13.sp, color = Fg, lineHeight = 17.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
                    ),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = Muted.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    accent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (accent) Accent else AccentSoft, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) OnAccent else Accent,
            maxLines = 1
        )
    }
}
