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
import kotlin.random.Random
import java.util.Locale

/**
 * 数据仓库：Room 持久化（AGP 9 内置 Kotlin + KSP2 已验证可用）。
 * 对外接口保持 StateFlow + 同步调用形态，UI 层无需改动。
 */
object ShiwuRepository {
    private val initLock = Any()
    /** 单把锁保护三个序列的「自增 + 持久化」，主线程与同步 IO 线程共用 */
    private val seqLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var db: AppDatabase? = null
    private var prefs: SharedPreferences? = null
    private var deviceStateStore: DeviceStateStore? = null
    private var initialized = false
    private var firstLaunchDay: Long = LocalDate.now().toEpochDay()
    private var deviceId = 0L
    private var itemSeq = 0L
    private var wishSeq = 0L
    private var listSeq = 0L

    @Volatile
    private var gidMigrated = false

    /** 迁移事务前落盘的清单旧→新 ID 映射，用于事务已提交但完成标记未写时的崩溃恢复 */
    private const val KEY_PENDING_LIST_ID_MAP = "gid_pending_list_id_map"

    /**
     * 回收站「彻底删除」的墓碑标记：不物理删行，保留 deleted 墓碑参与同步，
     * 但 trashItems() 不展示；避免同步时被远端旧墓碑复活。
     */
    private const val PURGED_DELETED_AT = Long.MIN_VALUE

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    private val _wishes = MutableStateFlow<List<Wish>>(emptyList())
    private val _lists = MutableStateFlow<List<InventoryList>>(emptyList())
    private val _categories = MutableStateFlow(defaultCategories)
    private val _locations = MutableStateFlow(defaultLocations)
    private val _themeMode = MutableStateFlow("system")
    private val _currentListId = MutableStateFlow(1L)
    private val _userName = MutableStateFlow("林川")
    private val _unlockedLists = MutableStateFlow<Set<Long>>(emptySet())

    val items: StateFlow<List<Item>> = _items.asStateFlow()
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()
    val lists: StateFlow<List<InventoryList>> = _lists.asStateFlow()
    val currentListId: StateFlow<Long> = _currentListId.asStateFlow()
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    val locations: StateFlow<List<String>> = _locations.asStateFlow()
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    val userName: StateFlow<String> = _userName.asStateFlow()
    val unlockedLists: StateFlow<Set<Long>> = _unlockedLists.asStateFlow()

    /** 当前设备标识（同步用） */
    val syncDeviceId: String
        get() = deviceId.toString()

    private var nicknameSet = false

    /** 全局唯一 ID：高 23 位为设备号，低 40 位为本地自增序号 */
    private fun globalId(seq: Long): Long = globalSyncId(deviceId, seq)

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
            loadDeviceState(context)

            // 旧的 JSON 存储不再使用（按确认不做数据迁移）
            File(context.filesDir, "shiwu_data.json").delete()

            runBlocking {
                ensureDefaults()
                migrateGlobalIdsIfNeeded()
                calibrateSequences()
                _items.value = db!!.itemDao().observeAll().first().map { it.toModel() }
                _wishes.value = db!!.wishDao().observeAll().first().map { it.toModel() }.filter { !it.deleted }
                _lists.value = db!!.listDao().observeAll().first().map { it.toModel() }.filter { !it.deleted }
                _categories.value = db!!.categoryDao().observeAll().first().map { it.toModel() }
                _locations.value = db!!.locationDao().observeAll().first().map { it.name }
            }

            scope.launch { db!!.itemDao().observeAll().collect { _items.value = it.map { e -> e.toModel() } } }
            scope.launch {
                db!!.wishDao().observeAll().collect { _wishes.value = it.map { e -> e.toModel() }.filter { w -> !w.deleted } }
            }
            scope.launch {
                db!!.listDao().observeAll().collect { _lists.value = it.map { e -> e.toModel() }.filter { l -> !l.deleted } }
            }
            scope.launch { db!!.categoryDao().observeAll().collect { _categories.value = it.map { e -> e.toModel() } } }
            scope.launch { db!!.locationDao().observeAll().collect { _locations.value = it.map { e -> e.name } } }

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

