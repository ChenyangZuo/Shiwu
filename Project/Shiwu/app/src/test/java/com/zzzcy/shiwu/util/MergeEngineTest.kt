package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.Category
import com.zzzcy.shiwu.data.InventoryList
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.SyncData
import com.zzzcy.shiwu.data.Wish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        listId: Long = 1L
    ) = Item(
        id = id, name = name, category = "数码", price = 1.0, purchaseDate = 0,
        listId = listId, rev = rev, updatedBy = by, deleted = deleted
    )

    private fun wish(
        id: Long,
        name: String,
        rev: Long,
        by: String,
        deleted: Boolean = false
    ) = Wish(
        id = id, name = name, category = "数码", price = 1.0,
        listId = 1L, rev = rev, updatedBy = by, deleted = deleted
    )

    private fun list(
        id: Long,
        name: String,
        rev: Long,
        by: String,
        deleted: Boolean = false
    ) = InventoryList(
        id = id, name = name, rev = rev, updatedBy = by, deleted = deleted
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
}
