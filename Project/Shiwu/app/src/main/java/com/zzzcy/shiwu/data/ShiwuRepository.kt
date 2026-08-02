package com.zzzcy.shiwu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.zzzcy.shiwu.util.csvEscape
import com.zzzcy.shiwu.util.parseCsv
import com.zzzcy.shiwu.util.PhotoUtils
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * 数据仓库：Room 持久化（AGP 9 内置 Kotlin + KSP2 已验证可用）。
 * 对外接口保持 StateFlow + 同步调用形态，UI 层无需改动。
 */
object ShiwuRepository {
    private val initLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var db: AppDatabase? = null
    private var prefs: SharedPreferences? = null
    private var initialized = false
    private var firstLaunchDay: Long = LocalDate.now().toEpochDay()
    private var nextItemId = 1L
    private var nextWishId = 1L
    private var nextListId = 1L

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    private val _wishes = MutableStateFlow<List<Wish>>(emptyList())
    private val _lists = MutableStateFlow<List<InventoryList>>(emptyList())
    private val _categories = MutableStateFlow(defaultCategories)
    private val _themeMode = MutableStateFlow("system")
    private val _currentListId = MutableStateFlow(1L)
    private val _userName = MutableStateFlow("林川")
    private val _unlockedLists = MutableStateFlow<Set<Long>>(emptySet())

    val items: StateFlow<List<Item>> = _items.asStateFlow()
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()
    val lists: StateFlow<List<InventoryList>> = _lists.asStateFlow()
    val currentListId: StateFlow<Long> = _currentListId.asStateFlow()
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    val userName: StateFlow<String> = _userName.asStateFlow()
    val unlockedLists: StateFlow<Set<Long>> = _unlockedLists.asStateFlow()

    private var nicknameSet = false

    /** 是否需要首次昵称设置（新装 App 首次启动时展示引导） */
    val needsNicknameSetup: Boolean
        get() = !nicknameSet

    fun init(context: Context) {
        synchronized(initLock) {
            if (initialized) return
            db = AppDatabase.build(context)
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
            nextItemId = prefs?.getLong("next_item_id", 1L) ?: 1L
            nextWishId = prefs?.getLong("next_wish_id", 1L) ?: 1L
            nextListId = prefs?.getLong("next_list_id", 1L) ?: 1L

            // 旧的 JSON 存储不再使用（按确认不做数据迁移）
            File(context.filesDir, "shiwu_data.json").delete()

            runBlocking {
                ensureDefaults()
                _items.value = db!!.itemDao().observeAll().first().map { it.toModel() }
                _wishes.value = db!!.wishDao().observeAll().first().map { it.toModel() }
                _lists.value = db!!.listDao().observeAll().first().map { it.toModel() }
                _categories.value = db!!.categoryDao().observeAll().first().map { it.toModel() }
            }

            scope.launch { db!!.itemDao().observeAll().collect { _items.value = it.map { e -> e.toModel() } } }
            scope.launch { db!!.wishDao().observeAll().collect { _wishes.value = it.map { e -> e.toModel() } } }
            scope.launch { db!!.listDao().observeAll().collect { _lists.value = it.map { e -> e.toModel() } } }
            scope.launch { db!!.categoryDao().observeAll().collect { _categories.value = it.map { e -> e.toModel() } } }

            // 后台把「保存时压缩」上线前存的旧大图原地压缩，不阻塞启动
            scope.launch { PhotoUtils.recompressExistingPhotos(context) }

            // 恢复上次使用的清单：已删除或已锁定的不回退展示，改选第一份未锁定清单；
            // 若全部清单都锁定，则指向无效 id（-1），界面不展示任何清单内容
            val savedListId = prefs?.getLong("current_list_id", -1L) ?: -1L
            val savedValidAndUnlocked =
                _lists.value.any { it.id == savedListId } && !isListLocked(savedListId)
            _currentListId.value = when {
                savedValidAndUnlocked -> savedListId
                else -> _lists.value.firstOrNull { !isListLocked(it.id) }?.id ?: -1L
            }
            initialized = true
        }
    }

    /** 首次启动结构数据：默认清单与默认分类（非假数据） */
    private suspend fun ensureDefaults() {
        val d = db ?: return
        if (d.listDao().count() == 0L) {
            val id = nextListId++
            val now = System.currentTimeMillis()
            d.listDao().upsert(ListEntity(id, "我的物品", now, now))
            prefs?.edit()
                ?.putLong("next_list_id", nextListId)
                ?.putLong("current_list_id", id)
                ?.apply()
        }
        if (d.categoryDao().count() == 0L) {
            d.categoryDao().upsertAll(defaultCategories.map { it.toEntity() })
        }
    }