    /**
     * 设备状态以 no_backup 目录下的 DeviceStateStore 为唯一来源：
     * - 有文件：完全以文件为准；
     * - 无文件（首次安装 / 从旧版本恢复备份）：生成新的 deviceId，绝不沿用 legacy prefs 的
     *   device_id，否则备份恢复/换机后会出现两台设备共用设备号；
     * - gidMigrated 必须继承 legacy prefs：旧版本已把 DB 内 ID 迁移为全局 ID，
     *   若丢失该标记会重跑迁移并重新分配 ID；
     * - legacy 序号仅作为初值，随后由 DB MAX(id) 校准。
     *
     * 读取完成后删除 legacy prefs 中的同步字段（昵称/主题/清单锁/WebDAV 配置等保留）。
     */
    private fun loadDeviceState(context: Context) {
        val store = DeviceStateStore(context)
        deviceStateStore = store
        val p = prefs
        val legacyGidMigrated = p?.getBoolean("gid_migrated", false) ?: false
        val legacyItemSeq = p?.getLong("item_seq", 0L) ?: 0L
        val legacyWishSeq = p?.getLong("wish_seq", 0L) ?: 0L
        val legacyListSeq = p?.getLong("list_seq", 0L) ?: 0L

        val stored = store.read()
        if (stored != null && stored.deviceId > 0L) {
            deviceId = stored.deviceId
            itemSeq = stored.itemSeq
            wishSeq = stored.wishSeq
            listSeq = stored.listSeq
            gidMigrated = stored.gidMigrated
        } else {
            deviceId = newDeviceId()
            itemSeq = legacyItemSeq.coerceAtLeast(0L)
            wishSeq = legacyWishSeq.coerceAtLeast(0L)
            listSeq = legacyListSeq.coerceAtLeast(0L)
            // 文件损坏但标记有效时也不能重跑迁移
            gidMigrated = legacyGidMigrated || (stored?.gidMigrated == true)
            store.write(DeviceState(deviceId, itemSeq, wishSeq, listSeq, gidMigrated))
        }

        // 同步删除 legacy key，避免旧备份继续携带 deviceId/序号
        p?.edit()
            ?.remove("device_id")
            ?.remove("item_seq")
            ?.remove("wish_seq")
            ?.remove("list_seq")
            ?.remove("gid_migrated")
            ?.commit()
    }

    private fun newDeviceId(): Long =
        (Random.nextLong() and 0x7FFFFFL).coerceAtLeast(1L)

    /** 首次启动结构数据：默认清单与默认分类（非假数据） */
    private suspend fun ensureDefaults() {
        val d = db ?: return
        if (d.listDao().count() == 0L) {
            val id = nextListId()
            val now = System.currentTimeMillis()
            d.listDao().upsert(
                ListEntity(id, "我的物品", now, now, 1, deviceId.toString(), 0, false, null)
            )
            prefs?.edit()?.putLong("current_list_id", id)?.commit()
        }
        if (d.categoryDao().count() == 0L) {
            d.categoryDao().upsertAll(defaultCategories.map { it.toEntity() })
        }
        if (d.locationDao().count() == 0L) {
            d.locationDao().upsertAll(defaultLocations.map { LocationEntity(it) })
        }
    }

    /**
     * 启用同步前的存量数据一次性迁移：把所有记录重新分配为全局唯一 ID，
     * 并在同一事务内重写引用（items.listId / wishes.listId / wishes.purchasedItemId）。
     *
     * 崩溃安全：清单旧→新 ID 映射在事务前写入 prefs。若事务提交后、完成标记前崩溃，
     * 下次启动可凭该映射补做清单锁迁移；若事务尚未提交，则可复用映射重跑迁移。
     * 完成标记只在锁迁移、current_list_id 重写、序号持久化全部完成后写入。
     */
    private suspend fun migrateGlobalIdsIfNeeded() {
        val d = db ?: return
        if (gidMigrated) {
            clearPendingListIdMap()
            return
        }
        val pending = loadPendingListIdMap()
        val lists = d.listDao().observeAll().first()
        if (pending != null) {
            val pendingApplied = lists.none { it.id in pending.keys } &&
                lists.any { it.id in pending.values }
            if (pendingApplied) {
                finishGlobalIdMigration(pending)
                return
            }
        }

        val items = d.itemDao().observeAll().first()
        val wishes = d.wishDao().observeAll().first()
        val needsMigration = lists.any { !isGlobalSyncId(it.id) } ||
            items.any { !isGlobalSyncId(it.id) } ||
            wishes.any { !isGlobalSyncId(it.id) }
        if (!needsMigration) {
            // DB 已是全局 ID（旧实现已提交事务但完成标记丢失）：不能重新分配 ID。
            // 此时没有可靠的旧→新映射，清单锁只能保持原样。
            clearPendingListIdMap()
            markGidMigrated()
            return
        }

        val listIdMap = LinkedHashMap<Long, Long>()
        pending?.forEach { (oldId, newId) -> listIdMap[oldId] = newId }
        val itemIdMap = HashMap<Long, Long>()
        val wishIdMap = HashMap<Long, Long>()

        // 自增 + 持久化在同一把 seqLock 内；同一批次只落盘一次。
        allocateIds {
            lists.forEach { list ->
                if (!isGlobalSyncId(list.id) && !listIdMap.containsKey(list.id)) {
                    listIdMap[list.id] = nextListIdLocked()
                }
            }
            items.forEach { item ->
                if (!isGlobalSyncId(item.id)) itemIdMap[item.id] = nextItemIdLocked()
            }
            wishes.forEach { wish ->
                if (!isGlobalSyncId(wish.id)) wishIdMap[wish.id] = nextWishIdLocked()
            }
        }
        // 关键顺序：先持久化映射，再提交事务。事务提交后崩溃也能补迁移清单锁。
        if (listIdMap.isNotEmpty()) savePendingListIdMap(listIdMap)

        val existingListIds = lists.mapTo(HashSet()) { it.id }
        val fallbackListId = listIdMap.values.firstOrNull() ?: lists.firstOrNull()?.id
        fun remapList(listId: Long): Long =
            listIdMap[listId] ?: listId.takeIf { it in existingListIds } ?: fallbackListId ?: listId

        d.withTransaction {
            lists.forEach { list ->
                val newId = listIdMap[list.id] ?: return@forEach
                if (newId != list.id) {
                    d.listDao().purge(list.id)
                    d.listDao().upsert(
                        list.copy(
                            id = newId,
                            rev = 1,
                            updatedBy = deviceId.toString(),
                            syncedRev = 0
                        )
                    )
                }
            }
            items.forEach { item ->
                val newId = itemIdMap[item.id] ?: item.id
                val newListId = remapList(item.listId)
                if (newId != item.id) d.itemDao().delete(item.id)
                d.itemDao().upsert(
                    item.copy(
                        id = newId,
                        listId = newListId,
                        rev = 1,
                        updatedBy = deviceId.toString(),
                        syncedRev = 0
                    )
                )
            }
            wishes.forEach { wish ->
                val newId = wishIdMap[wish.id] ?: wish.id
                val newListId = remapList(wish.listId)
                val newPurchased = wish.purchasedItemId?.let { oldPurchased ->
                    itemIdMap[oldPurchased]
                        ?: oldPurchased.takeIf { pid -> items.any { it.id == pid } }
                }
                if (newId != wish.id) d.wishDao().purge(wish.id)
                d.wishDao().upsert(
                    wish.copy(
                        id = newId,
                        listId = newListId,
                        purchasedItemId = newPurchased,
                        rev = 1,
                        updatedBy = deviceId.toString(),
                        syncedRev = 0
                    )
                )
            }
        }
        finishGlobalIdMigration(listIdMap)
    }

