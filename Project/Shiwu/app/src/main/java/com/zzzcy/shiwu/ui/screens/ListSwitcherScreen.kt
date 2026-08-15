package com.zzzcy.shiwu.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.CardSurface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface

/** 清单切换面板：切换 / 新建 / 重命名 / 删除 / 清单锁 */
@Composable
fun ListSwitcherScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lists by ShiwuRepository.lists.collectAsState()
    val currentId by ShiwuRepository.currentListId.collectAsState()
    val unlocked by ShiwuRepository.unlockedLists.collectAsState()

    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<InventoryList?>(null) }
    var deleteTarget by remember { mutableStateOf<InventoryList?>(null) }
    var draft by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    // 切换鉴权
    var authTarget by remember { mutableStateOf<InventoryList?>(null) }
    var authPassword by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    val authLock = authTarget?.let { ShiwuRepository.listLock(it.id) }

    // 锁设置
    var lockSetupTarget by remember { mutableStateOf<InventoryList?>(null) }
    var setupPassword by remember { mutableStateOf("") }
    var setupOldPassword by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }

    val biometricPrompt = remember { mutableStateOf<BiometricPrompt?>(null) }

    fun finishSwitch(list: InventoryList) {
        ShiwuRepository.setCurrentListId(list.id)
        onClose()
    }

    fun openPasswordAuth(list: InventoryList) {
        authPassword = ""
        authError = null
        authTarget = list
    }

    fun authSuccess(list: InventoryList) {
        ShiwuRepository.unlockList(list.id)
        authTarget = null
        finishSwitch(list)
    }

    fun launchFingerprint(list: InventoryList) {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPrompt.value = null
                    authSuccess(list)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPrompt.value = null
                    // 指纹弹窗关闭/取消/失败后，回到密码输入
                    openPasswordAuth(list)
                }
            }
        )
        biometricPrompt.value = prompt
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁清单")
            .setSubtitle("「${list.name}」已开启保护")
            .setNegativeButtonText("使用密码")
            .build()
        prompt.authenticate(info)
    }

    fun onListRowClick(list: InventoryList) {
        if (ShiwuRepository.isListLocked(list.id) && !ShiwuRepository.isListUnlocked(list.id)) {
            val lock = ShiwuRepository.listLock(list.id)
            if (lock?.fingerprint == true && biometricAvailable(context)) {
                launchFingerprint(list) // 优先直接弹系统指纹；关闭/取消后落到密码
            } else {
                openPasswordAuth(list)
            }
        } else {
            finishSwitch(list)
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
                    val locked = ShiwuRepository.isListLocked(list.id) && list.id !in unlocked
                    ListRow(
                        name = list.name,
                        itemCount = ShiwuRepository.listItemCount(list.id),
                        wishCount = ShiwuRepository.listWishCount(list.id),
                        active = list.id == currentId,
                        locked = locked,
                        onClick = { onListRowClick(list) },
                        onRename = {
                            draft = list.name
                            renameTarget = list
                        },
                        onDelete = { deleteTarget = list },
                        onLock = {
                            setupPassword = ""
                            setupOldPassword = ""
                            setupError = null
                            lockSetupTarget = list
                        }
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

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新建清单") },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth()) {
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
                        if (draft.isEmpty()) {
                            Text(
                                text = "清单名称",
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = Muted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Bg2, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                        if (newPassword.isEmpty()) {
                            Text(
                                text = "设置密码（可选，至少 4 位）",
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = Muted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = draft.trim()
                    if (name.isNotEmpty()) {
                        val id = ShiwuRepository.addList(name)
                        if (newPassword.isNotBlank()) {
                            ShiwuRepository.setListPassword(id, newPassword)
                            ShiwuRepository.unlockList(id)
                        }
                        ShiwuRepository.setCurrentListId(id)
                        draft = ""
                        newPassword = ""
                        showNew = false
                    }
                }) { Text("确定", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }) { Text("取消") }
            }
        )
    }

    authTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                authTarget = null
                authPassword = ""
                authError = null
            },
            title = { Text("清单已锁定") },
            text = {
                Column {
                    Text(
                        text = "「${target.name}」已开启保护，验证通过后才能查看与修改。",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = authPassword,
                            onValueChange = {
                                authPassword = it
                                authError = null
                            },
                            textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Bg2, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                        if (authPassword.isEmpty()) {
                            Text(
                                text = "输入密码",
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = Muted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    authError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 11.sp, color = Danger)
                    }
                    if (authLock?.fingerprint == true && biometricAvailable(context)) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { launchFingerprint(target) }) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("指纹解锁", color = Accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ShiwuRepository.verifyListPassword(target.id, authPassword)) {
                        authSuccess(target)
                    } else {
                        authError = "密码不正确"
                    }
                }) { Text("解锁", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    authTarget = null
                    authPassword = ""
                    authError = null
                }) { Text("取消") }
            }
        )
    }

    lockSetupTarget?.let { target ->
        val lock = remember(target.id) { ShiwuRepository.listLock(target.id) }
        var showChange by remember(target.id) { mutableStateOf(false) }
        var showDisable by remember(target.id) { mutableStateOf(false) }
        var fpState by remember(target.id) { mutableStateOf(lock?.fingerprint ?: false) }

        AlertDialog(
            onDismissRequest = {
                lockSetupTarget = null
                setupPassword = ""
                setupOldPassword = ""
                setupError = null
            },
            title = { Text("清单锁 · ${target.name}") },
            text = {
                Column {
                    Text(
                        text = if (lock?.enabled == true) "已开启密码保护" else "未设置保护",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        lock?.enabled != true -> {
                            BasicTextField(
                                value = setupPassword,
                                onValueChange = { setupPassword = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Bg2, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                            Text("设置密码（至少 4 位）", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                        }
                        showDisable -> {
                            Text("输入当前密码以关闭保护：", fontSize = 12.sp, color = Muted)
                            Spacer(Modifier.height(8.dp))
                            BasicTextField(
                                value = setupOldPassword,
                                onValueChange = { setupOldPassword = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Bg2, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                        else -> {
                            if (!showChange) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showChange = true }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("修改密码", fontSize = 13.sp, color = Accent)
                                }
                            } else {
                                BasicTextField(
                                    value = setupOldPassword,
                                    onValueChange = { setupOldPassword = it },
                                    textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Bg2, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                )
                                Text("当前密码", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                                Spacer(Modifier.height(8.dp))
                                BasicTextField(
                                    value = setupPassword,
                                    onValueChange = { setupPassword = it },
                                    textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Bg2, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                )
                                Text("新密码（至少 4 位）", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("指纹解锁", fontSize = 13.sp, color = Fg)
                                }
                                Switch(
                                    checked = fpState,
                                    onCheckedChange = {
                                        if (biometricAvailable(context)) {
                                            fpState = it
                                            ShiwuRepository.setListFingerprint(target.id, it)
                                        } else {
                                            Toast.makeText(context, "此设备不支持指纹", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showDisable = true }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("关闭保护", fontSize = 13.sp, color = Danger)
                            }
                        }
                    }
                    setupError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 11.sp, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = ShiwuRepository.listLock(target.id)
                    when {
                        current?.enabled != true -> {
                            if (ShiwuRepository.setListPassword(target.id, setupPassword)) {
                                lockSetupTarget = null
                                setupPassword = ""
                            } else {
                                setupError = "密码至少 4 位"
                            }
                        }
                        showDisable -> {
                            if (ShiwuRepository.disableListLock(target.id, setupOldPassword)) {
                                lockSetupTarget = null
                                setupOldPassword = ""
                            } else {
                                setupError = "当前密码不正确"
                            }
                        }
                        showChange -> {
                            if (!ShiwuRepository.verifyListPassword(target.id, setupOldPassword)) {
                                setupError = "当前密码不正确"
                            } else if (ShiwuRepository.setListPassword(target.id, setupPassword)) {
                                lockSetupTarget = null
                                setupPassword = ""
                                setupOldPassword = ""
                            } else {
                                setupError = "新密码至少 4 位"
                            }
                        }
                        else -> lockSetupTarget = null
                    }
                }) {
                    Text(
                        text = when {
                            lock?.enabled != true -> "设置密码"
                            showDisable -> "确认关闭"
                            showChange -> "保存新密码"
                            else -> "完成"
                        },
                        color = if (showDisable) Danger else Accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    lockSetupTarget = null
                    setupPassword = ""
                    setupOldPassword = ""
                    setupError = null
                }) { Text("取消") }
            }
        )
    }

    if (showNew || renameTarget != null) {
        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名清单") },
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
                        ShiwuRepository.renameList(target.id, draft)
                        renameTarget = null
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("取消") }
                }
            )
        }
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

private fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

@Composable
private fun ListRow(
    name: String,
    itemCount: Int,
    wishCount: Int,
    active: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit
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
                    if (locked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "已锁定",
                            tint = Muted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (locked) "已锁定" else "物品 $itemCount · 心愿 $wishCount",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLock),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = "清单锁", tint = Muted, modifier = Modifier.size(17.dp))
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
