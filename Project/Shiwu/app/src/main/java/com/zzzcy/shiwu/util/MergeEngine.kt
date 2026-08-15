package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.Category
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.SyncData
import com.zzzcy.shiwu.data.SyncReport
import com.zzzcy.shiwu.data.Wish

/**
 * 合并引擎（纯 Kotlin，不依赖 Android）：本地 ∪ 远端，逐记录按 rev / updatedBy LWW，
 * 输方存为冲突副本；清单删除后仍活跃的物品归入默认清单。
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
        val listStats = IntArray(3) // added / updated / deleted
        val itemStats = IntArray(3)
        val wishStats = IntArray(3)

        val lists = mergeLists(local.lists, remote.lists, newId, deviceId, listStats, listNotes)
        val items = mergeItems(local.items, remote.items, newId, deviceId, itemStats, itemNotes)
        val wishes = mergeWishes(local.wishes, remote.wishes, newId, deviceId, wishStats, wishNotes)

        // 孤儿物品归位：物品活跃但所在清单已在合并结果中被删除 → 归入默认清单
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

    // ---------- 通用 LWW ----------

    /** 返回 (胜者, 败者)；完全相同返回 (record, null) */
    private fun <T> lww(a: T, b: T, revOf: (T) -> Long, byOf: (T) -> String): Pair<T, T?> {
        if (revOf(a) == revOf(b) && byOf(a) == byOf(b)) return a to null
        val cmp = compareValuesBy(a, b, revOf, byOf)
        return if (cmp >= 0) a to b else b to a
    }

    // ---------- 清单 ----------

    private fun mergeLists(
        local: List<InventoryList>,
        remote: List<InventoryList>,
        newId: () -> Long,
        deviceId: String,
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
                    val (winner, loser) = lww(l, r, { it.rev }, { it.updatedBy })
                    if (loser == null) {
                        result[id] = winner
                    } else {
                        stats[1]++
                        if (winner.deleted && !loser.deleted) {
                            // 删除胜：输方内容存为冲突副本
                            val copy = listConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "清单「${loser.name}」的修改已保存为冲突副本"
                        } else if (!winner.deleted && loser.deleted) {
                            // 修改胜：删除失效
                            result[id] = winner
                        } else if (!winner.deleted) {
                            val copy = listConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "清单「${loser.name}」的修改已保存为冲突副本"
                        }
                        result[id] = winner
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
            deleted = false,
            deletedAt = null
        )

    // ---------- 物品 ----------

    private fun mergeItems(
        local: List<Item>,
        remote: List<Item>,
        newId: () -> Long,
        deviceId: String,
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
                    val (winner, loser) = lww(l, r, { it.rev }, { it.updatedBy })
                    if (loser == null) {
                        result[id] = winner
                    } else {
                        stats[1]++
                        if (winner.deleted && !loser.deleted) {
                            val copy = itemConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "「${loser.name}」的修改已保存为冲突副本"
                        } else if (!winner.deleted && loser.deleted) {
                            // 修改胜：删除失效
                        } else if (!winner.deleted) {
                            val copy = itemConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "「${loser.name}」的修改已保存为冲突副本"
                        }
                        result[id] = winner
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
            deleted = false,
            deletedAt = null
        )

    // ---------- 心愿 ----------

    private fun mergeWishes(
        local: List<Wish>,
        remote: List<Wish>,
        newId: () -> Long,
        deviceId: String,
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
                    val (winner, loser) = lww(l, r, { it.rev }, { it.updatedBy })
                    if (loser == null) {
                        result[id] = winner
                    } else {
                        stats[1]++
                        if (winner.deleted && !loser.deleted) {
                            val copy = wishConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "心愿「${loser.name}」的修改已保存为冲突副本"
                        } else if (!winner.deleted && loser.deleted) {
                            // 修改胜：删除失效
                        } else if (!winner.deleted) {
                            val copy = wishConflictCopy(loser, newId(), winner.rev, winner.updatedBy)
                            result[copy.id] = copy
                            notes += "心愿「${loser.name}」的修改已保存为冲突副本"
                        }
                        result[id] = winner
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
            deleted = false,
            deletedAt = null
        )
}
