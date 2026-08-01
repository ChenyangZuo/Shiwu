package com.zzzcy.shiwu.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import com.zzzcy.shiwu.util.csvEscape
import com.zzzcy.shiwu.util.parseCsv
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * 本地 JSON 文件持久化的数据仓库。
 * 说明：本阶段原计划用 Room，但 AGP 9 内置 Kotlin 与 kapt/KSP 均不兼容，
 * 故按计划兜底为 JSON 文件存储；对外接口保持与数据库方案一致，后续可替换实现。
 */
object ShiwuRepository {
    private val lock = Any()
    private var dataFile: File? = null
    private var initialized = false
    private var prefs: SharedPreferences? = null
    private var firstLaunchDay: Long = LocalDate.now().toEpochDay()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    private val _wishes = MutableStateFlow<List<Wish>>(emptyList())
    private val _lists = MutableStateFlow<List<InventoryList>>(emptyList())
    private val _categories = MutableStateFlow(defaultCategories)
    private val _themeMode = MutableStateFlow("system")
    private var nextItemId = 1L
    private var nextWishId = 1L
    private var nextListId = 1L
    private val _currentListId = MutableStateFlow(1L)

    val items: StateFlow<List<Item>> = _items.asStateFlow()
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()
    val lists: StateFlow<List<InventoryList>> = _lists.asStateFlow()
    val currentListId: StateFlow<Long> = _currentListId.asStateFlow()
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _userName = MutableStateFlow("林川")
    val userName: StateFlow<String> = _userName.asStateFlow()
    private var nicknameSet = false

