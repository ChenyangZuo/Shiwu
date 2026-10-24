package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.Category
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.SyncData
import com.zzzcy.shiwu.data.Wish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeEngineTest {

    private var nextId = 900L
    private fun alloc(): Long = nextId++

    private fun item(
        id: Long,
        name: String,
        rev: Long,
        by: String,
        deleted: Boolean = false,
        listId: Long = 1L,
        syncedRev: Long = 0
    ) = Item(
        id = id, name = name, category = "数码", price = 1.0, purchaseDate = 0,
        listId = listId, rev = rev, updatedBy = by, syncedRev = syncedRev, deleted = deleted
    )

    private fun wish(
        id: Long,
        name: String,
        rev: Long,
        by: String,
        deleted: Boolean = false,
        listId: Long = 1L,
        syncedRev: Long = 0
    ) = Wish(
        id = id, name = name, category = "数码", price = 1.0,
        listId = listId, rev = rev, updatedBy = by, syncedRev = syncedRev, deleted = deleted
    )

    private fun list(
        id: Long,
        name: String,
        rev: Long,
        by: String,
        deleted: Boolean = false,
        syncedRev: Long = 0
    ) = InventoryList(
        id = id, name = name, rev = rev, updatedBy = by, syncedRev = syncedRev, deleted = deleted
    )

    private fun merge(
        local: SyncData,
        remote: SyncData,
        defaultListId: Long = 1L
    ) = MergeEngine.merge(local, remote, defaultListId, ::alloc, "DEVICE_TEST")

    @Test
    fun `两端各自新增 → 并集`() {
        val local = SyncData(
            lists = listOf(list(1, "我的物品", 1, "A")),
            items = listOf(item(11, "本地物品", 1, "A")),
            wishes = emptyList(),
            categories = listOf(Category("数码"))
        )
        val remote = SyncData(
            lists = listOf(list(1, "我的物品", 1, "A")),
            items = listOf(item(12, "远端物品", 1, "B")),
            wishes = listOf(wish(21, "远端心愿", 1, "B")),
            categories = listOf(Category("阅读"))
        )
        val r = merge(local, remote)
        assertEquals(2, r.items.size)
        assertEquals(1, r.wishes.size)
        assertEquals(2, r.categories.size)
        assertTrue(r.report.added >= 3)
        assertEquals(0, r.report.conflicts)
    }

    @Test
    fun `同一记录两端都改 → rev 大者胜，输方存冲突副本`() {
        val local = SyncData(emptyList(), listOf(item(11, "耳机", 2, "A")), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机-新版", 3, "B")), emptyList(), emptyList())
        val r = merge(local, remote)
        val winner = r.items.first { it.id == 11L }
        assertEquals("耳机-新版", winner.name)
        val copies = r.items.filter { it.name.contains("（冲突副本）") }
        assertEquals(1, copies.size)
        assertEquals("耳机（冲突副本）", copies[0].name)
        assertEquals(1, r.report.conflicts)
    }

    @Test
    fun `一端删除一端修改 → 删除胜时修改存副本；修改胜时删除失效`() {
        // 删除胜（rev 更高）
        val local = SyncData(emptyList(), listOf(item(11, "耳机", 2, "A")), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机", 3, "B", deleted = true)), emptyList(), emptyList())
        val r1 = merge(local, remote)
        assertTrue(r1.items.first { it.id == 11L }.deleted)
        assertEquals(1, r1.items.count { it.name.contains("（冲突副本）") })

        // 修改胜（本地 rev 更高且未删）
        val local2 = SyncData(emptyList(), listOf(item(11, "耳机-本地", 3, "A")), emptyList(), emptyList())
        val remote2 = SyncData(emptyList(), listOf(item(11, "耳机", 2, "B", deleted = true)), emptyList(), emptyList())
        val r2 = merge(local2, remote2)
        val winner2 = r2.items.first { it.id == 11L }
        assertFalse(winner2.deleted)
        assertEquals("耳机-本地", winner2.name)
        assertEquals(0, r2.items.count { it.name.contains("（冲突副本）") })
    }

    @Test
    fun `两端都删除 → 保留一个墓碑，不产生副本`() {
        val local = SyncData(emptyList(), listOf(item(11, "耳机", 2, "A", deleted = true)), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机", 3, "B", deleted = true)), emptyList(), emptyList())
        val r = merge(local, remote)
        assertEquals(1, r.items.size)
        assertTrue(r.items[0].deleted)
        assertEquals(0, r.report.conflicts)
    }

    @Test
    fun `同 rev 同 updatedBy → 视为同一记录`() {
        val local = SyncData(emptyList(), listOf(item(11, "耳机", 2, "A")), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机", 2, "A")), emptyList(), emptyList())
        val r = merge(local, remote)
        assertEquals(1, r.items.size)
        assertEquals(0, r.report.conflicts)
    }

    @Test
    fun `本地未修改 → 采纳远端更新，不产生冲突副本`() {
        // 本地 rev 1 且 syncedRev 1（上次同步过），远端已改到 rev 2
        val local = SyncData(emptyList(), listOf(item(11, "耳机", 1, "A", syncedRev = 1)), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机-远端改", 2, "B")), emptyList(), emptyList())
        val r = merge(local, remote)
        assertEquals("耳机-远端改", r.items.first { it.id == 11L }.name)
        assertEquals(0, r.report.conflicts)
        assertEquals(1, r.report.updated)
    }

    @Test
    fun `本地修改、远端未变 → 本地胜，不产生副本`() {
        val local = SyncData(emptyList(), listOf(item(11, "耳机-本地改", 2, "A", syncedRev = 1)), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机", 1, "A")), emptyList(), emptyList())
        val r = merge(local, remote)
        assertEquals("耳机-本地改", r.items.first { it.id == 11L }.name)
        assertEquals(0, r.report.conflicts)
    }

    @Test
    fun `双方都在上次同步后修改且同 rev → 按 deviceId 决胜并产生副本`() {
        val local = SyncData(emptyList(), listOf(item(11, "耳机-A", 2, "A", syncedRev = 1)), emptyList(), emptyList())
        val remote = SyncData(emptyList(), listOf(item(11, "耳机-B", 2, "B")), emptyList(), emptyList())
        val r = merge(local, remote)
        // deviceId 字典序："A" < "B" → B 胜
        assertEquals("耳机-B", r.items.first { it.id == 11L }.name)
        assertEquals(1, r.report.conflicts)
        assertEquals(1, r.items.count { it.name.contains("（冲突副本）") })
    }

    @Test
    fun `清单被删除但物品活跃 → 归入默认清单并记录`() {
        val local = SyncData(
            lists = listOf(
                list(1, "我的物品", 1, "A"),
                list(2, "旧清单", 1, "A")
            ),
            items = listOf(item(11, "遗留物品", 2, "A", listId = 2)),
            wishes = emptyList(),
            categories = emptyList()
        )
        // 远端把旧清单删了（rev 更高），但物品仍在旧清单下且活跃
        val remote = SyncData(
            lists = listOf(
                list(1, "我的物品", 1, "A"),
                list(2, "旧清单", 3, "B", deleted = true)
            ),
            items = listOf(item(11, "遗留物品", 2, "A", listId = 2)),
            wishes = emptyList(),
            categories = emptyList()
        )
        val r = merge(local, remote)
        val orphan = r.items.first { it.id == 11L }
        assertEquals(1L, orphan.listId)
        assertTrue(r.report.notes.any { it.contains("归入默认清单") })
    }

    @Test
    fun `心愿双改冲突与删除墓碑`() {
        val local = SyncData(emptyList(), emptyList(), listOf(wish(21, "心愿", 2, "A")), emptyList())
        val remote = SyncData(emptyList(), emptyList(), listOf(wish(21, "心愿-改", 3, "B")), emptyList())
        val r = merge(local, remote)
        assertEquals("心愿-改", r.wishes.first { it.id == 21L }.name)
        assertEquals(1, r.wishes.count { it.name.contains("（冲突副本）") })
    }

    // ---------- 首次同步 / 默认清单去重 / 报告语义 / 孤儿兜底 ----------

    @Test
    fun `首次同步：本地仅有空默认清单 → 本地清单转墓碑并采纳远端`() {
        val local = SyncData(
            lists = listOf(list(1, "我的物品", 1, "A")),
            items = emptyList(),
            wishes = emptyList(),
            categories = emptyList()
        )
        val remote = SyncData(
            lists = listOf(list(2, "我的物品", 3, "B")),
            items = listOf(item(21, "远端物品", 1, "B", listId = 2L)),
            wishes = emptyList(),
            categories = emptyList()
        )
        val r = merge(local, remote)

        val localList = r.lists.first { it.id == 1L }
        assertTrue(localList.deleted)
        assertEquals(2L, localList.rev)
        assertEquals("DEVICE_TEST", localList.updatedBy)
        assertNotNull(localList.deletedAt)

        val activeDefaults = r.lists.filter { !it.deleted && it.name == "我的物品" }
        assertEquals(1, activeDefaults.size)
        assertEquals(2L, activeDefaults[0].id)
        assertEquals(2L, r.items.first { it.id == 21L }.listId)
        assertTrue(r.report.notes.any { it.contains("首次同步") })
        assertEquals(1, r.report.deleted)
    }

    @Test
    fun `两台设备各有一个我的物品 → 只保留内容多的一份，空清单转墓碑`() {
        val sixty = (1..60).map { item(100L + it, "物品$it", 1, "A", listId = 1L) }
        val local = SyncData(
            lists = listOf(list(1, "我的物品", 1, "A")),
            items = sixty,
            wishes = emptyList(),
            categories = emptyList()
        )
        val remote = SyncData(
            lists = listOf(list(2, "我的物品", 1, "B")),
            items = emptyList(),
            wishes = emptyList(),
            categories = emptyList()
        )
        val r = merge(local, remote)

        val activeDefaults = r.lists.filter { !it.deleted && it.name == "我的物品" }
        assertEquals(1, activeDefaults.size)
        assertEquals(1L, activeDefaults[0].id)
        assertTrue(r.lists.first { it.id == 2L }.deleted)
        assertEquals(60, r.items.count { !it.deleted && it.listId == 1L })
        assertEquals(1, r.report.deleted)
        assertTrue(r.report.notes.any { it.contains("默认清单") })
    }

    @Test
    fun `两端已同步且无变化 → 报告 added updated deleted conflicts 全为 0`() {
        val local = SyncData(
            lists = listOf(list(1, "我的物品", 3, "A", syncedRev = 3)),
            items = listOf(item(11, "耳机", 3, "A", syncedRev = 3)),
            wishes = listOf(wish(21, "心愿", 3, "A", syncedRev = 3)),
            categories = listOf(Category("数码"))
        )
        val remote = SyncData(
            lists = local.lists.map { it.copy() },
            items = local.items.map { it.copy() },
            wishes = local.wishes.map { it.copy() },
            categories = local.categories.map { it.copy() }
        )
        val r = merge(local, remote)
        assertEquals(0, r.report.added)
        assertEquals(0, r.report.updated)
        assertEquals(0, r.report.deleted)
        assertEquals(0, r.report.conflicts)
        assertTrue(r.report.notes.isEmpty())
    }

    @Test
    fun `心愿所在清单被删除 → 心愿归入默认清单且 rev 加一`() {
        val local = SyncData(
            lists = listOf(
                list(1, "我的物品", 1, "A"),
                list(2, "旧清单", 1, "A")
            ),
            items = emptyList(),
            wishes = listOf(wish(21, "机械键盘", 2, "A", listId = 2L)),
            categories = emptyList()
        )
        val remote = SyncData(
            lists = listOf(
                list(1, "我的物品", 1, "A"),
                list(2, "旧清单", 3, "B", deleted = true)
            ),
            items = emptyList(),
            wishes = listOf(wish(21, "机械键盘", 2, "A", listId = 2L)),
            categories = emptyList()
        )
        val r = merge(local, remote)

        val orphan = r.wishes.first { it.id == 21L }
        assertEquals(1L, orphan.listId)
        assertEquals(3L, orphan.rev)
        assertEquals("DEVICE_TEST", orphan.updatedBy)
        assertTrue(r.report.notes.any { it.contains("心愿「机械键盘」所在清单已删除，已归入默认清单") })
    }

    @Test
    fun `所有清单都被删除 → 自动创建默认清单并收纳活跃记录`() {
        val local = SyncData(
            lists = listOf(list(1, "我的物品", 2, "A", deleted = true)),
            items = listOf(item(11, "耳机", 2, "A", listId = 1L)),
            wishes = listOf(wish(21, "键盘", 2, "A", listId = 1L)),
            categories = emptyList()
        )
        val remote = SyncData(
            lists = listOf(list(1, "我的物品", 2, "A", deleted = true)),
            items = listOf(item(11, "耳机", 2, "A", listId = 1L)),
            wishes = listOf(wish(21, "键盘", 2, "A", listId = 1L)),
            categories = emptyList()
        )
        val r = merge(local, remote)

        val activeLists = r.lists.filter { !it.deleted }
        assertEquals(1, activeLists.size)
        val created = activeLists[0]
        assertEquals("我的物品", created.name)
        assertEquals(1L, created.rev)
        assertEquals("DEVICE_TEST", created.updatedBy)

        val item = r.items.first { it.id == 11L }
        assertEquals(created.id, item.listId)
        assertEquals(3L, item.rev)
        val wish = r.wishes.first { it.id == 21L }
        assertEquals(created.id, wish.listId)
        assertEquals(3L, wish.rev)
        assertTrue(r.report.notes.any { it.contains("自动创建默认清单") })
    }
}
