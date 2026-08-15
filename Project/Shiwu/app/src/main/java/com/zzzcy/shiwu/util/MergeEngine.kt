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
 * - 清单被删除但物品仍活跃 → 归入默认清单并记录。
 */
object MergeEngine {

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
        deviceId: String
    ): MergeResult {
        val listNotes = mutableListOf<String>()
        val itemNotes = mutableListOf<String>()
        val wishNotes = mutableListOf<String>()
        val listStats = IntArray(3)
        val itemStats = IntArray(3)
        val wishStats = IntArray(3)

        val lists = mergeLists(local.lists, remote.lists, newId, listStats, listNotes)
        val items = mergeItems(local.items, remote.items, newId, itemStats, itemNotes)
        val wishes = mergeWishes(local.wishes, remote.wishes, newId, wishStats, wishNotes)

        // 孤儿物品归位
        val activeListIds = lists.filter { !it.deleted }.map { it.id }.toSet()
        val fallbackId = if (defaultListId in activeListIds) defaultListId else activeListIds.firstOrNull()
        val finalItems = if (fallbackId == null) {
            items
        } else {
            items.map { item ->
                if (!item.deleted && item.listId !in activeListIds) {
                    itemNotes += "「${item.name}」所在清单已删除，已归入默认清单"
                    item.copy(listId = fallbackId, rev = item.rev + 1, updatedBy = deviceId)
                } else {
                    item
                }
            }
        }

        // 分类：按名称并集（v1 不追源重命名）
        val mergedCategories = LinkedHashMap<String, Category>()
        (local.categories + remote.categories).forEach { mergedCategories.putIfAbsent(it.name, it) }

        val report = SyncReport(
            added = listStats[0] + itemStats[0] + wishStats[0],
            updated = listStats[1] + itemStats[1] + wishStats[1],
            deleted = listStats[2] + itemStats[2] + wishStats[2],
            conflicts = listNotes.size + itemNotes.size + wishNotes.size,
            notes = listNotes + itemNotes + wishNotes
        )
        return MergeResult(lists, finalItems, wishes, mergedCategories.values.toList(), report)
    }

    // ---------- 通用合并 ----------

    private class MergeOutcome<T>(val record: T, val conflictCopy: T? = null)

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

    // ---------- 清单 ----------

    private fun mergeLists(
        local: List<InventoryList>,
        remote: List<InventoryList>,
        newId: () -> Long,
        stats: IntArray,
        notes: MutableList<String>
    ): List<InventoryList> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val result = LinkedHashMap<Long, InventoryList>()
        (localMap.keys + remoteMap.keys).forEach { id ->
            val l = localMap[id]
            val r = remoteMap[id]
            when {
                l == null -> {
                    result[id] = r!!
                    stats[0]++
                }
                r == null -> {
                    result[id] = l
                    stats[0]++
                }
                else -> {
                    val outcome = mergeRecord(
                        l, r,
                        { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
                        { loser, winner -> listConflictCopy(loser, newId(), winner.rev, winner.updatedBy) }
                    )
                    if (outcome.record !== l) stats[1]++
                    result[id] = outcome.record
                    outcome.conflictCopy?.let { copy ->
                        result[copy.id] = copy
                        notes += "清单「${copy.name}」的修改已保存为冲突副本"
                    }
                }
            }
        }
        stats[2] = result.values.count { it.deleted }
        return result.values.toList()
    }

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

    // ---------- 物品 ----------

    private fun mergeItems(
        local: List<Item>,
        remote: List<Item>,
        newId: () -> Long,
        stats: IntArray,
        notes: MutableList<String>
    ): List<Item> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val result = LinkedHashMap<Long, Item>()
        (localMap.keys + remoteMap.keys).forEach { id ->
            val l = localMap[id]
            val r = remoteMap[id]
            when {
                l == null -> {
                    result[id] = r!!
                    stats[0]++
                }
                r == null -> {
                    result[id] = l
                    stats[0]++
                }
                else -> {
                    val outcome = mergeRecord(
                        l, r,
                        { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
                        { loser, winner -> itemConflictCopy(loser, newId(), winner.rev, winner.updatedBy) }
                    )
                    if (outcome.record !== l) stats[1]++
                    result[id] = outcome.record
                    outcome.conflictCopy?.let { copy ->
                        result[copy.id] = copy
                        notes += "「${copy.name}」的修改已保存为冲突副本"
                    }
                }
            }
        }
        stats[2] = result.values.count { it.deleted }
        return result.values.toList()
    }

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

    // ---------- 心愿 ----------

    private fun mergeWishes(
        local: List<Wish>,
        remote: List<Wish>,
        newId: () -> Long,
        stats: IntArray,
        notes: MutableList<String>
    ): List<Wish> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val result = LinkedHashMap<Long, Wish>()
        (localMap.keys + remoteMap.keys).forEach { id ->
            val l = localMap[id]
            val r = remoteMap[id]
            when {
                l == null -> {
                    result[id] = r!!
                    stats[0]++
                }
                r == null -> {
                    result[id] = l
                    stats[0]++
                }
                else -> {
                    val outcome = mergeRecord(
                        l, r,
                        { it.rev }, { it.updatedBy }, { it.syncedRev }, { it.deleted },
                        { loser, winner -> wishConflictCopy(loser, newId(), winner.rev, winner.updatedBy) }
                    )
                    if (outcome.record !== l) stats[1]++
                    result[id] = outcome.record
                    outcome.conflictCopy?.let { copy ->
                        result[copy.id] = copy
                        notes += "心愿「${copy.name}」的修改已保存为冲突副本"
                    }
                }
            }
        }
        stats[2] = result.values.count { it.deleted }
        return result.values.toList()
    }

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