    /** 是否需要首次昵称设置（新装 App 首次启动时展示引导） */
    val needsNicknameSetup: Boolean
        get() = !nicknameSet

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            dataFile = File(context.filesDir, "shiwu_data.json")
            prefs = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
            _userName.value = prefs?.getString("user_name", "林川") ?: "林川"
            nicknameSet = prefs?.getBoolean("nickname_set", false) ?: false
            // 老用户已有昵称但无标记：视为已设置，避免升级后弹引导
            val hasLegacyName = prefs?.contains("user_name") == true
            if (hasLegacyName && !nicknameSet) {
                nicknameSet = true
                prefs?.edit()?.putBoolean("nickname_set", true)?.apply()
            }
            _themeMode.value = prefs?.getString("theme_mode", "system") ?: "system"
            firstLaunchDay = prefs?.getLong("first_launch_day", -1L) ?: -1L
            if (firstLaunchDay < 0) {
                firstLaunchDay = LocalDate.now().toEpochDay()
                prefs?.edit()?.putLong("first_launch_day", firstLaunchDay)?.apply()
            }
            load()
            // 恢复上次使用的清单；若已被删除则回退到第一份
            val savedListId = prefs?.getLong("current_list_id", -1L) ?: -1L
            val valid = _lists.value.any { it.id == savedListId }
            _currentListId.value = if (valid) savedListId else (_lists.value.firstOrNull()?.id ?: 1L)
            initialized = true
        }
    }

    /** 当前清单实体 */
    val currentList: InventoryList?
        get() = _lists.value.firstOrNull { it.id == _currentListId.value }

    fun listName(id: Long): String = _lists.value.firstOrNull { it.id == id }?.name ?: "未知清单"

    // ---------- 清单管理 ----------

    /** 清单内物品数（不含回收站） */
    fun listItemCount(listId: Long): Int = _items.value.count { it.listId == listId && !it.deleted }

    /** 清单内心愿数（未购买） */
    fun listWishCount(listId: Long): Int = _wishes.value.count { it.listId == listId && !it.purchased }

    /** 新建清单并返回 id */
    fun addList(name: String): Long = mutate {
        val id = nextListId++
        _lists.value += InventoryList(id = id, name = name.trim().ifEmpty { "我的物品 $id" })
        id
    }

    fun renameList(id: Long, name: String) = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate
        _lists.value = _lists.value.map {
            if (it.id == id) it.copy(name = trimmed, updatedAt = System.currentTimeMillis()) else it
        }
    }

    /**
     * 删除清单：清单内物品移入回收站、心愿一并删除；不允许删除最后一份清单。
     * 若删除的是当前清单，自动切到第一份剩余清单。
     */
    fun deleteList(id: Long): Boolean = mutate {
        if (_lists.value.size <= 1) return@mutate false
        _lists.value = _lists.value.filterNot { it.id == id }
        _items.value = _items.value.map {
            if (it.listId == id) it.copy(deleted = true, deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) else it
        }
        _wishes.value = _wishes.value.filterNot { it.listId == id }
        if (_currentListId.value == id) {
            _currentListId.value = _lists.value.first().id
            prefs?.edit()?.putLong("current_list_id", _currentListId.value)?.apply()
        }
        true
    }

    fun setCurrentListId(id: Long) {
        if (_lists.value.none { it.id == id }) return
        _currentListId.value = id
        prefs?.edit()?.putLong("current_list_id", id)?.apply()
    }

    /** 使用天数（自首次启动起） */
    val memberDays: Int
        get() = (LocalDate.now().toEpochDay() - firstLaunchDay).toInt().coerceAtLeast(0)

    /** 更新昵称并持久化 */
    fun updateUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _userName.value) return
        _userName.value = trimmed
        prefs?.edit()?.putString("user_name", trimmed)?.putBoolean("nickname_set", true)?.apply()
        nicknameSet = true
    }

    /** 首次启动设置昵称（引导流程专用，确保落盘） */
    fun setupNickname(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _userName.value = trimmed
        prefs?.edit()
            ?.putString("user_name", trimmed)
            ?.putBoolean("nickname_set", true)
            ?.apply()
        nicknameSet = true
    }

    /** 设置主题模式：light / dark / system */
    fun setThemeMode(mode: String) {
        if (mode !in listOf("light", "dark", "system")) return
        _themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode)?.apply()
    }

    // ---------- 分类管理 ----------

    fun addCategory(name: String): Boolean = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _categories.value.any { it.name == trimmed }) return@mutate false
        _categories.value += Category(trimmed, "shopping")
        true
    }

    fun renameCategory(oldName: String, newName: String) = mutate {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return@mutate
        _categories.value = _categories.value.map { if (it.name == oldName) it.copy(name = trimmed) else it }
        _items.value = _items.value.map { if (it.category == oldName) it.copy(category = trimmed) else it }
        _wishes.value = _wishes.value.map { if (it.category == oldName) it.copy(category = trimmed) else it }
    }

    /** 更新分类图标 */
    fun updateCategoryIcon(name: String, icon: String) = mutate {
        _categories.value = _categories.value.map { if (it.name == name) it.copy(icon = icon) else it }
    }

    fun deleteCategory(name: String) = mutate {
        val fallback = _categories.value.firstOrNull { it.name != name }?.name ?: "其他"
        _categories.value = _categories.value.filterNot { it.name == name }
        _items.value = _items.value.map { if (it.category == name) it.copy(category = fallback) else it }
        _wishes.value = _wishes.value.map { if (it.category == name) it.copy(category = fallback) else it }
    }

    /** 分类当前配置的图标 key（未配置回退默认映射） */
    fun categoryIconKey(name: String): String =
        _categories.value.firstOrNull { it.name == name }?.icon ?: defaultIconKeyForCategory(name)

    fun categoryUsage(name: String): Int =
        _items.value.count { it.category == name && !it.deleted } +
            _wishes.value.count { it.category == name && !it.purchased }

    // ---------- 查询 ----------

    fun activeItems(): List<Item> = _items.value.filter { !it.deleted }

    /** 指定清单内的在用物品 */
    fun itemsOf(listId: Long): List<Item> = _items.value.filter { !it.deleted && it.listId == listId }

    fun trashedItems(): List<Item> = _items.value.filter { it.deleted }

    fun itemById(id: Long): Item? = _items.value.firstOrNull { it.id == id }

    fun wishById(id: Long): Wish? = _wishes.value.firstOrNull { it.id == id }

    // ---------- 物品 CRUD ----------

    fun addItem(
        name: String,
        category: String,
        price: Double,
        purchaseDate: Long,
        photos: List<String> = emptyList(),
        note: String = ""
    ): Long = mutate {
        val id = nextItemId++
        _items.value += Item(id, name, category, price, purchaseDate, photos, note, listId = _currentListId.value)
        id
    }

    fun updateItem(item: Item) = mutate {
        _items.value = _items.value.map { if (it.id == item.id) item.copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun retireItem(id: Long, date: Long) = mutate {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(retired = true, retirementDate = date, updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun unretireItem(id: Long) = mutate {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(retired = false, retirementDate = null, updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun softDeleteItem(id: Long) = mutate {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(deleted = true, deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun restoreItem(id: Long) = mutate {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(deleted = false, deletedAt = null, updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun purgeItem(id: Long) = mutate {
        _items.value = _items.value.filterNot { it.id == id }
    }

    // ---------- 心愿 CRUD ----------

    fun addWish(
        name: String,
        category: String,
        price: Double,
        priority: String,
        savedAmount: Double,
        note: String
    ): Long = mutate {
        val id = nextWishId++
        _wishes.value += Wish(id, name, category, price, priority, savedAmount, note, listId = _currentListId.value)
        id
    }

    fun updateWish(wish: Wish) = mutate {
        _wishes.value = _wishes.value.map { if (it.id == wish.id) wish.copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun deleteWish(id: Long) = mutate {
        _wishes.value = _wishes.value.filterNot { it.id == id }
    }

    /** 标记心愿已购买：自动生成对应物品 */
    fun markPurchased(wishId: Long): Long? = mutate {
        val wish = _wishes.value.firstOrNull { it.id == wishId } ?: return@mutate null
        val itemId = nextItemId++
        _items.value += Item(
            id = itemId,
            name = wish.name,
            category = wish.category,
            price = wish.price,
            purchaseDate = LocalDate.now().toEpochDay(),
            listId = wish.listId
        )
        _wishes.value = _wishes.value.map {
            if (it.id == wishId) it.copy(purchased = true, purchasedItemId = itemId, updatedAt = System.currentTimeMillis()) else it
        }
        itemId
    }

    // ---------- 统计 ----------

    data class HomeStats(
        val totalValue: Double,
        val itemCount: Int,
        val avgDayCost: Double,
        val monthNew: Int
    )

    fun homeStats(listId: Long = _currentListId.value): HomeStats {
        val list = itemsOf(listId)
        val now = LocalDate.now()
        return HomeStats(
            totalValue = list.sumOf { it.price },
            itemCount = list.size,
            avgDayCost = list.sumOf { it.dayCost },
            monthNew = list.count { LocalDate.ofEpochDay(it.purchaseDate).yearMonth() == now.yearMonth() }
        )
    }

    fun wishStats(listId: Long = _currentListId.value): Triple<Double, Int, Double> {
        val list = _wishes.value.filter { !it.purchased && it.listId == listId }
        return Triple(
            list.sumOf { it.price },
            list.size,
            list.sumOf { it.savedAmount }
        )
    }

    /** 近 6 个月总价值趋势（按购入时间累计） */
    fun trendPoints(months: Int = 6, listId: Long = _currentListId.value): List<Pair<String, Double>> {
        val list = itemsOf(listId)
        val now = YearMonth.now()
        return (months - 1 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            val end = ym.atEndOfMonth().toEpochDay()
            val value = list.filter { LocalDate.ofEpochDay(it.purchaseDate) <= ym.atEndOfMonth() }.sumOf { it.price }
            "${ym.monthValue}月" to value
        }
    }

    /** 分类占比 */
    fun categoryShares(listId: Long = _currentListId.value): List<Triple<String, Double, Double>> {
        val list = itemsOf(listId)
        val total = list.sumOf { it.price }
        if (total <= 0) return emptyList()
        return list.groupBy { it.category }
            .map { (cat, items) -> Triple(cat, items.sumOf { it.price }, items.sumOf { it.price } / total) }
            .sortedByDescending { it.second }
    }

    fun topDayCosts(limit: Int = 3, listId: Long = _currentListId.value): List<Item> =
        itemsOf(listId).sortedByDescending { it.dayCost }.take(limit)

    // ---------- 导入导出 ----------

    /** 导出全部数据（与存储文件同格式） */
    fun exportJson(): String = synchronized(lock) {
        JSONObject().apply {
            put("app", "shiwu")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("items", JSONArray(_items.value.map { it.toJson() }))
            put("wishes", JSONArray(_wishes.value.map { it.toJson() }))
            put("lists", JSONArray(_lists.value.map { it.toJson() }))
            put("categories", JSONArray(_categories.value.map { it.toJson() }))
        }.toString(1)
    }

    /** 导入数据（合并：不覆盖现有记录，冲突项重新分配 id） */
    fun importJson(text: String): Int = mutate {
        val root = JSONObject(text)
        // 清单：旧备份无 lists 时全部归入当前清单；有则按 id 映射新建
        val listIdMap = HashMap<Long, Long>()
        val larr = root.optJSONArray("lists")
        if (larr != null && larr.length() > 0) {
            for (i in 0 until larr.length()) {
                val l = listFromJson(larr.getJSONObject(i))
                val newId = nextListId++
                _lists.value += l.copy(id = newId)
                listIdMap[l.id] = newId
            }
        }
        val arr = root.optJSONArray("items") ?: JSONArray()
        var imported = 0
        for (i in 0 until arr.length()) {
            val item = itemFromJson(arr.getJSONObject(i))
            val targetList = listIdMap[item.listId] ?: _currentListId.value
            _items.value += item.copy(
                id = nextItemId++,
                listId = targetList,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            imported++
        }
        val warr = root.optJSONArray("wishes") ?: JSONArray()
        for (i in 0 until warr.length()) {
            val wish = wishFromJson(warr.getJSONObject(i))
            val targetList = listIdMap[wish.listId] ?: _currentListId.value
            _wishes.value += wish.copy(
                id = nextWishId++,
                listId = targetList,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            imported++
        }
        val carr = root.optJSONArray("categories")
        if (carr != null) {
            val merged = _categories.value.toMutableList()
            val existing = merged.map { it.name }.toMutableSet()
            for (i in 0 until carr.length()) {
                val el = carr.get(i)
                val name = if (el is JSONObject) el.getString("name") else el.toString()
                if (name.isNotBlank() && name !in existing) {
                    val icon = if (el is JSONObject) el.optString("icon", "shopping") else defaultIconKeyForCategory(name)
                    merged.add(Category(name, icon))
                    existing.add(name)
                }
            }
            _categories.value = merged
        }
        imported
    }

    /** CSV 表头（中文，与导入模板一致） */
    val csvHeaders = listOf("名称", "分类", "价格", "购入日期", "备注", "状态", "退役日期", "照片文件名")

    /** 导出物品清单为 CSV（纯文字；照片列记录文件名，照片本体随 ZIP 导出） */
    fun exportCsv(): String = synchronized(lock) {
        val sb = StringBuilder()
        sb.append(csvHeaders.joinToString(",") { csvEscape(it) }).append("\n")
        itemsOf(_currentListId.value).forEach { item ->
            sb.append(
                listOf(
                    item.name,
                    item.category,
                    "%.2f".format(Locale.US, item.price),
                    LocalDate.ofEpochDay(item.purchaseDate).format(DateFmt),
                    item.note,
                    if (item.retired) "已退役" else "使用中",
                    item.retirementDate?.let { LocalDate.ofEpochDay(it).format(DateFmt) } ?: "",
                    item.photos.firstOrNull()?.let { File(it).name } ?: ""
                ).joinToString(",") { csvEscape(it) }
            ).append("\n")
        }
        sb.toString()
    }

    /** 生成 CSV 导入模板（表头 + 示例行） */
    fun csvTemplate(): String = buildString {
        append(csvHeaders.joinToString(",") { csvEscape(it) }).append("\n")
        append(
            listOf(
                "Sony WH-1000XM5 无线降噪耳机", "数码", "2899.00", "2025-06-15",
                "通勤降噪耳机", "使用中", "", ""
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "Kindle Paperwhite", "阅读", "998.00", "2024-03-01",
                "护眼阅读器", "已退役", "2025-01-20", ""
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "客制化机械键盘", "数码", "899.00", "2025-12-01",
                "", "使用中", "", "photo_123456.jpg"
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
    }

    /**
     * 导入 CSV 物品清单（合并，重新分配 id）。
     * photoMap：照片文件名 → 已复制到本机私有目录的绝对路径（来自 ZIP 导入）。
     */
    fun importCsv(text: String, photoMap: Map<String, String> = emptyMap()): Int = mutate {
        val rows = parseCsv(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return@mutate 0
        val header = rows.first().map { it.trim() }
        val idx = csvHeaders.mapNotNull { h -> header.indexOf(h).takeIf { it >= 0 }?.let { h to it } }.toMap()
        val now = System.currentTimeMillis()
        var imported = 0
        rows.drop(1).forEach { row ->
            fun col(name: String): String = idx[name]?.let { row.getOrNull(it)?.trim() ?: "" } ?: ""
            val name = col("名称")
            if (name.isEmpty()) return@forEach
            val retired = col("状态") in setOf("已退役", "退役", "是", "true")
            val retirement = if (retired) {
                runCatching { LocalDate.parse(col("退役日期")).toEpochDay() }
                    .getOrDefault(LocalDate.now().toEpochDay())
            } else {
                null
            }
            val photoPath = col("照片文件名").let { photoMap[it] }
            _items.value += Item(
                id = nextItemId++,
                name = name,
                category = col("分类").ifBlank { "未分类" },
                price = col("价格").toDoubleOrNull() ?: 0.0,
                purchaseDate = runCatching { LocalDate.parse(col("购入日期")).toEpochDay() }
                    .getOrDefault(LocalDate.now().toEpochDay()),
                photos = if (photoPath != null) listOf(photoPath) else emptyList(),
                note = col("备注"),
                retired = retired,
                retirementDate = retirement,
                createdAt = now,
                updatedAt = now,
                listId = _currentListId.value
            )
            imported++
        }
        imported
    }

    /** 当前在用物品引用的照片文件（去重），供 ZIP 导出使用 */
    fun exportPhotoFiles(): List<File> = synchronized(lock) {
        itemsOf(_currentListId.value)
            .flatMap { it.photos }
            .distinct()
            .mapNotNull { runCatching { File(it) }.getOrNull() }
            .filter { it.exists() && it.isFile }
    }

    // ---------- 持久化 ----------

    private fun <T> mutate(block: () -> T): T = synchronized(lock) {
        val result = block()
        save()
        result
    }

    private fun load() {
        val f = dataFile ?: return
        if (!f.exists()) {
            seed()
            return
        }
        try {
            val root = JSONObject(f.readText())
            nextItemId = root.optLong("nextItemId", 1L)
            nextWishId = root.optLong("nextWishId", 1L)
            var items = ArrayList<Item>()
            val arr = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) items.add(itemFromJson(arr.getJSONObject(i)))
            var wishes = ArrayList<Wish>()
            val warr = root.optJSONArray("wishes") ?: JSONArray()
            for (i in 0 until warr.length()) wishes.add(wishFromJson(warr.getJSONObject(i)))
            val lists = ArrayList<InventoryList>()
            val larr = root.optJSONArray("lists")
            if (larr != null) {
                for (i in 0 until larr.length()) lists.add(listFromJson(larr.getJSONObject(i)))
            }
            if (lists.isEmpty()) {
                // 旧版本数据迁移：创建默认清单「我的物品」，归属全部物品/心愿
                lists.add(InventoryList(1, "我的物品"))
                nextListId = 2
                items = ArrayList(items.map { it.copy(listId = 1) })
                wishes = ArrayList(wishes.map { it.copy(listId = 1) })
            } else {
                nextListId = (lists.maxOfOrNull { it.id } ?: 0L) + 1
                // 兜底：缺失/未知清单归属的物品落到第一份清单
                val firstId = lists.first().id
                items = ArrayList(items.map { if (lists.any { l -> l.id == it.listId }) it else it.copy(listId = firstId) })
                wishes = ArrayList(wishes.map { if (lists.any { l -> l.id == it.listId }) it else it.copy(listId = firstId) })
            }
            val categories = ArrayList<Category>()
            val carr = root.optJSONArray("categories")
            if (carr != null) {
                for (i in 0 until carr.length()) {
                    val el = carr.get(i)
                    categories.add(
                        if (el is JSONObject) {
                            categoryFromJson(el)
                        } else {
                            val name = el.toString()
                            Category(name, defaultIconKeyForCategory(name))
                        }
                    )
                }
            }
            _lists.value = lists
            _items.value = items
            _wishes.value = wishes
            if (categories.isNotEmpty()) _categories.value = categories
        } catch (_: Exception) {
            seed()
        }
    }

    private fun save() {
        val f = dataFile ?: return
        val root = JSONObject().apply {
            put("nextItemId", nextItemId)
            put("nextWishId", nextWishId)
            put("nextListId", nextListId)
            put("items", JSONArray(_items.value.map { it.toJson() }))
            put("wishes", JSONArray(_wishes.value.map { it.toJson() }))
            put("lists", JSONArray(_lists.value.map { it.toJson() }))
            put("categories", JSONArray(_categories.value.map { it.toJson() }))
        }
        f.writeText(root.toString(1))
    }

    /** 首次启动初始化：只建默认清单与分类，不写入任何假数据 */
    private fun seed() {
        nextItemId = 1
        nextWishId = 1
        nextListId = 2
        _lists.value = listOf(InventoryList(1, "我的物品"))
        _items.value = emptyList()
        _wishes.value = emptyList()
        _categories.value = defaultCategories
        save()
    }

}

private fun LocalDate.yearMonth(): YearMonth = YearMonth.of(year, monthValue)
