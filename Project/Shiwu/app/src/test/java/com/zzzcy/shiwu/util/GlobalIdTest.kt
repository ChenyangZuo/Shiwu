package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.globalSyncId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalIdTest {
    @Test
    fun `跨设备 ID 永不碰撞且在 Long 正数范围内`() {
        val deviceA = 123L
        val deviceB = 456L
        val idsA = (1L..2000L).map { globalSyncId(deviceA, it) }
        val idsB = (1L..2000L).map { globalSyncId(deviceB, it) }
        assertEquals(2000, idsA.toSet().size)
        assertEquals(2000, idsB.toSet().size)
        assertTrue(idsA.none { it in idsB })
        assertTrue(idsA.all { it > 0 })
        assertTrue(idsB.all { it > 0 })
    }

    @Test
    fun `同一设备内 ID 按序号递增`() {
        val a = globalSyncId(7, 1)
        val b = globalSyncId(7, 2)
        assertTrue(b > a)
    }
}
