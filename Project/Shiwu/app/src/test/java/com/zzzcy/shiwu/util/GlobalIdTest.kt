package com.zzzcy.shiwu.util

import com.zzzcy.shiwu.data.SYNC_SEQ_MASK
import com.zzzcy.shiwu.data.calibrateSequence
import com.zzzcy.shiwu.data.globalSyncId
import com.zzzcy.shiwu.data.isGlobalSyncId
import com.zzzcy.shiwu.data.syncSeqOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `deviceId 0 与 23 位最大设备号边界`() {
        // deviceId = 0 时退化为纯序号（仍在正数范围），但运行时应保证 deviceId >= 1
        assertEquals(1L, globalSyncId(0, 1))
        assertEquals(123456789L, globalSyncId(0, 123456789))

        // 23 位全 1 + 40 位全 1 恰好是 Long.MAX_VALUE，不溢出为负数
        val maxDeviceId = 0x7FFFFFL
        val maxSeq = SYNC_SEQ_MASK
        val maxId = globalSyncId(maxDeviceId, maxSeq)
        assertEquals(Long.MAX_VALUE, maxId)
        assertTrue(maxId > 0)
        assertTrue(globalSyncId(maxDeviceId, maxSeq - 1) < maxId)
    }

    @Test
    fun `全局 ID 低 40 位可还原序号`() {
        assertEquals(123L, syncSeqOf(globalSyncId(42, 123)))
        assertEquals(1L, syncSeqOf(globalSyncId(0x7FFFFF, 1)))
        assertEquals(SYNC_SEQ_MASK, syncSeqOf(globalSyncId(0x7FFFFF, SYNC_SEQ_MASK)))
        assertEquals(0L, syncSeqOf(globalSyncId(42, 0)))
    }

    @Test
    fun `区分全局 ID 与存量本地自增 ID`() {
        assertTrue(isGlobalSyncId(globalSyncId(1, 1)))
        assertTrue(isGlobalSyncId(globalSyncId(0x7FFFFF, 1)))
        assertFalse(isGlobalSyncId(1))
        assertFalse(isGlobalSyncId(SYNC_SEQ_MASK))
    }

    @Test
    fun `序列校准取持久化值与 DB 最大 id 低 40 位的较大者`() {
        // DB 中存在更大的序号：校准后必须跳过它，避免新 ID 撞车
        assertEquals(100L, calibrateSequence(3L, globalSyncId(777, 100)))
        // DB 最大 id 的序号更小：保留持久化值
        assertEquals(500L, calibrateSequence(500L, globalSyncId(777, 4)))
        // 空表/无记录：保留持久化值（负值归零）
        assertEquals(500L, calibrateSequence(500L, null))
        assertEquals(0L, calibrateSequence(-5L, null))
        // 旧格式本地 id（高 23 位为 0）同样按低 40 位校准
        assertEquals(42L, calibrateSequence(3L, 42L))
    }
}