    /** 事务提交后收尾：迁清单锁 → 校正 current_list_id → 清映射 → 最后写完成标记 */
    private suspend fun finishGlobalIdMigration(listIdMap: Map<Long, Long>) {
        migrateListLockPrefs(listIdMap)
        val d = db
        val p = prefs
        if (d == null || p == null) return
        val actualLists = d.listDao().observeAll().first()
        val current = p.getLong("current_list_id", -1L)
        val mapped = listIdMap[current]
        val target = when {
            actualLists.any { it.id == current } -> current
            mapped != null && actualLists.any { it.id == mapped } -> mapped
            else -> actualLists.firstOrNull()?.id
        }
        if (target != null && target != current) {
            p.edit().putLong("current_list_id", target).commit()
        }
        clearPendingListIdMap()
        markGidMigrated()
    }

    /**
     * 把 list_lock_<旧id> 复制到 list_lock_<新id> 并删除旧 key。
     * 崩溃重入时新 key 已存在则不覆盖（保留已复制/已更新的值），仅清理旧 key。
     */
    private fun migrateListLockPrefs(listIdMap: Map<Long, Long>) {
        if (listIdMap.isEmpty()) return
        val p = prefs ?: return
        val editor = p.edit()
        var changed = false
        listIdMap.forEach { (oldId, newId) ->
            if (oldId == newId) return@forEach
            val oldKey = lockPrefKey(oldId)
            val raw = p.getString(oldKey, null) ?: return@forEach
            val newKey = lockPrefKey(newId)
            if (!p.contains(newKey)) editor.putString(newKey, raw)
            editor.remove(oldKey)
            changed = true
        }
        if (changed) editor.commit()
    }

    private fun markGidMigrated() {
        synchronized(seqLock) {
            gidMigrated = true
            persistCountersLocked()
        }
    }

    // ---------- 序列分配（主线程 + 同步 IO 线程共用，必须在同一把锁内自增并持久化） ----------

    private fun <T> allocateIds(block: () -> T): T = synchronized(seqLock) {
        try {
            block()
        } finally {
            persistCountersLocked()
        }
    }

    private fun nextItemId(): Long = allocateIds { nextItemIdLocked() }

    private fun nextWishId(): Long = allocateIds { nextWishIdLocked() }

    private fun nextListId(): Long = allocateIds { nextListIdLocked() }

    private fun nextItemIdLocked(): Long {
        itemSeq += 1
        return globalId(itemSeq)
    }

    private fun nextWishIdLocked(): Long {
        wishSeq += 1
        return globalId(wishSeq)
    }

    private fun nextListIdLocked(): Long {
        listSeq += 1
        return globalId(listSeq)
    }