    private fun persistCounters() {
        prefs?.edit()
            ?.putLong("next_item_id", nextItemId)
            ?.putLong("next_wish_id", nextWishId)
            ?.putLong("next_list_id", nextListId)
            ?.apply()
    }

    private suspend fun withDb(block: suspend (AppDatabase) -> Unit) {
        val d = db ?: return
        withContext(Dispatchers.IO) { block(d) }
    }

    /** 当前清单实体 */
    val currentList: InventoryList?
        get() = _lists.value.firstOrNull { it.id == _currentListId.value }

    fun listName(id: Long): String = _lists.value.firstOrNull { it.id == id }?.name ?: "未知清单"

    // ---------- 清单管理 ----------

    fun listItemCount(listId: Long): Int = _items.value.count { it.listId == listId && !it.deleted }

    fun listWishCount(listId: Long): Int = _wishes.value.count { it.listId == listId && !it.purchased }

    fun addList(name: String): Long {
        val id = nextListId++
        val list = InventoryList(id = id, name = name.trim().ifEmpty { "我的物品 $id" })
        persistCounters()
        scope.launch { db?.listDao()?.upsert(list.toEntity()) }
        return id
    }

    fun renameList(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _lists.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            db?.listDao()?.upsert(current.copy(name = trimmed, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    /** 删除清单：物品移入回收站、心愿一并删除；不允许删除最后一份清单 */
    fun deleteList(id: Long): Boolean {
        if (_lists.value.size <= 1) return false
        prefs?.edit()?.remove(lockPrefKey(id))?.apply()
        _unlockedLists.value = _unlockedLists.value - id
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.itemDao().softDeleteByList(id, now)
                d.wishDao().deleteByList(id)
                d.listDao().delete(id)
            }
        }
        if (_currentListId.value == id) {
            _currentListId.value = _lists.value.firstOrNull { it.id != id }?.id ?: -1L
            prefs?.edit()?.putLong("current_list_id", _currentListId.value)?.apply()
        }
        return true
    }

    fun setCurrentListId(id: Long) {
        if (_lists.value.none { it.id == id }) return
        _currentListId.value = id
        prefs?.edit()?.putLong("current_list_id", id)?.apply()
    }

    // ---------- 清单锁（仅应用层鉴权，不加密原始数据） ----------

    data class ListLock(
        val enabled: Boolean,
        val passwordHash: String,
        val fingerprint: Boolean
    )

    private fun lockPrefKey(listId: Long) = "list_lock_$listId"

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun listLock(listId: Long): ListLock? {
        val raw = prefs?.getString(lockPrefKey(listId), null) ?: return null
        return try {
            val o = JSONObject(raw)
            ListLock(
                enabled = o.optBoolean("enabled", false),
                passwordHash = o.optString("passwordHash", ""),
                fingerprint = o.optBoolean("fingerprint", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isListLocked(listId: Long): Boolean = listLock(listId)?.enabled == true

    private fun saveLock(listId: Long, lock: ListLock) {
        val o = JSONObject().apply {
            put("enabled", lock.enabled)
            put("passwordHash", lock.passwordHash)
            put("fingerprint", lock.fingerprint)
        }
        prefs?.edit()?.putString(lockPrefKey(listId), o.toString())?.apply()
    }

    fun setListPassword(listId: Long, password: String): Boolean {
        val trimmed = password.trim()
        if (trimmed.length < 4) return false
        val old = listLock(listId)
        saveLock(
            listId,
            ListLock(
                enabled = true,
                passwordHash = sha256(trimmed),
                fingerprint = old?.fingerprint ?: false
            )
        )
        return true
    }

    fun verifyListPassword(listId: Long, password: String): Boolean {
        val lock = listLock(listId) ?: return false
        return lock.enabled && lock.passwordHash == sha256(password.trim())
    }

    fun setListFingerprint(listId: Long, enabled: Boolean) {
        val lock = listLock(listId) ?: return
        if (!lock.enabled || lock.passwordHash.isEmpty()) return
        saveLock(listId, lock.copy(fingerprint = enabled))
    }

    fun disableListLock(listId: Long, password: String): Boolean {
        if (!verifyListPassword(listId, password)) return false
        prefs?.edit()?.remove(lockPrefKey(listId))?.apply()
        _unlockedLists.value = _unlockedLists.value - listId
        return true
    }

    fun isListUnlocked(listId: Long): Boolean = listId in _unlockedLists.value

    fun unlockList(listId: Long) {
        _unlockedLists.value = _unlockedLists.value + listId
    }

    // ---------- 用户与设置 ----------

    /** 使用天数（自首次启动起） */
    val memberDays: Int
        get() = (LocalDate.now().toEpochDay() - firstLaunchDay).toInt().coerceAtLeast(0)

    fun updateUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _userName.value) return
        _userName.value = trimmed
        prefs?.edit()?.putString("user_name", trimmed)?.putBoolean("nickname_set", true)?.apply()
        nicknameSet = true
    }

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

    fun setThemeMode(mode: String) {
        if (mode !in listOf("light", "dark", "system")) return
        _themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode)?.apply()
    }

    // ---------- 分类管理 ----------

    fun addCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _categories.value.any { it.name == trimmed }) return false
        _categories.value += Category(trimmed, "shopping")
        scope.launch { db?.categoryDao()?.upsert(Category(trimmed, "shopping").toEntity()) }
        return true
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.categoryDao().rename(oldName, trimmed)
                d.itemDao().updateCategory(oldName, trimmed, now)
                d.wishDao().updateCategory(oldName, trimmed, now)
            }
        }
    }

    fun updateCategoryIcon(name: String, icon: String) {
        scope.launch { db?.categoryDao()?.updateIcon(name, icon) }
    }

    fun deleteCategory(name: String) {
        val fallback = _categories.value.firstOrNull { it.name != name }?.name ?: "其他"
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.categoryDao().delete(name)
                d.itemDao().updateCategory(name, fallback, now)
                d.wishDao().updateCategory(name, fallback, now)
            }
        }
    }

    fun categoryIconKey(name: String): String =
        _categories.value.firstOrNull { it.name == name }?.icon ?: defaultIconKeyForCategory(name)

    fun categoryUsage(name: String): Int =
        _items.value.count { it.category == name && !it.deleted } +
            _wishes.value.count { it.category == name && !it.purchased }

    // ---------- 查询 ----------

    fun activeItems(): List<Item> = _items.value.filter { !it.deleted }

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
        note: String = "",
        productionDate: Long? = null,
        expiryDate: Long? = null,
        shelfLifeDays: Int? = null
    ): Long {
        val id = nextItemId++
        persistCounters()
        val item = Item(
            id, name, category, price, purchaseDate, photos, note,
            productionDate = productionDate,
            expiryDate = expiryDate,
            shelfLifeDays = shelfLifeDays,
            listId = _currentListId.value
        )
        scope.launch { db?.itemDao()?.upsert(item.toEntity()) }
        return id
    }

    fun updateItem(item: Item) {
        scope.launch {
            db?.itemDao()?.upsert(item.copy(updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun retireItem(id: Long, date: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(retired = true, retirementDate = date, updatedAt = System.currentTimeMillis()).toEntity()
                )
            }
        }
    }

    fun unretireItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(retired = false, retirementDate = null, updatedAt = System.currentTimeMillis()).toEntity()
                )
            }
        }
    }

    fun softDeleteItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(deleted = true, deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()).toEntity()
                )
            }
        }
    }

    fun restoreItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(deleted = false, deletedAt = null, updatedAt = System.currentTimeMillis()).toEntity()
                )
            }
        }
    }

    fun purgeItem(id: Long) {
        scope.launch { db?.itemDao()?.delete(id) }
    }

    // ---------- 心愿 CRUD ----------

    fun addWish(
        name: String,
        category: String,
        price: Double,
        priority: String,
        savedAmount: Double,
        note: String
    ): Long {
        val id = nextWishId++
        persistCounters()
        val wish = Wish(id, name, category, price, priority, savedAmount, note, listId = _currentListId.value)
        scope.launch { db?.wishDao()?.upsert(wish.toEntity()) }
        return id
    }

    fun updateWish(wish: Wish) {
        scope.launch {
            db?.wishDao()?.upsert(wish.copy(updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun deleteWish(id: Long) {
        scope.launch { db?.wishDao()?.delete(id) }
    }

    /** 标记心愿已购买：自动生成对应物品（同一清单） */
    fun markPurchased(wishId: Long): Long? {
        val wish = _wishes.value.firstOrNull { it.id == wishId } ?: return null
        val itemId = nextItemId++
        persistCounters()
        val item = Item(
            id = itemId,
            name = wish.name,
            category = wish.category,
            price = wish.price,
            purchaseDate = LocalDate.now().toEpochDay(),
            listId = wish.listId
        )
        val updatedWish = wish.copy(purchased = true, purchasedItemId = itemId)
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                d.itemDao().upsert(item.toEntity())
                d.wishDao().upsert(updatedWish.toEntity())
            }
        }
        return itemId
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

    /** 分类占比：按当前清单的物品数量统计（分类件数 / 总件数） */
    fun categoryShares(listId: Long = _currentListId.value): List<Triple<String, Int, Double>> {
        val list = itemsOf(listId)
        val total = list.size
        if (total <= 0) return emptyList()
        return list.groupBy { it.category }
            .map { (cat, items) -> Triple(cat, items.size, items.size.toDouble() / total) }
            .sortedByDescending { it.second }
    }

    fun topDayCosts(limit: Int = 3, listId: Long = _currentListId.value): List<Item> =
        itemsOf(listId).sortedByDescending { it.dayCost }.take(limit)

    // ---------- 导入导出 ----------

    /** 导出全部数据（与旧 JSON 备份格式一致） */
    fun exportJson(): String {
        return JSONObject().apply {
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
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        // 清单：旧备份无 lists 时全部归入当前清单；有则按 id 映射新建
        val listIdMap = HashMap<Long, Long>()
        val listsToInsert = ArrayList<ListEntity>()
        val larr = root.optJSONArray("lists")
        if (larr != null && larr.length() > 0) {
            for (i in 0 until larr.length()) {
                val l = listFromJson(larr.getJSONObject(i))
                val newId = nextListId++
                listsToInsert.add(l.copy(id = newId).toEntity())
                listIdMap[l.id] = newId
            }
        }
        val itemsToInsert = ArrayList<ItemEntity>()
        val arr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val item = itemFromJson(arr.getJSONObject(i))
            val targetList = listIdMap[item.listId] ?: _currentListId.value
            itemsToInsert.add(
                item.copy(
                    id = nextItemId++,
                    listId = targetList,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ).toEntity()
            )
        }
        val wishesToInsert = ArrayList<WishEntity>()
        val warr = root.optJSONArray("wishes") ?: JSONArray()
        for (i in 0 until warr.length()) {
            val wish = wishFromJson(warr.getJSONObject(i))
            val targetList = listIdMap[wish.listId] ?: _currentListId.value
            wishesToInsert.add(
                wish.copy(
                    id = nextWishId++,
                    listId = targetList,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ).toEntity()
            )
        }
        val carr = root.optJSONArray("categories")
        val categoriesToInsert = ArrayList<CategoryEntity>()
        if (carr != null) {
            val existing = _categories.value.map { it.name }.toMutableSet()
            for (i in 0 until carr.length()) {
                val el = carr.get(i)
                val name = if (el is JSONObject) el.getString("name") else el.toString()
                if (name.isNotBlank() && name !in existing) {
                    val icon = if (el is JSONObject) el.optString("icon", "shopping") else defaultIconKeyForCategory(name)
                    categoriesToInsert.add(Category(name, icon).toEntity())
                    existing.add(name)
                }
            }
        }
        persistCounters()
        val imported = itemsToInsert.size + wishesToInsert.size
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                if (listsToInsert.isNotEmpty()) listsToInsert.forEach { d.listDao().upsert(it) }
                if (itemsToInsert.isNotEmpty()) d.itemDao().upsertAll(itemsToInsert)
                if (wishesToInsert.isNotEmpty()) d.wishDao().upsertAll(wishesToInsert)
                if (categoriesToInsert.isNotEmpty()) d.categoryDao().upsertAll(categoriesToInsert)
            }
        }
        return imported
    }

    /** CSV 表头（中文，与导入模板一致） */
    val csvHeaders = listOf("名称", "分类", "价格", "购入日期", "备注", "状态", "退役日期", "照片文件名", "生产日期", "到期日期", "保质期（天）")

    /** 导出物品清单为 CSV（纯文字；照片列记录文件名，照片本体随 ZIP 导出） */
    fun exportCsv(): String {
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
                    item.photos.firstOrNull()?.let { File(it).name } ?: "",
                    item.productionDate?.let { LocalDate.ofEpochDay(it).format(DateFmt) } ?: "",
                    item.expiryDate?.let { LocalDate.ofEpochDay(it).format(DateFmt) } ?: "",
                    item.shelfLifeDays?.toString() ?: ""
                ).joinToString(",") { csvEscape(it) }
            ).append("\n")
        }
        return sb.toString()
    }

    /** 生成 CSV 导入模板（表头 + 示例行） */
    fun csvTemplate(): String = buildString {
        append(csvHeaders.joinToString(",") { csvEscape(it) }).append("\n")
        append(
            listOf(
                "Sony WH-1000XM5 无线降噪耳机", "数码", "2899.00", "2025-06-15",
                "通勤降噪耳机", "使用中", "", "", "2025-05-20", "", "1095"
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "Kindle Paperwhite", "阅读", "998.00", "2024-03-01",
                "护眼阅读器", "已退役", "2025-01-20", "", "2023-12-01", "2025-12-01", ""
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "客制化机械键盘", "数码", "899.00", "2025-12-01",
                "", "使用中", "", "photo_123456.jpg", "", "2027-01-01", "730"
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
    }

    /**
     * 导入 CSV 物品清单（合并，重新分配 id）。
     * photoMap：照片文件名 → 已复制到本机私有目录的绝对路径（来自 ZIP 导入）。
     */
    fun importCsv(text: String, photoMap: Map<String, String> = emptyMap()): Int {
        val rows = parseCsv(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return 0
        val header = rows.first().map { it.trim() }
        val idx = csvHeaders.mapNotNull { h -> header.indexOf(h).takeIf { it >= 0 }?.let { h to it } }.toMap()
        val now = System.currentTimeMillis()
        val itemsToInsert = ArrayList<ItemEntity>()
        val categoriesToInsert = ArrayList<CategoryEntity>()
        val existingCategories = _categories.value.map { it.name }.toMutableSet()
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
            val productionDate = runCatching { LocalDate.parse(col("生产日期")).toEpochDay() }.getOrNull()
            val expiryDate = runCatching { LocalDate.parse(col("到期日期")).toEpochDay() }.getOrNull()
            val shelfLifeDays = col("保质期（天）").toIntOrNull()
            // 三选二：任填两项则补齐第三项；不足两项视为无保质期信息
            val (prod, exp, shelf) = when {
                productionDate != null && shelfLifeDays != null && shelfLifeDays > 0 ->
                    Triple(
                        productionDate,
                        LocalDate.ofEpochDay(productionDate).plusDays(shelfLifeDays.toLong()).toEpochDay(),
                        shelfLifeDays
                    )
                productionDate != null && expiryDate != null && expiryDate > productionDate ->
                    Triple(productionDate, expiryDate, (expiryDate - productionDate).toInt())
                expiryDate != null && shelfLifeDays != null && shelfLifeDays > 0 && expiryDate >= shelfLifeDays ->
                    Triple(
                        LocalDate.ofEpochDay(expiryDate).minusDays(shelfLifeDays.toLong()).toEpochDay(),
                        expiryDate,
                        shelfLifeDays
                    )
                else -> Triple(null, null, null)
            }
            val photoPath = col("照片文件名").let { photoMap[it] }
            val categoryName = col("分类").ifBlank { "未分类" }
            if (categoryName !in existingCategories) {
                categoriesToInsert.add(Category(categoryName, defaultIconKeyForCategory(categoryName)).toEntity())
                existingCategories.add(categoryName)
            }
            val item = Item(
                id = nextItemId++,
                name = name,
                category = categoryName,
                price = col("价格").toDoubleOrNull() ?: 0.0,
                purchaseDate = runCatching { LocalDate.parse(col("购入日期")).toEpochDay() }
                    .getOrDefault(LocalDate.now().toEpochDay()),
                photos = if (photoPath != null) listOf(photoPath) else emptyList(),
                note = col("备注"),
                retired = retired,
                retirementDate = retirement,
                productionDate = prod,
                expiryDate = exp,
                shelfLifeDays = shelf,
                createdAt = now,
                updatedAt = now,
                listId = _currentListId.value
            )
            itemsToInsert.add(item.toEntity())
        }
        persistCounters()
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                if (categoriesToInsert.isNotEmpty()) d.categoryDao().upsertAll(categoriesToInsert)
                if (itemsToInsert.isNotEmpty()) d.itemDao().upsertAll(itemsToInsert)
            }
        }
        return itemsToInsert.size
    }

    /** 当前在用物品引用的照片文件（去重），供 ZIP 导出使用 */
    fun exportPhotoFiles(): List<File> {
        return itemsOf(_currentListId.value)
            .flatMap { it.photos }
            .distinct()
            .mapNotNull { runCatching { File(it) }.getOrNull() }
            .filter { it.exists() && it.isFile }
    }
}

private fun LocalDate.yearMonth(): YearMonth = YearMonth.of(year, monthValue)
