package com.zzzcy.shiwu.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一次同步的完整数据快照（含墓碑；照片以文件名参与同步） */
data class SyncData(
    val lists: List<InventoryList>,
    val items: List<Item>,
    val wishes: List<Wish>,
    val categories: List<Category>
)

/** 同步结果报告 */
data class SyncReport(
    val added: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val notes: List<String> = emptyList()
) {
    fun summary(): String = buildString {
        append("新增 $added · 更新 $updated · 删除 $deleted")
        if (conflicts > 0) append(" · 冲突副本 $conflicts")
    }
}

/** 全局唯一 ID：高 23 位设备号 + 低 40 位本地序号（Long 范围内正数） */
fun globalSyncId(deviceId: Long, seq: Long): Long = (deviceId shl 40) or seq

fun SyncData.maxRev(): Long =
    (lists.maxOfOrNull { it.rev } ?: 0L)
        .coerceAtLeast(items.maxOfOrNull { it.rev } ?: 0L)
        .coerceAtLeast(wishes.maxOfOrNull { it.rev } ?: 0L)

// ---------- 序列化（照片映射为文件名） ----------

private fun itemToSyncJson(item: Item): JSONObject =
    item.copy(photos = item.photos.map { File(it).name }).toJson()

private fun itemFromSyncJson(o: JSONObject, localPhotosDir: File): Item {
    val item = itemFromJson(o)
    return item.copy(photos = item.photos.map { File(localPhotosDir, it).absolutePath })
}

fun SyncData.toSnapshotJson(): String = JSONObject().apply {
    put("app", "shiwu")
    put("type", "snapshot")
    put("gRev", maxRev())
    put("lists", JSONArray(lists.map { it.toJson() }))
    put("items", JSONArray(items.map { itemToSyncJson(it) }))
    put("wishes", JSONArray(wishes.map { it.toJson() }))
    put("categories", JSONArray(categories.map { it.toJson() }))
}.toString(1)

fun snapshotFromJson(text: String, localPhotosDir: File): SyncData {
    val root = JSONObject(text)
    val lists = root.optJSONArray("lists")?.let { arr ->
        (0 until arr.length()).map { listFromJson(arr.getJSONObject(it)) }
    } ?: emptyList()
    val items = root.optJSONArray("items")?.let { arr ->
        (0 until arr.length()).map { itemFromSyncJson(arr.getJSONObject(it), localPhotosDir) }
    } ?: emptyList()
    val wishes = root.optJSONArray("wishes")?.let { arr ->
        (0 until arr.length()).map { wishFromJson(arr.getJSONObject(it)) }
    } ?: emptyList()
    val categories = root.optJSONArray("categories")?.let { arr ->
        (0 until arr.length()).map { categoryFromJson(arr.getJSONObject(it)) }
    } ?: emptyList()
    return SyncData(lists, items, wishes, categories)
}

/** 设备注册表 JSON（id / 名称 / 最后同步时间） */
fun devicesToJson(devices: List<Triple<String, String, Long>>): String = JSONObject().apply {
    put("devices", JSONArray(devices.map { (id, name, last) ->
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("lastSync", last)
        }
    }))
}.toString(1)

fun devicesFromJson(text: String): List<Triple<String, String, Long>> {
    val root = JSONObject(text)
    val arr = root.optJSONArray("devices") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        Triple(o.optString("id", ""), o.optString("name", ""), o.optLong("lastSync", 0L))
    }
}
