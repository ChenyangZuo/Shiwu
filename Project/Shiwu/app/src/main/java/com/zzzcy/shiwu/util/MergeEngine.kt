package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.Category
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.SyncData
import com.zzzcy.shiwu.data.SyncReport
import com.zzzcy.shiwu.data.Wish

/**
 * 合并引擎（纯 Kotlin，不依赖 Android）：
 * - 仅一端有 → 采纳；
 * - 双方一致 → 原样保留；
 * - 本地未修改（rev <= syncedRev）→ 采纳远端（更新 / 删除），不产生冲突；
 * - 远端未高于上次同步版本 → 本地修改胜，不产生冲突；
 * - 双方都在上次同步后修改 → LWW（rev 大者胜，同 rev 比 deviceId），输方存为冲突副本；
 * - 清单被删除但物品 / 心愿仍活跃 → 归入默认清单并记录；
 * - 全新设备（本机只有一份空的自动默认清单）→ 采纳远端清单，本机空清单转墓碑；
 * - 多端各自生成的同名默认清单 → 保留内容最多的一份，其余转墓碑并把内容并入；
 * - 所有清单都被删除但仍有活跃记录 → 自动重建默认清单。
 */
object MergeEngine {

    private const val DEFAULT_LIST_NAME = "我的物品"

    data class MergeResult(
        val lists: List<InventoryList>,
        val items: List<Item>,
        val wishes: List<Wish>,
        val categories: List<Category>,
        val report: SyncReport
    )

