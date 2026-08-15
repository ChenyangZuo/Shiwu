package com.zzzcy.shiwu.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.data.formatDate
import com.zzzcy.shiwu.ui.components.HeaderIconButton
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps
import com.zzzcy.shiwu.ui.components.PhotoImage
import com.zzzcy.shiwu.ui.components.PillChip
import com.zzzcy.shiwu.ui.components.dashedBorder
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.AccentSoft
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Border2
import com.zzzcy.shiwu.ui.theme.Danger
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.Muted2
import com.zzzcy.shiwu.ui.theme.NumFamily
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface
import com.zzzcy.shiwu.util.PhotoUtils
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormScreen(
    editingItem: Item?,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: (Long) -> Unit = {}
) {
    val context = LocalContext.current

    var name by remember(editingItem?.id) { mutableStateOf(editingItem?.name ?: "") }
    var category by remember(editingItem?.id) { mutableStateOf(editingItem?.category ?: "数码") }
    var location by remember(editingItem?.id) { mutableStateOf(editingItem?.location ?: "") }
    var priceText by remember(editingItem?.id) {
        mutableStateOf(if (editingItem != null && editingItem.price > 0) editingItem.price.toString() else "")
    }
    var purchaseDate by remember(editingItem?.id) {
        mutableStateOf(LocalDate.ofEpochDay(editingItem?.purchaseDate ?: LocalDate.now().toEpochDay()))
    }
    var productionDate by remember(editingItem?.id) { mutableStateOf(editingItem?.productionDate) }
    var expiryDate by remember(editingItem?.id) { mutableStateOf(editingItem?.expiryDate) }
    var shelfLifeText by remember(editingItem?.id) {
        mutableStateOf(editingItem?.shelfLifeDays?.toString() ?: "")
    }
    var note by remember(editingItem?.id) { mutableStateOf(editingItem?.note ?: "") }
    var photos by remember(editingItem?.id) { mutableStateOf(editingItem?.photos ?: emptyList()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showProductionPicker by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var showNewLocation by remember { mutableStateOf(false) }
    var newLocationText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    /** 单张照片：新照片直接替换旧照片，并清理旧文件 */
    fun replacePhoto(path: String) {
        photos.firstOrNull()?.let { runCatching { File(it).delete() } }
        photos = listOf(path)
    }

    // 拍照暂存文件
    var cameraTemp by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            PhotoUtils.moveCameraPhoto(context, cameraTemp)?.let { path ->
                replacePhoto(path)
            }
        }
        cameraTemp = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val picked = uri?.let { PhotoUtils.copyToInternal(context, it) }
        if (picked != null) replacePhoto(picked)
    }

    /** 三选二：设置生产日期时，用保质期或到期日期推算第三项 */
    fun setProductionDate(d: Long) {
        productionDate = d
        val s = shelfLifeText.toIntOrNull()
        if (s != null && s > 0) {
            expiryDate = LocalDate.ofEpochDay(d).plusDays(s.toLong()).toEpochDay()
        } else if (expiryDate != null && expiryDate!! > d) {
            shelfLifeText = (expiryDate!! - d).toString()
        }
    }

    /** 三选二：设置到期日期时，用生产日期或保质期推算第三项 */
    fun setExpiryDate(d: Long) {
        expiryDate = d
        val p = productionDate
        if (p != null && d > p) {
            shelfLifeText = (d - p).toString()
        } else {
            val s = shelfLifeText.toIntOrNull()
            if (s != null && s > 0 && d >= s) {
                productionDate = LocalDate.ofEpochDay(d).minusDays(s.toLong()).toEpochDay()
            }
        }
    }

    /** 三选二：设置保质期天数时，用生产日期或到期日期推算第三项 */
    fun setShelfLife(text: String) {
        shelfLifeText = text
        val s = text.toIntOrNull()
        val p = productionDate
        val e = expiryDate
        if (s != null && s > 0 && p != null) {
            expiryDate = LocalDate.ofEpochDay(p).plusDays(s.toLong()).toEpochDay()
        } else if (s != null && s > 0 && e != null && e >= s) {
            productionDate = LocalDate.ofEpochDay(e).minusDays(s.toLong()).toEpochDay()
        }
    }

    /** 保存时规整三字段：任填两项则补齐第三项；不足两项视为无保质期信息 */
    fun resolveExpiry(): Triple<Long?, Long?, Int?> {
        val p = productionDate
        val e = expiryDate
        val s = shelfLifeText.toIntOrNull()
        return when {
            p != null && s != null && s > 0 ->
                Triple(p, LocalDate.ofEpochDay(p).plusDays(s.toLong()).toEpochDay(), s)
            p != null && e != null && e > p ->
                Triple(p, e, (e - p).toInt())
            e != null && s != null && s > 0 && e >= s ->
                Triple(LocalDate.ofEpochDay(e).minusDays(s.toLong()).toEpochDay(), e, s)
            else -> Triple(null, null, null)
        }
    }

    fun save() {
        val price = priceText.toDoubleOrNull()
        val (prod, exp, shelf) = resolveExpiry()
        when {
            name.isBlank() -> error = "请输入物品名称"
            price == null || price < 0 -> error = "请输入有效的价格（≥ 0）"
            category.isBlank() -> error = "请选择分类"
            else -> {
                error = null
                if (editingItem == null) {
                    val id = ShiwuRepository.addItem(
                        name = name.trim(),
                        category = category,
                        price = price,
                        purchaseDate = purchaseDate.toEpochDay(),
                        photos = photos,
                        note = note.trim(),
                        productionDate = prod,
                        expiryDate = exp,
                        shelfLifeDays = shelf,
                        location = location.ifBlank { null }
                    )
                    onSaved(id)
                } else {
                    ShiwuRepository.updateItem(
                        editingItem.copy(
                            name = name.trim(),
                            category = category,
                            price = price,
                            purchaseDate = purchaseDate.toEpochDay(),
                            photos = photos,
                            note = note.trim(),
                            productionDate = prod,
                            expiryDate = exp,
                            shelfLifeDays = shelf,
                            location = location.ifBlank { null }
                        )
                    )
                    onSaved(editingItem.id)
                }
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
                    text = if (editingItem == null) "新增物品" else "编辑物品",
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
                    .padding(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhotoField(
                    photos = photos,
                    onPickGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onTakePhoto = {
                        val (file, uri) = PhotoUtils.createCameraUri(context)
                        cameraTemp = file
                        cameraLauncher.launch(uri)
                    },
                    onRemove = {
                        photos.firstOrNull()?.let { runCatching { File(it).delete() } }
                        photos = emptyList()
                    }
                )
                FormTextField(
                    label = "名称",
                    required = true,
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "例:Sony WH-1000XM5 无线降噪耳机",
                    focused = true
                )
                CategoryField(
                    selected = category,
                    onSelect = { category = it },
                    onCreateNew = { showNewCategory = true }
                )
                LocationField(
                    selected = location,
                    onSelect = { location = it },
                    onCreateNew = { showNewLocation = true }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FormTextField(
                            label = "价格",
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
                    }
                    Column(Modifier.weight(1f)) {
                        Column {
                            FieldLabel("购入日期")
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                                    .background(Surface, RoundedCornerShape(10.dp))
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = purchaseDate.formatDate(),
                                    fontSize = 13.sp,
                                    fontFamily = NumFamily,
                                    color = Fg,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = Muted2,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                FieldLabel("保质期信息", hint = "选填 · 填任意两项，第三项自动计算")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        ExpiryDateField(
                            label = "生产日期",
                            date = productionDate,
                            onClick = { showProductionPicker = true }
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        ExpiryDateField(
                            label = "到期日期",
                            date = expiryDate,
                            onClick = { showExpiryPicker = true }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormTextField(
                    label = "保质期（天）",
                    required = false,
                    value = shelfLifeText,
                    onValueChange = { input ->
                        if (input.length <= 6 && (input.isEmpty() || input.all { it.isDigit() })) {
                            setShelfLife(input)
                        }
                    },
                    placeholder = "选填，如 365",
                    keyboardType = KeyboardType.Number
                )
                FormTextField(
                    label = "备注",
                    required = false,
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "选填：购买渠道、保修信息等"
                )
                CalcHint()

                if (error != null) {
                    Text(
                        text = error ?: "",
                        fontSize = 11.sp,
                        color = Danger
                    )
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
                    text = if (editingItem == null) "添加物品" else "保存修改",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent,
                    letterSpacing = 0.5.sp
                )
            }
            if (editingItem != null) {
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
                        text = "删除物品",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Danger
                    )
                }
            }
        }

        if (showDatePicker) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = purchaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            purchaseDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = state)
            }
        }

        if (showProductionPicker) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = (productionDate?.let { LocalDate.ofEpochDay(it) } ?: purchaseDate)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showProductionPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            setProductionDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                        }
                        showProductionPicker = false
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showProductionPicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = state)
            }
        }

        if (showExpiryPicker) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = (expiryDate?.let { LocalDate.ofEpochDay(it) } ?: purchaseDate)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showExpiryPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            setExpiryDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                        }
                        showExpiryPicker = false
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showExpiryPicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = state)
            }
        }

        if (showNewCategory) {
            AlertDialog(
                onDismissRequest = { showNewCategory = false },
                title = { Text("新建分类") },
                text = {
                    Column {
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
                    }
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

        if (showNewLocation) {
            AlertDialog(
                onDismissRequest = { showNewLocation = false },
                title = { Text("新建存放地点") },
                text = {
                    Column {
                        BasicTextField(
                            value = newLocationText,
                            onValueChange = { newLocationText = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Fg, lineHeight = 18.sp),
                            cursorBrush = SolidColor(Accent),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Bg2, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = newLocationText.trim()
                        if (trimmed.isNotBlank() && ShiwuRepository.addLocation(trimmed)) {
                            location = trimmed
                            newLocationText = ""
                            showNewLocation = false
                        }
                    }) { Text("确定", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewLocation = false }) { Text("取消") }
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除物品") },
                text = { Text("「${name}」将移入回收站，可稍后恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        editingItem?.let {
                            ShiwuRepository.softDeleteItem(it.id)
                            onDeleted(it.id)
                        }
                        showDeleteConfirm = false
                        onClose()
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
private fun ExpiryDateField(label: String, date: Long?, onClick: () -> Unit) {
    Column {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .background(Surface, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date?.let { LocalDate.ofEpochDay(it).formatDate() } ?: "未设置",
                fontSize = 13.sp,
                fontFamily = NumFamily,
                color = if (date != null) Fg else Muted2,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Muted2,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
internal fun FieldLabel(label: String, required: Boolean = false, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Muted,
            letterSpacing = 0.6.sp
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 11.sp,
                color = Muted2,
                letterSpacing = 0.2.sp
            )
        } else if (required) {
            Text(
                text = "必填",
                fontSize = 11.sp,
                color = Danger,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
private fun PhotoField(
    photos: List<String>,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemove: () -> Unit
) {
    Column {
        FieldLabel("照片", hint = "选填 · 仅 1 张")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .dashedBorder(Border2, cornerRadius = 10.dp)
                    .clickable(onClick = onPickGallery),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "添加",
                        fontSize = 9.5.sp,
                        color = Muted,
                        letterSpacing = 0.6.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .dashedBorder(Border2, cornerRadius = 10.dp)
                    .clickable(onClick = onTakePhoto),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "拍照",
                        fontSize = 9.5.sp,
                        color = Muted,
                        letterSpacing = 0.6.sp
                    )
                }
            }
            photos.firstOrNull()?.let { path ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bg2)
                ) {
                    PhotoImage(
                        path = path,
                        targetWidth = 200,
                        targetHeight = 200,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(topEnd = 10.dp, bottomStart = 10.dp))
                            .clickable { onRemove() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "移除",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FormTextField(
    label: String,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    unit: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    focused: Boolean = false
) {
    Column {
        FieldLabel(label, required)
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .then(
                    if (focused || value.isNotEmpty()) {
                        Modifier
                            .shadow(6.dp, shape, spotColor = Accent.copy(alpha = 0.12f), clip = false)
                            .border(1.dp, Accent, shape)
                    } else {
                        Modifier.border(1.dp, Border, shape)
                    }
                )
                .background(Surface, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (unit != null) {
                Text(
                    text = unit,
                    fontFamily = SerifFamily,
                    fontSize = 14.sp,
                    color = Muted
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(fontSize = 13.sp, color = Fg, lineHeight = 17.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = Muted2,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryField(
    selected: String,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit
) {
    Column {
        FieldLabel("分类", required = true)
        Spacer(Modifier.height(6.dp))
        val categories by ShiwuRepository.categories.collectAsState()
        val names = categories.map { it.name }
        val chips = if (selected !in names) names + selected else names
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips.forEach { chip ->
                PillChip(chip, active = chip == selected) { onSelect(chip) }
            }
            PillChip("+ 新建", active = false, onClick = onCreateNew)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocationField(
    selected: String,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit
) {
    Column {
        FieldLabel("存放地点")
        Spacer(Modifier.height(6.dp))
        val locations by ShiwuRepository.locations.collectAsState()
        val names = locations
        val chips = buildList {
            add("未设置")
            if (selected.isNotEmpty() && selected !in names) add(selected)
            addAll(names)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips.forEach { chip ->
                PillChip(
                    chip,
                    active = chip == (selected.ifEmpty { "未设置" })
                ) {
                    onSelect(if (chip == "未设置") "" else chip)
                }
            }
            PillChip("+ 新建", active = false, onClick = onCreateNew)
        }
    }
}

@Composable
private fun CalcHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "保存后自动计算:",
            fontSize = 10.5.sp,
            color = Accent,
            letterSpacing = 0.2.sp
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "日均成本 = 价格 ÷ 持有天数",
            fontFamily = NumFamily,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Accent
        )
    }
}