    /** 调用方必须持有 seqLock */
    private fun persistCountersLocked() {
        deviceStateStore?.write(DeviceState(deviceId, itemSeq, wishSeq, listSeq, gidMigrated))
    }

    /**
     * 用 DB 中各表 MAX(id) 的低 40 位校准计数器，防止 prefs/DeviceState 丢失或回退后
     * 从 1 开始分配到与已有记录冲突的 ID。
     */
    private suspend fun calibrateSequences() {
        val d = db ?: return
        val itemMax = d.itemDao().maxSeq()
        val wishMax = d.wishDao().maxSeq()
        val listMax = d.listDao().maxSeq()
        synchronized(seqLock) {
            itemSeq = calibrateSequence(itemSeq, itemMax)
            wishSeq = calibrateSequence(wishSeq, wishMax)
            listSeq = calibrateSequence(listSeq, listMax)
            persistCountersLocked()
        }
    }

    // ---------- 全局 ID 迁移的崩溃恢复辅助 ----------

    private fun savePendingListIdMap(listIdMap: Map<Long, Long>) {
        if (listIdMap.isEmpty()) {
            clearPendingListIdMap()
            return
        }
        val o = JSONObject()
        listIdMap.forEach { (oldId, newId) -> o.put(oldId.toString(), newId) }
        prefs?.edit()?.putString(KEY_PENDING_LIST_ID_MAP, o.toString())?.commit()
    }

