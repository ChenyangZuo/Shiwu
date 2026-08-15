package com.zzzcy.shiwu.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelsTest {

    @Test
    fun `快照往返：照片映射为文件名，回读映射为本地绝对路径`() {
        val localPhotos = File("/tmp/shiwu_test_photos").apply { mkdirs() }
        val data = SyncData(
            lists = listOf(
                InventoryList(id = 1, name = "我的物品", rev = 1, updatedBy = "A", deleted = false)
            ),
            items = listOf(
                Item(
                    id = 11, name = "耳机", category = "数码", price = 1.0, purchaseDate = 0,
                    photos = listOf("/data/foo/files/photos/abc.jpg"),
                    listId = 1, rev = 2, updatedBy = "A", deleted = false
                )
            ),
            wishes = listOf(
                Wish(id = 21, name = "心愿", category = "数码", price = 1.0, listId = 1, rev = 1, updatedBy = "B", deleted = true)
            ),
            categories = listOf(Category("数码"))
        )

        val json = data.toSnapshotJson()
        val back = snapshotFromJson(json, localPhotos)

        assertEquals(1, back.items.size)
        assertEquals("abc.jpg", File(back.items[0].photos[0]).name)
        assertTrue(back.items[0].photos[0].startsWith(localPhotos.absolutePath))
        assertEquals(2, back.items[0].rev)
        assertEquals(1, back.wishes.size)
        assertTrue(back.wishes[0].deleted)
        assertEquals("我的物品", back.lists[0].name)
        assertEquals(1, back.categories.size)
    }

    @Test
    fun `设备注册表往返`() {
        val json = devicesToJson(
            listOf(Triple("123", "手机", 1000L), Triple("456", "平板", 2000L))
        )
        val back = devicesFromJson(json)
        assertEquals(2, back.size)
        assertEquals("手机", back.first().second)
    }
}
