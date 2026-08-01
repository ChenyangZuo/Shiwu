package com.zzzcy.shiwu.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

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
    private var nextItemId = 1L
    private var nextWishId = 1L

    val items: StateFlow<List<Item>> = _items.asStateFlow()
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()

    private val _userName = MutableStateFlow("林川")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            dataFile = File(context.filesDir, "shiwu_data.json")
            prefs = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
            _userName.value = prefs?.getString("user_name", "林川") ?: "林川"
            firstLaunchDay = prefs?.getLong("first_launch_day", -1L) ?: -1L
            if (firstLaunchDay < 0) {
                firstLaunchDay = LocalDate.now().toEpochDay()
                prefs?.edit()?.putLong("first_launch_day", firstLaunchDay)?.apply()
            }
            load()
            initialized = true
        }
    }

    /** 使用天数（自首次启动起） */
    val memberDays: Int
        get() = (LocalDate.now().toEpochDay() - firstLaunchDay).toInt().coerceAtLeast(0)

    /** 更新昵称并持久化 */
    fun updateUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _userName.value) return
        _userName.value = trimmed
        prefs?.edit()?.putString("user_name", trimmed)?.apply()
    }

    // ---------- 查询 ----------

    fun activeItems(): List<Item> = _items.value.filter { !it.deleted }

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
        _items.value += Item(id, name, category, price, purchaseDate, photos, note)
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
        _wishes.value += Wish(id, name, category, price, priority, savedAmount, note)
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
            purchaseDate = LocalDate.now().toEpochDay()
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

    fun homeStats(): HomeStats {
        val list = activeItems()
        val now = LocalDate.now()
        return HomeStats(
            totalValue = list.sumOf { it.price },
            itemCount = list.size,
            avgDayCost = list.sumOf { it.dayCost },
            monthNew = list.count { LocalDate.ofEpochDay(it.purchaseDate).yearMonth() == now.yearMonth() }
        )
    }

    fun wishStats(): Triple<Double, Int, Double> {
        val list = _wishes.value.filter { !it.purchased }
        return Triple(
            list.sumOf { it.price },
            list.size,
            list.sumOf { it.savedAmount }
        )
    }

    /** 近 6 个月总价值趋势（按购入时间累计） */
    fun trendPoints(months: Int = 6): List<Pair<String, Double>> {
        val list = activeItems()
        val now = YearMonth.now()
        return (months - 1 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            val end = ym.atEndOfMonth().toEpochDay()
            val value = list.filter { LocalDate.ofEpochDay(it.purchaseDate) <= ym.atEndOfMonth() }.sumOf { it.price }
            "${ym.monthValue}月" to value
        }
    }

    /** 分类占比 */
    fun categoryShares(): List<Triple<String, Double, Double>> {
        val list = activeItems()
        val total = list.sumOf { it.price }
        if (total <= 0) return emptyList()
        return list.groupBy { it.category }
            .map { (cat, items) -> Triple(cat, items.sumOf { it.price }, items.sumOf { it.price } / total) }
            .sortedByDescending { it.second }
    }

    fun topDayCosts(limit: Int = 3): List<Item> =
        activeItems().sortedByDescending { it.dayCost }.take(limit)

    // ---------- 导入导出 / 备份 ----------

    /** 导出全部数据（与存储文件同格式） */
    fun exportJson(): String = synchronized(lock) {
        JSONObject().apply {
            put("app", "shiwu")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("items", JSONArray(_items.value.map { it.toJson() }))
            put("wishes", JSONArray(_wishes.value.map { it.toJson() }))
        }.toString(1)
    }

    /** 导入数据（合并：不覆盖现有记录，冲突项重新分配 id） */
    fun importJson(text: String): Int = mutate {
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: JSONArray()
        var imported = 0
        for (i in 0 until arr.length()) {
            val item = itemFromJson(arr.getJSONObject(i))
            _items.value += item.copy(id = nextItemId++, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            imported++
        }
        val warr = root.optJSONArray("wishes") ?: JSONArray()
        for (i in 0 until warr.length()) {
            val wish = wishFromJson(warr.getJSONObject(i))
            _wishes.value += wish.copy(id = nextWishId++, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            imported++
        }
        imported
    }

    data class BackupInfo(
        val count: Int,
        val totalSizeBytes: Long,
        val lastBackupAt: Long?
    )

    fun backupInfo(): BackupInfo {
        val dir = backupDir() ?: return BackupInfo(0, 0, null)
        val files = dir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList()
        return BackupInfo(
            count = files.size,
            totalSizeBytes = files.sumOf { it.length() },
            lastBackupAt = files.maxOfOrNull { it.lastModified() }
        )
    }

    /** 创建一份备份快照 */
    fun createBackup(): Long = synchronized(lock) {
        val dir = backupDir() ?: return 0L
        val f = File(dir, "backup_${System.currentTimeMillis()}.json")
        f.writeText(exportJson())
        f.length()
    }

    private fun backupDir(): File? {
        val base = dataFile?.parentFile ?: return null
        return File(base, "backups").apply { mkdirs() }
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
            val items = ArrayList<Item>()
            val arr = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) items.add(itemFromJson(arr.getJSONObject(i)))
            val wishes = ArrayList<Wish>()
            val warr = root.optJSONArray("wishes") ?: JSONArray()
            for (i in 0 until warr.length()) wishes.add(wishFromJson(warr.getJSONObject(i)))
            _items.value = items
            _wishes.value = wishes
        } catch (_: Exception) {
            seed()
        }
    }

    private fun save() {
        val f = dataFile ?: return
        val root = JSONObject().apply {
            put("nextItemId", nextItemId)
            put("nextWishId", nextWishId)
            put("items", JSONArray(_items.value.map { it.toJson() }))
            put("wishes", JSONArray(_wishes.value.map { it.toJson() }))
        }
        f.writeText(root.toString(1))
    }

    /** 首次启动写入示例数据（迁移自第一版 MockData） */
    private fun seed() {
        val today = LocalDate.now().toEpochDay()
        fun daysAgo(days: Long) = today - days

        val items = listOf(
            Item(1, "Sony WH-1000XM5 无线降噪耳机", "数码", 2899.0, daysAgo(124)),
            Item(2, "Kindle Paperwhite 11代", "阅读", 1098.0, daysAgo(287)),
            Item(3, "iPad Air M2 11 寸 256G", "数码", 4799.0, daysAgo(56)),
            Item(4, "The North Face 冲锋衣", "服饰", 1890.0, daysAgo(412)),
            Item(
                5, "小米手环 8 NFC", "数码", 249.0, daysAgo(400),
                retired = true, retirementDate = daysAgo(30)
            )
        )
        val wishes = listOf(
            Wish(1, "MacBook Pro 14\" M4 Pro", "数码", 14999.0, "HIGH", note = "预计 11月"),
            Wish(2, "DJI Pocket 3 全能套装", "影像", 4499.0, "MEDIUM", savedAmount = 3200.0, note = "已攒 ¥3,200"),
            Wish(3, "一加 13 16+512G", "数码", 4499.0, "MEDIUM", note = "等待发布"),
            Wish(4, "飞天茅台 53° 500ml", "收藏", 2499.0, "MEDIUM", note = "节日备礼"),
            Wish(5, "徕卡 Q3 43 紧凑全幅", "影像", 46800.0, "DREAM", note = "长期目标")
        )
        nextItemId = 6
        nextWishId = 6
        _items.value = items
        _wishes.value = wishes
        save()
    }
}

private fun LocalDate.yearMonth(): YearMonth = YearMonth.of(year, monthValue)
