package com.zzzcy.shiwu.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * 照片位图内存缓存：按「文件路径 + 目标尺寸」缓存采样解码结果。
 * 首页网格滑出屏幕再滑回时直接命中，避免重复解码造成的卡顿；
 * 容量约为可用堆的 1/8（8MB ~ 64MB），由系统按字节数自动淘汰。
 */
object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun maxBytes(): Int {
        val heap = Runtime.getRuntime().maxMemory()
        return (heap / 8)
            .coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024)
            .toInt()
    }

    fun get(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        cache.get(key(path, reqWidth, reqHeight))

    fun put(path: String, reqWidth: Int, reqHeight: Int, bitmap: Bitmap) {
        cache.put(key(path, reqWidth, reqHeight), bitmap)
    }

    private fun key(path: String, reqWidth: Int, reqHeight: Int): String =
        "$path#$reqWidth#$reqHeight"
}