    private fun loadPendingListIdMap(): Map<Long, Long>? {
        val raw = prefs?.getString(KEY_PENDING_LIST_ID_MAP, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val keys = o.keys()
            val map = LinkedHashMap<Long, Long>()
            while (keys.hasNext()) {
                val key = keys.next()
                val oldId = key.toLongOrNull() ?: continue
                val newId = o.optLong(key, 0L)
                if (newId > 0L) map[oldId] = newId
            }
            map.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun clearPendingListIdMap() {
        val p = prefs ?: return
        if (p.contains(KEY_PENDING_LIST_ID_MAP)) {
            p.edit().remove(KEY_PENDING_LIST_ID_MAP).commit()
        }
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
        val id = nextListId()
        val list = InventoryList(id = id, name = name.trim().ifEmpty { "我的物品" }, rev = 1, updatedBy = syncDeviceId)
        scope.launch { db?.listDao()?.upsert(list.toEntity()) }
        return id
    }

    fun renameList(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _lists.value.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            db?.listDao()?.upsert(
                current.copy(name = trimmed, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
            )
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
            val current = _lists.value.firstOrNull { it.id == id } ?: return@launch
            d.withTransaction {
                _items.value.filter { it.listId == id }.forEach { item ->
                    d.itemDao().upsert(
                        item.copy(deleted = true, deletedAt = now, updatedAt = now, rev = item.rev + 1, updatedBy = syncDeviceId).toEntity()
                    )
                }
                _wishes.value.filter { it.listId == id }.forEach { wish ->
                    d.wishDao().upsert(
                        wish.copy(deleted = true, deletedAt = now, updatedAt = now, rev = wish.rev + 1, updatedBy = syncDeviceId).toEntity()
                    )
                }
                d.listDao().upsert(
                    current.copy(deleted = true, deletedAt = now, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
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

    /**
     * 分类改名：items / wishes 中的分类名作为普通记录更新传播（bump rev + updatedBy），
     * 使两端最终收敛。
     *
     * 注意：categories 集合本身不参与同步合并，「名称并集 / 删除后可能复活」是 v1 已知限制。
     */
    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.categoryDao().rename(oldName, trimmed)
                d.itemDao().updateCategory(oldName, trimmed, now, syncDeviceId)
                d.wishDao().updateCategory(oldName, trimmed, now, syncDeviceId)
            }
        }
    }

    fun updateCategoryIcon(name: String, icon: String) {
        scope.launch { db?.categoryDao()?.updateIcon(name, icon) }
    }

    /** 删除分类：同 [renameCategory]，通过 bump 记录 rev 让引用它的 items / wishes 传播更新 */
    fun deleteCategory(name: String) {
        val fallback = _categories.value.firstOrNull { it.name != name }?.name ?: "其他"
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.categoryDao().delete(name)
                d.itemDao().updateCategory(name, fallback, now, syncDeviceId)
                d.wishDao().updateCategory(name, fallback, now, syncDeviceId)
            }
        }
    }

    fun categoryIconKey(name: String): String =
        _categories.value.firstOrNull { it.name == name }?.icon ?: defaultIconKeyForCategory(name)

    fun categoryUsage(name: String): Int =
        _items.value.count { it.category == name && !it.deleted } +
            _wishes.value.count { it.category == name && !it.purchased }

    // ---------- 存放地点管理 ----------

    fun addLocation(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _locations.value.any { it == trimmed }) return false
        _locations.value += trimmed
        scope.launch { db?.locationDao()?.upsert(LocationEntity(trimmed)) }
        return true
    }

    /** 地点改名：items.location 作为普通记录更新传播（bump rev + updatedBy） */
    fun renameLocation(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.locationDao().rename(oldName, trimmed)
                d.itemDao().updateLocation(oldName, trimmed, now, syncDeviceId)
            }
        }
    }

    /** 删除地点：清空 items.location 并 bump rev，让删除也作为普通更新传播 */
    fun deleteLocation(name: String) {
        scope.launch {
            val d = db ?: return@launch
            val now = System.currentTimeMillis()
            d.withTransaction {
                d.locationDao().delete(name)
                d.itemDao().clearLocation(name, now, syncDeviceId)
            }
        }
    }

    fun locationUsage(name: String): Int =
        _items.value.count { it.location == name && !it.deleted }

    // ---------- 查询 ----------

    fun activeItems(): List<Item> = _items.value.filter { !it.deleted }

    fun itemsOf(listId: Long): List<Item> = _items.value.filter { !it.deleted && it.listId == listId }

    fun trashedItems(): List<Item> =
        _items.value.filter { it.deleted && it.deletedAt != PURGED_DELETED_AT }

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
        shelfLifeDays: Int? = null,
        location: String? = null
    ): Long {
        val id = nextItemId()
        val item = Item(
            id, name, category, price, purchaseDate, photos, note,
            productionDate = productionDate,
            expiryDate = expiryDate,
            shelfLifeDays = shelfLifeDays,
            location = location,
            listId = _currentListId.value,
            rev = 1,
            updatedBy = syncDeviceId
        )
        scope.launch { db?.itemDao()?.upsert(item.toEntity()) }
        return id
    }

    fun updateItem(item: Item) {
        val now = System.currentTimeMillis()
        scope.launch {
            db?.itemDao()?.upsert(
                item.copy(updatedAt = now, rev = item.rev + 1, updatedBy = syncDeviceId).toEntity()
            )
        }
    }

    fun retireItem(id: Long, date: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            val now = System.currentTimeMillis()
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(retired = true, retirementDate = date, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
            }
        }
    }

    fun unretireItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            val now = System.currentTimeMillis()
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(retired = false, retirementDate = null, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
            }
        }
    }

    fun softDeleteItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            val now = System.currentTimeMillis()
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(deleted = true, deletedAt = now, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
            }
        }
    }

    fun restoreItem(id: Long) {
        _items.value.firstOrNull { it.id == id }?.let { current ->
            val now = System.currentTimeMillis()
            scope.launch {
                db?.itemDao()?.upsert(
                    current.copy(deleted = false, deletedAt = null, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
            }
        }
    }

    /**
     * 把物品移动到另一清单（同步按普通更新传播）。
     * 目标清单加密且未解锁时拒绝移动；源清单必须已解锁（能查看详情即已解锁）。
     */
    fun moveItemToList(itemId: Long, targetListId: Long): Boolean {
        val item = _items.value.firstOrNull { it.id == itemId } ?: return false
        if (item.listId == targetListId) return false
        if (_lists.value.none { it.id == targetListId }) return false
        if (isListLocked(targetListId) && !isListUnlocked(targetListId)) return false
        val now = System.currentTimeMillis()
        scope.launch {
            db?.itemDao()?.upsert(
                item.copy(listId = targetListId, updatedAt = now, rev = item.rev + 1, updatedBy = syncDeviceId).toEntity()
            )
        }
        return true
    }

    /**
     * 彻底删除：不再物理删行，而是写一条 deleted 墓碑（deletedAt 用哨兵值，回收站不展示）。
     * 这样同步快照里始终有该墓碑，远端旧墓碑不会把它复活；照片文件同时删除。
     */
    fun purgeItem(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val tombstone = item.copy(
            deleted = true,
            deletedAt = PURGED_DELETED_AT,
            updatedAt = now,
            rev = item.rev + 1,
            updatedBy = syncDeviceId,
            photos = emptyList()
        )
        scope.launch {
            val d = db ?: return@launch
            d.itemDao().upsert(tombstone.toEntity())
            item.photos.forEach { path -> runCatching { File(path).delete() } }
        }
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
        val id = nextWishId()
        val wish = Wish(
            id, name, category, price, priority, savedAmount, note,
            listId = _currentListId.value, rev = 1, updatedBy = syncDeviceId
        )
        scope.launch { db?.wishDao()?.upsert(wish.toEntity()) }
        return id
    }

    fun updateWish(wish: Wish) {
        val now = System.currentTimeMillis()
        scope.launch {
            db?.wishDao()?.upsert(
                wish.copy(updatedAt = now, rev = wish.rev + 1, updatedBy = syncDeviceId).toEntity()
            )
        }
    }

    fun deleteWish(id: Long) {
        _wishes.value.firstOrNull { it.id == id }?.let { current ->
            val now = System.currentTimeMillis()
            scope.launch {
                db?.wishDao()?.upsert(
                    current.copy(deleted = true, deletedAt = now, updatedAt = now, rev = current.rev + 1, updatedBy = syncDeviceId).toEntity()
                )
            }
        }
    }

    /** 标记心愿已购买：自动生成对应物品（同一清单） */
    fun markPurchased(wishId: Long): Long? {
        val wish = _wishes.value.firstOrNull { it.id == wishId } ?: return null
        val itemId = nextItemId()
        val now = System.currentTimeMillis()
        val item = Item(
            id = itemId,
            name = wish.name,
            category = wish.category,
            price = wish.price,
            purchaseDate = LocalDate.now().toEpochDay(),
            listId = wish.listId,
            rev = 1,
            updatedBy = syncDeviceId
        )
        val updatedWish = wish.copy(
            purchased = true, purchasedItemId = itemId, updatedAt = now,
            rev = wish.rev + 1, updatedBy = syncDeviceId
        )
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
            put("items", JSONArray(_items.value.filter { !it.deleted }.map { it.toJson() }))
            put("wishes", JSONArray(_wishes.value.map { it.toJson() }))
            put("lists", JSONArray(_lists.value.map { it.toJson() }))
            put("categories", JSONArray(_categories.value.map { it.toJson() }))
            put("locations", JSONArray(_locations.value))
        }.toString(1)
    }

    /** 导入数据（合并：不覆盖现有记录，冲突项重新分配 id） */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        // 清单：旧备份无 lists 时全部归入当前清单；有则按 id 映射新建
        val listIdMap = HashMap<Long, Long>()
        val itemIdMap = HashMap<Long, Long>()
        val listsToInsert = ArrayList<ListEntity>()
        val itemsToInsert = ArrayList<ItemEntity>()
        val wishesToInsert = ArrayList<WishEntity>()
        allocateIds {
            val larr = root.optJSONArray("lists")
            if (larr != null && larr.length() > 0) {
                for (i in 0 until larr.length()) {
                    val l = listFromJson(larr.getJSONObject(i))
                    val newId = nextListIdLocked()
                    listsToInsert.add(
                        l.copy(
                            id = newId,
                            rev = 1,
                            updatedBy = syncDeviceId,
                            syncedRev = 0,
                            deleted = false,
                            deletedAt = null
                        ).toEntity()
                    )
                    listIdMap[l.id] = newId
                }
            }
            val arr = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = itemFromJson(arr.getJSONObject(i))
                val targetList = listIdMap[item.listId] ?: _currentListId.value
                val newId = nextItemIdLocked()
                itemIdMap[item.id] = newId
                itemsToInsert.add(
                    item.copy(
                        id = newId,
                        listId = targetList,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        rev = 1,
                        updatedBy = syncDeviceId,
                        syncedRev = 0,
                        deleted = false,
                        deletedAt = null
                    ).toEntity()
                )
            }
            val warr = root.optJSONArray("wishes") ?: JSONArray()
            for (i in 0 until warr.length()) {
                val wish = wishFromJson(warr.getJSONObject(i))
                val targetList = listIdMap[wish.listId] ?: _currentListId.value
                wishesToInsert.add(
                    wish.copy(
                        id = nextWishIdLocked(),
                        listId = targetList,
                        // 备份中的心愿可能引用旧 item id，必须映射到本次新分配的 item id
                        purchasedItemId = wish.purchasedItemId?.let { itemIdMap[it] },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        rev = 1,
                        updatedBy = syncDeviceId,
                        syncedRev = 0,
                        deleted = false,
                        deletedAt = null
                    ).toEntity()
                )
            }
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
        val locationsToInsert = ArrayList<LocationEntity>()
        val existingLocations = _locations.value.toMutableSet()
        val larr2 = root.optJSONArray("locations")
        if (larr2 != null) {
            for (i in 0 until larr2.length()) {
                val name = larr2.getString(i).trim()
                if (name.isNotBlank() && name !in existingLocations) {
                    locationsToInsert.add(LocationEntity(name))
                    existingLocations.add(name)
                }
            }
        }
        // 兜底：备份中物品引用了未知地点时也自动补建，保证「我的-存放地点管理」可见
        itemsToInsert.forEach { item ->
            item.location?.takeIf { it.isNotBlank() && it !in existingLocations }?.let {
                locationsToInsert.add(LocationEntity(it))
                existingLocations.add(it)
            }
        }
        val imported = itemsToInsert.size + wishesToInsert.size
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                if (listsToInsert.isNotEmpty()) listsToInsert.forEach { d.listDao().upsert(it) }
                if (itemsToInsert.isNotEmpty()) d.itemDao().upsertAll(itemsToInsert)
                if (wishesToInsert.isNotEmpty()) d.wishDao().upsertAll(wishesToInsert)
                if (categoriesToInsert.isNotEmpty()) d.categoryDao().upsertAll(categoriesToInsert)
                if (locationsToInsert.isNotEmpty()) d.locationDao().upsertAll(locationsToInsert)
            }
        }
        return imported
    }

    /** CSV 表头（中文，与导入模板一致） */
    val csvHeaders = listOf("名称", "分类", "存放地点", "价格", "购入日期", "备注", "状态", "退役日期", "照片文件名", "生产日期", "到期日期", "保质期（天）")

    /** 导出物品清单为 CSV（纯文字；照片列记录文件名，照片本体随 ZIP 导出） */
    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.append(csvHeaders.joinToString(",") { csvEscape(it) }).append("\n")
        itemsOf(_currentListId.value).forEach { item ->
            sb.append(
                listOf(
                    item.name,
                    item.category,
                    item.location ?: "",
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
                "Sony WH-1000XM5 无线降噪耳机", "数码", "家", "2899.00", "2025-06-15",
                "通勤降噪耳机", "使用中", "", "", "2025-05-20", "", "1095"
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "Kindle Paperwhite", "阅读", "", "998.00", "2024-03-01",
                "护眼阅读器", "已退役", "2025-01-20", "", "2023-12-01", "2025-12-01", ""
            ).joinToString(",") { csvEscape(it) }
        ).append("\n")
        append(
            listOf(
                "客制化机械键盘", "数码", "公司", "899.00", "2025-12-01",
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
        val locationsToInsert = ArrayList<LocationEntity>()
        val existingLocations = _locations.value.toMutableSet()
        allocateIds {
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
                val locationName = col("存放地点").ifBlank { null }
                if (locationName != null && locationName !in existingLocations) {
                    locationsToInsert.add(LocationEntity(locationName))
                    existingLocations.add(locationName)
                }
                val item = Item(
                    id = nextItemIdLocked(),
                    name = name,
                    category = categoryName,
                    location = locationName,
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
                    listId = _currentListId.value,
                    rev = 1,
                    updatedBy = syncDeviceId
                )
                itemsToInsert.add(item.toEntity())
            }
        }
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                if (categoriesToInsert.isNotEmpty()) d.categoryDao().upsertAll(categoriesToInsert)
                if (locationsToInsert.isNotEmpty()) d.locationDao().upsertAll(locationsToInsert)
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

    // ---------- WebDAV 同步支持 ----------

    /** 读取包含墓碑的完整数据（同步快照用） */
    suspend fun loadSyncSnapshot(): SyncData {
        val d = db ?: return SyncData(emptyList(), emptyList(), emptyList(), emptyList())
        return SyncData(
            lists = d.listDao().observeAll().first().map { it.toModel() },
            items = d.itemDao().observeAll().first().map { it.toModel() },
            wishes = d.wishDao().observeAll().first().map { it.toModel() },
            categories = d.categoryDao().observeAll().first().map { it.toModel() }
        )
    }

    /** 分配同步用新 ID（物品冲突副本等）。各表使用各自序列，避免跨表主键碰撞。 */
    fun allocSyncId(): Long = nextItemId()

    fun allocSyncWishId(): Long = nextWishId()

    fun allocSyncListId(): Long = nextListId()

    /**
     * 把合并结果写回本地库（REPLACE），UI 流自动刷新。
     *
     * expected 是本次同步下载/合并前读取的本地快照。写回前逐条用 DB 当前行与 expected 的
     * rev/updatedBy/deleted 比对：若同步期间用户又编辑/新增/彻底删除过，立即抛错中止写回，
     * 避免旧快照覆盖新编辑（远端已写成功，下一次同步会把本地新修改合并上传）。
     */
    suspend fun applySyncData(data: SyncData, expected: SyncData? = null) {
        val d = db ?: return
        d.withTransaction {
            if (expected != null) {
                val expectedLists = expected.lists.associateBy { it.id }
                val expectedItems = expected.items.associateBy { it.id }
                val expectedWishes = expected.wishes.associateBy { it.id }

                data.lists.forEach { merged ->
                    val e = expectedLists[merged.id] ?: return@forEach
                    val cur = d.listDao().findById(merged.id)
                    if (cur == null || cur.rev != e.rev || cur.updatedBy != e.updatedBy || cur.deleted != e.deleted) {
                        throw IllegalStateException("同步期间本地清单有修改，已中止写回；请重试")
                    }
                }
                data.items.forEach { merged ->
                    val e = expectedItems[merged.id] ?: return@forEach
                    val cur = d.itemDao().findById(merged.id)
                    if (cur == null || cur.rev != e.rev || cur.updatedBy != e.updatedBy || cur.deleted != e.deleted) {
                        throw IllegalStateException("同步期间本地物品有修改，已中止写回；请重试")
                    }
                }
                data.wishes.forEach { merged ->
                    val e = expectedWishes[merged.id] ?: return@forEach
                    val cur = d.wishDao().findById(merged.id)
                    if (cur == null || cur.rev != e.rev || cur.updatedBy != e.updatedBy || cur.deleted != e.deleted) {
                        throw IllegalStateException("同步期间本地心愿有修改，已中止写回；请重试")
                    }
                }
            }
            // 写回时把 syncedRev 推进到合并后的 rev，避免下次同步误判为「本地修改」
            if (data.lists.isNotEmpty()) d.listDao().upsertAll(data.lists.map { it.copy(syncedRev = it.rev).toEntity() })
            if (data.items.isNotEmpty()) d.itemDao().upsertAll(data.items.map { it.copy(syncedRev = it.rev).toEntity() })
            if (data.wishes.isNotEmpty()) d.wishDao().upsertAll(data.wishes.map { it.copy(syncedRev = it.rev).toEntity() })
            if (data.categories.isNotEmpty()) d.categoryDao().upsertAll(data.categories.map { it.toEntity() })
        }
        // 事务提交后再校正当前清单：远端可能删除了当前清单，或首次同步才采纳远端清单。
        // _lists 的 Flow 可能尚未刷新，因此直接查库拿未删除清单。
        val visibleLists = d.listDao().visibleLists()
        if (visibleLists.none { it.id == _currentListId.value }) {
            val nextId = visibleLists.firstOrNull()?.id ?: -1L
            _currentListId.value = nextId
            prefs?.edit()?.putLong("current_list_id", nextId)?.commit()
        }
    }

    /** 批量生成测试数据（仅调试用）：在当前清单创建物品 / 心愿，用于同步演练 */
    fun generateTestData(itemCount: Int, wishCount: Int) {
        val listId = _currentListId.value
        val names = listOf(
            "降噪耳机", "机械键盘", "保温杯", "台灯", "雨伞", "背包", "充电宝", "鼠标", "显示器",
            "笔记本支架", "咖啡壶", "香薰机", "路由器", "摄像头", "音箱", "游戏手柄", "瑜伽垫",
            "跑鞋", "卫衣", "围巾"
        )
        val cats = listOf("数码", "服饰", "阅读", "家居", "收藏", "影像", "其他")
        val locs = listOf("家", "车", "公司", null)
        val now = System.currentTimeMillis()
        val (newItems, newWishes) = allocateIds {
            val items = (0 until itemCount).map { i ->
                Item(
                    id = nextItemIdLocked(),
                    name = "${names[i % names.size]} #${i + 1}",
                    category = cats[i % cats.size],
                    price = (50 + (i * 37) % 3000) / 10.0,
                    purchaseDate = LocalDate.now().minusDays((i % 365).toLong()).toEpochDay(),
                    location = locs[i % locs.size],
                    note = "测试数据",
                    listId = listId,
                    rev = 1,
                    updatedBy = syncDeviceId,
                    createdAt = now,
                    updatedAt = now
                )
            }
            val wishes = (0 until wishCount).map { i ->
                Wish(
                    id = nextWishIdLocked(),
                    name = "心愿 ${names[i % names.size]} #${i + 1}",
                    category = cats[i % cats.size],
                    price = (200 + (i * 53) % 5000) / 10.0,
                    priority = listOf("HIGH", "MEDIUM", "DREAM")[i % 3],
                    note = "测试数据",
                    listId = listId,
                    rev = 1,
                    updatedBy = syncDeviceId,
                    createdAt = now,
                    updatedAt = now
                )
            }
            items to wishes
        }
        scope.launch {
            val d = db ?: return@launch
            d.withTransaction {
                if (newItems.isNotEmpty()) d.itemDao().upsertAll(newItems.map { it.toEntity() })
                if (newWishes.isNotEmpty()) d.wishDao().upsertAll(newWishes.map { it.toEntity() })
            }
        }
    }
}

/** 全局 ID 低 40 位序列掩码 */
internal const val SYNC_SEQ_MASK = 0xFFFFFFFFFFL

/** 取全局 ID 的低 40 位本地序号 */
internal fun syncSeqOf(globalId: Long): Long = globalId and SYNC_SEQ_MASK

/** 是否为全局 ID（高 23 位设备号非 0；存量本地自增 ID 都小于 2^40） */
internal fun isGlobalSyncId(id: Long): Boolean = (id ushr 40) != 0L

/**
 * 序列校准：取持久化值与 DB 中最大序号的较大者。
 * [maxIdOrSeq] 既可以是全局 ID（会取低 40 位），也可以是已取过低 40 位的 DAO 结果。
 * 用于 prefs/DeviceState 丢失或回退后避免从 1 开始撞已有 ID。
 */
internal fun calibrateSequence(current: Long, maxIdOrSeq: Long?): Long {
    val base = current.coerceAtLeast(0L)
    val fromDb = maxIdOrSeq?.let { syncSeqOf(it) } ?: 0L
    return maxOf(base, fromDb)
}

private fun LocalDate.yearMonth(): YearMonth = YearMonth.of(year, monthValue)