    fun merge(
        local: SyncData,
        remote: SyncData,
        defaultListId: Long,
        newId: () -> Long,
        deviceId: String,
        // 冲突副本的 ID 必须来自对应表的序列，避免 items/lists/wishes 跨表序号复用造成主键碰撞。
        newWishId: () -> Long = newId,
        newListId: () -> Long = newId
    ): MergeResult {
        val listNotes = mutableListOf<String>()
        val itemNotes = mutableListOf<String>()
        val wishNotes = mutableListOf<String>()

        val listBucket = mergeAll(
            local.lists, remote.lists, newListId,
            { it.id }, { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
            ::listConflictCopy,
            { "清单「${it.name}」的修改已保存为冲突副本" },
            listNotes
        )
        val itemBucket = mergeAll(
            local.items, remote.items, newId,
            { it.id }, { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
            ::itemConflictCopy,
            { "「${it.name}」的修改已保存为冲突副本" },
            itemNotes
        )
        val wishBucket = mergeAll(
            local.wishes, remote.wishes, newWishId,
            { it.id }, { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
            ::wishConflictCopy,
            { "心愿「${it.name}」的修改已保存为冲突副本" },
            wishNotes
        )

        val lists = listBucket.records
        val items = itemBucket.records
        val wishes = wishBucket.records

        var added = listBucket.added + itemBucket.added + wishBucket.added
        var updated = listBucket.updated + itemBucket.updated + wishBucket.updated
        val conflicts = listBucket.conflicts + itemBucket.conflicts + wishBucket.conflicts

        val now = System.currentTimeMillis()

        // B1.1 全新设备：本机只有一份空的自动默认清单，远端已有清单 → 直接采纳远端
        val localActiveLists = local.lists.filter { !it.deleted }
        if (localActiveLists.size == 1 &&
            local.items.isEmpty() &&
            local.wishes.isEmpty() &&
            remote.lists.any { !it.deleted } &&
            remote.lists.none { it.id == localActiveLists[0].id }
        ) {
            val localList = localActiveLists[0]
            lists[localList.id] = tombstone(lists[localList.id] ?: localList, now, deviceId)
            listNotes += "首次同步：已采用远端清单，本机空默认清单已移除"
        }

        // B1.2 多端各自生成的同名默认清单去重
        updated += dedupeDefaultLists(lists, items, wishes, now, deviceId, listNotes)

        // B4 所有清单都被删除但仍有活跃记录 → 自动重建默认清单
        // （lists 为空表示快照里没有清单数据，不属于“清单全删”，保持原样）
        var activeListIds = lists.values.filter { !it.deleted }.map { it.id }.toSet()
        val hasActiveRecords = items.values.any { !it.deleted } || wishes.values.any { !it.deleted }
        if (lists.isNotEmpty() && activeListIds.isEmpty() && hasActiveRecords) {
            val created = InventoryList(
                id = newListId(),
                name = DEFAULT_LIST_NAME,
                createdAt = now,
                updatedAt = now,
                rev = 1,
                updatedBy = deviceId
            )
            lists[created.id] = created
            activeListIds = setOf(created.id)
            added++
            listNotes += "所有清单均已删除，已自动创建默认清单「$DEFAULT_LIST_NAME」"
        }

        val fallbackId = when {
            activeListIds.isEmpty() -> null
            defaultListId in activeListIds -> defaultListId
            else -> activeListIds.first()
        }

        // 孤儿物品归位（含 B4 新建清单的情况）
        if (fallbackId != null) {
            items.keys.toList().forEach { id ->
                val item = items.getValue(id)
                if (!item.deleted && item.listId !in activeListIds) {
                    items[id] = item.copy(listId = fallbackId, rev = item.rev + 1, updatedBy = deviceId)
                    updated++
                    itemNotes += "「${item.name}」所在清单已删除，已归入默认清单"
                }
            }
            // B3 孤儿心愿同样归位
            wishes.keys.toList().forEach { id ->
                val wish = wishes.getValue(id)
                if (!wish.deleted && wish.listId !in activeListIds) {
                    wishes[id] = wish.copy(listId = fallbackId, rev = wish.rev + 1, updatedBy = deviceId)
                    updated++
                    wishNotes += "心愿「${wish.name}」所在清单已删除，已归入默认清单"
                }
            }
        }

        // B2 删除计数：只统计“本次新出现”的删除（本地原本没删的墓碑）
        val localListById = local.lists.associateBy { it.id }
        val localItemById = local.items.associateBy { it.id }
        val localWishById = local.wishes.associateBy { it.id }
        val deleted =
            lists.values.count { it.deleted && localListById[it.id]?.deleted != true } +
                items.values.count { it.deleted && localItemById[it.id]?.deleted != true } +
                wishes.values.count { it.deleted && localWishById[it.id]?.deleted != true }

        // 分类：按名称并集（v1 不追源重命名）
        val mergedCategories = LinkedHashMap<String, Category>()
        (local.categories + remote.categories).forEach { mergedCategories.putIfAbsent(it.name, it) }

        val report = SyncReport(
            added = added,
            updated = updated,
            deleted = deleted,
            conflicts = conflicts,
            notes = listNotes + itemNotes + wishNotes
        )
        return MergeResult(
            lists = lists.values.toList(),
            items = items.values.toList(),
            wishes = wishes.values.toList(),
            categories = mergedCategories.values.toList(),
            report = report
        )
    }

    // ---------- 同名默认清单去重 ----------

    /**
     * 合并后若存在多份未删除且名称恰为“我的物品”的清单：
     * 保留 canonical（有效物品+心愿最多 → createdAt 最早 → id 最小），
     * 其余清单中的活跃记录改挂到 canonical（rev+1），清单本身转墓碑。
     * @return 因改挂而更新的记录数
     */
    private fun dedupeDefaultLists(
        lists: LinkedHashMap<Long, InventoryList>,
        items: LinkedHashMap<Long, Item>,
        wishes: LinkedHashMap<Long, Wish>,
        now: Long,
        deviceId: String,
        notes: MutableList<String>
    ): Int {
        val defaults = lists.values.filter { !it.deleted && it.name == DEFAULT_LIST_NAME }
        if (defaults.size < 2) return 0

        val counts = HashMap<Long, Int>()
        items.values.forEach { if (!it.deleted) counts[it.listId] = (counts[it.listId] ?: 0) + 1 }
        wishes.values.forEach { if (!it.deleted) counts[it.listId] = (counts[it.listId] ?: 0) + 1 }

        val canonical = defaults.sortedWith(
            compareByDescending<InventoryList> { counts[it.id] ?: 0 }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        ).first()

        var moved = 0
        defaults.forEach { duplicate ->
            if (duplicate.id == canonical.id) return@forEach
            items.keys.toList().forEach { id ->
                val item = items.getValue(id)
                if (!item.deleted && item.listId == duplicate.id) {
                    items[id] = item.copy(listId = canonical.id, rev = item.rev + 1, updatedBy = deviceId)
                    moved++
                }
            }
            wishes.keys.toList().forEach { id ->
                val wish = wishes.getValue(id)
                if (!wish.deleted && wish.listId == duplicate.id) {
                    wishes[id] = wish.copy(listId = canonical.id, rev = wish.rev + 1, updatedBy = deviceId)
                    moved++
                }
            }
            lists[duplicate.id] = tombstone(duplicate, now, deviceId)
            notes += "默认清单「$DEFAULT_LIST_NAME」重复，已合并为一份"
        }
        return moved
    }

    private fun tombstone(list: InventoryList, now: Long, deviceId: String): InventoryList =
        list.copy(
            deleted = true,
            deletedAt = now,
            updatedAt = now,
            rev = list.rev + 1,
            updatedBy = deviceId
        )

    // ---------- 通用合并 ----------

    private class MergeOutcome<T>(val record: T, val conflictCopy: T? = null)

    private class MergeBucket<T>(
        val records: LinkedHashMap<Long, T>,
        val added: Int,
        val updated: Int,
        val conflicts: Int
    )

    private fun <T> mergeAll(
        local: List<T>,
        remote: List<T>,
        newId: () -> Long,
        idOf: (T) -> Long,
        revOf: (T) -> Long,
        byOf: (T) -> String,
        syncedRevOf: (T) -> Long,
        deletedOf: (T) -> Boolean,
        makeConflictCopy: (loser: T, newId: Long, winnerRev: Long, winnerBy: String) -> T,
        conflictNote: (T) -> String,
        notes: MutableList<String>
    ): MergeBucket<T> {
        val localById = local.associateBy(idOf)
        val remoteById = remote.associateBy(idOf)
        val records = LinkedHashMap<Long, T>()
        var added = 0
        var updated = 0
        var conflicts = 0
        (localById.keys + remoteById.keys).forEach { id ->
            val l = localById[id]
            val r = remoteById[id]
            when {
                l == null -> {
                    records[id] = r!!
                    added++
                }
                r == null -> {
                    records[id] = l
                    added++
                }
                else -> {
                    val outcome = mergeRecord(
                        l, r, revOf, byOf, syncedRevOf, deletedOf
                    ) { loser, winner ->
                        makeConflictCopy(loser, newId(), revOf(winner), byOf(winner))
                    }
                    if (outcome.record !== l) updated++
                    records[id] = outcome.record
                    outcome.conflictCopy?.let { copy ->
                        records[idOf(copy)] = copy
                        conflicts++
                        notes += conflictNote(copy)
                    }
                }
            }
        }
        return MergeBucket(records, added, updated, conflicts)
    }

    private fun <T> mergeRecord(
        local: T,
        remote: T,
        revOf: (T) -> Long,
        byOf: (T) -> String,
        syncedRevOf: (T) -> Long,
        deletedOf: (T) -> Boolean,
        conflictCopy: (loser: T, winner: T) -> T?
    ): MergeOutcome<T> {
        if (revOf(local) == revOf(remote) && byOf(local) == byOf(remote)) {
            return MergeOutcome(local)
        }
        // 本地自上次同步后未修改 → 采纳远端
        if (revOf(local) <= syncedRevOf(local)) {
            return MergeOutcome(remote)
        }
        // 远端版本不高于我们上次同步到的版本 → 远端未变，本地修改胜
        if (revOf(remote) <= syncedRevOf(local)) {
            return MergeOutcome(local)
        }
        // 双方都在上次同步后修改 → 真冲突，LWW
        val (winner, loser) = lww(local, remote, revOf, byOf)
        val copy = when {
            deletedOf(winner) && !deletedOf(loser) -> conflictCopy(loser, winner)
            !deletedOf(winner) && deletedOf(loser) -> null
            !deletedOf(winner) -> conflictCopy(loser, winner)
            else -> null
        }
        return MergeOutcome(winner, copy)
    }

    private fun <T> lww(a: T, b: T, revOf: (T) -> Long, byOf: (T) -> String): Pair<T, T> {
        val cmp = compareValuesBy(a, b, revOf, byOf)
        return if (cmp >= 0) a to b else b to a
    }

    // ---------- 各类记录的冲突副本 ----------

    private fun listConflictCopy(original: InventoryList, newId: Long, winnerRev: Long, winnerBy: String): InventoryList =
        original.copy(
            id = newId,
            name = if (original.name.contains("（冲突副本）")) original.name else "${original.name}（冲突副本）",
            rev = winnerRev + 1,
            updatedBy = winnerBy,
            syncedRev = winnerRev,
            deleted = false,
            deletedAt = null
        )

    private fun itemConflictCopy(original: Item, newId: Long, winnerRev: Long, winnerBy: String): Item =
        original.copy(
            id = newId,
            name = if (original.name.contains("（冲突副本）")) original.name else "${original.name}（冲突副本）",
            rev = winnerRev + 1,
            updatedBy = winnerBy,
            syncedRev = winnerRev,
            deleted = false,
            deletedAt = null
        )

    private fun wishConflictCopy(original: Wish, newId: Long, winnerRev: Long, winnerBy: String): Wish =
        original.copy(
            id = newId,
            name = if (original.name.contains("（冲突副本）")) original.name else "${original.name}（冲突副本）",
            rev = winnerRev + 1,
            updatedBy = winnerBy,
            syncedRev = winnerRev,
            deleted = false,
            deletedAt = null
        )
}
